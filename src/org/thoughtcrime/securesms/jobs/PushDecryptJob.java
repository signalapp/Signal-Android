package org.thoughtcrime.securesms.jobs;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.TextUtils;
import android.util.Pair;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.google.android.gms.common.util.IOUtils;

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
import org.thoughtcrime.securesms.ConversationListActivity;
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
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.groups.GroupMessageProcessor;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.linkpreview.Link;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.loki.FriendRequestHandler;
import org.thoughtcrime.securesms.loki.LokiAPIUtilities;
import org.thoughtcrime.securesms.loki.LokiMessageDatabase;
import org.thoughtcrime.securesms.loki.LokiPreKeyBundleDatabase;
import org.thoughtcrime.securesms.loki.LokiPreKeyRecordDatabase;
import org.thoughtcrime.securesms.loki.LokiThreadDatabase;
import org.thoughtcrime.securesms.loki.MultiDeviceUtilities;
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
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
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
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.loki.api.DeviceLinkingSession;
import org.whispersystems.signalservice.loki.api.LokiAPI;
import org.whispersystems.signalservice.loki.api.LokiStorageAPI;
import org.whispersystems.signalservice.loki.api.PairingAuthorisation;
import org.whispersystems.signalservice.loki.crypto.LokiServiceCipher;
import org.whispersystems.signalservice.loki.messaging.LokiMessageFriendRequestStatus;
import org.whispersystems.signalservice.loki.messaging.LokiServiceMessage;
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus;
import org.whispersystems.signalservice.loki.messaging.LokiThreadSessionResetStatus;
import org.whispersystems.signalservice.loki.utilities.PromiseUtil;

