/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.sms;

import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.MmsSendJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceGroupUpdateJob;
import org.thoughtcrime.securesms.jobs.PushGroupSendJob;
import org.thoughtcrime.securesms.jobs.PushMediaSendJob;
import org.thoughtcrime.securesms.jobs.PushTextSendJob;
import org.thoughtcrime.securesms.jobs.SmsSendJob;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.loki.BackgroundMessage;
import org.thoughtcrime.securesms.loki.FriendRequestHandler;
import org.thoughtcrime.securesms.loki.GeneralUtilitiesKt;
import org.thoughtcrime.securesms.loki.MultiDeviceOpenGroupUpdateJob;
import org.thoughtcrime.securesms.loki.MultiDeviceUtilities;
import org.thoughtcrime.securesms.loki.PushBackgroundMessageSendJob;
import org.thoughtcrime.securesms.loki.PushMessageSyncSendJob;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.push.AccountManagerFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.loki.api.LokiDeviceLinkUtilities;
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus;
import org.whispersystems.signalservice.loki.utilities.PromiseUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kotlin.Unit;

public class MessageSender {

  private static final String TAG = MessageSender.class.getSimpleName();

  private enum MessageType { TEXT, MEDIA }

  public static void syncAllContacts(Context context, Address recipient) {
    ApplicationContext.getInstance(context).getJobManager().add(new MultiDeviceContactUpdateJob(context, recipient, true));
  }

  public static void syncAllGroups(Context context) {
    ApplicationContext.getInstance(context).getJobManager().add(new MultiDeviceGroupUpdateJob());
  }

  public static void syncAllOpenGroups(Context context) {
    ApplicationContext.getInstance(context).getJobManager().add(new MultiDeviceOpenGroupUpdateJob());
  }

  /**
   * Send a contact sync message to all our devices telling them that we want to sync `contact`
   */
  public static void syncContact(Context context, Address contact) {
    // Don't bother sending a contact sync message if it's one of our devices that we want to sync across
    MultiDeviceUtilities.isOneOfOurDevices(context, contact).success(isOneOfOurDevice -> {
      if (!isOneOfOurDevice) {
        ApplicationContext.getInstance(context).getJobManager().add(new MultiDeviceContactUpdateJob(context, contact));
      }
      return Unit.INSTANCE;
    });
  }

