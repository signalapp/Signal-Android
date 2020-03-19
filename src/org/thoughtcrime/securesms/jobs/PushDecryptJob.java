package org.thoughtcrime.securesms.jobs;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.TextUtils;
import android.util.Pair;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.libsignal.metadata.InvalidMetadataMessageException;
import org.signal.libsignal.metadata.InvalidMetadataVersionException;
import org.signal.libsignal.metadata.ProtocolDuplicateMessageException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyIdException;
import org.signal.libsignal.metadata.ProtocolInvalidMessageException;
import org.signal.libsignal.metadata.ProtocolInvalidVersionException;
import org.signal.libsignal.metadata.ProtocolLegacyMessageException;
import org.signal.libsignal.metadata.ProtocolNoSessionException;
import org.signal.libsignal.metadata.ProtocolUntrustedIdentityException;
import org.signal.libsignal.metadata.SelfSendException;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.attachments.PointerAttachment;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactModelMapper;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.crypto.storage.SignalProtocolStoreImpl;
import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase;
import org.thoughtcrime.securesms.database.MessagingDatabase;
import org.thoughtcrime.securesms.database.MessagingDatabase.InsertResult;
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.StickerDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupMessageProcessor;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.linkpreview.Link;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.loki.FriendRequestHandler;
import org.thoughtcrime.securesms.loki.LokiMessageDatabase;
import org.thoughtcrime.securesms.loki.LokiSessionResetImplementation;
import org.thoughtcrime.securesms.loki.LokiThreadDatabase;
import org.thoughtcrime.securesms.loki.MultiDeviceUtilities;
import org.thoughtcrime.securesms.loki.redesign.activities.HomeActivity;
import org.thoughtcrime.securesms.loki.redesign.messaging.LokiAPIUtilities;
import org.thoughtcrime.securesms.loki.redesign.messaging.LokiPreKeyBundleDatabase;
import org.thoughtcrime.securesms.loki.redesign.utilities.Broadcaster;
import org.thoughtcrime.securesms.loki.redesign.utilities.OpenGroupUtilities;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingExpirationUpdateMessage;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.QuoteModel;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.WebRtcCallService;
import org.thoughtcrime.securesms.sms.IncomingEncryptedMessage;
import org.thoughtcrime.securesms.sms.IncomingEndSessionMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingEndSessionMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.loki.LokiSessionResetProtocol;
import org.whispersystems.libsignal.loki.LokiSessionResetStatus;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Preview;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.loki.api.DeviceLink;
import org.whispersystems.signalservice.loki.api.DeviceLinkingSession;
import org.whispersystems.signalservice.loki.api.LokiAPI;
import org.whispersystems.signalservice.loki.api.LokiDeviceLinkUtilities;
import org.whispersystems.signalservice.loki.api.LokiFileServerAPI;
import org.whispersystems.signalservice.loki.api.LokiPublicChat;
import org.whispersystems.signalservice.loki.crypto.LokiServiceCipher;
import org.whispersystems.signalservice.loki.messaging.LokiMessageFriendRequestStatus;
import org.whispersystems.signalservice.loki.messaging.LokiServiceMessage;
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus;
import org.whispersystems.signalservice.loki.utilities.PromiseUtil;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import kotlin.Unit;
import network.loki.messenger.R;
import nl.komponents.kovenant.Promise;

public class PushDecryptJob extends BaseJob implements InjectableType {

  public static final String KEY = "PushDecryptJob";

  public static final String TAG = PushDecryptJob.class.getSimpleName();

  private static final String KEY_MESSAGE_ID     = "message_id";
  private static final String KEY_SMS_MESSAGE_ID = "sms_message_id";

  private long messageId;
  private long smsMessageId;

  @Inject SignalServiceMessageSender messageSender;
  private Address author;

  public PushDecryptJob(Context context) {
    this(context, -1);
  }

  public PushDecryptJob(Context context, long pushMessageId) {
    this(context, pushMessageId, -1);
  }

  public PushDecryptJob(Context context, long pushMessageId, long smsMessageId) {
    this(new Job.Parameters.Builder()
                           .setQueue("__PUSH_DECRYPT_JOB__")
                           .setMaxAttempts(10)
                           .build(),
         pushMessageId,
         smsMessageId);
    setContext(context);
  }