import java.io.IOException;
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
                                                                         .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, ConversationListActivity.class), 0))
                                                                         .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE)
                                                                         .build());

  }

  private void handleMessage(@NonNull SignalServiceEnvelope envelope, @NonNull Optional<Long> smsMessageId) {
    try {
      GroupDatabase            groupDatabase            = DatabaseFactory.getGroupDatabase(context);
      SignalProtocolStore      axolotlStore             = new SignalProtocolStoreImpl(context);
      LokiThreadDatabase       lokiThreadDatabase       = DatabaseFactory.getLokiThreadDatabase(context);
      LokiPreKeyRecordDatabase lokiPreKeyRecordDatabase = DatabaseFactory.getLokiPreKeyRecordDatabase(context);
      SignalServiceAddress     localAddress             = new SignalServiceAddress(TextSecurePreferences.getLocalNumber(context));
      LokiServiceCipher        cipher                   = new LokiServiceCipher(localAddress, axolotlStore, lokiThreadDatabase, lokiPreKeyRecordDatabase, UnidentifiedAccessUtil.getCertificateValidator());

      // Loki - Handle session reset logic
      if (!envelope.isFriendRequest() && cipher.getSessionStatus(envelope) == null && envelope.isPreKeySignalMessage()) {
        cipher.validateBackgroundMessage(envelope, envelope.getContent());
      }

      SignalServiceContent content = cipher.decrypt(envelope);

      if (shouldIgnore(content)) {
        Log.i(TAG, "Ignoring message.");
        return;
      }

      // Loki - Handle friend request acceptance if needed
      acceptFriendRequestIfNeeded(envelope, content);

      // Loki - Store pre key bundle if needed
      if (content.lokiServiceMessage.isPresent()) {
        LokiServiceMessage lokiMessage = content.lokiServiceMessage.get();
        if (lokiMessage.getPreKeyBundleMessage() != null) {
          int registrationID = TextSecurePreferences.getLocalRegistrationId(context);
          LokiPreKeyBundleDatabase lokiPreKeyBundleDatabase = DatabaseFactory.getLokiPreKeyBundleDatabase(context);

          // Only store the pre key bundle if we don't have one in our database
          if (registrationID > 0 && !lokiPreKeyBundleDatabase.hasPreKeyBundle(envelope.getSource())) {
            Log.d("Loki", "Received a pre key bundle from: " + envelope.getSource() + ".");
            PreKeyBundle preKeyBundle = lokiMessage.getPreKeyBundleMessage().getPreKeyBundle(registrationID);
            lokiPreKeyBundleDatabase.setPreKeyBundle(envelope.getSource(), preKeyBundle);
          }
        }

        if (lokiMessage.getAddressMessage() != null) {
          // TODO: Loki - Handle address message
        }
      }

      // Loki - Store the sender display name if needed
      Optional<String> rawSenderDisplayName = content.senderDisplayName;
      if (rawSenderDisplayName.isPresent() && rawSenderDisplayName.get().length() > 0) {
        setDisplayName(envelope.getSource(), rawSenderDisplayName.get());

        // If we got a name from our primary device then we also set that
        String ourPrimaryDevice = TextSecurePreferences.getMasterHexEncodedPublicKey(context);
        if (ourPrimaryDevice != null && envelope.getSource().equals(ourPrimaryDevice)) {
          TextSecurePreferences.setProfileName(context, rawSenderDisplayName.get());
        }
      }

      // TODO: Deleting the display name
      if (content.getPairingAuthorisation().isPresent()) {
        handlePairingMessage(content.getPairingAuthorisation().get(), envelope, content);
      } else if (content.getDataMessage().isPresent()) {
        SignalServiceDataMessage message        = content.getDataMessage().get();
        boolean                  isMediaMessage = message.getAttachments().isPresent() || message.getQuote().isPresent() || message.getSharedContacts().isPresent() || message.getPreviews().isPresent() || message.getSticker().isPresent();

        if      (message.isEndSession())        handleEndSessionMessage(content, smsMessageId);
        else if (message.isGroupUpdate())       handleGroupMessage(content, message, smsMessageId);
        else if (message.isExpirationUpdate())  handleExpirationUpdate(content, message, smsMessageId);
        else if (isMediaMessage)                handleMediaMessage(content, message, smsMessageId, Optional.absent());
        else if (message.getBody().isPresent()) handleTextMessage(content, message, smsMessageId, Optional.absent());

        if (message.getGroupInfo().isPresent() && groupDatabase.isUnknownGroup(GroupUtil.getEncodedId(message.getGroupInfo().get().getGroupId(), false))) {
          handleUnknownGroupMessage(content, message.getGroupInfo().get());
        }

        if (message.getProfileKey().isPresent() && message.getProfileKey().get().length == 32) {
          handleProfileKey(content, message);
        }

        // Loki - This doesn't get invoked for group chats
        if (content.isNeedsReceipt()) {
          handleNeedsDeliveryReceipt(content, message);
        }

        // Loki - Handle friend request logic if needed
        updateFriendRequestStatusIfNeeded(envelope, content, message);
      } else if (content.getSyncMessage().isPresent()) {
        TextSecurePreferences.setMultiDevice(context, true);

        SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();

        if      (syncMessage.getSent().isPresent())                  handleSynchronizeSentMessage(content, syncMessage.getSent().get());
        else if (syncMessage.getRequest().isPresent())               handleSynchronizeRequestMessage(syncMessage.getRequest().get());
        else if (syncMessage.getRead().isPresent())                  handleSynchronizeReadMessage(syncMessage.getRead().get(), content.getTimestamp());
        else if (syncMessage.getVerified().isPresent())              handleSynchronizeVerifiedMessage(syncMessage.getVerified().get());
        else if (syncMessage.getStickerPackOperations().isPresent()) handleSynchronizeStickerPackOperation(syncMessage.getStickerPackOperations().get());
        else if (syncMessage.getContacts().isPresent())              handleSynchronizeContactMessage(syncMessage.getContacts().get());
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

      // Loki - Handle session reset logic
      if (!envelope.isFriendRequest()) {
        cipher.handleSessionResetRequestIfNeeded(envelope, cipher.getSessionStatus(envelope));
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
      TextSecureSessionStore sessionStore = new TextSecureSessionStore(context);
      LokiThreadDatabase lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(context);

      Log.d("Loki", "Received a session reset request from: " + content.getSender() + "; archiving the session.");

      sessionStore.archiveAllSessions(content.getSender());
      lokiThreadDatabase.setSessionResetStatus(threadId, LokiThreadSessionResetStatus.REQUEST_RECEIVED);

      Log.d("Loki", "Sending a ping back to " + content.getSender() + ".");
      String contactID = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId).getAddress().toString();
      MessageSender.sendBackgroundMessage(context, contactID);

      SecurityEvent.broadcastSecurityUpdateEvent(context);
      MessageNotifier.updateNotification(context, threadId);
    }
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

    if (message.getExpiresInSeconds() != 0 && message.getExpiresInSeconds() != getMessageDestination(content, message).getExpireMessages()) {
      handleExpirationUpdate(content, message, Optional.absent());
    }

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }
  }

  private void handleUnknownGroupMessage(@NonNull SignalServiceContent content,
                                         @NonNull SignalServiceGroup group)
  {
    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new RequestGroupInfoJob(content.getSender(), group.getGroupId()));
  }

  private void handleExpirationUpdate(@NonNull SignalServiceContent content,
                                      @NonNull SignalServiceDataMessage message,
                                      @NonNull Optional<Long> smsMessageId)
      throws StorageFailedException
  {
    try {
      MmsDatabase          database     = DatabaseFactory.getMmsDatabase(context);
      Recipient            recipient    = getMessageDestination(content, message);
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

  private void handleSynchronizeContactMessage(@NonNull ContactsMessage contactsMessage) {
    if (contactsMessage.getContactsStream().isStream()) {
      Log.d("Loki", "Received contact sync message");

      try {
        InputStream in = contactsMessage.getContactsStream().asStream().getInputStream();
        DeviceContactsInputStream contactsInputStream = new DeviceContactsInputStream(in);
        List<DeviceContact> devices = contactsInputStream.readAll();
        for (DeviceContact deviceContact : devices) {
          // Check if we have the contact as a friend and that we're not trying to sync our own device
          String pubKey = deviceContact.getNumber();
          Address address = Address.fromSerialized(pubKey);
          if (!address.isPhone() || address.toPhoneString().equals(TextSecurePreferences.getLocalNumber(context))) { continue; }

          /*
          If we're not friends with the contact we received or our friend request expired then we should send them a friend request
          otherwise if we have received a friend request with from them then we should automatically accept the friend request
           */
          Recipient recipient = Recipient.from(context, address, false);
          long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
          LokiThreadFriendRequestStatus status = DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadId);
          if (status == LokiThreadFriendRequestStatus.NONE || status == LokiThreadFriendRequestStatus.REQUEST_EXPIRED) {
            MessageSender.sendBackgroundFriendRequest(context, pubKey, "Please accept to enable messages to be synced across devices");
            Log.d("Loki", "Sent friend request to " + pubKey);
          } else if (status == LokiThreadFriendRequestStatus.REQUEST_RECEIVED) {
            // Accept the incoming friend request
            becomeFriendsWithContact(pubKey, false);
            // Send them an accept message back
            MessageSender.sendBackgroundMessage(context, pubKey);
            Log.d("Loki", "Became friends with " + deviceContact.getNumber());
          }

          // TODO: Handle blocked - If user is not blocked then we should do the friend request logic otherwise add them to our block list
          // TODO: Handle expiration timer - Update expiration timer?
          // TODO: Handle avatar - Download and set avatar?
        }
      } catch (Exception e) {
        Log.d("Loki", "Failed to sync contact: " + e);
      }
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

      if (message.getMessage().getGroupInfo().isPresent() && groupDatabase.isUnknownGroup(GroupUtil.getEncodedId(message.getMessage().getGroupInfo().get().getGroupId(), false))) {
        handleUnknownGroupMessage(content, message.getMessage().getGroupInfo().get());
      }

      if (message.getMessage().getProfileKey().isPresent()) {
        Recipient recipient = null;

        if      (message.getDestination().isPresent())            recipient = Recipient.from(context, Address.fromSerialized(message.getDestination().get()), false);
        else if (message.getMessage().getGroupInfo().isPresent()) recipient = Recipient.from(context, Address.fromSerialized(GroupUtil.getEncodedId(message.getMessage().getGroupInfo().get().getGroupId(), false)), false);


        if (recipient != null && !recipient.isSystemContact() && !recipient.isProfileSharing()) {
          DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipient, true);
        }
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
    Recipient originalRecipient = getMessageDestination(content, message);
    Recipient primaryDeviceRecipient = getMessagePrimaryDestination(content, message);

    notifyTypingStoppedFromIncomingMessage(primaryDeviceRecipient, content.getSender(), content.getSenderDevice());

    Optional<QuoteModel>        quote          = getValidatedQuote(message.getQuote());
    Optional<List<Contact>>     sharedContacts = getContacts(message.getSharedContacts());
    Optional<List<LinkPreview>> linkPreviews   = getLinkPreviews(message.getPreviews(), message.getBody().or(""));
    Optional<Attachment>        sticker        = getStickerAttachment(message.getSticker());

    // If message is from group then we need to map it to the correct sender
    Address sender = message.isGroupUpdate() ? Address.fromSerialized(content.getSender()) : primaryDeviceRecipient.getAddress();
    IncomingMediaMessage        mediaMessage   = new IncomingMediaMessage(sender, message.getTimestamp(), -1,
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

    // Loki - Store message server ID
    updateGroupChatMessageServerID(messageServerIDOrNull, insertResult);

    // Loki - Update mapping of message to original thread id
    if (insertResult.isPresent()) {
      MessageNotifier.updateNotification(context, insertResult.get().getThreadId());

      ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
      LokiMessageDatabase lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(context);
      long originalThreadId = threadDatabase.getThreadIdFor(originalRecipient);
      lokiMessageDatabase.setOriginalThreadID(insertResult.get().getMessageId(), originalThreadId);
    }
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
    SmsDatabase database  = DatabaseFactory.getSmsDatabase(context);
    String      body      = message.getBody().isPresent() ? message.getBody().get() : "";
    Recipient   originalRecipient = getMessageDestination(content, message);
    Recipient   primaryDeviceRecipient = getMessagePrimaryDestination(content, message);

    if (message.getExpiresInSeconds() != originalRecipient.getExpireMessages()) {
      handleExpirationUpdate(content, message, Optional.absent());
    }

    Long threadId = null;

    if (smsMessageId.isPresent() && !message.getGroupInfo().isPresent()) {
      threadId = database.updateBundleMessageBody(smsMessageId.get(), body).second;
    } else {
      notifyTypingStoppedFromIncomingMessage(primaryDeviceRecipient, content.getSender(), content.getSenderDevice());

      // If message is from group then we need to map it to the correct sender
      Address sender = message.isGroupUpdate() ? Address.fromSerialized(content.getSender()) : primaryDeviceRecipient.getAddress();
      IncomingTextMessage _textMessage = new IncomingTextMessage(sender,
                                                                content.getSenderDevice(),
                                                                message.getTimestamp(), body,
                                                                message.getGroupInfo(),
                                                                message.getExpiresInSeconds() * 1000L,
                                                                content.isNeedsReceipt());

      IncomingEncryptedMessage textMessage = new IncomingEncryptedMessage(_textMessage, body);

      // Ignore the message if the body is empty
      if (textMessage.getMessageBody().length() == 0) { return; }

      // Insert the message into the database
      Optional<InsertResult> insertResult = database.insertMessageInbox(textMessage);

      Long messageId = null;
      if (insertResult.isPresent()) {
        threadId = insertResult.get().getThreadId();
        messageId = insertResult.get().getMessageId();
      }

      if (smsMessageId.isPresent()) database.deleteMessage(smsMessageId.get());

      // Loki - Cache the user hex encoded public key (for mentions)
      if (threadId != null) {
        LokiAPIUtilities.INSTANCE.populateUserHexEncodedPublicKeyCacheIfNeeded(threadId, context);
        LokiAPI.Companion.cache(textMessage.getSender().serialize(), threadId);
      }

      // Loki - Store message server ID
      updateGroupChatMessageServerID(messageServerIDOrNull, insertResult);

      // Loki - Update mapping of message to original thread id
      if (messageId != null) {
        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
        LokiMessageDatabase lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(context);
        long originalThreadId = threadDatabase.getThreadIdFor(originalRecipient);
        lokiMessageDatabase.setOriginalThreadID(messageId, originalThreadId);
      }

      boolean isGroupMessage = message.getGroupInfo().isPresent();
      if (threadId != null && !isGroupMessage) {
        MessageNotifier.updateNotification(context, threadId);
      }
    }
  }

  private boolean isValidPairingMessage(@NonNull PairingAuthorisation authorisation) {
    boolean isSecondaryDevice = TextSecurePreferences.getMasterHexEncodedPublicKey(context) != null;
    String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context);
    boolean isRequest = (authorisation.getType() == PairingAuthorisation.Type.REQUEST);
    if (authorisation.getRequestSignature() == null) {
      Log.d("Loki", "Ignoring pairing request message without a request signature.");
      return false;
    } else if (isRequest && isSecondaryDevice) {
      Log.d("Loki", "Ignoring unexpected pairing request message (the device is already paired as a secondary device).");
      return false;
    } else if (isRequest && !authorisation.getPrimaryDevicePublicKey().equals(userHexEncodedPublicKey)) {
      Log.d("Loki", "Ignoring pairing request message addressed to another user.");
      return false;
    } else if (isRequest && authorisation.getSecondaryDevicePublicKey().equals(userHexEncodedPublicKey)) {
      Log.d("Loki", "Ignoring pairing request message from self.");
      return false;
    }
    return authorisation.verify();
  }

  private void handlePairingMessage(@NonNull PairingAuthorisation authorisation, @NonNull SignalServiceEnvelope envelope, @NonNull SignalServiceContent content) {
    String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context);
    if (authorisation.getType() == PairingAuthorisation.Type.REQUEST) {
      handlePairingRequestMessage(authorisation);
    } else if (authorisation.getSecondaryDevicePublicKey().equals(userHexEncodedPublicKey)) {
      handlePairingAuthorisationMessage(authorisation, envelope, content);
    }
  }

  private void handlePairingRequestMessage(@NonNull PairingAuthorisation authorisation) {
    boolean isValid = isValidPairingMessage(authorisation);
    DeviceLinkingSession linkingSession = DeviceLinkingSession.Companion.getShared();
    if (isValid && linkingSession.isListeningForLinkingRequests()) {
      linkingSession.processLinkingRequest(authorisation);
    }
  }

  private void handlePairingAuthorisationMessage(@NonNull PairingAuthorisation authorisation, @NonNull SignalServiceEnvelope envelope, @NonNull SignalServiceContent content) {
    // Prepare
    boolean isSecondaryDevice = TextSecurePreferences.getMasterHexEncodedPublicKey(context) != null;
    if (isSecondaryDevice) {
      Log.d("Loki", "Ignoring unexpected pairing authorisation message (the device is already paired as a secondary device).");
      return;
    }
    boolean isValid = isValidPairingMessage(authorisation);
    if (!isValid) {
      Log.d("Loki", "Ignoring invalid pairing authorisation message.");
      return;
    }
    if (!DeviceLinkingSession.Companion.getShared().isListeningForLinkingRequests()) {
      Log.d("Loki", "Ignoring pairing authorisation message.");
      return;
    }
    if (authorisation.getType() != PairingAuthorisation.Type.GRANT) { return; }
    Log.d("Loki", "Received pairing authorisation message from: " + authorisation.getPrimaryDevicePublicKey() + ".");
    // Process
    DeviceLinkingSession.Companion.getShared().processLinkingAuthorization(authorisation);
    // Store the primary device's public key
    String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context);
    DatabaseFactory.getLokiAPIDatabase(context).removePairingAuthorisations(userHexEncodedPublicKey);
    DatabaseFactory.getLokiAPIDatabase(context).insertOrUpdatePairingAuthorisation(authorisation);
    TextSecurePreferences.setMasterHexEncodedPublicKey(context, authorisation.getPrimaryDevicePublicKey());
    TextSecurePreferences.setMultiDevice(context, true);
    // Send a background message to the primary device
    MessageSender.sendBackgroundMessage(context, authorisation.getPrimaryDevicePublicKey());
    // Propagate the updates to the file server
    LokiStorageAPI storageAPI = LokiStorageAPI.Companion.getShared();
    storageAPI.updateUserDeviceMappings();
    // Update display names
    if (content.senderDisplayName.isPresent() && content.senderDisplayName.get().length() > 0) {
        TextSecurePreferences.setProfileName(context, content.senderDisplayName.get());
    }

    // Contact sync
    if (content.getSyncMessage().isPresent() && content.getSyncMessage().get().getContacts().isPresent()) {
      handleSynchronizeContactMessage(content.getSyncMessage().get().getContacts().get());
    }
  }

  private void setDisplayName(String hexEncodedPublicKey, String profileName) {
    String displayName = profileName + " (..." + hexEncodedPublicKey.substring(hexEncodedPublicKey.length() - 8) + ")";
    DatabaseFactory.getLokiUserDatabase(context).setDisplayName(hexEncodedPublicKey, displayName);
  }

  private void updateGroupChatMessageServerID(Optional<Long> messageServerIDOrNull, Optional<InsertResult> insertResult) {
    if (insertResult.isPresent() && messageServerIDOrNull.isPresent()) {
      long messageID = insertResult.get().getMessageId();
      long messageServerID = messageServerIDOrNull.get();
      DatabaseFactory.getLokiMessageDatabase(context).setServerID(messageID, messageServerID);
    }
  }

  private void acceptFriendRequestIfNeeded(@NonNull SignalServiceEnvelope envelope, @NonNull SignalServiceContent content) {
    // If we get anything other than a friend request, we can assume that we have a session with the other user
    if (envelope.isFriendRequest() || isGroupChatMessage(content)) { return; }
    becomeFriendsWithContact(content.getSender(), true);
  }

  private void becomeFriendsWithContact(String pubKey, boolean syncContact) {
    LokiThreadDatabase lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(context);
    Recipient contactID = Recipient.from(context, Address.fromSerialized(pubKey), false);
    if (contactID.isGroupRecipient()) return;

    long threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(contactID);
    LokiThreadFriendRequestStatus threadFriendRequestStatus = lokiThreadDatabase.getFriendRequestStatus(threadID);
    if (threadFriendRequestStatus == LokiThreadFriendRequestStatus.FRIENDS) { return; }
    // If the thread's friend request status is not `FRIENDS`, but we're receiving a message,
    // it must be a friend request accepted message. Declining a friend request doesn't send a message.
    lokiThreadDatabase.setFriendRequestStatus(threadID, LokiThreadFriendRequestStatus.FRIENDS);
    // Send out a contact sync message
    if (syncContact) {
      MessageSender.syncContact(context, contactID.getAddress());
    }
    // Update the last message if needed
    LokiStorageAPI.shared.getPrimaryDevicePublicKey(pubKey).success(primaryDevice -> {
      Util.runOnMain(() -> {
        long primaryDeviceThreadID = primaryDevice == null ? threadID : DatabaseFactory.getThreadDatabase(context).getThreadIdFor(Recipient.from(context, Address.fromSerialized(primaryDevice), false));
        FriendRequestHandler.updateLastFriendRequestMessage(context, primaryDeviceThreadID, LokiMessageFriendRequestStatus.REQUEST_ACCEPTED);
      });
      return Unit.INSTANCE;
    });
  }

  private void updateFriendRequestStatusIfNeeded(@NonNull SignalServiceEnvelope envelope, @NonNull SignalServiceContent content, @NonNull SignalServiceDataMessage message) {
    if (!envelope.isFriendRequest() || message.isGroupUpdate()) { return; }
    // This handles the case where another user sends us a regular message without authorisation
    boolean shouldBecomeFriends = PromiseUtil.get(MultiDeviceUtilities.shouldAutomaticallyBecomeFriendsWithDevice(content.getSender(), context), false);
    if (shouldBecomeFriends) {
      // Become friends AND update the message they sent
      becomeFriendsWithContact(content.getSender(), true);
      // Send them an accept message back
      MessageSender.sendBackgroundMessage(context, content.getSender());
    } else {
      // Do regular friend request logic checks
      Recipient originalRecipient = getMessageDestination(content, message);
      Recipient primaryDeviceRecipient = getMessagePrimaryDestination(content, message);
      LokiThreadDatabase lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(context);

      // Loki - Friend requests only work in direct chats
      if (!originalRecipient.getAddress().isPhone()) { return; }

      long threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(originalRecipient);
      long primaryDeviceThreadID = DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(primaryDeviceRecipient);
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

  private void handleCorruptMessage(@NonNull String sender, int senderDevice, long timestamp,
                                    @NonNull Optional<Long> smsMessageId)
  {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp);

      if (insertResult.isPresent()) {
        smsDatabase.markAsDecryptFailed(insertResult.get().getMessageId());
        MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsDecryptFailed(smsMessageId.get());
    }
  }

  private void handleNoSessionMessage(@NonNull String sender, int senderDevice, long timestamp,
                                      @NonNull Optional<Long> smsMessageId)
  {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp);

      if (insertResult.isPresent()) {
        smsDatabase.markAsNoSession(insertResult.get().getMessageId());
        MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsNoSession(smsMessageId.get());
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
    RecipientDatabase database      = DatabaseFactory.getRecipientDatabase(context);
    Address           sourceAddress = Address.fromSerialized(content.getSender());
    Recipient         recipient     = Recipient.from(context, sourceAddress, false);

    if (recipient.getProfileKey() == null || !MessageDigest.isEqual(recipient.getProfileKey(), message.getProfileKey().get())) {
      database.setProfileKey(recipient, message.getProfileKey().get());
      database.setUnidentifiedAccessMode(recipient, RecipientDatabase.UnidentifiedAccessMode.UNKNOWN);
      ApplicationContext.getInstance(context).getJobManager().add(new RetrieveProfileJob(recipient));
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
    // Redirect message to primary device conversation
    Address sender = Address.fromSerialized(content.getSender());
    if (sender.isPhone()) {
      Recipient primaryDevice = getPrimaryDeviceRecipient(content.getSender());
      sender = primaryDevice.getAddress();
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

      // Redirect message to primary device conversation
      Address sender = Address.fromSerialized(content.getSender());
      if (sender.isPhone()) {
        Recipient primaryDevice = getPrimaryDeviceRecipient(content.getSender());
        sender = primaryDevice.getAddress();
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
      Address   groupAddress   = Address.fromSerialized(GroupUtil.getEncodedId(typingMessage.getGroupId().get(), false));
      Recipient groupRecipient = Recipient.from(context, groupAddress, false);

      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
    } else {
      // See if we need to redirect the message
      author = getPrimaryDeviceRecipient(content.getSender());
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
    Recipient primaryDevice = getPrimaryDeviceRecipient(sender);
    SmsDatabase         database    = DatabaseFactory.getSmsDatabase(context);
    IncomingTextMessage textMessage = new IncomingTextMessage(primaryDevice.getAddress(),
                                                              senderDevice, timestamp, "",
                                                              Optional.absent(), 0, false);

    textMessage = new IncomingEncryptedMessage(textMessage, "");
    return database.insertMessageInbox(textMessage);
  }

  private Recipient getSyncMessageDestination(SentTranscriptMessage message) {
    if (message.getMessage().getGroupInfo().isPresent()) {
      return Recipient.from(context, Address.fromSerialized(GroupUtil.getEncodedId(message.getMessage().getGroupInfo().get().getGroupId(), false)), false);
    } else {
      return Recipient.from(context, Address.fromSerialized(message.getDestination().get()), false);
    }
  }

  private Recipient getSyncMessagePrimaryDestination(SentTranscriptMessage message) {
    if (message.getMessage().getGroupInfo().isPresent()) {
      return getSyncMessageDestination(message);
    } else {
      return getPrimaryDeviceRecipient(message.getDestination().get());
    }
  }

  private Recipient getMessageDestination(SignalServiceContent content, SignalServiceDataMessage message) {
    if (message.getGroupInfo().isPresent()) {
      return Recipient.from(context, Address.fromSerialized(GroupUtil.getEncodedId(message.getGroupInfo().get().getGroupId(), false)), false);
    } else {
      return Recipient.from(context, Address.fromSerialized(content.getSender()), false);
    }
  }

  private Recipient getMessagePrimaryDestination(SignalServiceContent content, SignalServiceDataMessage message) {
    if (message.getGroupInfo().isPresent()) {
      return getMessageDestination(content, message);
    } else {
      return getPrimaryDeviceRecipient(content.getSender());
    }
  }

  /**
   * Get the primary device recipient of the passed in device.
   *
   * If the device doesn't have a primary device then it will return the same device.
   * If the device is our primary device then it will return our current device.
   * Otherwise it will return the primary device.
   */
  private Recipient getPrimaryDeviceRecipient(String pubKey) {
    try {
      String primaryDevice = LokiStorageAPI.shared.getPrimaryDevicePublicKey(pubKey).get();
      String publicKey = (primaryDevice != null) ? primaryDevice : pubKey;
      // If the public key matches our primary device then we need to forward the message to ourselves (Note to self)
      String ourPrimaryDevice = TextSecurePreferences.getMasterHexEncodedPublicKey(context);
      if (ourPrimaryDevice != null && ourPrimaryDevice.equals(publicKey)) {
        publicKey = TextSecurePreferences.getLocalNumber(context);
      }
      return Recipient.from(context, Address.fromSerialized(publicKey), false);
    } catch (Exception e) {
      Log.d("Loki", "Failed to get primary device public key for message. " + e.getMessage());
      return Recipient.from(context, Address.fromSerialized(pubKey), false);
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

    if (content.getPairingAuthorisation().isPresent()) {
      return false;
    } else if (content.getDataMessage().isPresent()) {
      SignalServiceDataMessage message      = content.getDataMessage().get();
      Recipient                conversation = getMessageDestination(content, message);

      if (conversation.isGroupRecipient() && conversation.isBlocked()) {
        return true;
      } else if (conversation.isGroupRecipient()) {
        GroupDatabase    groupDatabase = DatabaseFactory.getGroupDatabase(context);
        Optional<String> groupId       = message.getGroupInfo().isPresent() ? Optional.of(GroupUtil.getEncodedId(message.getGroupInfo().get().getGroupId(), false))
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
        boolean isOurDevice = MultiDeviceUtilities.isOneOfOurDevices(context, sender.getAddress()).get();
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

  private boolean isGroupChatMessage(SignalServiceContent content) {
    return content.getDataMessage().isPresent() && content.getDataMessage().get().isGroupUpdate();
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