  public static void sendBackgroundMessageToAllDevices(Context context, String contactHexEncodedPublicKey) {
    // Send the background message to the original pubkey
    sendBackgroundMessage(context, contactHexEncodedPublicKey);

    // Go through the other devices and only send background messages if we're friends or we have received friend request
    LokiDeviceLinkUtilities.INSTANCE.getAllLinkedDeviceHexEncodedPublicKeys(contactHexEncodedPublicKey).success(devices -> {
      Util.runOnMain(() -> {
        for (String device : devices) {
          // Don't send message to the device we already have sent to
          if (device.equals(contactHexEncodedPublicKey)) { continue; }
          Recipient recipient = Recipient.from(context, Address.fromSerialized(device), false);
          long threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(recipient);
          if (threadID < 0) { continue; }
          LokiThreadFriendRequestStatus friendRequestStatus = DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadID);
          if (friendRequestStatus == LokiThreadFriendRequestStatus.FRIENDS || friendRequestStatus == LokiThreadFriendRequestStatus.REQUEST_RECEIVED) {
            sendBackgroundMessage(context, device);
          } else if (friendRequestStatus == LokiThreadFriendRequestStatus.NONE || friendRequestStatus == LokiThreadFriendRequestStatus.REQUEST_EXPIRED) {
            sendBackgroundFriendRequest(context, device, "Please accept to enable messages to be synced across devices");
          }
        }
      });
      return Unit.INSTANCE;
    });
  }

  // region Background message

  // We don't call the message sender here directly and instead we just opt to create a specific job for the send
  // This is because calling message sender directly would cause the application to freeze in some cases as it was blocking the thread when waiting for a response from the send
  public static void sendBackgroundMessage(Context context, String contactHexEncodedPublicKey) {
    ApplicationContext.getInstance(context).getJobManager().add(new PushBackgroundMessageSendJob(BackgroundMessage.create(contactHexEncodedPublicKey)));
  }

  public static void sendBackgroundFriendRequest(Context context, String contactHexEncodedPublicKey, String messageBody) {
    ApplicationContext.getInstance(context).getJobManager().add(new PushBackgroundMessageSendJob(BackgroundMessage.createFriendRequest(contactHexEncodedPublicKey, messageBody)));
  }

  public static void sendUnpairRequest(Context context, String contactHexEncodedPublicKey) {
    ApplicationContext.getInstance(context).getJobManager().add(new PushBackgroundMessageSendJob(BackgroundMessage.createUnpairingRequest(contactHexEncodedPublicKey)));
  }

  public static void sendRestoreSessionMessage(Context context, String contactHexEncodedPublicKey) {
    ApplicationContext.getInstance(context).getJobManager().add(new PushBackgroundMessageSendJob(BackgroundMessage.createSessionRestore(contactHexEncodedPublicKey)));
  }

  public static void sendBackgroundSessionRequest(Context context, String contactHexEncodedPublicKey) {
    ApplicationContext.getInstance(context).getJobManager().add(new PushBackgroundMessageSendJob(BackgroundMessage.createSessionRequest(contactHexEncodedPublicKey)));
  }
  // endregion

  public static long send(final Context context,
                          final OutgoingTextMessage message,
                          final long threadId,
                          final boolean forceSms,
                          final SmsDatabase.InsertListener insertListener)
  {
    SmsDatabase database    = DatabaseFactory.getSmsDatabase(context);
    Recipient   recipient   = message.getRecipient();
    boolean     keyExchange = message.isKeyExchange();

    long allocatedThreadId;

    if (threadId == -1) {
      allocatedThreadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
    } else {
      allocatedThreadId = threadId;
    }

    long messageId = database.insertMessageOutbox(allocatedThreadId, message, forceSms, System.currentTimeMillis(), insertListener);

    // Loki - Set the message's friend request status as soon as it has hit the database
    if (message.isFriendRequest) {
      FriendRequestHandler.updateFriendRequestState(context, FriendRequestHandler.ActionType.Sending, messageId, allocatedThreadId);
    }

    sendTextMessage(context, recipient, forceSms, keyExchange, messageId);

    return allocatedThreadId;
  }

  public static long send(final Context context,
                          final OutgoingMediaMessage message,
                          final long threadId,
                          final boolean forceSms,
                          final SmsDatabase.InsertListener insertListener)
  {
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
    MmsDatabase    database       = DatabaseFactory.getMmsDatabase(context);

    long allocatedThreadId;

    if (threadId == -1) {
      allocatedThreadId = threadDatabase.getThreadIdFor(message.getRecipient(), message.getDistributionType());
    } else {
      allocatedThreadId = threadId;
    }

    Recipient recipient = message.getRecipient();

    // Loki - Turn into a GIF message if possible
    if (message.getLinkPreviews().isEmpty() && message.getAttachments().isEmpty() && LinkPreviewUtil.isWhitelistedMediaUrl(message.getBody())) {
      new LinkPreviewRepository(context).fetchGIF(context, message.getBody(), attachmentOrNull -> Util.runOnMain(() -> {
        Attachment attachment = attachmentOrNull.orNull();
        try {
          if (attachment != null) { message.getAttachments().add(attachment); }
          long messageID = database.insertMessageOutbox(message, allocatedThreadId, forceSms, insertListener);
          // Loki - Set the message's friend request status as soon as it has hit the database
          if (message.isFriendRequest && !recipient.getAddress().isGroup() && !message.isGroup()) {
            FriendRequestHandler.updateFriendRequestState(context, FriendRequestHandler.ActionType.Sending, messageID, allocatedThreadId);
          }
          sendMediaMessage(context, recipient, forceSms, messageID, message.getExpiresIn());
        } catch (Exception e) {
          Log.w(TAG, e);
          // TODO: Handle
        }
      }));
    } else {
      try {
        long messageID = database.insertMessageOutbox(message, allocatedThreadId, forceSms, insertListener);
        // Loki - Set the message's friend request status as soon as it has hit the database
        if (message.isFriendRequest && !recipient.getAddress().isGroup() && !message.isGroup()) {
          FriendRequestHandler.updateFriendRequestState(context, FriendRequestHandler.ActionType.Sending, messageID, allocatedThreadId);
        }
        sendMediaMessage(context, recipient, forceSms, messageID, message.getExpiresIn());
      } catch (MmsException e) {
        Log.w(TAG, e);
        return threadId;
      }
    }

    return allocatedThreadId;
  }

  public static void sendSyncMessageToOurDevices(final Context context,
                                                 final long    messageID,
                                                 final long    timestamp,
                                                 final byte[]  message,
                                                 final int     ttl) {
    String ourPublicKey = TextSecurePreferences.getLocalNumber(context);
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    LokiDeviceLinkUtilities.INSTANCE.getAllLinkedDeviceHexEncodedPublicKeys(ourPublicKey).success(devices -> {
      Util.runOnMain(() -> {
        for (String device : devices) {
          // Don't send to ourselves
          if (device.equals(ourPublicKey)) { continue; }

          // Create a send job for our device
          Address address = Address.fromSerialized(device);
          jobManager.add(new PushMessageSyncSendJob(messageID, address, timestamp, message, ttl));
        }
      });
      return Unit.INSTANCE;
    });
  }

  public static void resendGroupMessage(Context context, MessageRecord messageRecord, Address filterAddress) {
    if (!messageRecord.isMms()) throw new AssertionError("Not Group");
    sendGroupPush(context, messageRecord.getRecipient(), messageRecord.getId(), filterAddress);
  }

  public static void resend(Context context, MessageRecord messageRecord) {
    long       messageId   = messageRecord.getId();
    boolean    forceSms    = messageRecord.isForcedSms();
    boolean    keyExchange = messageRecord.isKeyExchange();
    long       expiresIn   = messageRecord.getExpiresIn();
    Recipient  recipient   = messageRecord.getRecipient();

    if (messageRecord.isMms()) {
      sendMediaMessage(context, recipient, forceSms, messageId, expiresIn);
    } else {
      sendTextMessage(context, recipient, forceSms, keyExchange, messageId);
    }
  }

  private static void sendMediaMessage(Context context, Recipient recipient, boolean forceSms, long messageId, long expiresIn)
  {
    if (isLocalSelfSend(context, recipient, forceSms)) {
      sendLocalMediaSelf(context, messageId);
    } else if (isGroupPushSend(recipient)) {
      sendGroupPush(context, recipient, messageId, null);
    } else {
      sendMediaPush(context, recipient, messageId);
    }
  }

  private static void sendTextMessage(Context context, Recipient recipient,
                                      boolean forceSms, boolean keyExchange,
                                      long messageId)
  {
    if (isLocalSelfSend(context, recipient, forceSms)) {
      sendLocalTextSelf(context, messageId);
    } else {
      sendTextPush(context, recipient, messageId);
    }
  }

  private static void sendTextPush(Context context, Recipient recipient, long messageId) {
    sendMessagePush(context, MessageType.TEXT, recipient, messageId);
  }

  private static void sendMediaPush(Context context, Recipient recipient, long messageId) {
    sendMessagePush(context, MessageType.MEDIA, recipient, messageId);
  }

  private static void sendMessagePush(Context context, MessageType type, Recipient recipient, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();

    // Just send the message normally if it's a group message or we're sending to one of our devices
    String recipientHexEncodedPublicKey = recipient.getAddress().serialize();
    if (GeneralUtilitiesKt.isPublicChat(context, recipientHexEncodedPublicKey) || PromiseUtil.get(MultiDeviceUtilities.isOneOfOurDevices(context, recipient.getAddress()), false)) {
      if (type == MessageType.MEDIA) {
        PushMediaSendJob.enqueue(context, jobManager, messageId, recipient.getAddress(), false);
      } else {
        jobManager.add(new PushTextSendJob(messageId, recipient.getAddress()));
      }
      return;
    }

    // If we get here then we are sending a message to a device that is not ours
    boolean[] hasSentSyncMessage = { false };
    MultiDeviceUtilities.getAllDevicePublicKeysWithFriendStatus(context, recipientHexEncodedPublicKey).success(devices -> {
      int friendCount = MultiDeviceUtilities.getFriendCount(context, devices.keySet());
      Util.runOnMain(() -> {
        ArrayList<Job> jobs = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : devices.entrySet()) {
          String deviceHexEncodedPublicKey = entry.getKey();
          boolean isFriend = entry.getValue();

          Address address = Address.fromSerialized(deviceHexEncodedPublicKey);
          long messageIDToUse = recipientHexEncodedPublicKey.equals(deviceHexEncodedPublicKey) ? messageId : -1L;

          if (isFriend) {
            // Send a normal message if the user is friends with the recipient
            // We should also send a sync message if we haven't already sent one
            boolean shouldSendSyncMessage = !hasSentSyncMessage[0] && address.isPhone();
            if (type == MessageType.MEDIA) {
              jobs.add(new PushMediaSendJob(messageId, messageIDToUse, address, false, null, shouldSendSyncMessage));
            } else {
              jobs.add(new PushTextSendJob(messageId, messageIDToUse, address, shouldSendSyncMessage));
            }
            if (shouldSendSyncMessage) { hasSentSyncMessage[0] = true; }
          } else {
            // Send friend requests to non-friends. If the user is friends with any
            // of the devices then send out a default friend request message.
            boolean isFriendsWithAny = (friendCount > 0);
            String defaultFriendRequestMessage = isFriendsWithAny ? "Please accept to enable messages to be synced across devices" : null;
            if (type == MessageType.MEDIA) {
              jobs.add(new PushMediaSendJob(messageId, messageIDToUse, address, true, defaultFriendRequestMessage, false));
            } else {
              jobs.add(new PushTextSendJob(messageId, messageIDToUse, address, true, defaultFriendRequestMessage, false));
            }
          }
        }

        // Start the send
        if (type == MessageType.MEDIA) {
          PushMediaSendJob.enqueue(context, jobManager, (List<PushMediaSendJob>)(List)jobs);
        } else {
          // Schedule text send jobs
          jobManager.startChain(jobs).enqueue();
        }
      });
      return Unit.INSTANCE;
    });
  }

  private static void sendGroupPush(Context context, Recipient recipient, long messageId, Address filterAddress) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    PushGroupSendJob.enqueue(context, jobManager, messageId, recipient.getAddress(), filterAddress);
  }

  private static void sendSms(Context context, Recipient recipient, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new SmsSendJob(context, messageId, recipient.getName()));
  }

  private static void sendMms(Context context, long messageId) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    jobManager.add(new MmsSendJob(messageId));
  }

  private static boolean isPushTextSend(Context context, Recipient recipient, boolean keyExchange) {
    return true;
    // Loki - Original code
    // ========
//    if (!TextSecurePreferences.isPushRegistered(context)) {
//      return false;
//    }
//
//    if (keyExchange) {
//      return false;
//    }
//
//    return isPushDestination(context, recipient);
    // ========
  }

  private static boolean isPushMediaSend(Context context, Recipient recipient) {
    return true;
    // Loki - Original code
    // ========
//    if (!TextSecurePreferences.isPushRegistered(context)) {
//      return false;
//    }
//
//    if (recipient.isGroupRecipient()) {
//      return false;
//    }
//
//    return isPushDestination(context, recipient);
    // ========
  }

  private static boolean isGroupPushSend(Recipient recipient) {
    return recipient.getAddress().isGroup() &&
           !recipient.getAddress().isMmsGroup();
  }

  private static boolean isPushDestination(Context context, Recipient destination) {
    if (destination.resolve().getRegistered() == RecipientDatabase.RegisteredState.REGISTERED) {
      return true;
    } else if (destination.resolve().getRegistered() == RecipientDatabase.RegisteredState.NOT_REGISTERED) {
      return false;
    } else {
      try {
        SignalServiceAccountManager   accountManager = AccountManagerFactory.createManager(context);
        Optional<ContactTokenDetails> registeredUser = accountManager.getContact(destination.getAddress().serialize());

        if (!registeredUser.isPresent()) {
          DatabaseFactory.getRecipientDatabase(context).setRegistered(destination, RecipientDatabase.RegisteredState.NOT_REGISTERED);
          return false;
        } else {
          DatabaseFactory.getRecipientDatabase(context).setRegistered(destination, RecipientDatabase.RegisteredState.REGISTERED);
          return true;
        }
      } catch (IOException e1) {
        Log.w(TAG, e1);
        return false;
      }
    }
  }

  private static boolean isLocalSelfSend(@NonNull Context context, @NonNull Recipient recipient, boolean forceSms) {
    return recipient.isLocalNumber()                       &&
           !forceSms                                       &&
           TextSecurePreferences.isPushRegistered(context) &&
           !TextSecurePreferences.isMultiDevice(context);
  }

  private static void sendLocalMediaSelf(Context context, long messageId) {
    try {
      ExpiringMessageManager expirationManager  = ApplicationContext.getInstance(context).getExpiringMessageManager();
      AttachmentDatabase     attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context);
      MmsDatabase            mmsDatabase        = DatabaseFactory.getMmsDatabase(context);
      MmsSmsDatabase         mmsSmsDatabase     = DatabaseFactory.getMmsSmsDatabase(context);
      OutgoingMediaMessage   message            = mmsDatabase.getOutgoingMessage(messageId);
      SyncMessageId          syncId             = new SyncMessageId(Address.fromSerialized(TextSecurePreferences.getLocalNumber(context)), message.getSentTimeMillis());

      for (Attachment attachment : message.getAttachments()) {
        attachmentDatabase.markAttachmentUploaded(messageId, attachment);
      }

      mmsDatabase.markAsSent(messageId, true);
      mmsDatabase.markUnidentified(messageId, true);

      mmsSmsDatabase.incrementDeliveryReceiptCount(syncId, System.currentTimeMillis());
      mmsSmsDatabase.incrementReadReceiptCount(syncId, System.currentTimeMillis());

      if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
        mmsDatabase.markExpireStarted(messageId);
        expirationManager.scheduleDeletion(messageId, true, message.getExpiresIn());
      }
    } catch (NoSuchMessageException | MmsException e) {
      Log.w("Failed to update self-sent message.", e);
    }
  }

  private static void sendLocalTextSelf(Context context, long messageId) {
    try {
      ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
      SmsDatabase            smsDatabase       = DatabaseFactory.getSmsDatabase(context);
      MmsSmsDatabase         mmsSmsDatabase    = DatabaseFactory.getMmsSmsDatabase(context);
      SmsMessageRecord       message           = smsDatabase.getMessage(messageId);
      SyncMessageId          syncId            = new SyncMessageId(Address.fromSerialized(TextSecurePreferences.getLocalNumber(context)), message.getDateSent());

      smsDatabase.markAsSent(messageId, true);
      smsDatabase.markUnidentified(messageId, true);

      mmsSmsDatabase.incrementDeliveryReceiptCount(syncId, System.currentTimeMillis());
      mmsSmsDatabase.incrementReadReceiptCount(syncId, System.currentTimeMillis());

      if (message.getExpiresIn() > 0) {
        smsDatabase.markExpireStarted(messageId);
        expirationManager.scheduleDeletion(message.getId(), message.isMms(), message.getExpiresIn());
      }
    } catch (NoSuchMessageException e) {
      Log.w("Failed to update self-sent message.", e);
    }
  }
}
