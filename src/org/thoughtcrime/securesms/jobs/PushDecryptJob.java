package org.thoughtcrime.securesms.jobs;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.attachments.PointerAttachment;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.crypto.storage.SignalProtocolStoreImpl;
import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.MessagingDatabase;
import org.thoughtcrime.securesms.database.MessagingDatabase.InsertResult;
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.groups.GroupMessageProcessor;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingExpirationUpdateMessage;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.QuoteModel;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.WebRtcCallService;
import org.thoughtcrime.securesms.sms.IncomingEncryptedMessage;
import org.thoughtcrime.securesms.sms.IncomingEndSessionMessage;
import org.thoughtcrime.securesms.sms.IncomingPreKeyBundleMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingEndSessionMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.security.MessageDigest;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PushDecryptJob extends ContextJob {

  private static final long serialVersionUID = 2L;

  public static final String TAG = PushDecryptJob.class.getSimpleName();

  private final long messageId;
  private final long smsMessageId;

  public PushDecryptJob(Context context, long pushMessageId) {
    this(context, pushMessageId, -1);
  }

  public PushDecryptJob(Context context, long pushMessageId, long smsMessageId) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withGroupId("__PUSH_DECRYPT_JOB__")
                                .withWakeLock(true, 5, TimeUnit.SECONDS)
                                .create());
    this.messageId    = pushMessageId;
    this.smsMessageId = smsMessageId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws NoSuchMessageException {
    if (!IdentityKeyUtil.hasIdentityKey(context)) {
      Log.w(TAG, "Skipping job, waiting for migration...");
      return;
    }

    if (TextSecurePreferences.getNeedsSqlCipherMigration(context)) {
      Log.w(TAG, "Skipping job, waiting for sqlcipher migration...");
      NotificationManagerCompat.from(context).notify(494949,
                                                     new NotificationCompat.Builder(context)
                                                         .setSmallIcon(R.drawable.icon_notification)
                                                         .setPriority(NotificationCompat.PRIORITY_HIGH)
                                                         .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                                                         .setContentTitle(context.getString(R.string.PushDecryptJob_new_locked_message))
                                                         .setContentText(context.getString(R.string.PushDecryptJob_unlock_to_view_pending_messages))
                                                         .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, ConversationListActivity.class), 0))
                                                         .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE)
                                                         .build());
      return;
    }

    PushDatabase          database             = DatabaseFactory.getPushDatabase(context);
    SignalServiceEnvelope envelope             = database.get(messageId);
    Optional<Long>        optionalSmsMessageId = smsMessageId > 0 ? Optional.of(smsMessageId) : Optional.absent();

    handleMessage(envelope, optionalSmsMessageId);
    database.delete(messageId);
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {

  }

  private void handleMessage(SignalServiceEnvelope envelope, Optional<Long> smsMessageId) {
    try {
      GroupDatabase        groupDatabase = DatabaseFactory.getGroupDatabase(context);
      SignalProtocolStore  axolotlStore  = new SignalProtocolStoreImpl(context);
      SignalServiceAddress localAddress  = new SignalServiceAddress(TextSecurePreferences.getLocalNumber(context));
      SignalServiceCipher  cipher        = new SignalServiceCipher(localAddress, axolotlStore);

      SignalServiceContent content = cipher.decrypt(envelope);

      if (content.getDataMessage().isPresent()) {
        SignalServiceDataMessage message = content.getDataMessage().get();

        if      (message.isEndSession())                                                 handleEndSessionMessage(envelope, message, smsMessageId);
        else if (message.isGroupUpdate())                                                handleGroupMessage(envelope, message, smsMessageId);
        else if (message.isExpirationUpdate())                                           handleExpirationUpdate(envelope, message, smsMessageId);
        else if (message.getAttachments().isPresent() || message.getQuote().isPresent()) handleMediaMessage(envelope, message, smsMessageId);
        else if (message.getBody().isPresent())                                          handleTextMessage(envelope, message, smsMessageId);

        if (message.getGroupInfo().isPresent() && groupDatabase.isUnknownGroup(GroupUtil.getEncodedId(message.getGroupInfo().get().getGroupId(), false))) {
          handleUnknownGroupMessage(envelope, message.getGroupInfo().get());
        }

        if (message.getProfileKey().isPresent() && message.getProfileKey().get().length == 32) {
          handleProfileKey(envelope, message);
        }
      } else if (content.getSyncMessage().isPresent()) {
        SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();

        if      (syncMessage.getSent().isPresent())     handleSynchronizeSentMessage(envelope, syncMessage.getSent().get());
        else if (syncMessage.getRequest().isPresent())  handleSynchronizeRequestMessage(syncMessage.getRequest().get());
        else if (syncMessage.getRead().isPresent())     handleSynchronizeReadMessage(syncMessage.getRead().get(), envelope.getTimestamp());
        else if (syncMessage.getVerified().isPresent()) handleSynchronizeVerifiedMessage(syncMessage.getVerified().get());
        else                                           Log.w(TAG, "Contains no known sync types...");
      } else if (content.getCallMessage().isPresent()) {
        Log.w(TAG, "Got call message...");
        SignalServiceCallMessage message = content.getCallMessage().get();

        if      (message.getOfferMessage().isPresent())      handleCallOfferMessage(envelope, message.getOfferMessage().get(), smsMessageId);
        else if (message.getAnswerMessage().isPresent())     handleCallAnswerMessage(envelope, message.getAnswerMessage().get());
        else if (message.getIceUpdateMessages().isPresent()) handleCallIceUpdateMessage(envelope, message.getIceUpdateMessages().get());
        else if (message.getHangupMessage().isPresent())     handleCallHangupMessage(envelope, message.getHangupMessage().get(), smsMessageId);
        else if (message.getBusyMessage().isPresent())       handleCallBusyMessage(envelope, message.getBusyMessage().get());
      } else if (content.getReceiptMessage().isPresent()) {
        SignalServiceReceiptMessage message = content.getReceiptMessage().get();

        if      (message.isReadReceipt())     handleReadReceipt(envelope, message);
        else if (message.isDeliveryReceipt()) handleDeliveryReceipt(envelope, message);
      } else {
        Log.w(TAG, "Got unrecognized message...");
      }

      if (envelope.isPreKeySignalMessage()) {
        ApplicationContext.getInstance(context).getJobManager().add(new RefreshPreKeysJob(context));
      }
    } catch (InvalidVersionException e) {
      Log.w(TAG, e);
      handleInvalidVersionMessage(envelope, smsMessageId);
    } catch (InvalidMessageException | InvalidKeyIdException | InvalidKeyException | MmsException e) {
      Log.w(TAG, e);
      handleCorruptMessage(envelope, smsMessageId);
    } catch (NoSessionException e) {
      Log.w(TAG, e);
      handleNoSessionMessage(envelope, smsMessageId);
    } catch (LegacyMessageException e) {
      Log.w(TAG, e);
      handleLegacyMessage(envelope, smsMessageId);
    } catch (DuplicateMessageException e) {
      Log.w(TAG, e);
      handleDuplicateMessage(envelope, smsMessageId);
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, e);
      handleUntrustedIdentityMessage(envelope, smsMessageId);
    }
  }

  private void handleCallOfferMessage(@NonNull SignalServiceEnvelope envelope,
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
      intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.fromExternal(context, envelope.getSource()));
      intent.putExtra(WebRtcCallService.EXTRA_REMOTE_DESCRIPTION, message.getDescription());
      intent.putExtra(WebRtcCallService.EXTRA_TIMESTAMP, envelope.getTimestamp());

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
      else                                                context.startService(intent);
    }
  }

  private void handleCallAnswerMessage(@NonNull SignalServiceEnvelope envelope,
                                       @NonNull AnswerMessage message)
  {
    Log.w(TAG, "handleCallAnswerMessage...");
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(WebRtcCallService.ACTION_RESPONSE_MESSAGE);
    intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
    intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.fromExternal(context, envelope.getSource()));
    intent.putExtra(WebRtcCallService.EXTRA_REMOTE_DESCRIPTION, message.getDescription());

    context.startService(intent);
  }

  private void handleCallIceUpdateMessage(@NonNull SignalServiceEnvelope envelope,
                                          @NonNull List<IceUpdateMessage> messages)
  {
    Log.w(TAG, "handleCallIceUpdateMessage... " + messages.size());
    for (IceUpdateMessage message : messages) {
      Intent intent = new Intent(context, WebRtcCallService.class);
      intent.setAction(WebRtcCallService.ACTION_ICE_MESSAGE);
      intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
      intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.fromExternal(context, envelope.getSource()));
      intent.putExtra(WebRtcCallService.EXTRA_ICE_SDP, message.getSdp());
      intent.putExtra(WebRtcCallService.EXTRA_ICE_SDP_MID, message.getSdpMid());
      intent.putExtra(WebRtcCallService.EXTRA_ICE_SDP_LINE_INDEX, message.getSdpMLineIndex());

      context.startService(intent);
    }
  }

  private void handleCallHangupMessage(@NonNull SignalServiceEnvelope envelope,
                                       @NonNull HangupMessage message,
                                       @NonNull Optional<Long> smsMessageId)
  {
    Log.w(TAG, "handleCallHangupMessage");
    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).markAsMissedCall(smsMessageId.get());
    } else {
      Intent intent = new Intent(context, WebRtcCallService.class);
      intent.setAction(WebRtcCallService.ACTION_REMOTE_HANGUP);
      intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
      intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.fromExternal(context, envelope.getSource()));

      context.startService(intent);
    }
  }

  private void handleCallBusyMessage(@NonNull SignalServiceEnvelope envelope,
                                     @NonNull BusyMessage message)
  {
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(WebRtcCallService.ACTION_REMOTE_BUSY);
    intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
    intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.fromExternal(context, envelope.getSource()));

    context.startService(intent);
  }

  private void handleEndSessionMessage(@NonNull SignalServiceEnvelope    envelope,
                                       @NonNull SignalServiceDataMessage message,
                                       @NonNull Optional<Long>           smsMessageId)
  {
    SmsDatabase         smsDatabase         = DatabaseFactory.getSmsDatabase(context);
    IncomingTextMessage incomingTextMessage = new IncomingTextMessage(Address.fromExternal(context, envelope.getSource()),
                                                                      envelope.getSourceDevice(),
                                                                      message.getTimestamp(),
                                                                      "", Optional.absent(), 0);

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
      SessionStore sessionStore = new TextSecureSessionStore(context);
      sessionStore.deleteAllSessions(envelope.getSource());

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
      SessionStore sessionStore = new TextSecureSessionStore(context);
      sessionStore.deleteAllSessions(recipient.getAddress().toPhoneString());

      SecurityEvent.broadcastSecurityUpdateEvent(context);

      long messageId = database.insertMessageOutbox(threadId, outgoingEndSessionMessage,
                                                    false, message.getTimestamp(),
                                                    null);
      database.markAsSent(messageId, true);
    }

    return threadId;
  }

  private void handleGroupMessage(@NonNull SignalServiceEnvelope envelope,
                                  @NonNull SignalServiceDataMessage message,
                                  @NonNull Optional<Long> smsMessageId)
  {
    GroupMessageProcessor.process(context, envelope, message, false);

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }
  }

  private void handleUnknownGroupMessage(@NonNull SignalServiceEnvelope envelope,
                                         @NonNull SignalServiceGroup group)
  {
    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new RequestGroupInfoJob(context, envelope.getSource(), group.getGroupId()));
  }

  private void handleExpirationUpdate(@NonNull SignalServiceEnvelope envelope,
                                      @NonNull SignalServiceDataMessage message,
                                      @NonNull Optional<Long> smsMessageId)
      throws MmsException
  {
    MmsDatabase          database     = DatabaseFactory.getMmsDatabase(context);
    Recipient            recipient    = getMessageDestination(envelope, message);
    IncomingMediaMessage mediaMessage = new IncomingMediaMessage(Address.fromExternal(context, envelope.getSource()),
                                                                 message.getTimestamp(), -1,
                                                                 message.getExpiresInSeconds() * 1000L, true,
                                                                 Optional.fromNullable(envelope.getRelay()),
                                                                 Optional.absent(), message.getGroupInfo(),
                                                                 Optional.absent(), Optional.absent());



    database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

    DatabaseFactory.getRecipientDatabase(context).setExpireMessages(recipient, message.getExpiresInSeconds());

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }
  }

  private void handleSynchronizeVerifiedMessage(@NonNull VerifiedMessage verifiedMessage) {
    IdentityUtil.processVerifiedMessage(context, verifiedMessage);
  }

  private void handleSynchronizeSentMessage(@NonNull SignalServiceEnvelope envelope,
                                            @NonNull SentTranscriptMessage message)
      throws MmsException
  {
    GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);

    Long threadId;

    if (message.getMessage().isEndSession()) {
      threadId = handleSynchronizeSentEndSessionMessage(message);
    } else if (message.getMessage().isGroupUpdate()) {
      threadId = GroupMessageProcessor.process(context, envelope, message.getMessage(), true);
    } else if (message.getMessage().isExpirationUpdate()) {
      threadId = handleSynchronizeSentExpirationUpdate(message);
    } else if (message.getMessage().getAttachments().isPresent() || message.getMessage().getQuote().isPresent()) {
      threadId = handleSynchronizeSentMediaMessage(message);
    } else {
      threadId = handleSynchronizeSentTextMessage(message);
    }

    if (message.getMessage().getGroupInfo().isPresent() && groupDatabase.isUnknownGroup(GroupUtil.getEncodedId(message.getMessage().getGroupInfo().get().getGroupId(), false))) {
      handleUnknownGroupMessage(envelope, message.getMessage().getGroupInfo().get());
    }

    if (message.getMessage().getProfileKey().isPresent()) {
      Recipient recipient = null;

      if      (message.getDestination().isPresent())            recipient = Recipient.from(context, Address.fromExternal(context, message.getDestination().get()), false);
      else if (message.getMessage().getGroupInfo().isPresent()) recipient = Recipient.from(context, Address.fromSerialized(GroupUtil.getEncodedId(message.getMessage().getGroupInfo().get().getGroupId(), false)), false);


      if (recipient != null && !recipient.isSystemContact() && !recipient.isProfileSharing()) {
        DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipient, true);
      }
    }

    if (threadId != null) {
      DatabaseFactory.getThreadDatabase(getContext()).setRead(threadId, true);
      MessageNotifier.updateNotification(getContext());
    }

    MessageNotifier.setLastDesktopActivityTimestamp(message.getTimestamp());
  }

  private void handleSynchronizeRequestMessage(@NonNull RequestMessage message)
  {
    if (message.isContactsRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceContactUpdateJob(getContext()));
    }

    if (message.isGroupsRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceGroupUpdateJob(getContext()));
    }

    if (message.isBlockedListRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceBlockedUpdateJob(getContext()));
    }

    if (message.isConfigurationRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceReadReceiptUpdateJob(getContext(), TextSecurePreferences.isReadReceiptsEnabled(getContext())));
    }
  }

  private void handleSynchronizeReadMessage(@NonNull List<ReadMessage> readMessages, long envelopeTimestamp)
  {
    for (ReadMessage readMessage : readMessages) {
      List<Pair<Long, Long>> expiringText = DatabaseFactory.getSmsDatabase(context).setTimestampRead(new SyncMessageId(Address.fromExternal(context, readMessage.getSender()), readMessage.getTimestamp()), envelopeTimestamp);
      List<Pair<Long, Long>> expiringMedia = DatabaseFactory.getMmsDatabase(context).setTimestampRead(new SyncMessageId(Address.fromExternal(context, readMessage.getSender()), readMessage.getTimestamp()), envelopeTimestamp);

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

  private void handleMediaMessage(@NonNull SignalServiceEnvelope envelope,
                                  @NonNull SignalServiceDataMessage message,
                                  @NonNull Optional<Long> smsMessageId)
      throws MmsException
  {
    MmsDatabase          database     = DatabaseFactory.getMmsDatabase(context);
    Recipient            recipient    = getMessageDestination(envelope, message);
    Optional<QuoteModel> quote        = getValidatedQuote(message.getQuote());
    IncomingMediaMessage mediaMessage = new IncomingMediaMessage(Address.fromExternal(context, envelope.getSource()),
                                                                 message.getTimestamp(), -1,
                                                                 message.getExpiresInSeconds() * 1000L, false,
                                                                 Optional.fromNullable(envelope.getRelay()),
                                                                 message.getBody(),
                                                                 message.getGroupInfo(),
                                                                 message.getAttachments(),
                                                                 quote);

    if (message.getExpiresInSeconds() != recipient.getExpireMessages()) {
      handleExpirationUpdate(envelope, message, Optional.absent());
    }

    Optional<InsertResult> insertResult = database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

    if (insertResult.isPresent()) {
      List<DatabaseAttachment> attachments = DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(insertResult.get().getMessageId());

      for (DatabaseAttachment attachment : attachments) {
        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new AttachmentDownloadJob(context, insertResult.get().getMessageId(), attachment.getAttachmentId(), false));
      }

      if (smsMessageId.isPresent()) {
        DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
      }

      MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
    }
  }

  private long handleSynchronizeSentExpirationUpdate(@NonNull SentTranscriptMessage message)
      throws MmsException
  {
    MmsDatabase database   = DatabaseFactory.getMmsDatabase(context);
    Recipient   recipient  = getSyncMessageDestination(message);

    OutgoingExpirationUpdateMessage expirationUpdateMessage = new OutgoingExpirationUpdateMessage(recipient,
                                                                                                  message.getTimestamp(),
                                                                                                  message.getMessage().getExpiresInSeconds() * 1000L);

    long threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
    long messageId = database.insertMessageOutbox(expirationUpdateMessage, threadId, false, null);

    database.markAsSent(messageId, true);

    DatabaseFactory.getRecipientDatabase(context).setExpireMessages(recipient, message.getMessage().getExpiresInSeconds());

    return threadId;
  }

  private long handleSynchronizeSentMediaMessage(@NonNull SentTranscriptMessage message)
      throws MmsException
  {
    MmsDatabase           database     = DatabaseFactory.getMmsDatabase(context);
    Recipient             recipients   = getSyncMessageDestination(message);
    Optional<QuoteModel>  quote        = getValidatedQuote(message.getMessage().getQuote());
    OutgoingMediaMessage  mediaMessage = new OutgoingMediaMessage(recipients, message.getMessage().getBody().orNull(),
                                                                  PointerAttachment.forPointers(message.getMessage().getAttachments()),
                                                                  message.getTimestamp(), -1,
                                                                  message.getMessage().getExpiresInSeconds() * 1000,
                                                                  ThreadDatabase.DistributionTypes.DEFAULT, quote.orNull());

    mediaMessage = new OutgoingSecureMediaMessage(mediaMessage);

    if (recipients.getExpireMessages() != message.getMessage().getExpiresInSeconds()) {
      handleSynchronizeSentExpirationUpdate(message);
    }

    long threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    long messageId = database.insertMessageOutbox(mediaMessage, threadId, false, null);

    database.markAsSent(messageId, true);

    for (DatabaseAttachment attachment : DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(messageId)) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new AttachmentDownloadJob(context, messageId, attachment.getAttachmentId(), false));
    }

    if (message.getMessage().getExpiresInSeconds() > 0) {
      database.markExpireStarted(messageId, message.getExpirationStartTimestamp());
      ApplicationContext.getInstance(context)
                        .getExpiringMessageManager()
                        .scheduleDeletion(messageId, true,
                                          message.getExpirationStartTimestamp(),
                                          message.getMessage().getExpiresInSeconds() * 1000L);
    }

    return threadId;
  }

  private void handleTextMessage(@NonNull SignalServiceEnvelope envelope,
                                 @NonNull SignalServiceDataMessage message,
                                 @NonNull Optional<Long> smsMessageId)
      throws MmsException
  {
    SmsDatabase database  = DatabaseFactory.getSmsDatabase(context);
    String      body      = message.getBody().isPresent() ? message.getBody().get() : "";
    Recipient   recipient = getMessageDestination(envelope, message);

    if (message.getExpiresInSeconds() != recipient.getExpireMessages()) {
      handleExpirationUpdate(envelope, message, Optional.absent());
    }

    Long threadId;

    if (smsMessageId.isPresent() && !message.getGroupInfo().isPresent()) {
      threadId = database.updateBundleMessageBody(smsMessageId.get(), body).second;
    } else {
      IncomingTextMessage textMessage = new IncomingTextMessage(Address.fromExternal(context, envelope.getSource()),
                                                                envelope.getSourceDevice(),
                                                                message.getTimestamp(), body,
                                                                message.getGroupInfo(),
                                                                message.getExpiresInSeconds() * 1000L);

      textMessage = new IncomingEncryptedMessage(textMessage, body);
      Optional<InsertResult> insertResult = database.insertMessageInbox(textMessage);

      if (insertResult.isPresent()) threadId = insertResult.get().getThreadId();
      else                          threadId = null;

      if (smsMessageId.isPresent()) database.deleteMessage(smsMessageId.get());
    }

    if (threadId != null) {
      MessageNotifier.updateNotification(context, threadId);
    }
  }

  private long handleSynchronizeSentTextMessage(@NonNull SentTranscriptMessage message)
      throws MmsException
  {

    Recipient recipient       = getSyncMessageDestination(message);
    String    body            = message.getMessage().getBody().or("");
    long      expiresInMillis = message.getMessage().getExpiresInSeconds() * 1000L;

    if (recipient.getExpireMessages() != message.getMessage().getExpiresInSeconds()) {
      handleSynchronizeSentExpirationUpdate(message);
    }

    long threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);

    MessagingDatabase database;
    long              messageId;

    if (recipient.getAddress().isGroup()) {
      OutgoingMediaMessage outgoingMediaMessage = new OutgoingMediaMessage(recipient, new SlideDeck(), body, message.getTimestamp(), -1, expiresInMillis, ThreadDatabase.DistributionTypes.DEFAULT, null);
      outgoingMediaMessage = new OutgoingSecureMediaMessage(outgoingMediaMessage);

      messageId = DatabaseFactory.getMmsDatabase(context).insertMessageOutbox(outgoingMediaMessage, threadId, false, null);
      database  = DatabaseFactory.getMmsDatabase(context);
    } else {
      OutgoingTextMessage outgoingTextMessage = new OutgoingEncryptedMessage(recipient, body, expiresInMillis);

      messageId = DatabaseFactory.getSmsDatabase(context).insertMessageOutbox(threadId, outgoingTextMessage, false, message.getTimestamp(), null);
      database  = DatabaseFactory.getSmsDatabase(context);
    }

    database.markAsSent(messageId, true);

    if (expiresInMillis > 0) {
      database.markExpireStarted(messageId, message.getExpirationStartTimestamp());
      ApplicationContext.getInstance(context)
                        .getExpiringMessageManager()
                        .scheduleDeletion(messageId, false, message.getExpirationStartTimestamp(), expiresInMillis);
    }

    return threadId;
  }

  private void handleInvalidVersionMessage(@NonNull SignalServiceEnvelope envelope,
                                           @NonNull Optional<Long> smsMessageId)
  {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(envelope);

      if (insertResult.isPresent()) {
        smsDatabase.markAsInvalidVersionKeyExchange(insertResult.get().getMessageId());
        MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsInvalidVersionKeyExchange(smsMessageId.get());
    }
  }

  private void handleCorruptMessage(@NonNull SignalServiceEnvelope envelope,
                                    @NonNull Optional<Long> smsMessageId)
  {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(envelope);

      if (insertResult.isPresent()) {
        smsDatabase.markAsDecryptFailed(insertResult.get().getMessageId());
        MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsDecryptFailed(smsMessageId.get());
    }
  }

  private void handleNoSessionMessage(@NonNull SignalServiceEnvelope envelope,
                                      @NonNull Optional<Long> smsMessageId)
  {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(envelope);

      if (insertResult.isPresent()) {
        smsDatabase.markAsNoSession(insertResult.get().getMessageId());
        MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsNoSession(smsMessageId.get());
    }
  }

  private void handleLegacyMessage(@NonNull SignalServiceEnvelope envelope,
                                   @NonNull Optional<Long> smsMessageId)
  {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(envelope);

      if (insertResult.isPresent()) {
        smsDatabase.markAsLegacyVersion(insertResult.get().getMessageId());
        MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsLegacyVersion(smsMessageId.get());
    }
  }

  @SuppressWarnings("unused")
  private void handleDuplicateMessage(@NonNull SignalServiceEnvelope envelope,
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

  private void handleUntrustedIdentityMessage(@NonNull SignalServiceEnvelope envelope,
                                              @NonNull Optional<Long> smsMessageId)
  {
    try {
      SmsDatabase         database       = DatabaseFactory.getSmsDatabase(context);
      Address             sourceAddress  = Address.fromExternal(context, envelope.getSource());
      byte[]              serialized     = envelope.hasLegacyMessage() ? envelope.getLegacyMessage() : envelope.getContent();
      PreKeySignalMessage whisperMessage = new PreKeySignalMessage(serialized);
      IdentityKey         identityKey    = whisperMessage.getIdentityKey();
      String              encoded        = Base64.encodeBytes(serialized);

      IncomingTextMessage   textMessage    = new IncomingTextMessage(sourceAddress,
                                                                     envelope.getSourceDevice(),
                                                                     envelope.getTimestamp(), encoded,
                                                                     Optional.absent(), 0);

      if (!smsMessageId.isPresent()) {
        IncomingPreKeyBundleMessage bundleMessage = new IncomingPreKeyBundleMessage(textMessage, encoded, envelope.hasLegacyMessage());
        Optional<InsertResult>      insertResult  = database.insertMessageInbox(bundleMessage);

        if (insertResult.isPresent()) {
          database.setMismatchedIdentity(insertResult.get().getMessageId(), sourceAddress, identityKey);
          MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
        }
      } else {
        database.updateMessageBody(smsMessageId.get(), encoded);
        database.markAsPreKeyBundle(smsMessageId.get());
        database.setMismatchedIdentity(smsMessageId.get(), sourceAddress, identityKey);
      }
    } catch (InvalidMessageException | InvalidVersionException e) {
      throw new AssertionError(e);
    }
  }

  private void handleProfileKey(@NonNull SignalServiceEnvelope envelope,
                                @NonNull SignalServiceDataMessage message)
  {
    RecipientDatabase database      = DatabaseFactory.getRecipientDatabase(context);
    Address           sourceAddress = Address.fromExternal(context, envelope.getSource());
    Recipient         recipient     = Recipient.from(context, sourceAddress, false);

    if (recipient.getProfileKey() == null || !MessageDigest.isEqual(recipient.getProfileKey(), message.getProfileKey().get())) {
      database.setProfileKey(recipient, message.getProfileKey().get());
      ApplicationContext.getInstance(context).getJobManager().add(new RetrieveProfileJob(context, recipient));
    }
  }

  private void handleDeliveryReceipt(@NonNull SignalServiceEnvelope envelope,
                                     @NonNull SignalServiceReceiptMessage message)
  {
    for (long timestamp : message.getTimestamps()) {
      Log.w(TAG, String.format("Received encrypted delivery receipt: (XXXXX, %d)", timestamp));
      DatabaseFactory.getMmsSmsDatabase(context)
                     .incrementDeliveryReceiptCount(new SyncMessageId(Address.fromExternal(context, envelope.getSource()), timestamp), System.currentTimeMillis());
    }
  }

  private void handleReadReceipt(@NonNull SignalServiceEnvelope envelope,
                                 @NonNull SignalServiceReceiptMessage message)
  {
    if (TextSecurePreferences.isReadReceiptsEnabled(context)) {
      for (long timestamp : message.getTimestamps()) {
        Log.w(TAG, String.format("Received encrypted read receipt: (XXXXX, %d)", timestamp));

        DatabaseFactory.getMmsSmsDatabase(context)
                       .incrementReadReceiptCount(new SyncMessageId(Address.fromExternal(context, envelope.getSource()), timestamp), envelope.getTimestamp());
      }
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

    Address       author  = Address.fromExternal(context, quote.get().getAuthor().getNumber());
    MessageRecord message = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(quote.get().getId(), author);

    if (message != null) {
      Log.w(TAG, "Found matching message record...");

      List<Attachment> attachments = new LinkedList<>();

      if (message.isMms()) {
        attachments = ((MmsMessageRecord) message).getSlideDeck().asAttachments();
      }

      return Optional.of(new QuoteModel(quote.get().getId(), author, message.getBody(), attachments));
    }

    Log.w(TAG, "Didn't find matching message record...");
    return Optional.of(new QuoteModel(quote.get().getId(),
                                      author,
                                      quote.get().getText(),
                                      PointerAttachment.forPointers(quote.get().getAttachments())));
  }

  private Optional<InsertResult> insertPlaceholder(@NonNull SignalServiceEnvelope envelope) {
    SmsDatabase         database    = DatabaseFactory.getSmsDatabase(context);
    IncomingTextMessage textMessage = new IncomingTextMessage(Address.fromExternal(context, envelope.getSource()),
                                                              envelope.getSourceDevice(),
                                                              envelope.getTimestamp(), "",
                                                              Optional.absent(), 0);

    textMessage = new IncomingEncryptedMessage(textMessage, "");
    return database.insertMessageInbox(textMessage);
  }

  private Recipient getSyncMessageDestination(SentTranscriptMessage message) {
    if (message.getMessage().getGroupInfo().isPresent()) {
      return Recipient.from(context, Address.fromExternal(context, GroupUtil.getEncodedId(message.getMessage().getGroupInfo().get().getGroupId(), false)), false);
    } else {
      return Recipient.from(context, Address.fromExternal(context, message.getDestination().get()), false);
    }
  }


  private Recipient getMessageDestination(SignalServiceEnvelope envelope, SignalServiceDataMessage message) {
    if (message.getGroupInfo().isPresent()) {
      return Recipient.from(context, Address.fromExternal(context, GroupUtil.getEncodedId(message.getGroupInfo().get().getGroupId(), false)), false);
    } else {
      return Recipient.from(context, Address.fromExternal(context, envelope.getSource()), false);
    }
  }
}