  private PushDecryptJob(@NonNull Job.Parameters parameters, long pushMessageId, long smsMessageId) {
    super(parameters);

    this.messageId    = pushMessageId;
    this.smsMessageId = smsMessageId;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId)
                             .putLong(KEY_SMS_MESSAGE_ID, smsMessageId)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws NoSuchMessageException {
    synchronized (PushReceivedJob.RECEIVE_LOCK) {
      if (needsMigration()) {
        Log.w(TAG, "Skipping, waiting for migration...");
        postMigrationNotification();
        return;
      }

      PushDatabase          database             = DatabaseFactory.getPushDatabase(context);
      SignalServiceEnvelope envelope             = database.get(messageId);
      Optional<Long>        optionalSmsMessageId = smsMessageId > 0 ? Optional.of(smsMessageId) : Optional.absent();

      handleMessage(envelope, optionalSmsMessageId);
      database.delete(messageId);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {

  }

  public void processMessage(@NonNull SignalServiceEnvelope envelope) {
    synchronized (PushReceivedJob.RECEIVE_LOCK) {
      if (needsMigration()) {
        Log.w(TAG, "Skipping and storing envelope, waiting for migration...");
        DatabaseFactory.getPushDatabase(context).insert(envelope);
        postMigrationNotification();
        return;
      }

      handleMessage(envelope, Optional.absent());
    }
  }

  private boolean needsMigration() {
    return !IdentityKeyUtil.hasIdentityKey(context) || TextSecurePreferences.getNeedsSqlCipherMigration(context);
  }

  private void postMigrationNotification() {
    NotificationManagerCompat.from(context).notify(494949,
                                                   new NotificationCompat.Builder(context, NotificationChannels.getMessagesChannel(context))
                                                                         .setSmallIcon(R.drawable.ic_notification)
                                                                         .setPriority(NotificationCompat.PRIORITY_HIGH)
                                                                         .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                                                                         .setContentTitle(context.getString(R.string.PushDecryptJob_new_locked_message))
                                                                         .setContentText(context.getString(R.string.PushDecryptJob_unlock_to_view_pending_messages))
                                                                         .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, HomeActivity.class), 0))
                                                                         .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE)
                                                                         .build());

  }

  private void handleMessage(@NonNull SignalServiceEnvelope envelope, @NonNull Optional<Long> smsMessageId) {
    try {
      GroupDatabase            groupDatabase            = DatabaseFactory.getGroupDatabase(context);
      SignalProtocolStore      axolotlStore             = new SignalProtocolStoreImpl(context);
      LokiThreadDatabase       lokiThreadDatabase       = DatabaseFactory.getLokiThreadDatabase(context);
      LokiSessionResetProtocol lokiSessionResetProtocol = new LokiSessionResetImplementation(context);
      SignalServiceAddress     localAddress             = new SignalServiceAddress(TextSecurePreferences.getLocalNumber(context));
      LokiServiceCipher        cipher                   = new LokiServiceCipher(localAddress, axolotlStore, lokiSessionResetProtocol, UnidentifiedAccessUtil.getCertificateValidator());

      SignalServiceContent content = cipher.decrypt(envelope);

      // Loki - Ignore any friend requests that we got before restoration
      if (content.isFriendRequest() && content.getTimestamp() < TextSecurePreferences.getRestorationTime(context)) {
        Log.d("Loki", "Ignoring friend request received before restoration.");
        return;
      }

      if (shouldIgnore(content)) {
        Log.i(TAG, "Ignoring message.");
        return;
      }

      // Loki - Handle friend request acceptance if needed
      if (!content.isFriendRequest() && !isGroupChatMessage(content)) {
        becomeFriendsWithContactIfNeeded(content.getSender(), true, false);
      }

      // Loki - Handle session request if needed
      handleSessionRequestIfNeeded(content);

      // Loki - Store pre key bundle if needed
      if (!content.getDeviceLink().isPresent()) {
        storePreKeyBundleIfNeeded(content);
      }

      if (content.lokiServiceMessage.isPresent()) {
        LokiServiceMessage lokiMessage = content.lokiServiceMessage.get();
        if (lokiMessage.getAddressMessage() != null) {
          // TODO: Loki - Handle address message
        }
      }

      // Loki - Store the sender display name if needed
      Optional<String> rawSenderDisplayName = content.senderDisplayName;
      if (rawSenderDisplayName.isPresent() && rawSenderDisplayName.get().length() > 0) {
        // If we got a name from our master device then set our display name to match
        String ourMasterDevice = TextSecurePreferences.getMasterHexEncodedPublicKey(context);
        if (ourMasterDevice != null && content.getSender().equals(ourMasterDevice)) {
          TextSecurePreferences.setProfileName(context, rawSenderDisplayName.get());
        }

        // If we receive a message from our device then don't set the display name in the database (as we probably have a alias set for them)
        MultiDeviceUtilities.isOneOfOurDevices(context, Address.fromSerialized(content.getSender())).success( isOneOfOurDevices -> {
          if (!isOneOfOurDevices) { setDisplayName(content.getSender(), rawSenderDisplayName.get()); }
          return Unit.INSTANCE;
        });
      }

      if (content.getDeviceLink().isPresent()) {
        handleDeviceLinkMessage(content.getDeviceLink().get(), content);
      } else if (content.getDataMessage().isPresent()) {
        SignalServiceDataMessage message        = content.getDataMessage().get();
        boolean                  isMediaMessage = message.getAttachments().isPresent() || message.getQuote().isPresent() || message.getSharedContacts().isPresent() || message.getPreviews().isPresent() || message.getSticker().isPresent();

        if (!content.isFriendRequest() && message.isUnlinkingRequest()) {
          // Make sure we got the request from our master device
          String ourMasterDevice = TextSecurePreferences.getMasterHexEncodedPublicKey(context);
          if (ourMasterDevice != null && ourMasterDevice.equals(content.getSender())) {
            TextSecurePreferences.setDatabaseResetFromUnpair(context, true);
            MultiDeviceUtilities.checkIsRevokedSlaveDevice(context);
          }
        } else {
          // Loki - Don't process session restore message any further
          if (message.isSessionRestorationRequest() || message.isSessionRequest()) { return; }

          if (message.isEndSession()) handleEndSessionMessage(content, smsMessageId);
          else if (message.isGroupUpdate()) handleGroupMessage(content, message, smsMessageId);
          else if (message.isExpirationUpdate())
            handleExpirationUpdate(content, message, smsMessageId);
          else if (isMediaMessage)
            handleMediaMessage(content, message, smsMessageId, Optional.absent());
          else if (message.getBody().isPresent())
            handleTextMessage(content, message, smsMessageId, Optional.absent());

          if (message.getGroupInfo().isPresent() && groupDatabase.isUnknownGroup(GroupUtil.getEncodedId(message.getGroupInfo().get()))) {
            handleUnknownGroupMessage(content, message.getGroupInfo().get());
          }

          if (message.getProfileKey().isPresent() && message.getProfileKey().get().length == 32) {
            handleProfileKey(content, message);
          }

          // Loki - This doesn't get invoked for group chats
          if (content.isNeedsReceipt()) {
            handleNeedsDeliveryReceipt(content, message);
          }

          // If we received a friend request, but we were already friends with the user, reset the session
          if (content.isFriendRequest() && !message.isGroupMessage()) {
            Recipient sender = Recipient.from(context, Address.fromSerialized(content.getSender()), false);
            ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
            long threadID = threadDatabase.getThreadIdIfExistsFor(sender);
            if (lokiThreadDatabase.getFriendRequestStatus(threadID) == LokiThreadFriendRequestStatus.FRIENDS) {
              resetSession(content.getSender());
              // Let our other devices know that we have reset the session
              MessageSender.syncContact(context, sender.getAddress());
            }
          }

          // Loki - Handle friend request logic if needed
          updateFriendRequestStatusIfNeeded(content, message);
        }
      } else if (content.getSyncMessage().isPresent()) {
        TextSecurePreferences.setMultiDevice(context, true);

        SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();

        if      (syncMessage.getSent().isPresent())                  handleSynchronizeSentMessage(content, syncMessage.getSent().get());
        else if (syncMessage.getRequest().isPresent())               handleSynchronizeRequestMessage(syncMessage.getRequest().get());
        else if (syncMessage.getRead().isPresent())                  handleSynchronizeReadMessage(syncMessage.getRead().get(), content.getTimestamp());
        else if (syncMessage.getVerified().isPresent())              handleSynchronizeVerifiedMessage(syncMessage.getVerified().get());
        else if (syncMessage.getStickerPackOperations().isPresent()) handleSynchronizeStickerPackOperation(syncMessage.getStickerPackOperations().get());
        else if (syncMessage.getContacts().isPresent())              handleContactSyncMessage(syncMessage.getContacts().get());
        else if (syncMessage.getGroups().isPresent())                handleGroupSyncMessage(content, syncMessage.getGroups().get());
        else if (syncMessage.getOpenGroups().isPresent())            handleOpenGroupSyncMessage(syncMessage.getOpenGroups().get());
        else                                                         Log.w(TAG, "Contains no known sync types...");
      } else if (content.getCallMessage().isPresent()) {
        Log.i(TAG, "Got call message...");
        SignalServiceCallMessage message = content.getCallMessage().get();

        if      (message.getOfferMessage().isPresent())      handleCallOfferMessage(content, message.getOfferMessage().get(), smsMessageId);
        else if (message.getAnswerMessage().isPresent())     handleCallAnswerMessage(content, message.getAnswerMessage().get());
        else if (message.getIceUpdateMessages().isPresent()) handleCallIceUpdateMessage(content, message.getIceUpdateMessages().get());
        else if (message.getHangupMessage().isPresent())     handleCallHangupMessage(content, message.getHangupMessage().get(), smsMessageId);
        else if (message.getBusyMessage().isPresent())       handleCallBusyMessage(content, message.getBusyMessage().get());
      } else if (content.getReceiptMessage().isPresent()) {
        SignalServiceReceiptMessage message = content.getReceiptMessage().get();

        if      (message.isReadReceipt())     handleReadReceipt(content, message);
        else if (message.isDeliveryReceipt()) handleDeliveryReceipt(content, message);
      } else if (content.getTypingMessage().isPresent()) {
        handleTypingMessage(content, content.getTypingMessage().get());
      } else {
        Log.w(TAG, "Got unrecognized message...");
      }

      resetRecipientToPush(Recipient.from(context, Address.fromSerialized(content.getSender()), false));

      if (envelope.isPreKeySignalMessage()) {
        ApplicationContext.getInstance(context).getJobManager().add(new RefreshPreKeysJob());
      }
    } catch (ProtocolInvalidVersionException e) {
      Log.w(TAG, e);
      handleInvalidVersionMessage(e.getSender(), e.getSenderDevice(), envelope.getTimestamp(), smsMessageId);
    } catch (ProtocolInvalidMessageException e) {
      Log.w(TAG, e);
      handleCorruptMessage(e.getSender(), e.getSenderDevice(), envelope.getTimestamp(), smsMessageId);
    } catch (ProtocolInvalidKeyIdException | ProtocolInvalidKeyException | ProtocolUntrustedIdentityException e) {
      Log.w(TAG, e);
      handleCorruptMessage(e.getSender(), e.getSenderDevice(), envelope.getTimestamp(), smsMessageId);
    } catch (StorageFailedException e) {
      Log.w(TAG, e);
      handleCorruptMessage(e.getSender(), e.getSenderDevice(), envelope.getTimestamp(), smsMessageId);
    } catch (ProtocolNoSessionException e) {
      Log.w(TAG, e);
      handleNoSessionMessage(e.getSender(), e.getSenderDevice(), envelope.getTimestamp(), smsMessageId);
    } catch (ProtocolLegacyMessageException e) {
      Log.w(TAG, e);
      handleLegacyMessage(e.getSender(), e.getSenderDevice(), envelope.getTimestamp(), smsMessageId);
    } catch (ProtocolDuplicateMessageException e) {
      Log.w(TAG, e);
      handleDuplicateMessage(e.getSender(), e.getSenderDevice(), envelope.getTimestamp(), smsMessageId);
    } catch (InvalidMetadataVersionException | InvalidMetadataMessageException e) {
      Log.w(TAG, e);
    } catch (SelfSendException e) {
      Log.i(TAG, "Dropping UD message from self.");
    }
  }

  private void handleCallOfferMessage(@NonNull SignalServiceContent content,
                                      @NonNull OfferMessage message,
                                      @NonNull Optional<Long> smsMessageId)
  {
    Log.w(TAG, "handleCallOfferMessage...");

    if (smsMessageId.isPresent()) {
      SmsDatabase database = DatabaseFactory.getSmsDatabase(context);
      database.markAsMissedCall(smsMessageId.get());
    } else {
      Intent intent = new Intent(context, WebRtcCallService.class);
      intent.setAction(WebRtcCallService.ACTION_INCOMING_CALL);
      intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
      intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.fromSerialized(content.getSender()));
      intent.putExtra(WebRtcCallService.EXTRA_REMOTE_DESCRIPTION, message.getDescription());
      intent.putExtra(WebRtcCallService.EXTRA_TIMESTAMP, content.getTimestamp());

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
      else                                                context.startService(intent);
    }
  }

  private void handleCallAnswerMessage(@NonNull SignalServiceContent content,
                                       @NonNull AnswerMessage message)
  {
    Log.i(TAG, "handleCallAnswerMessage...");
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(WebRtcCallService.ACTION_RESPONSE_MESSAGE);
    intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
    intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.fromSerialized(content.getSender()));
    intent.putExtra(WebRtcCallService.EXTRA_REMOTE_DESCRIPTION, message.getDescription());

    context.startService(intent);
  }

  private void handleCallIceUpdateMessage(@NonNull SignalServiceContent content,
                                          @NonNull List<IceUpdateMessage> messages)
  {
    Log.w(TAG, "handleCallIceUpdateMessage... " + messages.size());
    for (IceUpdateMessage message : messages) {
      Intent intent = new Intent(context, WebRtcCallService.class);
      intent.setAction(WebRtcCallService.ACTION_ICE_MESSAGE);
      intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
      intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.fromSerialized(content.getSender()));
      intent.putExtra(WebRtcCallService.EXTRA_ICE_SDP, message.getSdp());
      intent.putExtra(WebRtcCallService.EXTRA_ICE_SDP_MID, message.getSdpMid());
      intent.putExtra(WebRtcCallService.EXTRA_ICE_SDP_LINE_INDEX, message.getSdpMLineIndex());

      context.startService(intent);
    }
  }

  private void handleCallHangupMessage(@NonNull SignalServiceContent content,
                                       @NonNull HangupMessage message,
                                       @NonNull Optional<Long> smsMessageId)
  {
    Log.i(TAG, "handleCallHangupMessage");
    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).markAsMissedCall(smsMessageId.get());
    } else {
      Intent intent = new Intent(context, WebRtcCallService.class);
      intent.setAction(WebRtcCallService.ACTION_REMOTE_HANGUP);
      intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
      intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.fromSerialized(content.getSender()));

      context.startService(intent);
    }
  }

  private void handleCallBusyMessage(@NonNull SignalServiceContent content,
                                     @NonNull BusyMessage message)
  {
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(WebRtcCallService.ACTION_REMOTE_BUSY);
    intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
    intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.fromSerialized(content.getSender()));

    context.startService(intent);
  }

  private void handleEndSessionMessage(@NonNull SignalServiceContent content,
                                       @NonNull Optional<Long>       smsMessageId)
  {
    SmsDatabase         smsDatabase         = DatabaseFactory.getSmsDatabase(context);
    IncomingTextMessage incomingTextMessage = new IncomingTextMessage(Address.fromSerialized(content.getSender()),
                                                                      content.getSenderDevice(),
                                                                      content.getTimestamp(),
                                                                      "", Optional.absent(), 0,
                                                                      content.isNeedsReceipt());

    Long threadId;

    if (!smsMessageId.isPresent()) {
      IncomingEndSessionMessage incomingEndSessionMessage = new IncomingEndSessionMessage(incomingTextMessage);
      Optional<InsertResult>    insertResult              = smsDatabase.insertMessageInbox(incomingEndSessionMessage);

      if (insertResult.isPresent()) threadId = insertResult.get().getThreadId();
      else                          threadId = null;
    } else {
      smsDatabase.markAsEndSession(smsMessageId.get());
      threadId = smsDatabase.getThreadIdForMessage(smsMessageId.get());
    }

    if (threadId != null) {
      resetSession(content.getSender());
      MessageNotifier.updateNotification(context, threadId);
    }
  }

  private void resetSession(String hexEncodedPublicKey) {
      TextSecureSessionStore sessionStore = new TextSecureSessionStore(context);
      LokiThreadDatabase lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(context);

      Log.d("Loki", "Received a session reset request from: " + hexEncodedPublicKey + "; archiving the session.");

      sessionStore.archiveAllSessions(hexEncodedPublicKey);
      lokiThreadDatabase.setSessionResetStatus(hexEncodedPublicKey, LokiSessionResetStatus.REQUEST_RECEIVED);

      Log.d("Loki", "Sending a ping back to " + hexEncodedPublicKey + ".");
      MessageSender.sendBackgroundMessage(context, hexEncodedPublicKey);

      SecurityEvent.broadcastSecurityUpdateEvent(context);
  }

  private long handleSynchronizeSentEndSessionMessage(@NonNull SentTranscriptMessage message)
  {
    SmsDatabase               database                  = DatabaseFactory.getSmsDatabase(context);
    Recipient                 recipient                 = getSyncMessageDestination(message);
    OutgoingTextMessage       outgoingTextMessage       = new OutgoingTextMessage(recipient, "", -1);
    OutgoingEndSessionMessage outgoingEndSessionMessage = new OutgoingEndSessionMessage(outgoingTextMessage);

    long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);

    if (!recipient.isGroupRecipient()) {
      // TODO: Handle session reset on sync messages
      /*
      SessionStore sessionStore = new TextSecureSessionStore(context);
      sessionStore.deleteAllSessions(recipient.getAddress().toPhoneString());
       */

      SecurityEvent.broadcastSecurityUpdateEvent(context);

      long messageId = database.insertMessageOutbox(threadId, outgoingEndSessionMessage,
                                                    false, message.getTimestamp(),
                                                    null);
      database.markAsSent(messageId, true);
    }

    return threadId;
  }

  private void handleGroupMessage(@NonNull SignalServiceContent content,
                                  @NonNull SignalServiceDataMessage message,
                                  @NonNull Optional<Long> smsMessageId)
      throws StorageFailedException
  {
    GroupMessageProcessor.process(context, content, message, false);

    if (message.getExpiresInSeconds() != 0 && message.getExpiresInSeconds() != getRecipientForMessage(content, message).getExpireMessages()) {
      handleExpirationUpdate(content, message, Optional.absent());
    }

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }
  }

  private void handleUnknownGroupMessage(@NonNull SignalServiceContent content,
                                         @NonNull SignalServiceGroup group)
  {
    if (group.getGroupType() == SignalServiceGroup.GroupType.SIGNAL) {
      ApplicationContext.getInstance(context)
              .getJobManager()
              .add(new RequestGroupInfoJob(content.getSender(), group.getGroupId()));
    }
  }

  private void handleExpirationUpdate(@NonNull SignalServiceContent content,
                                      @NonNull SignalServiceDataMessage message,
                                      @NonNull Optional<Long> smsMessageId)
      throws StorageFailedException
  {
    try {
      MmsDatabase          database     = DatabaseFactory.getMmsDatabase(context);
      Recipient            recipient    = getRecipientForMessage(content, message);
      IncomingMediaMessage mediaMessage = new IncomingMediaMessage(Address.fromSerialized(content.getSender()),
                                                                   message.getTimestamp(), -1,
                                                                   message.getExpiresInSeconds() * 1000L, true,
                                                                   content.isNeedsReceipt(),
                                                                   Optional.absent(),
                                                                   message.getGroupInfo(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent());

        database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

        DatabaseFactory.getRecipientDatabase(context).setExpireMessages(recipient, message.getExpiresInSeconds());

      if (smsMessageId.isPresent()) {
        DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
      }
    } catch (MmsException e) {
      throw new StorageFailedException(e, content.getSender(), content.getSenderDevice());
    }
  }

  private void handleSynchronizeVerifiedMessage(@NonNull VerifiedMessage verifiedMessage) {
    IdentityUtil.processVerifiedMessage(context, verifiedMessage);
  }

  private void handleSynchronizeStickerPackOperation(@NonNull List<StickerPackOperationMessage> stickerPackOperations) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();

    for (StickerPackOperationMessage operation : stickerPackOperations) {
      if (operation.getPackId().isPresent() && operation.getPackKey().isPresent() && operation.getType().isPresent()) {
        String packId  = Hex.toStringCondensed(operation.getPackId().get());
        String packKey = Hex.toStringCondensed(operation.getPackKey().get());

        switch (operation.getType().get()) {
          case INSTALL:
            jobManager.add(new StickerPackDownloadJob(packId, packKey, false));
            break;
          case REMOVE:
            DatabaseFactory.getStickerDatabase(context).uninstallPack(packId);
            break;
        }
      } else {
        Log.w(TAG, "Received incomplete sticker pack operation sync.");
      }
    }
  }

  private void handleContactSyncMessage(@NonNull ContactsMessage contactsMessage) {
    if (!contactsMessage.getContactsStream().isStream()) { return; }
    Log.d("Loki", "Received contact sync message.");

    try {
      InputStream in = contactsMessage.getContactsStream().asStream().getInputStream();
      DeviceContactsInputStream contactsInputStream = new DeviceContactsInputStream(in);
      List<DeviceContact> deviceContacts = contactsInputStream.readAll();
      for (DeviceContact deviceContact : deviceContacts) {
        // Check if we have the contact as a friend and that we're not trying to sync our own device
        String hexEncodedPublicKey = deviceContact.getNumber();
        Address address = Address.fromSerialized(hexEncodedPublicKey);
        if (!address.isPhone() || address.toPhoneString().equals(TextSecurePreferences.getLocalNumber(context))) { continue; }

        /*
        If we're not friends with the contact we received or our friend request expired then we should send them a friend request.
        Otherwise, if we have received a friend request from them, automatically accept the friend request.
         */
        Recipient recipient = Recipient.from(context, address, false);
        long threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
        LokiThreadFriendRequestStatus status = DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadID);
        if (status == LokiThreadFriendRequestStatus.NONE || status == LokiThreadFriendRequestStatus.REQUEST_EXPIRED) {
          // TODO: We should ensure that our mapping has been uploaded to the server before sending out this message
          MessageSender.sendBackgroundFriendRequest(context, hexEncodedPublicKey, "Please accept to enable messages to be synced across devices");
          Log.d("Loki", "Sent friend request to " + hexEncodedPublicKey);
        } else if (status == LokiThreadFriendRequestStatus.REQUEST_RECEIVED) {
          // Accept the incoming friend request
          becomeFriendsWithContactIfNeeded(hexEncodedPublicKey, false, false);
          // Send them an accept message back
          MessageSender.sendBackgroundMessage(context, hexEncodedPublicKey);
          Log.d("Loki", "Became friends with " + deviceContact.getNumber());
        }

        // TODO: Handle blocked - If user is not blocked then we should do the friend request logic otherwise add them to our block list
        // TODO: Handle expiration timer - Update expiration timer?
        // TODO: Handle avatar - Download and set avatar?
      }
    } catch (Exception e) {
      Log.d("Loki", "Failed to sync contact: " + e + ".");
    }
  }

  private void handleGroupSyncMessage(@NonNull SignalServiceContent content, @NonNull SignalServiceAttachment groupMessage) {
    if (groupMessage.isStream()) {
      Log.d("Loki", "Received a group sync message.");
      try {
        InputStream in = groupMessage.asStream().getInputStream();
        DeviceGroupsInputStream groupsInputStream = new DeviceGroupsInputStream(in);
        List<DeviceGroup> groups = groupsInputStream.readAll();
        for (DeviceGroup group : groups) {
          SignalServiceGroup serviceGroup = new SignalServiceGroup(
              SignalServiceGroup.Type.UPDATE,
              group.getId(),
              SignalServiceGroup.GroupType.SIGNAL,
              group.getName().orNull(),
              group.getMembers(),
              group.getAvatar().orNull(),
              group.getAdmins()
          );
          SignalServiceDataMessage dataMessage = new SignalServiceDataMessage(content.getTimestamp(), serviceGroup, null, null);
          GroupMessageProcessor.process(context, content, dataMessage, false);
        }
      } catch (Exception e) {
        Log.d("Loki", "Failed to sync group due to error: " + e + ".");
      }
    }
  }

  private void handleOpenGroupSyncMessage(@NonNull List<LokiPublicChat> openGroups) {
    try {
      for (LokiPublicChat openGroup : openGroups) {
        long threadID = GroupManager.getPublicChatThreadId(openGroup.getId(), context);
        if (threadID > -1) continue;

        String url = openGroup.getServer();
        long channel = openGroup.getChannel();
        OpenGroupUtilities.addGroup(context, url, channel).fail(e -> {
          Log.d("Loki", "Failed to sync open group: " + url + " due to error: " + e + ".");
          return Unit.INSTANCE;
        });
      }
    } catch (Exception e) {
      Log.d("Loki", "Failed to sync open groups due to error: " + e + ".");
    }
  }

  private void handleSynchronizeSentMessage(@NonNull SignalServiceContent content,
                                            @NonNull SentTranscriptMessage message)
      throws StorageFailedException

  {
    try {
      GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);

      Long threadId;

      if (message.getMessage().isEndSession()) {
        threadId = handleSynchronizeSentEndSessionMessage(message);
      } else if (message.getMessage().isGroupUpdate()) {
        threadId = GroupMessageProcessor.process(context, content, message.getMessage(), true);
      } else if (message.getMessage().isExpirationUpdate()) {
        threadId = handleSynchronizeSentExpirationUpdate(message);
      } else if (message.getMessage().getAttachments().isPresent() || message.getMessage().getQuote().isPresent() || message.getMessage().getPreviews().isPresent() || message.getMessage().getSticker().isPresent()) {
        threadId = handleSynchronizeSentMediaMessage(message);
      } else {
        threadId = handleSynchronizeSentTextMessage(message);
      }

      if (message.getMessage().getGroupInfo().isPresent() && groupDatabase.isUnknownGroup(GroupUtil.getEncodedId(message.getMessage().getGroupInfo().get()))) {
        handleUnknownGroupMessage(content, message.getMessage().getGroupInfo().get());
      }

      String ourMasterDevice = TextSecurePreferences.getMasterHexEncodedPublicKey(context);
      boolean isSenderMasterDevice = ourMasterDevice != null && ourMasterDevice.equals(content.getSender());
      if (message.getMessage().getProfileKey().isPresent()) {
        Recipient recipient = null;

        if      (message.getDestination().isPresent())            recipient = Recipient.from(context, Address.fromSerialized(message.getDestination().get()), false);
        else if (message.getMessage().getGroupInfo().isPresent()) recipient = Recipient.from(context, Address.fromSerialized(GroupUtil.getEncodedId(message.getMessage().getGroupInfo().get())), false);


        if (recipient != null && !recipient.isSystemContact() && !recipient.isProfileSharing()) {
          DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipient, true);
        }

        // Loki - If we received a sync message from our master device then we need to extract the profile picture url
        if (isSenderMasterDevice) {
          handleProfileKey(content, message.getMessage());
        }
      }

      // Loki - Update display name from master device
      if (isSenderMasterDevice && content.senderDisplayName.isPresent() && content.senderDisplayName.get().length() > 0) {
        TextSecurePreferences.setProfileName(context, content.senderDisplayName.get());
      }

      if (threadId != null) {
        DatabaseFactory.getThreadDatabase(context).setRead(threadId, true);
        MessageNotifier.updateNotification(context);
      }

      MessageNotifier.setLastDesktopActivityTimestamp(message.getTimestamp());
    } catch (MmsException e) {
      throw new StorageFailedException(e, content.getSender(), content.getSenderDevice());
    }
  }

  private void handleSynchronizeRequestMessage(@NonNull RequestMessage message)
  {
    if (message.isContactsRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceContactUpdateJob(context, true));

      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new RefreshUnidentifiedDeliveryAbilityJob());
    }

    if (message.isGroupsRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceGroupUpdateJob());
    }

    if (message.isBlockedListRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceBlockedUpdateJob());
    }

    if (message.isConfigurationRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(context),
                                                                   TextSecurePreferences.isTypingIndicatorsEnabled(context),
                                                                   TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
                                                                   TextSecurePreferences.isLinkPreviewsEnabled(context)));

      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceStickerPackSyncJob());
    }
  }

  private void handleSynchronizeReadMessage(@NonNull List<ReadMessage> readMessages, long envelopeTimestamp)
  {
    for (ReadMessage readMessage : readMessages) {
      List<Pair<Long, Long>> expiringText = DatabaseFactory.getSmsDatabase(context).setTimestampRead(new SyncMessageId(Address.fromSerialized(readMessage.getSender()), readMessage.getTimestamp()), envelopeTimestamp);
      List<Pair<Long, Long>> expiringMedia = DatabaseFactory.getMmsDatabase(context).setTimestampRead(new SyncMessageId(Address.fromSerialized(readMessage.getSender()), readMessage.getTimestamp()), envelopeTimestamp);

      for (Pair<Long, Long> expiringMessage : expiringText) {
        ApplicationContext.getInstance(context)
                          .getExpiringMessageManager()
                          .scheduleDeletion(expiringMessage.first, false, envelopeTimestamp, expiringMessage.second);
      }

      for (Pair<Long, Long> expiringMessage : expiringMedia) {
        ApplicationContext.getInstance(context)
                          .getExpiringMessageManager()
                          .scheduleDeletion(expiringMessage.first, true, envelopeTimestamp, expiringMessage.second);
      }
    }

    MessageNotifier.setLastDesktopActivityTimestamp(envelopeTimestamp);
    MessageNotifier.cancelDelayedNotifications();
    MessageNotifier.updateNotification(context);
  }

  public void handleMediaMessage(@NonNull SignalServiceContent content,
                                 @NonNull SignalServiceDataMessage message,
                                 @NonNull Optional<Long> smsMessageId,
                                 @NonNull Optional<Long> messageServerIDOrNull)
      throws StorageFailedException
  {
    Recipient originalRecipient = getRecipientForMessage(content, message);
    Recipient masterRecipient = getMasterRecipientForMessage(content, message);

    notifyTypingStoppedFromIncomingMessage(masterRecipient, content.getSender(), content.getSenderDevice());

    Optional<QuoteModel>        quote          = getValidatedQuote(message.getQuote());
    Optional<List<Contact>>     sharedContacts = getContacts(message.getSharedContacts());
    Optional<List<LinkPreview>> linkPreviews   = getLinkPreviews(message.getPreviews(), message.getBody().or(""));
    Optional<Attachment>        sticker        = getStickerAttachment(message.getSticker());

    Address sender = masterRecipient.getAddress();

    // If message is from group then we need to map it to get the sender of the message
    if (message.isGroupMessage()) {
      sender = getMasterRecipient(content.getSender()).getAddress();
    }

    // Ignore messages from ourselves
    if (sender.serialize().equalsIgnoreCase(TextSecurePreferences.getLocalNumber(context))) { return; }

    IncomingMediaMessage mediaMessage = new IncomingMediaMessage(sender, message.getTimestamp(), -1,
       message.getExpiresInSeconds() * 1000L, false, content.isNeedsReceipt(), message.getBody(), message.getGroupInfo(), message.getAttachments(),
        quote, sharedContacts, linkPreviews, sticker);

    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    database.beginTransaction();

    // Ignore message if it has no body and no attachments or anything
    if (mediaMessage.getBody().isEmpty() && mediaMessage.getAttachments().isEmpty() && mediaMessage.getSharedContacts().isEmpty() && mediaMessage.getLinkPreviews().isEmpty()) {
      return;
    }

    Optional<InsertResult> insertResult;

    try {
      insertResult = database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

      if (insertResult.isPresent()) {
        List<DatabaseAttachment> allAttachments     = DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(insertResult.get().getMessageId());
        List<DatabaseAttachment> stickerAttachments = Stream.of(allAttachments).filter(Attachment::isSticker).toList();
        List<DatabaseAttachment> attachments        = Stream.of(allAttachments).filterNot(Attachment::isSticker).toList();

        forceStickerDownloadIfNecessary(stickerAttachments);

        for (DatabaseAttachment attachment : attachments) {
          ApplicationContext.getInstance(context).getJobManager().add(new AttachmentDownloadJob(insertResult.get().getMessageId(), attachment.getAttachmentId(), false));
        }

        if (smsMessageId.isPresent()) {
          DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
        }

        database.setTransactionSuccessful();
      }
    } catch (MmsException e) {
      throw new StorageFailedException(e, content.getSender(), content.getSenderDevice());
    } finally {
      database.endTransaction();
    }

    if (insertResult.isPresent()) {
      MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
    }

    // Loki - Run database updates in the background, we should look into fixing this in the future
    AsyncTask.execute(() -> {
      // Loki - Store message server ID
      updateGroupChatMessageServerID(messageServerIDOrNull, insertResult);

      // Loki - Update mapping of message to original thread ID
      if (insertResult.isPresent()) {
        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
        LokiMessageDatabase lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(context);
        long originalThreadId = threadDatabase.getThreadIdFor(originalRecipient);
        lokiMessageDatabase.setOriginalThreadID(insertResult.get().getMessageId(), originalThreadId);
      }
    });
  }

  private long handleSynchronizeSentExpirationUpdate(@NonNull SentTranscriptMessage message) throws MmsException {
    MmsDatabase database  = DatabaseFactory.getMmsDatabase(context);
    Recipient   recipient = getSyncMessagePrimaryDestination(message);

    OutgoingExpirationUpdateMessage expirationUpdateMessage = new OutgoingExpirationUpdateMessage(recipient,
                                                                                                  message.getTimestamp(),
                                                                                                  message.getMessage().getExpiresInSeconds() * 1000L);

    long threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
    long messageId = database.insertMessageOutbox(expirationUpdateMessage, threadId, false, null);

    database.markAsSent(messageId, true);

    DatabaseFactory.getRecipientDatabase(context).setExpireMessages(recipient, message.getMessage().getExpiresInSeconds());

    return threadId;
  }

  public long handleSynchronizeSentMediaMessage(@NonNull SentTranscriptMessage message)
      throws MmsException
  {
    MmsDatabase                 database        = DatabaseFactory.getMmsDatabase(context);
    Recipient                   recipients      = getSyncMessagePrimaryDestination(message);
    Optional<QuoteModel>        quote           = getValidatedQuote(message.getMessage().getQuote());
    Optional<Attachment>        sticker         = getStickerAttachment(message.getMessage().getSticker());
    Optional<List<Contact>>     sharedContacts  = getContacts(message.getMessage().getSharedContacts());
    Optional<List<LinkPreview>> previews        = getLinkPreviews(message.getMessage().getPreviews(), message.getMessage().getBody().or(""));
    List<Attachment>            syncAttachments = PointerAttachment.forPointers(message.getMessage().getAttachments());

    if (sticker.isPresent()) {
      syncAttachments.add(sticker.get());
    }

    OutgoingMediaMessage mediaMessage = new OutgoingMediaMessage(recipients, message.getMessage().getBody().orNull(),
                                                                 syncAttachments,
                                                                 message.getTimestamp(), -1,
                                                                 message.getMessage().getExpiresInSeconds() * 1000,
                                                                 ThreadDatabase.DistributionTypes.DEFAULT, quote.orNull(),
                                                                 sharedContacts.or(Collections.emptyList()),
                                                                 previews.or(Collections.emptyList()),
                                                                 Collections.emptyList(), Collections.emptyList());

    mediaMessage = new OutgoingSecureMediaMessage(mediaMessage);

    if (recipients.getExpireMessages() != message.getMessage().getExpiresInSeconds()) {
      handleSynchronizeSentExpirationUpdate(message);
    }

    long threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);

    database.beginTransaction();

    try {
      long messageId = database.insertMessageOutbox(mediaMessage, threadId, false, null);
      if (message.messageServerID >= 0) { DatabaseFactory.getLokiMessageDatabase(context).setServerID(messageId, message.messageServerID); }

      if (recipients.getAddress().isGroup()) {
        GroupReceiptDatabase receiptDatabase = DatabaseFactory.getGroupReceiptDatabase(context);
        List<Recipient>      members         = DatabaseFactory.getGroupDatabase(context).getGroupMembers(recipients.getAddress().toGroupString(), false);

        for (Recipient member : members) {
          receiptDatabase.setUnidentified(member.getAddress(), messageId, message.isUnidentified(member.getAddress().serialize()));
        }
      }

      database.markAsSent(messageId, true);
      database.markUnidentified(messageId, message.isUnidentified(recipients.getAddress().serialize()));

      List<DatabaseAttachment> allAttachments     = DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(messageId);
      List<DatabaseAttachment> stickerAttachments = Stream.of(allAttachments).filter(Attachment::isSticker).toList();
      List<DatabaseAttachment> attachments        = Stream.of(allAttachments).filterNot(Attachment::isSticker).toList();

      forceStickerDownloadIfNecessary(stickerAttachments);

      for (DatabaseAttachment attachment : attachments) {
        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new AttachmentDownloadJob(messageId, attachment.getAttachmentId(), false));
      }

      if (message.getMessage().getExpiresInSeconds() > 0) {
        database.markExpireStarted(messageId, message.getExpirationStartTimestamp());
        ApplicationContext.getInstance(context)
                          .getExpiringMessageManager()
                          .scheduleDeletion(messageId, true,
                                            message.getExpirationStartTimestamp(),
                                            message.getMessage().getExpiresInSeconds() * 1000L);
      }

      if (recipients.isLocalNumber()) {
        SyncMessageId id = new SyncMessageId(recipients.getAddress(), message.getTimestamp());
        DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(id, System.currentTimeMillis());
        DatabaseFactory.getMmsSmsDatabase(context).incrementReadReceiptCount(id, System.currentTimeMillis());
      }

      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }
    return threadId;
  }

  public void handleTextMessage(@NonNull SignalServiceContent content,
                                @NonNull SignalServiceDataMessage message,
                                @NonNull Optional<Long> smsMessageId,
                                @NonNull Optional<Long> messageServerIDOrNull)
      throws StorageFailedException
  {
    SmsDatabase database          = DatabaseFactory.getSmsDatabase(context);
    String      body              = message.getBody().isPresent() ? message.getBody().get() : "";
    Recipient   originalRecipient = getRecipientForMessage(content, message);
    Recipient   masterRecipient   = getMasterRecipientForMessage(content, message);

    if (message.getExpiresInSeconds() != originalRecipient.getExpireMessages()) {
      handleExpirationUpdate(content, message, Optional.absent());
    }

    Long threadId = null;

    if (smsMessageId.isPresent() && !message.getGroupInfo().isPresent()) {
      threadId = database.updateBundleMessageBody(smsMessageId.get(), body).second;
    } else {
      notifyTypingStoppedFromIncomingMessage(masterRecipient, content.getSender(), content.getSenderDevice());

      Address sender = masterRecipient.getAddress();

      // If message is from group then we need to map it to get the sender of the message
      if (message.isGroupMessage()) {
        sender = getMasterRecipient(content.getSender()).getAddress();
      }

      // Ignore messages from ourselves
      if (sender.serialize().equalsIgnoreCase(TextSecurePreferences.getLocalNumber(context))) { return; }

      IncomingTextMessage tm = new IncomingTextMessage(sender,
                                                       content.getSenderDevice(),
                                                       message.getTimestamp(), body,
                                                       message.getGroupInfo(),
                                                       message.getExpiresInSeconds() * 1000L,
                                                       content.isNeedsReceipt());

      IncomingEncryptedMessage textMessage = new IncomingEncryptedMessage(tm, body);

      // Ignore the message if the body is empty
      if (textMessage.getMessageBody().length() == 0) { return; }

      // Insert the message into the database
      Optional<InsertResult> insertResult = database.insertMessageInbox(textMessage);

      if (insertResult.isPresent()) {
        threadId = insertResult.get().getThreadId();
      }

      if (smsMessageId.isPresent()) database.deleteMessage(smsMessageId.get());

      if (threadId != null) {
        MessageNotifier.updateNotification(context, threadId);
      }

      // Loki - Run database updates in background, we should look into fixing this in the future
      AsyncTask.execute(() -> {
        if (insertResult.isPresent()) {
          InsertResult result = insertResult.get();
          // Loki - Cache the user hex encoded public key (for mentions)
          LokiAPIUtilities.INSTANCE.populateUserHexEncodedPublicKeyCacheIfNeeded(result.getThreadId(), context);
          LokiAPI.Companion.cache(textMessage.getSender().serialize(), result.getThreadId());

          // Loki - Store message server ID
          updateGroupChatMessageServerID(messageServerIDOrNull, insertResult);

          // Loki - Update mapping of message to original thread ID
          if (result.getMessageId() > -1) {
            ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
            LokiMessageDatabase lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(context);
            long originalThreadId = threadDatabase.getThreadIdFor(originalRecipient);
            lokiMessageDatabase.setOriginalThreadID(result.getMessageId(), originalThreadId);
          }
        }
      });
    }
  }

  private boolean isValidDeviceLinkMessage(@NonNull DeviceLink authorisation) {
    boolean isSecondaryDevice = TextSecurePreferences.getMasterHexEncodedPublicKey(context) != null;
    String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context);
    boolean isRequest = (authorisation.getType() == DeviceLink.Type.REQUEST);
    if (authorisation.getRequestSignature() == null) {
      Log.d("Loki", "Ignoring pairing request message without a request signature.");
      return false;
    } else if (isRequest && isSecondaryDevice) {
      Log.d("Loki", "Ignoring unexpected pairing request message (the device is already paired as a secondary device).");
      return false;
    } else if (isRequest && !authorisation.getMasterHexEncodedPublicKey().equals(userHexEncodedPublicKey)) {
      Log.d("Loki", "Ignoring pairing request message addressed to another user.");
      return false;
    } else if (isRequest && authorisation.getSlaveHexEncodedPublicKey().equals(userHexEncodedPublicKey)) {
      Log.d("Loki", "Ignoring pairing request message from self.");
      return false;
    }
    return authorisation.verify();
  }

  private void handleDeviceLinkMessage(@NonNull DeviceLink deviceLink, @NonNull SignalServiceContent content) {
    String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context);
    if (deviceLink.getType() == DeviceLink.Type.REQUEST) {
      handleDeviceLinkRequestMessage(deviceLink, content);
    } else if (deviceLink.getSlaveHexEncodedPublicKey().equals(userHexEncodedPublicKey)) {
      handleDeviceLinkAuthorizedMessage(deviceLink, content);
    }
  }

  private void handleDeviceLinkRequestMessage(@NonNull DeviceLink deviceLink, @NonNull SignalServiceContent content) {
    DeviceLinkingSession linkingSession = DeviceLinkingSession.Companion.getShared();
    if (!linkingSession.isListeningForLinkingRequests()) {
      new Broadcaster(context).broadcast("unexpectedDeviceLinkRequestReceived");
      return;
    }
    boolean isValid = isValidDeviceLinkMessage(deviceLink);
    if (!isValid) { return; }
    storePreKeyBundleIfNeeded(content);
    linkingSession.processLinkingRequest(deviceLink);
  }

  private void handleDeviceLinkAuthorizedMessage(@NonNull DeviceLink deviceLink, @NonNull SignalServiceContent content) {
    // Check preconditions
    boolean hasExistingDeviceLink = TextSecurePreferences.getMasterHexEncodedPublicKey(context) != null;
    if (hasExistingDeviceLink) {
      Log.d("Loki", "Ignoring unexpected device link message (the device is already linked as a slave device).");
      return;
    }
    boolean isValid = isValidDeviceLinkMessage(deviceLink);
    if (!isValid) {
      Log.d("Loki", "Ignoring invalid device link message.");
      return;
    }
    if (!DeviceLinkingSession.Companion.getShared().isListeningForLinkingRequests()) {
      Log.d("Loki", "Ignoring device link message.");
      return;
    }
    if (deviceLink.getType() != DeviceLink.Type.AUTHORIZATION) { return; }
    Log.d("Loki", "Received device link authorized message from: " + deviceLink.getMasterHexEncodedPublicKey() + ".");
    // Save pre key bundle if we somehow got one
    storePreKeyBundleIfNeeded(content);
    // Process
    DeviceLinkingSession.Companion.getShared().processLinkingAuthorization(deviceLink);
    // Store the master device's ID
    String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context);
    DatabaseFactory.getLokiAPIDatabase(context).clearDeviceLinks(userHexEncodedPublicKey);
    DatabaseFactory.getLokiAPIDatabase(context).addDeviceLink(deviceLink);
    TextSecurePreferences.setMasterHexEncodedPublicKey(context, deviceLink.getMasterHexEncodedPublicKey());
    TextSecurePreferences.setMultiDevice(context, true);
    // Send a background message to the master device
    MessageSender.sendBackgroundMessage(context, deviceLink.getMasterHexEncodedPublicKey());
    /*
     Update device link on the file server.
     We put this here because after receiving the authorisation message, we will also receive all sync messages.
     If these sync messages are contact syncs then we need to send them friend requests so that we can establish multi-device communication.
     If our device mapping is not stored on the server before the other party receives our message, they will think that they got a friend request from a non-multi-device user.
     */
    try {
      PromiseUtil.timeout(LokiFileServerAPI.shared.addDeviceLink(deviceLink), 8000).get();
    } catch (Exception e) {
      Log.w("Loki", "Failed to upload device links to the file server! " + e);
    }
    // Update display name if needed
    if (content.senderDisplayName.isPresent() && content.senderDisplayName.get().length() > 0) {
        TextSecurePreferences.setProfileName(context, content.senderDisplayName.get());
    }
    // Update profile picture if needed
    if (content.getDataMessage().isPresent()) {
      handleProfileKey(content, content.getDataMessage().get());
    }
    // Handle contact sync if needed
    if (content.getSyncMessage().isPresent() && content.getSyncMessage().get().getContacts().isPresent()) {
      handleContactSyncMessage(content.getSyncMessage().get().getContacts().get());
    }
  }

  private void setDisplayName(String hexEncodedPublicKey, String profileName) {
    String displayName = profileName + " (..." + hexEncodedPublicKey.substring(hexEncodedPublicKey.length() - 8) + ")";
    DatabaseFactory.getLokiUserDatabase(context).setDisplayName(hexEncodedPublicKey, displayName);
  }

  private void updateGroupChatMessageServerID(Optional<Long> messageServerIDOrNull, Optional<InsertResult> insertResult) {
    if (!insertResult.isPresent() || !messageServerIDOrNull.isPresent()) { return; }
    long messageID = insertResult.get().getMessageId();
    long messageServerID = messageServerIDOrNull.get();
    DatabaseFactory.getLokiMessageDatabase(context).setServerID(messageID, messageServerID);
  }

  private void storePreKeyBundleIfNeeded(@NonNull SignalServiceContent content) {
    Recipient sender = Recipient.from(context, Address.fromSerialized(content.getSender()), false);
    if (sender.isGroupRecipient() || !content.lokiServiceMessage.isPresent()) { return; }
    LokiServiceMessage lokiMessage = content.lokiServiceMessage.get();
    if (lokiMessage.getPreKeyBundleMessage() == null) { return; }
    int registrationID = TextSecurePreferences.getLocalRegistrationId(context);
    LokiPreKeyBundleDatabase lokiPreKeyBundleDatabase = DatabaseFactory.getLokiPreKeyBundleDatabase(context);
    if (registrationID <= 0) { return; }
    Log.d("Loki", "Received a pre key bundle from: " + content.getSender() + ".");
    PreKeyBundle preKeyBundle = lokiMessage.getPreKeyBundleMessage().getPreKeyBundle(registrationID);
    lokiPreKeyBundleDatabase.setPreKeyBundle(content.getSender(), preKeyBundle);

  }

  private void handleSessionRequestIfNeeded(@NonNull SignalServiceContent content) {
    if (!content.isFriendRequest() || !isSessionRequest(content)) { return; }
    // Check if the session request came from a member in one of our groups or one of our friends
    LokiDeviceLinkUtilities.INSTANCE.getMasterHexEncodedPublicKey(content.getSender()).success( masterHexEncodedPublicKey -> {
      String sender = masterHexEncodedPublicKey != null ? masterHexEncodedPublicKey : content.getSender();
      long threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(Recipient.from(context, Address.fromSerialized(sender), false));
      LokiThreadFriendRequestStatus threadFriendRequestStatus = DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadID);
      boolean isOurFriend = threadFriendRequestStatus == LokiThreadFriendRequestStatus.FRIENDS;
      boolean isInOneOfOurGroups = DatabaseFactory.getGroupDatabase(context).signalGroupsHaveMember(sender);
      boolean shouldAcceptSessionRequest = isOurFriend || isInOneOfOurGroups;
      if (shouldAcceptSessionRequest) {
        MessageSender.sendBackgroundMessage(context, content.getSender()); // Send a background message to acknowledge
      }
      return Unit.INSTANCE;
    });
  }

  private void becomeFriendsWithContactIfNeeded(String hexEncodedPublicKey, boolean requiresContactSync, boolean canSkip) {
    // Ignore friend requests to group recipients
    LokiThreadDatabase lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(context);
    Recipient contactID = Recipient.from(context, Address.fromSerialized(hexEncodedPublicKey), false);
    if (contactID.isGroupRecipient()) return;
    // Ignore friend requests to recipients we're already friends with
    long threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(contactID);
    LokiThreadFriendRequestStatus threadFriendRequestStatus = lokiThreadDatabase.getFriendRequestStatus(threadID);
    if (threadFriendRequestStatus == LokiThreadFriendRequestStatus.FRIENDS) { return; }
    // We shouldn't be able to skip from NONE to FRIENDS under normal circumstances.
    // Multi-device is the one exception to this rule because we want to automatically become friends with slave devices.
    if (!canSkip && threadFriendRequestStatus == LokiThreadFriendRequestStatus.NONE) { return; }
    // If the thread's friend request status is not `FRIENDS` or `NONE`, but we're receiving a message,
    // it must be a friend request accepted message. Declining a friend request doesn't send a message.
    lokiThreadDatabase.setFriendRequestStatus(threadID, LokiThreadFriendRequestStatus.FRIENDS);
    // Send out a contact sync message if needed
    if (requiresContactSync) {
      MessageSender.syncContact(context, contactID.getAddress());
    }
    // Enable profile sharing with the recipient
    DatabaseFactory.getRecipientDatabase(context).setProfileSharing(contactID, true);
    // Update the last message if needed
    LokiDeviceLinkUtilities.INSTANCE.getMasterHexEncodedPublicKey(hexEncodedPublicKey).success( masterHexEncodedPublicKey -> {
      Util.runOnMain(() -> {
        long masterThreadID = (masterHexEncodedPublicKey == null) ? threadID : DatabaseFactory.getThreadDatabase(context).getThreadIdFor(Recipient.from(context, Address.fromSerialized(masterHexEncodedPublicKey), false));
        FriendRequestHandler.updateLastFriendRequestMessage(context, masterThreadID, LokiMessageFriendRequestStatus.REQUEST_ACCEPTED);
      });
      return Unit.INSTANCE;
    });
  }

  private void updateFriendRequestStatusIfNeeded(@NonNull SignalServiceContent content, @NonNull SignalServiceDataMessage message) {
    if (!content.isFriendRequest() || message.isGroupMessage() || message.isSessionRequest()) { return; }
    Promise<Boolean, Exception> promise = PromiseUtil.timeout(MultiDeviceUtilities.shouldAutomaticallyBecomeFriendsWithDevice(content.getSender(), context), 8000);
    boolean shouldBecomeFriends = PromiseUtil.get(promise, false);
    if (shouldBecomeFriends) {
      // Become friends AND update the message they sent
      becomeFriendsWithContactIfNeeded(content.getSender(), true, true);
      // Send them an accept message back
      MessageSender.sendBackgroundMessage(context, content.getSender());
    } else {
      // Do regular friend request logic checks
      Recipient originalRecipient = getRecipientForMessage(content, message);
      Recipient masterRecipient = getMasterRecipientForMessage(content, message);
      LokiThreadDatabase lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(context);

      // Loki - Friend requests only work in direct chats
      if (!originalRecipient.getAddress().isPhone()) { return; }

      long threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(originalRecipient);
      long primaryDeviceThreadID = DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(masterRecipient);
      LokiThreadFriendRequestStatus threadFriendRequestStatus = lokiThreadDatabase.getFriendRequestStatus(threadID);

      if (threadFriendRequestStatus == LokiThreadFriendRequestStatus.REQUEST_SENT) {
        // This can happen if Alice sent Bob a friend request, Bob declined, but then Bob changed his
        // mind and sent a friend request to Alice. In this case we want Alice to auto-accept the request
        // and send a friend request accepted message back to Bob. We don't check that sending the
        // friend request accepted message succeeded. Even if it doesn't, the thread's current friend
        // request status will be set to `FRIENDS` for Alice making it possible
        // for Alice to send messages to Bob. When Bob receives a message, his thread's friend request status
        // will then be set to `FRIENDS`. If we do check for a successful send
        // before updating Alice's thread's friend request status to `FRIENDS`,
        // we can end up in a deadlock where both users' threads' friend request statuses are
        // `REQUEST_SENT`.
        lokiThreadDatabase.setFriendRequestStatus(threadID, LokiThreadFriendRequestStatus.FRIENDS);
        // Since messages are forwarded to the primary device thread, we need to update it there
        FriendRequestHandler.updateLastFriendRequestMessage(context, primaryDeviceThreadID, LokiMessageFriendRequestStatus.REQUEST_ACCEPTED);
        // Accept the friend request
        MessageSender.sendBackgroundMessage(context, content.getSender());
        // Send contact sync message
        MessageSender.syncContact(context, originalRecipient.getAddress());
      } else if (threadFriendRequestStatus != LokiThreadFriendRequestStatus.FRIENDS) {
        // Checking that the sender of the message isn't already a friend is necessary because otherwise
        // the following situation can occur: Alice and Bob are friends. Bob loses his database and his
        // friend request status is reset to `NONE`. Bob now sends Alice a friend
        // request. Alice's thread's friend request status is reset to
        // `REQUEST_RECEIVED`.
        lokiThreadDatabase.setFriendRequestStatus(threadID, LokiThreadFriendRequestStatus.REQUEST_RECEIVED);

        // Since messages are forwarded to the primary device thread, we need to update it there
        FriendRequestHandler.receivedIncomingFriendRequestMessage(context, primaryDeviceThreadID);
      }
    }
  }

  public long handleSynchronizeSentTextMessage(@NonNull SentTranscriptMessage message)
      throws MmsException
  {

    Recipient recipient       = getSyncMessagePrimaryDestination(message);
    String    body            = message.getMessage().getBody().or("");
    long      expiresInMillis = message.getMessage().getExpiresInSeconds() * 1000L;

    if (recipient.getExpireMessages() != message.getMessage().getExpiresInSeconds()) {
      handleSynchronizeSentExpirationUpdate(message);
    }

    long    threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
    boolean isGroup   = recipient.getAddress().isGroup();

    MessagingDatabase database;
    long              messageId;

    if (isGroup) {
      OutgoingMediaMessage outgoingMediaMessage = new OutgoingMediaMessage(recipient, new SlideDeck(), body, message.getTimestamp(), -1, expiresInMillis, ThreadDatabase.DistributionTypes.DEFAULT, null, Collections.emptyList(), Collections.emptyList());
      outgoingMediaMessage = new OutgoingSecureMediaMessage(outgoingMediaMessage);

      messageId = DatabaseFactory.getMmsDatabase(context).insertMessageOutbox(outgoingMediaMessage, threadId, false, null);
      if (message.messageServerID >= 0) { DatabaseFactory.getLokiMessageDatabase(context).setServerID(messageId, message.messageServerID); }

      database  = DatabaseFactory.getMmsDatabase(context);

      GroupReceiptDatabase receiptDatabase = DatabaseFactory.getGroupReceiptDatabase(context);
      List<Recipient>      members         = DatabaseFactory.getGroupDatabase(context).getGroupMembers(recipient.getAddress().toGroupString(), false);

      for (Recipient member : members) {
        receiptDatabase.setUnidentified(member.getAddress(), messageId, message.isUnidentified(member.getAddress().serialize()));
      }
    } else {
      OutgoingTextMessage outgoingTextMessage = new OutgoingEncryptedMessage(recipient, body, expiresInMillis);

      messageId = DatabaseFactory.getSmsDatabase(context).insertMessageOutbox(threadId, outgoingTextMessage, false, message.getTimestamp(), null);
      database  = DatabaseFactory.getSmsDatabase(context);
      database.markUnidentified(messageId, message.isUnidentified(recipient.getAddress().serialize()));
    }

    database.markAsSent(messageId, true);

    if (expiresInMillis > 0) {
      database.markExpireStarted(messageId, message.getExpirationStartTimestamp());
      ApplicationContext.getInstance(context)
                        .getExpiringMessageManager()
                        .scheduleDeletion(messageId, isGroup, message.getExpirationStartTimestamp(), expiresInMillis);
    }

    if (recipient.isLocalNumber()) {
      SyncMessageId id = new SyncMessageId(recipient.getAddress(), message.getTimestamp());
      DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(id, System.currentTimeMillis());
      DatabaseFactory.getMmsSmsDatabase(context).incrementReadReceiptCount(id, System.currentTimeMillis());
    }

    return threadId;
  }

  private void handleInvalidVersionMessage(@NonNull String sender, int senderDevice, long timestamp,
                                           @NonNull Optional<Long> smsMessageId)
  {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp);

      if (insertResult.isPresent()) {
        smsDatabase.markAsInvalidVersionKeyExchange(insertResult.get().getMessageId());
        MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsInvalidVersionKeyExchange(smsMessageId.get());
    }
  }

  private SmsMessageRecord getLastMessage(String sender) {
    try {
      SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);
      Recipient recipient = Recipient.from(context, Address.fromSerialized(sender), false);
      long threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(recipient);
      if (threadID < 0) { return null; }
      int messageCount = smsDatabase.getMessageCountForThread(threadID);
      if (messageCount <= 0) { return null; }
      long lastMessageID = smsDatabase.getIDForMessageAtIndex(threadID, messageCount - 1);
      return smsDatabase.getMessage(lastMessageID);
    } catch (Exception e) {
      return null;
    }
  }

  private void handleCorruptMessage(@NonNull String sender, int senderDevice, long timestamp,
                                    @NonNull Optional<Long> smsMessageId)
  {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      SmsMessageRecord lastMessage = getLastMessage(sender);
      if (lastMessage == null || !SmsDatabase.Types.isFailedDecryptType(lastMessage.getType())) {
        Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp);

        if (insertResult.isPresent()) {
          smsDatabase.markAsDecryptFailed(insertResult.get().getMessageId());
          MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
        }
      }
    } else {
      smsDatabase.markAsDecryptFailed(smsMessageId.get());
    }
    triggerSessionRestorePrompt(sender);
  }

  private void handleNoSessionMessage(@NonNull String sender, int senderDevice, long timestamp,
                                      @NonNull Optional<Long> smsMessageId)
  {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      SmsMessageRecord lastMessage = getLastMessage(sender);
      if (lastMessage == null || !SmsDatabase.Types.isNoRemoteSessionType(lastMessage.getType())) {
        Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp);

        if (insertResult.isPresent()) {
          smsDatabase.markAsNoSession(insertResult.get().getMessageId());
          MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
        }
      }
    } else {
      smsDatabase.markAsNoSession(smsMessageId.get());
    }
    triggerSessionRestorePrompt(sender);
  }

  private void triggerSessionRestorePrompt(@NonNull String sender) {
    Recipient primaryRecipient = getMasterRecipient(sender);
    if (!primaryRecipient.isGroupRecipient()) {
      long threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(primaryRecipient);
      DatabaseFactory.getLokiThreadDatabase(context).addSessionRestoreDevice(threadID, sender);
    }
  }

  private void handleLegacyMessage(@NonNull String sender, int senderDevice, long timestamp,
                                   @NonNull Optional<Long> smsMessageId)
  {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp);

      if (insertResult.isPresent()) {
        smsDatabase.markAsLegacyVersion(insertResult.get().getMessageId());
        MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsLegacyVersion(smsMessageId.get());
    }
  }

  @SuppressWarnings("unused")
  private void handleDuplicateMessage(@NonNull String sender, int senderDeviceId, long timestamp,
                                      @NonNull Optional<Long> smsMessageId)
  {
    // Let's start ignoring these now
//    SmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);
//
//    if (smsMessageId <= 0) {
//      Pair<Long, Long> messageAndThreadId = insertPlaceholder(masterSecret, envelope);
//      smsDatabase.markAsDecryptDuplicate(messageAndThreadId.first);
//      MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
//    } else {
//      smsDatabase.markAsDecryptDuplicate(smsMessageId);
//    }
  }

  private void handleProfileKey(@NonNull SignalServiceContent content,
                                @NonNull SignalServiceDataMessage message)
  {
    if (!message.getProfileKey().isPresent()) { return; }

    /*
    If we get a profile key then we don't need to map it to the primary device.
    For now a profile key is mapped one-to-one to avoid secondary devices setting the incorrect avatar for a primary device.
     */
    RecipientDatabase database      = DatabaseFactory.getRecipientDatabase(context);
    Recipient         recipient     = Recipient.from(context, Address.fromSerialized(content.getSender()), false);

    if (recipient.getProfileKey() == null || !MessageDigest.isEqual(recipient.getProfileKey(), message.getProfileKey().get())) {
      database.setProfileKey(recipient, message.getProfileKey().get());
      database.setUnidentifiedAccessMode(recipient, RecipientDatabase.UnidentifiedAccessMode.UNKNOWN);
      String url = content.senderProfilePictureURL.or("");
      ApplicationContext.getInstance(context).getJobManager().add(new RetrieveProfileAvatarJob(recipient, url));

      // Loki - If the recipient is our master device then we need to go and update our avatar mappings on the public chats
      if (recipient.isOurMasterDevice()) {
        ApplicationContext.getInstance(context).updatePublicChatProfilePictureIfNeeded();
      }
    }
  }

  private void handleNeedsDeliveryReceipt(@NonNull SignalServiceContent content,
                                          @NonNull SignalServiceDataMessage message)
  {
    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new SendDeliveryReceiptJob(Address.fromSerialized(content.getSender()), message.getTimestamp()));
  }

  @SuppressLint("DefaultLocale")
  private void handleDeliveryReceipt(@NonNull SignalServiceContent content,
                                     @NonNull SignalServiceReceiptMessage message)
  {
    // Redirect message to master device conversation
    Address sender = Address.fromSerialized(content.getSender());
    if (sender.isPhone()) {
      Recipient masterDevice = getMasterRecipient(content.getSender());
      sender = masterDevice.getAddress();
    }

    for (long timestamp : message.getTimestamps()) {
      Log.i(TAG, String.format("Received encrypted delivery receipt: (XXXXX, %d)", timestamp));
      DatabaseFactory.getMmsSmsDatabase(context)
                     .incrementDeliveryReceiptCount(new SyncMessageId(sender, timestamp), System.currentTimeMillis());
    }
  }

  @SuppressLint("DefaultLocale")
  private void handleReadReceipt(@NonNull SignalServiceContent content,
                                 @NonNull SignalServiceReceiptMessage message)
  {
    if (TextSecurePreferences.isReadReceiptsEnabled(context)) {

      // Redirect message to master device conversation
      Address sender = Address.fromSerialized(content.getSender());
      if (sender.isPhone()) {
        Recipient masterDevice = getMasterRecipient(content.getSender());
        sender = masterDevice.getAddress();
      }

      for (long timestamp : message.getTimestamps()) {
        Log.i(TAG, String.format("Received encrypted read receipt: (XXXXX, %d)", timestamp));

        DatabaseFactory.getMmsSmsDatabase(context)
                       .incrementReadReceiptCount(new SyncMessageId(sender, timestamp), content.getTimestamp());
      }
    }
  }

  private void handleTypingMessage(@NonNull SignalServiceContent content,
                                   @NonNull SignalServiceTypingMessage typingMessage)
  {
    if (!TextSecurePreferences.isTypingIndicatorsEnabled(context)) {
      return;
    }

    Recipient author = Recipient.from(context, Address.fromSerialized(content.getSender()), false);

    long threadId;

    if (typingMessage.getGroupId().isPresent()) {
      // Typing messages should only apply to signal groups, thus we use `getEncodedId`
      Address   groupAddress   = Address.fromSerialized(GroupUtil.getEncodedId(typingMessage.getGroupId().get(), false));
      Recipient groupRecipient = Recipient.from(context, groupAddress, false);

      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(groupRecipient);
    } else {
      // See if we need to redirect the message
      author = getMasterRecipient(content.getSender());
      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(author);
    }

    if (threadId <= 0) {
      Log.w(TAG, "Couldn't find a matching thread for a typing message.");
      return;
    }

    if (typingMessage.isTypingStarted()) {
      Log.d(TAG, "Typing started on thread " + threadId);
      ApplicationContext.getInstance(context).getTypingStatusRepository().onTypingStarted(context,threadId, author, content.getSenderDevice());
    } else {
      Log.d(TAG, "Typing stopped on thread " + threadId);
      ApplicationContext.getInstance(context).getTypingStatusRepository().onTypingStopped(context, threadId, author, content.getSenderDevice(), false);
    }
  }

  private Optional<QuoteModel> getValidatedQuote(Optional<SignalServiceDataMessage.Quote> quote) {
    if (!quote.isPresent()) return Optional.absent();

    if (quote.get().getId() <= 0) {
      Log.w(TAG, "Received quote without an ID! Ignoring...");
      return Optional.absent();
    }

    if (quote.get().getAuthor() == null) {
      Log.w(TAG, "Received quote without an author! Ignoring...");
      return Optional.absent();
    }

    Address       author  = Address.fromSerialized(quote.get().getAuthor().getNumber());
    MessageRecord message = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(quote.get().getId(), author);

    if (message != null) {
      Log.i(TAG, "Found matching message record...");

      List<Attachment> attachments = new LinkedList<>();

      if (message.isMms()) {
        MmsMessageRecord mmsMessage = (MmsMessageRecord) message;
        attachments = mmsMessage.getSlideDeck().asAttachments();
        if (attachments.isEmpty()) {
          attachments.addAll(Stream.of(mmsMessage.getLinkPreviews())
                                   .filter(lp -> lp.getThumbnail().isPresent())
                                   .map(lp -> lp.getThumbnail().get())
                                   .toList());
        }
      }

      return Optional.of(new QuoteModel(quote.get().getId(), author, message.getBody(), false, attachments));
    }

    Log.w(TAG, "Didn't find matching message record...");
    return Optional.of(new QuoteModel(quote.get().getId(),
                                      author,
                                      quote.get().getText(),
                                      true,
                                      PointerAttachment.forPointers(quote.get().getAttachments())));
  }

  private Optional<Attachment> getStickerAttachment(Optional<SignalServiceDataMessage.Sticker> sticker) {
    if (!sticker.isPresent()) {
      return Optional.absent();
    }

    if (sticker.get().getPackId() == null || sticker.get().getPackKey() == null || sticker.get().getAttachment() == null) {
      Log.w(TAG, "Malformed sticker!");
      return Optional.absent();
    }

    String          packId          = Hex.toStringCondensed(sticker.get().getPackId());
    String          packKey         = Hex.toStringCondensed(sticker.get().getPackKey());
    int             stickerId       = sticker.get().getStickerId();
    StickerLocator  stickerLocator  = new StickerLocator(packId, packKey, stickerId);
    StickerDatabase stickerDatabase = DatabaseFactory.getStickerDatabase(context);
    StickerRecord   stickerRecord   = stickerDatabase.getSticker(stickerLocator.getPackId(), stickerLocator.getStickerId(), false);

    if (stickerRecord != null) {
      return Optional.of(new UriAttachment(stickerRecord.getUri(),
                                           stickerRecord.getUri(),
                                           MediaUtil.IMAGE_WEBP,
                                           AttachmentDatabase.TRANSFER_PROGRESS_DONE,
                                           stickerRecord.getSize(),
                                           StickerSlide.WIDTH,
                                           StickerSlide.HEIGHT,
                                           null,
                                           String.valueOf(new SecureRandom().nextLong()),
                                           false,
                                           false,
                                           null,
                                           stickerLocator));
    } else {
      return Optional.of(PointerAttachment.forPointer(Optional.of(sticker.get().getAttachment()), stickerLocator).get());
    }
  }

  private Optional<List<Contact>> getContacts(Optional<List<SharedContact>> sharedContacts) {
    if (!sharedContacts.isPresent()) return Optional.absent();

    List<Contact> contacts = new ArrayList<>(sharedContacts.get().size());

    for (SharedContact sharedContact : sharedContacts.get()) {
      contacts.add(ContactModelMapper.remoteToLocal(sharedContact));
    }

    return Optional.of(contacts);
  }

  private Optional<List<LinkPreview>> getLinkPreviews(Optional<List<Preview>> previews, @NonNull String message) {
    if (!previews.isPresent()) return Optional.absent();

    List<LinkPreview> linkPreviews = new ArrayList<>(previews.get().size());

    for (Preview preview : previews.get()) {
      Optional<Attachment> thumbnail     = PointerAttachment.forPointer(preview.getImage());
      Optional<String>     url           = Optional.fromNullable(preview.getUrl());
      Optional<String>     title         = Optional.fromNullable(preview.getTitle());
      boolean              hasContent    = !TextUtils.isEmpty(title.or("")) || thumbnail.isPresent();
      boolean              presentInBody = url.isPresent() && Stream.of(LinkPreviewUtil.findWhitelistedUrls(message)).map(Link::getUrl).collect(Collectors.toSet()).contains(url.get());
      boolean              validDomain   = url.isPresent() && LinkPreviewUtil.isWhitelistedLinkUrl(url.get());

      if (hasContent && presentInBody && validDomain) {
        LinkPreview linkPreview = new LinkPreview(url.get(), title.or(""), thumbnail);
        linkPreviews.add(linkPreview);
      } else {
        Log.w(TAG, String.format("Discarding an invalid link preview. hasContent: %b presentInBody: %b validDomain: %b", hasContent, presentInBody, validDomain));
      }
    }

    return Optional.of(linkPreviews);
  }

  private Optional<InsertResult> insertPlaceholder(@NonNull String sender, int senderDevice, long timestamp) {
    Recipient           masterDevice = getMasterRecipient(sender);
    SmsDatabase         database     = DatabaseFactory.getSmsDatabase(context);
    IncomingTextMessage textMessage  = new IncomingTextMessage(masterDevice.getAddress(),
                                                              senderDevice, timestamp, "",
                                                              Optional.absent(), 0, false);

    textMessage = new IncomingEncryptedMessage(textMessage, "");
    return database.insertMessageInbox(textMessage);
  }

  private Recipient getSyncMessageDestination(SentTranscriptMessage message) {
    if (message.getMessage().isGroupMessage()) {
      return Recipient.from(context, Address.fromSerialized(GroupUtil.getEncodedId(message.getMessage().getGroupInfo().get())), false);
    } else {
      return Recipient.from(context, Address.fromSerialized(message.getDestination().get()), false);
    }
  }

  private Recipient getSyncMessagePrimaryDestination(SentTranscriptMessage message) {
    if (message.getMessage().isGroupMessage()) {
      return getSyncMessageDestination(message);
    } else {
      return getMasterRecipient(message.getDestination().get());
    }
  }

  private Recipient getRecipientForMessage(SignalServiceContent content, SignalServiceDataMessage message) {
    if (message.isGroupMessage()) {
      return Recipient.from(context, Address.fromSerialized(GroupUtil.getEncodedId(message.getGroupInfo().get())), false);
    } else {
      return Recipient.from(context, Address.fromSerialized(content.getSender()), false);
    }
  }

  private Recipient getMasterRecipientForMessage(SignalServiceContent content, SignalServiceDataMessage message) {
    if (message.isGroupMessage()) {
      return getRecipientForMessage(content, message);
    } else {
      return getMasterRecipient(content.getSender());
    }
  }

  /**
   * Get the master device recipient of the provided device.
   *
   * If the device doesn't have a master device this will return the same device.
   * If the device is our master device then it will return our current device.
   * Otherwise it will return the master device.
   */
  private Recipient getMasterRecipient(String hexEncodedPublicKey) {
    try {
      String masterHexEncodedPublicKey = PromiseUtil.timeout(LokiDeviceLinkUtilities.INSTANCE.getMasterHexEncodedPublicKey(hexEncodedPublicKey), 5000).get();
      String targetHexEncodedPublicKey = (masterHexEncodedPublicKey != null) ? masterHexEncodedPublicKey : hexEncodedPublicKey;
      // If the public key matches our master device then we need to forward the message to ourselves (note to self)
      String ourMasterHexEncodedPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context);
      if (ourMasterHexEncodedPublicKey != null && ourMasterHexEncodedPublicKey.equals(targetHexEncodedPublicKey)) {
        targetHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context);
      }
      return Recipient.from(context, Address.fromSerialized(targetHexEncodedPublicKey), false);
    } catch (Exception e) {
      Log.d("Loki", "Failed to get master device for: " + hexEncodedPublicKey + ". " + e.getMessage());
      return Recipient.from(context, Address.fromSerialized(hexEncodedPublicKey), false);
    }
  }

  private void notifyTypingStoppedFromIncomingMessage(@NonNull Recipient conversationRecipient, @NonNull String sender, int device) {
    Recipient author   = Recipient.from(context, Address.fromSerialized(sender), false);
    long      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(conversationRecipient);

    if (threadId > 0) {
      Log.d(TAG, "Typing stopped on thread " + threadId + " due to an incoming message.");
      ApplicationContext.getInstance(context).getTypingStatusRepository().onTypingStopped(context, threadId, author, device, true);
    }
  }

  private boolean shouldIgnore(@Nullable SignalServiceContent content) {
    if (content == null) {
      Log.w(TAG, "Got a message with null content.");
      return true;
    }

    Recipient sender = Recipient.from(context, Address.fromSerialized(content.getSender()), false);

    if (content.getDeviceLink().isPresent()) {
      return false;
    } else if (content.getDataMessage().isPresent()) {
      SignalServiceDataMessage message      = content.getDataMessage().get();
      Recipient                conversation = getRecipientForMessage(content, message);

      if (conversation.isGroupRecipient() && conversation.isBlocked()) {
        return true;
      } else if (conversation.isGroupRecipient()) {
        GroupDatabase    groupDatabase = DatabaseFactory.getGroupDatabase(context);
        Optional<String> groupId       = message.getGroupInfo().isPresent() ? Optional.of(GroupUtil.getEncodedId(message.getGroupInfo().get()))
                                                                            : Optional.absent();

        if (groupId.isPresent() && groupDatabase.isUnknownGroup(groupId.get())) {
          return false;
        }

        boolean isTextMessage    = message.getBody().isPresent();
        boolean isMediaMessage   = message.getAttachments().isPresent() || message.getQuote().isPresent() || message.getSharedContacts().isPresent();
        boolean isExpireMessage  = message.isExpirationUpdate();
        boolean isContentMessage = !message.isGroupUpdate() && (isTextMessage || isMediaMessage || isExpireMessage);
        boolean isGroupActive    = groupId.isPresent() && groupDatabase.isActive(groupId.get());
        boolean isLeaveMessage   = message.getGroupInfo().isPresent() && message.getGroupInfo().get().getType() == SignalServiceGroup.Type.QUIT;

        return (isContentMessage && !isGroupActive) || (sender.isBlocked() && !isLeaveMessage);
      } else {
        return sender.isBlocked();
      }
    } else if (content.getCallMessage().isPresent() || content.getTypingMessage().isPresent()) {
      return sender.isBlocked();
    } else if (content.getSyncMessage().isPresent()) {
      try {
        // We should ignore a sync message if the sender is not one of our devices
        boolean isOurDevice = PromiseUtil.timeout(MultiDeviceUtilities.isOneOfOurDevices(context, sender.getAddress()), 5000).get();
        if (!isOurDevice) {
          Log.w(TAG, "Got a sync message from a device that is not ours!.");
        }
        return !isOurDevice;
      } catch (Exception e) {
        return true;
      }
    }

    return false;
  }

  private boolean isSessionRequest(SignalServiceContent content) {
    return content.getDataMessage().isPresent() && content.getDataMessage().get().isSessionRequest();
  }

  private boolean isGroupChatMessage(SignalServiceContent content) {
    return content.getDataMessage().isPresent() && content.getDataMessage().get().isGroupMessage();
  }

  private void resetRecipientToPush(@NonNull Recipient recipient) {
    if (recipient.isForceSmsSelection()) {
      DatabaseFactory.getRecipientDatabase(context).setForceSmsSelection(recipient, false);
    }
  }

  private void forceStickerDownloadIfNecessary(List<DatabaseAttachment> stickerAttachments) {
    if (stickerAttachments.isEmpty()) return;

    DatabaseAttachment stickerAttachment = stickerAttachments.get(0);

    if (stickerAttachment.getTransferState() != AttachmentDatabase.TRANSFER_PROGRESS_DONE) {
      AttachmentDownloadJob downloadJob = new AttachmentDownloadJob(messageId, stickerAttachment.getAttachmentId(), true);

      try {
        ApplicationContext.getInstance(context).injectDependencies(downloadJob);
        downloadJob.setContext(context);
        downloadJob.doWork();
      } catch (Exception e) {
        Log.w(TAG, "Failed to download sticker inline. Scheduling.");
        ApplicationContext.getInstance(context).getJobManager().add(downloadJob);
      }
    }
  }

  @SuppressWarnings("WeakerAccess")
  private static class StorageFailedException extends Exception {
    private final String sender;
    private final int senderDevice;

    private StorageFailedException(Exception e, String sender, int senderDevice) {
      super(e);
      this.sender       = sender;
      this.senderDevice = senderDevice;
    }

    public String getSender() {
      return sender;
    }

    public int getSenderDevice() {
      return senderDevice;
    }
  }

  public static final class Factory implements Job.Factory<PushDecryptJob> {
    @Override
    public @NonNull PushDecryptJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new PushDecryptJob(parameters, data.getLong(KEY_MESSAGE_ID), data.getLong(KEY_SMS_MESSAGE_ID));
    }
  }
}
