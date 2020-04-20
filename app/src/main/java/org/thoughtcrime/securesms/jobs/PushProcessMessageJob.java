package org.thoughtcrime.securesms.jobs;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.attachments.PointerAttachment;
import org.thoughtcrime.securesms.attachments.TombstoneAttachment;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactModelMapper;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase.GroupReceiptInfo;
import org.thoughtcrime.securesms.database.MessagingDatabase;
import org.thoughtcrime.securesms.database.MessagingDatabase.InsertResult;
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.StickerDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.BadGroupIdException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupV1MessageProcessor;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.linkpreview.Link;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingExpirationUpdateMessage;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.QuoteModel;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.ringrtc.IceCandidateParcel;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.WebRtcCallService;
import org.thoughtcrime.securesms.sms.IncomingEncryptedMessage;
import org.thoughtcrime.securesms.sms.IncomingEndSessionMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingEndSessionMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.RemoteDeleteUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Preview;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ConfigurationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.MessageRequestResponseMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewOnceOpenMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PushProcessMessageJob extends BaseJob {

  public static final String KEY   = "PushProcessJob";
  public static final String QUEUE = "__PUSH_PROCESS_JOB__";

  public static final String TAG = Log.tag(PushProcessMessageJob.class);

  private static final String KEY_MESSAGE_STATE      = "message_state";
  private static final String KEY_MESSAGE_PLAINTEXT  = "message_content";
  private static final String KEY_MESSAGE_ID         = "message_id";
  private static final String KEY_SMS_MESSAGE_ID     = "sms_message_id";
  private static final String KEY_TIMESTAMP          = "timestamp";
  private static final String KEY_EXCEPTION_SENDER   = "exception_sender";
  private static final String KEY_EXCEPTION_DEVICE   = "exception_device";
  private static final String KEY_EXCEPTION_GROUP_ID = "exception_groupId";

  @NonNull  private final MessageState      messageState;
  @Nullable private final byte[]            serializedPlaintextContent;
  @Nullable private final ExceptionMetadata exceptionMetadata;
            private final long              messageId;
            private final long              smsMessageId;
            private final long              timestamp;

  PushProcessMessageJob(@NonNull byte[] serializedPlaintextContent,
                        long pushMessageId,
                        long smsMessageId,
                        long timestamp)
  {
    this(MessageState.DECRYPTED_OK,
         serializedPlaintextContent,
         null,
         pushMessageId,
         smsMessageId,
         timestamp);
  }

  PushProcessMessageJob(@NonNull MessageState messageState,
                        @NonNull ExceptionMetadata exceptionMetadata,
                        long pushMessageId,
                        long smsMessageId,
                        long timestamp)
  {
    this(messageState,
         null,
         exceptionMetadata,
         pushMessageId,
         smsMessageId,
         timestamp);
  }

  private PushProcessMessageJob(@NonNull MessageState messageState,
                                @Nullable byte[] serializedPlaintextContent,
                                @Nullable ExceptionMetadata exceptionMetadata,
                                long pushMessageId,
                                long smsMessageId,
                                long timestamp)
  {
    this(new Parameters.Builder()
                       .setQueue(QUEUE)
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build(),
         messageState,
         serializedPlaintextContent,
         exceptionMetadata,
         pushMessageId,
         smsMessageId,
         timestamp);
  }

  private PushProcessMessageJob(@NonNull Parameters parameters,
                                @NonNull MessageState messageState,
                                @Nullable byte[] serializedPlaintextContent,
                                @Nullable ExceptionMetadata exceptionMetadata,
                                long pushMessageId,
                                long smsMessageId,
                                long timestamp)
  {
    super(parameters);

    this.messageState               = messageState;
    this.exceptionMetadata          = exceptionMetadata;
    this.serializedPlaintextContent = serializedPlaintextContent;
    this.messageId                  = pushMessageId;
    this.smsMessageId               = smsMessageId;
    this.timestamp                  = timestamp;
  }

  @Override
  public @NonNull Data serialize() {
    Data.Builder dataBuilder = new Data.Builder()
                                       .putInt(KEY_MESSAGE_STATE, messageState.ordinal())
                                       .putLong(KEY_MESSAGE_ID, messageId)
                                       .putLong(KEY_SMS_MESSAGE_ID, smsMessageId)
                                       .putLong(KEY_TIMESTAMP, timestamp);

    if (messageState == MessageState.DECRYPTED_OK) {
      //noinspection ConstantConditions
      dataBuilder.putString(KEY_MESSAGE_PLAINTEXT, Base64.encodeBytes(serializedPlaintextContent));
    } else {
      //noinspection ConstantConditions
      dataBuilder.putString(KEY_EXCEPTION_SENDER, exceptionMetadata.sender)
                 .putInt(KEY_EXCEPTION_DEVICE, exceptionMetadata.senderDevice)
                 .putString(KEY_EXCEPTION_GROUP_ID, exceptionMetadata.groupId == null ? null : exceptionMetadata.groupId.toString());
    }

    return dataBuilder.build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() {
    Optional<Long> optionalSmsMessageId = smsMessageId > 0 ? Optional.of(smsMessageId) : Optional.absent();

    if (messageState == MessageState.DECRYPTED_OK) {
      SignalServiceContent content = SignalServiceContent.deserialize(serializedPlaintextContent);
      handleMessage(content, optionalSmsMessageId);
    } else {
      //noinspection ConstantConditions
      handleExceptionMessage(exceptionMetadata, optionalSmsMessageId);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return false;
  }

  @Override
  public void onFailure() {
  }

  private void handleMessage(@Nullable SignalServiceContent content, @NonNull Optional<Long> smsMessageId) {
    try {
      GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);

      if (content == null || shouldIgnore(content)) {
        Log.i(TAG, "Ignoring message.");
        return;
      }

      if (content.getDataMessage().isPresent()) {
        SignalServiceDataMessage message        = content.getDataMessage().get();
        boolean                  isMediaMessage = message.getAttachments().isPresent() || message.getQuote().isPresent() || message.getSharedContacts().isPresent() || message.getPreviews().isPresent() || message.getSticker().isPresent();
        Optional<GroupId>        groupId        = GroupUtil.idFromGroupContext(message.getGroupContext());
        boolean                  isGv2Message   = groupId.isPresent() && groupId.get().isV2();

        if (isGv2Message) {
          Log.w(TAG, "Ignoring GV2 message.");
          return;
        }

        if      (isInvalidMessage(message))             handleInvalidMessage(content.getSender(), content.getSenderDevice(), groupId, content.getTimestamp(), smsMessageId);
        else if (message.isEndSession())                handleEndSessionMessage(content, smsMessageId);
        else if (message.isGroupV1Update())             handleGroupV1Message(content, message, smsMessageId);
        else if (message.isExpirationUpdate())          handleExpirationUpdate(content, message, smsMessageId);
        else if (message.getReaction().isPresent())     handleReaction(content, message);
        else if (message.getRemoteDelete().isPresent()) handleRemoteDelete(content, message);
        else if (isMediaMessage)                        handleMediaMessage(content, message, smsMessageId);
        else if (message.getBody().isPresent())         handleTextMessage(content, message, smsMessageId, groupId);

        if (groupId.isPresent() && groupDatabase.isUnknownGroup(groupId.get())) {
          handleUnknownGroupMessage(content, message.getGroupContext().get());
        }

        if (message.getProfileKey().isPresent()) {
          handleProfileKey(content, message.getProfileKey().get());
        }

        if (content.isNeedsReceipt()) {
          handleNeedsDeliveryReceipt(content, message);
        }
      } else if (content.getSyncMessage().isPresent()) {
        TextSecurePreferences.setMultiDevice(context, true);

        SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();

        if      (syncMessage.getSent().isPresent())                   handleSynchronizeSentMessage(content, syncMessage.getSent().get());
        else if (syncMessage.getRequest().isPresent())                handleSynchronizeRequestMessage(syncMessage.getRequest().get());
        else if (syncMessage.getRead().isPresent())                   handleSynchronizeReadMessage(syncMessage.getRead().get(), content.getTimestamp());
        else if (syncMessage.getViewOnceOpen().isPresent())           handleSynchronizeViewOnceOpenMessage(syncMessage.getViewOnceOpen().get(), content.getTimestamp());
        else if (syncMessage.getVerified().isPresent())               handleSynchronizeVerifiedMessage(syncMessage.getVerified().get());
        else if (syncMessage.getStickerPackOperations().isPresent())  handleSynchronizeStickerPackOperation(syncMessage.getStickerPackOperations().get());
        else if (syncMessage.getConfiguration().isPresent())          handleSynchronizeConfigurationMessage(syncMessage.getConfiguration().get());
        else if (syncMessage.getBlockedList().isPresent())            handleSynchronizeBlockedListMessage(syncMessage.getBlockedList().get());
        else if (syncMessage.getFetchType().isPresent())              handleSynchronizeFetchMessage(syncMessage.getFetchType().get());
        else if (syncMessage.getMessageRequestResponse().isPresent()) handleSynchronizeMessageRequestResponse(syncMessage.getMessageRequestResponse().get());
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

      resetRecipientToPush(Recipient.externalPush(context, content.getSender()));

      Optional<SignalServiceContent> earlyContent = ApplicationDependencies.getEarlyMessageCache()
                                                                           .retrieve(Recipient.externalPush(context, content.getSender()).getId(),
                                                                                     content.getTimestamp());
      if (earlyContent.isPresent()) {
        Log.i(TAG, "Found dependent content that was retrieved earlier. Processing.");
        handleMessage(earlyContent.get(), Optional.absent());
      }
    } catch (StorageFailedException e) {
      Log.w(TAG, e);
      handleCorruptMessage(e.getSender(), e.getSenderDevice(), timestamp, smsMessageId);
    } catch (BadGroupIdException e) {
      Log.w(TAG, "Ignoring message with bad group id", e);
    }
  }

  private void handleExceptionMessage(@NonNull ExceptionMetadata e, @NonNull Optional<Long> smsMessageId) {
    switch (messageState) {

      case INVALID_VERSION:
        Log.w(TAG, "Handling invalid version");
        handleInvalidVersionMessage(e.sender, e.senderDevice, timestamp, smsMessageId);
        break;

      case CORRUPT_MESSAGE:
        Log.w(TAG, "Handling corrupt message");
        handleCorruptMessage(e.sender, e.senderDevice, timestamp, smsMessageId);
        break;

      case NO_SESSION:
        Log.w(TAG, "Handling no session");
        handleNoSessionMessage(e.sender, e.senderDevice, timestamp, smsMessageId);
        break;

      case LEGACY_MESSAGE:
        Log.w(TAG, "Handling legacy message");
        handleLegacyMessage(e.sender, e.senderDevice, timestamp, smsMessageId);
        break;

      case DUPLICATE_MESSAGE:
        Log.w(TAG, "Handling duplicate message");
        handleDuplicateMessage(e.sender, e.senderDevice, timestamp, smsMessageId);
        break;

      case UNSUPPORTED_DATA_MESSAGE:
        Log.w(TAG, "Handling unsupported data message");
        handleUnsupportedDataMessage(e.sender, e.senderDevice, Optional.fromNullable(e.groupId), timestamp, smsMessageId);
        break;

      default:
        throw new AssertionError("Not handled " + messageState);
    }
  }

  private void handleCallOfferMessage(@NonNull SignalServiceContent content,
                                      @NonNull OfferMessage message,
                                      @NonNull Optional<Long> smsMessageId)
  {
    Log.i(TAG, "handleCallOfferMessage...");

    if (smsMessageId.isPresent()) {
      SmsDatabase database = DatabaseFactory.getSmsDatabase(context);
      database.markAsMissedCall(smsMessageId.get());
    } else {
      Intent     intent     = new Intent(context, WebRtcCallService.class);
      RemotePeer remotePeer = new RemotePeer(Recipient.externalPush(context, content.getSender()).getId());

      intent.setAction(WebRtcCallService.ACTION_RECEIVE_OFFER)
            .putExtra(WebRtcCallService.EXTRA_CALL_ID,           message.getId())
            .putExtra(WebRtcCallService.EXTRA_REMOTE_PEER,       remotePeer)
            .putExtra(WebRtcCallService.EXTRA_REMOTE_DEVICE,     content.getSenderDevice())
            .putExtra(WebRtcCallService.EXTRA_OFFER_DESCRIPTION, message.getDescription())
            .putExtra(WebRtcCallService.EXTRA_TIMESTAMP,         content.getTimestamp());

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
      else                                                context.startService(intent);
    }
  }

  private void handleCallAnswerMessage(@NonNull SignalServiceContent content,
                                       @NonNull AnswerMessage message)
  {
    Log.i(TAG, "handleCallAnswerMessage...");
    Intent     intent     = new Intent(context, WebRtcCallService.class);
    RemotePeer remotePeer = new RemotePeer(Recipient.externalPush(context, content.getSender()).getId());

    intent.setAction(WebRtcCallService.ACTION_RECEIVE_ANSWER)
          .putExtra(WebRtcCallService.EXTRA_CALL_ID,            message.getId())
          .putExtra(WebRtcCallService.EXTRA_REMOTE_PEER,        remotePeer)
          .putExtra(WebRtcCallService.EXTRA_REMOTE_DEVICE,      content.getSenderDevice())
          .putExtra(WebRtcCallService.EXTRA_ANSWER_DESCRIPTION, message.getDescription());

    context.startService(intent);
  }

  private void handleCallIceUpdateMessage(@NonNull SignalServiceContent content,
                                          @NonNull List<IceUpdateMessage> messages)
  {
    Log.i(TAG, "handleCallIceUpdateMessage... " + messages.size());

    ArrayList<IceCandidateParcel> iceCandidates = new ArrayList<>(messages.size());
    long callId = -1;
    for (IceUpdateMessage iceMessage : messages) {
      iceCandidates.add(new IceCandidateParcel(iceMessage));
      callId = iceMessage.getId();
    }

    Intent     intent     = new Intent(context, WebRtcCallService.class);
    RemotePeer remotePeer = new RemotePeer(Recipient.externalPush(context, content.getSender()).getId());

    intent.setAction(WebRtcCallService.ACTION_RECEIVE_ICE_CANDIDATES)
          .putExtra(WebRtcCallService.EXTRA_CALL_ID,       callId)
          .putExtra(WebRtcCallService.EXTRA_REMOTE_PEER,   remotePeer)
          .putExtra(WebRtcCallService.EXTRA_REMOTE_DEVICE, content.getSenderDevice())
          .putParcelableArrayListExtra(WebRtcCallService.EXTRA_ICE_CANDIDATES, iceCandidates);

    context.startService(intent);
  }

  private void handleCallHangupMessage(@NonNull SignalServiceContent content,
                                       @NonNull HangupMessage message,
                                       @NonNull Optional<Long> smsMessageId)
  {
    Log.i(TAG, "handleCallHangupMessage");
    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).markAsMissedCall(smsMessageId.get());
    } else {
      Intent     intent     = new Intent(context, WebRtcCallService.class);
      RemotePeer remotePeer = new RemotePeer(Recipient.externalPush(context, content.getSender()).getId());

      intent.setAction(WebRtcCallService.ACTION_RECEIVE_HANGUP)
            .putExtra(WebRtcCallService.EXTRA_CALL_ID,       message.getId())
            .putExtra(WebRtcCallService.EXTRA_REMOTE_PEER,   remotePeer)
            .putExtra(WebRtcCallService.EXTRA_REMOTE_DEVICE, content.getSenderDevice());

      context.startService(intent);
    }
  }

  private void handleCallBusyMessage(@NonNull SignalServiceContent content,
                                     @NonNull BusyMessage message)
  {
    Log.i(TAG, "handleCallBusyMessage");

    Intent     intent     = new Intent(context, WebRtcCallService.class);
    RemotePeer remotePeer = new RemotePeer(Recipient.externalPush(context, content.getSender()).getId());

    intent.setAction(WebRtcCallService.ACTION_RECEIVE_BUSY)
          .putExtra(WebRtcCallService.EXTRA_CALL_ID,       message.getId())
          .putExtra(WebRtcCallService.EXTRA_REMOTE_PEER,   remotePeer)
          .putExtra(WebRtcCallService.EXTRA_REMOTE_DEVICE, content.getSenderDevice());

    context.startService(intent);
  }

  private void handleEndSessionMessage(@NonNull SignalServiceContent content,
                                       @NonNull Optional<Long>       smsMessageId)
  {
    SmsDatabase         smsDatabase         = DatabaseFactory.getSmsDatabase(context);
    IncomingTextMessage incomingTextMessage = new IncomingTextMessage(Recipient.externalPush(context, content.getSender()).getId(),
                                                                      content.getSenderDevice(),
                                                                      content.getTimestamp(),
                                                                      content.getServerTimestamp(),
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
      SessionStore sessionStore = new TextSecureSessionStore(context);
      sessionStore.deleteAllSessions(content.getSender().getIdentifier());

      SecurityEvent.broadcastSecurityUpdateEvent(context);
      MessageNotifier.updateNotification(context, threadId);
    }
  }

  private long handleSynchronizeSentEndSessionMessage(@NonNull SentTranscriptMessage message)
      throws BadGroupIdException
  {
    SmsDatabase               database                  = DatabaseFactory.getSmsDatabase(context);
    Recipient                 recipient                 = getSyncMessageDestination(message);
    OutgoingTextMessage       outgoingTextMessage       = new OutgoingTextMessage(recipient, "", -1);
    OutgoingEndSessionMessage outgoingEndSessionMessage = new OutgoingEndSessionMessage(outgoingTextMessage);

    long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);

    if (!recipient.isGroup()) {
      SessionStore sessionStore = new TextSecureSessionStore(context);
      sessionStore.deleteAllSessions(recipient.requireServiceId());

      SecurityEvent.broadcastSecurityUpdateEvent(context);

      long messageId = database.insertMessageOutbox(threadId, outgoingEndSessionMessage,
                                                    false, message.getTimestamp(),
                                                    null);
      database.markAsSent(messageId, true);
    }

    return threadId;
  }

  private void handleGroupV1Message(@NonNull SignalServiceContent content,
                                    @NonNull SignalServiceDataMessage message,
                                    @NonNull Optional<Long> smsMessageId)
      throws StorageFailedException, BadGroupIdException
  {
    GroupV1MessageProcessor.process(context, content, message, false);

    if (message.getExpiresInSeconds() != 0 && message.getExpiresInSeconds() != getMessageDestination(content, message).getExpireMessages()) {
      handleExpirationUpdate(content, message, Optional.absent());
    }

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }
  }

  private void handleUnknownGroupMessage(@NonNull SignalServiceContent content,
                                         @NonNull SignalServiceGroupContext group)
      throws BadGroupIdException
  {
    if (group.getGroupV1().isPresent()) {
      SignalServiceGroup groupV1 = group.getGroupV1().get();
      if (groupV1.getType() != SignalServiceGroup.Type.REQUEST_INFO) {
        ApplicationDependencies.getJobManager().add(new RequestGroupInfoJob(Recipient.externalPush(context, content.getSender()).getId(), GroupId.v1(groupV1.getGroupId())));
      } else {
        Log.w(TAG, "Received a REQUEST_INFO message for a group we don't know about. Ignoring.");
      }
    } else {
      Log.w(TAG, "Received a message for a group we don't know about without a GV1 context. Ignoring.");
    }
  }

  private void handleExpirationUpdate(@NonNull SignalServiceContent content,
                                      @NonNull SignalServiceDataMessage message,
                                      @NonNull Optional<Long> smsMessageId)
      throws StorageFailedException, BadGroupIdException
  {
    try {
      MmsDatabase          database     = DatabaseFactory.getMmsDatabase(context);
      Recipient            sender       = Recipient.externalPush(context, content.getSender());
      Recipient            recipient    = getMessageDestination(content, message);
      IncomingMediaMessage mediaMessage = new IncomingMediaMessage(sender.getId(),
                                                                   message.getTimestamp(),
                                                                   content.getServerTimestamp(),
                                                                   -1,
                                                                   message.getExpiresInSeconds() * 1000L,
                                                                   true,
                                                                   false,
                                                                   content.isNeedsReceipt(),
                                                                   Optional.absent(),
                                                                   message.getGroupContext(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent());

        database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

        DatabaseFactory.getRecipientDatabase(context).setExpireMessages(recipient.getId(), message.getExpiresInSeconds());

      if (smsMessageId.isPresent()) {
        DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
      }
    } catch (MmsException e) {
      throw new StorageFailedException(e, content.getSender().getIdentifier(), content.getSenderDevice());
    }
  }

  private void handleReaction(@NonNull SignalServiceContent content, @NonNull SignalServiceDataMessage message) {
    SignalServiceDataMessage.Reaction reaction = message.getReaction().get();

    Recipient     targetAuthor  = Recipient.externalPush(context, reaction.getTargetAuthor());
    MessageRecord targetMessage = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(reaction.getTargetSentTimestamp(), targetAuthor.getId());

    if (targetMessage != null && !targetMessage.isRemoteDelete()) {
      Recipient         reactionAuthor = Recipient.externalPush(context, content.getSender());
      MessagingDatabase db             = targetMessage.isMms() ? DatabaseFactory.getMmsDatabase(context) : DatabaseFactory.getSmsDatabase(context);

      if (reaction.isRemove()) {
        db.deleteReaction(targetMessage.getId(), reactionAuthor.getId());
        MessageNotifier.updateNotification(context);
      } else {
        ReactionRecord reactionRecord = new ReactionRecord(reaction.getEmoji(), reactionAuthor.getId(), message.getTimestamp(), System.currentTimeMillis());
        db.addReaction(targetMessage.getId(), reactionRecord);
        MessageNotifier.updateNotification(context, targetMessage.getThreadId(), false);
      }
    } else if (targetMessage != null) {
      Log.w(TAG, "[handleReaction] Found a matching message, but it's flagged as remotely deleted. timestamp: " + reaction.getTargetSentTimestamp() + "  author: " + targetAuthor.getId());
    } else {
      Log.w(TAG, "[handleReaction] Could not find matching message! timestamp: " + reaction.getTargetSentTimestamp() + "  author: " + targetAuthor.getId());
      ApplicationDependencies.getEarlyMessageCache().store(targetAuthor.getId(), reaction.getTargetSentTimestamp(), content);
    }
  }

  private void handleRemoteDelete(@NonNull SignalServiceContent content, @NonNull SignalServiceDataMessage message) {
    SignalServiceDataMessage.RemoteDelete delete = message.getRemoteDelete().get();

    Recipient     sender        = Recipient.externalPush(context, content.getSender());
    MessageRecord targetMessage = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(delete.getTargetSentTimestamp(), sender.getId());

    if (targetMessage != null && RemoteDeleteUtil.isValidReceive(targetMessage, sender, content.getServerTimestamp())) {
      MessagingDatabase db = targetMessage.isMms() ? DatabaseFactory.getMmsDatabase(context) : DatabaseFactory.getSmsDatabase(context);
      db.markAsRemoteDelete(targetMessage.getId());
      MessageNotifier.updateNotification(context, targetMessage.getThreadId(), false);
    } else if (targetMessage == null) {
      Log.w(TAG, "[handleRemoteDelete] Could not find matching message! timestamp: " + delete.getTargetSentTimestamp() + "  author: " + sender.getId());
      ApplicationDependencies.getEarlyMessageCache().store(sender.getId(), delete.getTargetSentTimestamp(), content);
    } else {
      Log.w(TAG, String.format(Locale.ENGLISH, "[handleRemoteDelete] Invalid remote delete! deleteTime: %d, targetTime: %d, deleteAuthor: %s, targetAuthor: %s",
                                                content.getServerTimestamp(), targetMessage.getServerTimestamp(), sender.getId(), targetMessage.getRecipient().getId()));
    }
  }

  private void handleSynchronizeVerifiedMessage(@NonNull VerifiedMessage verifiedMessage) {
    IdentityUtil.processVerifiedMessage(context, verifiedMessage);
  }

  private void handleSynchronizeStickerPackOperation(@NonNull List<StickerPackOperationMessage> stickerPackOperations) {
    JobManager jobManager = ApplicationDependencies.getJobManager();

    for (StickerPackOperationMessage operation : stickerPackOperations) {
      if (operation.getPackId().isPresent() && operation.getPackKey().isPresent() && operation.getType().isPresent()) {
        String packId  = Hex.toStringCondensed(operation.getPackId().get());
        String packKey = Hex.toStringCondensed(operation.getPackKey().get());

        switch (operation.getType().get()) {
          case INSTALL:
            jobManager.add(StickerPackDownloadJob.forInstall(packId, packKey, false));
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

  private void handleSynchronizeConfigurationMessage(@NonNull ConfigurationMessage configurationMessage) {
    if (configurationMessage.getReadReceipts().isPresent()) {
      TextSecurePreferences.setReadReceiptsEnabled(context, configurationMessage.getReadReceipts().get());
    }

    if (configurationMessage.getUnidentifiedDeliveryIndicators().isPresent()) {
      TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(context, configurationMessage.getReadReceipts().get());
    }

    if (configurationMessage.getTypingIndicators().isPresent()) {
      TextSecurePreferences.setTypingIndicatorsEnabled(context, configurationMessage.getTypingIndicators().get());
    }

    if (configurationMessage.getLinkPreviews().isPresent()) {
      TextSecurePreferences.setLinkPreviewsEnabled(context, configurationMessage.getReadReceipts().get());
    }
  }

  private void handleSynchronizeBlockedListMessage(@NonNull BlockedListMessage blockMessage) {
    DatabaseFactory.getRecipientDatabase(context).applyBlockedUpdate(blockMessage.getAddresses(), blockMessage.getGroupIds());
  }

  private static void handleSynchronizeFetchMessage(@NonNull SignalServiceSyncMessage.FetchType fetchType) {
    Log.i(TAG, "Received fetch request with type: " + fetchType);

    switch (fetchType) {
      case LOCAL_PROFILE:
        ApplicationDependencies.getJobManager().add(new RefreshOwnProfileJob());
        break;
      case STORAGE_MANIFEST:
        StorageSyncHelper.scheduleSyncForDataChange();
        break;
      default:
        Log.w(TAG, "Received a fetch message for an unknown type.");
    }
  }

  private void handleSynchronizeMessageRequestResponse(@NonNull MessageRequestResponseMessage response)
      throws BadGroupIdException
  {
    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    ThreadDatabase    threadDatabase    = DatabaseFactory.getThreadDatabase(context);

    Recipient recipient;

    if (response.getPerson().isPresent()) {
      recipient = Recipient.externalPush(context, response.getPerson().get());
    } else if (response.getGroupId().isPresent()) {
      GroupId groupId = GroupId.v1(response.getGroupId().get());
      recipient = Recipient.externalGroup(context, groupId);
    } else {
      Log.w(TAG, "Message request response was missing a thread recipient! Skipping.");
      return;
    }

    long threadId = threadDatabase.getThreadIdFor(recipient);

    switch (response.getType()) {
      case ACCEPT:
        recipientDatabase.setProfileSharing(recipient.getId(), true);
        recipientDatabase.setBlocked(recipient.getId(), false);
        break;
      case DELETE:
        recipientDatabase.setProfileSharing(recipient.getId(), false);
        if (threadId > 0) threadDatabase.deleteConversation(threadId);
        break;
      case BLOCK:
        recipientDatabase.setBlocked(recipient.getId(), true);
        recipientDatabase.setProfileSharing(recipient.getId(), false);
        break;
      case BLOCK_AND_DELETE:
        recipientDatabase.setBlocked(recipient.getId(), true);
        recipientDatabase.setProfileSharing(recipient.getId(), false);
        if (threadId > 0) threadDatabase.deleteConversation(threadId);
        break;
      default:
        Log.w(TAG, "Got an unknown response type! Skipping");
        break;
    }
  }

  private void handleSynchronizeSentMessage(@NonNull SignalServiceContent content,
                                            @NonNull SentTranscriptMessage message)
      throws StorageFailedException, BadGroupIdException
  {
    try {
      GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);

      Long threadId = null;

      if (message.isRecipientUpdate()) {
        handleGroupRecipientUpdate(message);
      } else if (message.getMessage().isEndSession()) {
        threadId = handleSynchronizeSentEndSessionMessage(message);
      } else if (message.getMessage().isGroupV1Update()) {
        threadId = GroupV1MessageProcessor.process(context, content, message.getMessage(), true);
      } else if (message.getMessage().isExpirationUpdate()) {
        threadId = handleSynchronizeSentExpirationUpdate(message);
      } else if (message.getMessage().getReaction().isPresent()) {
        handleReaction(content, message.getMessage());
        threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(getSyncMessageDestination(message));
        threadId = threadId != -1 ? threadId : null;
      } else if (message.getMessage().getRemoteDelete().isPresent()) {
        handleRemoteDelete(content, message.getMessage());
      } else if (message.getMessage().getAttachments().isPresent() || message.getMessage().getQuote().isPresent() || message.getMessage().getPreviews().isPresent() || message.getMessage().getSticker().isPresent() || message.getMessage().isViewOnce()) {
        threadId = handleSynchronizeSentMediaMessage(message);
      } else {
        threadId = handleSynchronizeSentTextMessage(message);
      }

      if (message.getMessage().getGroupContext().isPresent() && groupDatabase.isUnknownGroup(GroupUtil.idFromGroupContext(message.getMessage().getGroupContext().get()))) {
        handleUnknownGroupMessage(content, message.getMessage().getGroupContext().get());
      }

      if (message.getMessage().getProfileKey().isPresent()) {
        Recipient recipient = getSyncMessageDestination(message);

        if (recipient != null && !recipient.isSystemContact() && !recipient.isProfileSharing()) {
          DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipient.getId(), true);
        }
      }

      if (threadId != null) {
        DatabaseFactory.getThreadDatabase(context).setRead(threadId, true);
        MessageNotifier.updateNotification(context);
      }

      MessageNotifier.setLastDesktopActivityTimestamp(message.getTimestamp());
    } catch (MmsException e) {
      throw new StorageFailedException(e, content.getSender().getIdentifier(), content.getSenderDevice());
    }
  }

  private void handleSynchronizeRequestMessage(@NonNull RequestMessage message)
  {
    if (message.isContactsRequest()) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob(true));
    }

    if (message.isGroupsRequest()) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceGroupUpdateJob());
    }

    if (message.isBlockedListRequest()) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceBlockedUpdateJob());
    }

    if (message.isConfigurationRequest()) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(context),
                                                                                        TextSecurePreferences.isTypingIndicatorsEnabled(context),
                                                                                        TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
                                                                                        TextSecurePreferences.isLinkPreviewsEnabled(context)));
      ApplicationDependencies.getJobManager().add(new MultiDeviceStickerPackSyncJob());
    }

    if (message.isKeysRequest()) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceKeysUpdateJob());
    }
  }

  private void handleSynchronizeReadMessage(@NonNull List<ReadMessage> readMessages, long envelopeTimestamp)
  {
    for (ReadMessage readMessage : readMessages) {
      List<Pair<Long, Long>> expiringText  = DatabaseFactory.getSmsDatabase(context).setTimestampRead(new SyncMessageId(Recipient.externalPush(context, readMessage.getSender()).getId(), readMessage.getTimestamp()), envelopeTimestamp);
      List<Pair<Long, Long>> expiringMedia = DatabaseFactory.getMmsDatabase(context).setTimestampRead(new SyncMessageId(Recipient.externalPush(context, readMessage.getSender()).getId(), readMessage.getTimestamp()), envelopeTimestamp);

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

  private void handleSynchronizeViewOnceOpenMessage(@NonNull ViewOnceOpenMessage openMessage, long envelopeTimestamp) {
    RecipientId   author    = Recipient.externalPush(context, openMessage.getSender()).getId();
    long          timestamp = openMessage.getTimestamp();
    MessageRecord record    = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(timestamp, author);

    if (record != null && record.isMms()) {
      DatabaseFactory.getAttachmentDatabase(context).deleteAttachmentFilesForViewOnceMessage(record.getId());
    }

    MessageNotifier.setLastDesktopActivityTimestamp(envelopeTimestamp);
    MessageNotifier.cancelDelayedNotifications();
    MessageNotifier.updateNotification(context);
  }

  private void handleMediaMessage(@NonNull SignalServiceContent content,
                                  @NonNull SignalServiceDataMessage message,
                                  @NonNull Optional<Long> smsMessageId)
      throws StorageFailedException, BadGroupIdException
  {
    notifyTypingStoppedFromIncomingMessage(getMessageDestination(content, message), content.getSender(), content.getSenderDevice());

    Optional<InsertResult> insertResult;

    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    database.beginTransaction();

    try {
      Optional<QuoteModel>        quote          = getValidatedQuote(message.getQuote());
      Optional<List<Contact>>     sharedContacts = getContacts(message.getSharedContacts());
      Optional<List<LinkPreview>> linkPreviews   = getLinkPreviews(message.getPreviews(), message.getBody().or(""));
      Optional<Attachment>        sticker        = getStickerAttachment(message.getSticker());
      IncomingMediaMessage        mediaMessage   = new IncomingMediaMessage(Recipient.externalPush(context, content.getSender()).getId(),
                                                                            message.getTimestamp(),
                                                                            content.getServerTimestamp(),
                                                                            -1,
                                                                            message.getExpiresInSeconds() * 1000L,
                                                                            false,
                                                                            message.isViewOnce(),
                                                                            content.isNeedsReceipt(),
                                                                            message.getBody(),
                                                                            message.getGroupContext(),
                                                                            message.getAttachments(),
                                                                            quote,
                                                                            sharedContacts,
                                                                            linkPreviews,
                                                                            sticker);

      insertResult = database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

      if (insertResult.isPresent()) {
        List<DatabaseAttachment> allAttachments     = DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(insertResult.get().getMessageId());
        List<DatabaseAttachment> stickerAttachments = Stream.of(allAttachments).filter(Attachment::isSticker).toList();
        List<DatabaseAttachment> attachments        = Stream.of(allAttachments).filterNot(Attachment::isSticker).toList();

        forceStickerDownloadIfNecessary(stickerAttachments);

        for (DatabaseAttachment attachment : attachments) {
          ApplicationDependencies.getJobManager().add(new AttachmentDownloadJob(insertResult.get().getMessageId(), attachment.getAttachmentId(), false));
        }

        if (smsMessageId.isPresent()) {
          DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
        }

        database.setTransactionSuccessful();
      }
    } catch (MmsException e) {
      throw new StorageFailedException(e, content.getSender().getIdentifier(), content.getSenderDevice());
    } finally {
      database.endTransaction();
    }

    if (insertResult.isPresent()) {
      MessageNotifier.updateNotification(context, insertResult.get().getThreadId());

      if (message.isViewOnce()) {
        ApplicationContext.getInstance(context).getViewOnceMessageManager().scheduleIfNecessary();
      }
    }
  }

  private long handleSynchronizeSentExpirationUpdate(@NonNull SentTranscriptMessage message)
      throws MmsException, BadGroupIdException
  {
    MmsDatabase database   = DatabaseFactory.getMmsDatabase(context);
    Recipient   recipient  = getSyncMessageDestination(message);

    OutgoingExpirationUpdateMessage expirationUpdateMessage = new OutgoingExpirationUpdateMessage(recipient,
                                                                                                  message.getTimestamp(),
                                                                                                  message.getMessage().getExpiresInSeconds() * 1000L);

    long threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
    long messageId = database.insertMessageOutbox(expirationUpdateMessage, threadId, false, null);

    database.markAsSent(messageId, true);

    DatabaseFactory.getRecipientDatabase(context).setExpireMessages(recipient.getId(), message.getMessage().getExpiresInSeconds());

    return threadId;
  }

  private long handleSynchronizeSentMediaMessage(@NonNull SentTranscriptMessage message)
      throws MmsException, BadGroupIdException
  {
    MmsDatabase                 database        = DatabaseFactory.getMmsDatabase(context);
    Recipient                   recipients      = getSyncMessageDestination(message);
    Optional<QuoteModel>        quote           = getValidatedQuote(message.getMessage().getQuote());
    Optional<Attachment>        sticker         = getStickerAttachment(message.getMessage().getSticker());
    Optional<List<Contact>>     sharedContacts  = getContacts(message.getMessage().getSharedContacts());
    Optional<List<LinkPreview>> previews        = getLinkPreviews(message.getMessage().getPreviews(), message.getMessage().getBody().or(""));
    boolean                     viewOnce        = message.getMessage().isViewOnce();
    List<Attachment>            syncAttachments = viewOnce ? Collections.singletonList(new TombstoneAttachment(MediaUtil.VIEW_ONCE, false))
                                                           : PointerAttachment.forPointers(message.getMessage().getAttachments());

    if (sticker.isPresent()) {
      syncAttachments.add(sticker.get());
    }

    OutgoingMediaMessage mediaMessage = new OutgoingMediaMessage(recipients, message.getMessage().getBody().orNull(),
                                                                 syncAttachments,
                                                                 message.getTimestamp(), -1,
                                                                 message.getMessage().getExpiresInSeconds() * 1000,
                                                                 viewOnce,
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
      long messageId = database.insertMessageOutbox(mediaMessage, threadId, false, GroupReceiptDatabase.STATUS_UNKNOWN, null);

      if (recipients.isGroup()) {
        updateGroupReceiptStatus(message, messageId, recipients.requireGroupId());
      } else {
        database.markUnidentified(messageId, isUnidentified(message, recipients));
      }

      database.markAsSent(messageId, true);

      List<DatabaseAttachment> allAttachments     = DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(messageId);
      List<DatabaseAttachment> stickerAttachments = Stream.of(allAttachments).filter(Attachment::isSticker).toList();
      List<DatabaseAttachment> attachments        = Stream.of(allAttachments).filterNot(Attachment::isSticker).toList();

      forceStickerDownloadIfNecessary(stickerAttachments);

      for (DatabaseAttachment attachment : attachments) {
        ApplicationDependencies.getJobManager().add(new AttachmentDownloadJob(messageId, attachment.getAttachmentId(), false));
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
        SyncMessageId id = new SyncMessageId(recipients.getId(), message.getTimestamp());
        DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(id, System.currentTimeMillis());
        DatabaseFactory.getMmsSmsDatabase(context).incrementReadReceiptCount(id, System.currentTimeMillis());
      }

      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }
    return threadId;
  }

  private void handleGroupRecipientUpdate(@NonNull SentTranscriptMessage message)
      throws BadGroupIdException
  {
    Recipient recipient = getSyncMessageDestination(message);

    if (!recipient.isGroup()) {
      Log.w(TAG, "Got recipient update for a non-group message! Skipping.");
      return;
    }

    MmsSmsDatabase database = DatabaseFactory.getMmsSmsDatabase(context);
    MessageRecord  record   = database.getMessageFor(message.getTimestamp(), Recipient.self().getId());

    if (record == null) {
      Log.w(TAG, "Got recipient update for non-existing message! Skipping.");
      return;
    }

    if (!record.isMms()) {
      Log.w(TAG, "Recipient update matched a non-MMS message! Skipping.");
      return;
    }

    updateGroupReceiptStatus(message, record.getId(), recipient.requireGroupId());
  }

  private void updateGroupReceiptStatus(@NonNull SentTranscriptMessage message, long messageId, @NonNull GroupId groupString) {
    GroupReceiptDatabase      receiptDatabase   = DatabaseFactory.getGroupReceiptDatabase(context);
    List<Recipient>           messageRecipients = Stream.of(message.getRecipients()).map(address -> Recipient.externalPush(context, address)).toList();
    List<Recipient>           members           = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupString, GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
    Map<RecipientId, Integer> localReceipts     = Stream.of(receiptDatabase.getGroupReceiptInfo(messageId))
                                                        .collect(Collectors.toMap(GroupReceiptInfo::getRecipientId, GroupReceiptInfo::getStatus));

    for (Recipient messageRecipient : messageRecipients) {
      //noinspection ConstantConditions
      if (localReceipts.containsKey(messageRecipient.getId()) && localReceipts.get(messageRecipient.getId()) < GroupReceiptDatabase.STATUS_UNDELIVERED) {
        receiptDatabase.update(messageRecipient.getId(), messageId, GroupReceiptDatabase.STATUS_UNDELIVERED, message.getTimestamp());
      } else if (!localReceipts.containsKey(messageRecipient.getId())) {
        receiptDatabase.insert(Collections.singletonList(messageRecipient.getId()), messageId, GroupReceiptDatabase.STATUS_UNDELIVERED, message.getTimestamp());
      }
    }

    for (Recipient member : members) {
      receiptDatabase.setUnidentified(member.getId(), messageId, message.isUnidentified(member.requireServiceId()));
    }
  }

  private void handleTextMessage(@NonNull SignalServiceContent content,
                                 @NonNull SignalServiceDataMessage message,
                                 @NonNull Optional<Long> smsMessageId,
                                 @NonNull Optional<GroupId> groupId)
      throws StorageFailedException, BadGroupIdException
  {
    SmsDatabase database  = DatabaseFactory.getSmsDatabase(context);
    String      body      = message.getBody().isPresent() ? message.getBody().get() : "";
    Recipient   recipient = getMessageDestination(content, message);

    if (message.getExpiresInSeconds() != recipient.getExpireMessages()) {
      handleExpirationUpdate(content, message, Optional.absent());
    }

    Long threadId;

    if (smsMessageId.isPresent() && !message.getGroupContext().isPresent()) {
      threadId = database.updateBundleMessageBody(smsMessageId.get(), body).second;
    } else {
      notifyTypingStoppedFromIncomingMessage(recipient, content.getSender(), content.getSenderDevice());

      IncomingTextMessage textMessage = new IncomingTextMessage(Recipient.externalPush(context, content.getSender()).getId(),
                                                                content.getSenderDevice(),
                                                                message.getTimestamp(),
                                                                content.getServerTimestamp(),
                                                                body,
                                                                groupId,
                                                                message.getExpiresInSeconds() * 1000L,
                                                                content.isNeedsReceipt());

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
      throws MmsException, BadGroupIdException
  {
    Recipient recipient       = getSyncMessageDestination(message);
    String    body            = message.getMessage().getBody().or("");
    long      expiresInMillis = message.getMessage().getExpiresInSeconds() * 1000L;

    if (recipient.getExpireMessages() != message.getMessage().getExpiresInSeconds()) {
      handleSynchronizeSentExpirationUpdate(message);
    }

    long    threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
    boolean isGroup   = recipient.isGroup();

    MessagingDatabase database;
    long              messageId;

    if (isGroup) {
      OutgoingMediaMessage outgoingMediaMessage = new OutgoingMediaMessage(recipient, new SlideDeck(), body, message.getTimestamp(), -1, expiresInMillis, false, ThreadDatabase.DistributionTypes.DEFAULT, null, Collections.emptyList(), Collections.emptyList());
      outgoingMediaMessage = new OutgoingSecureMediaMessage(outgoingMediaMessage);

      messageId = DatabaseFactory.getMmsDatabase(context).insertMessageOutbox(outgoingMediaMessage, threadId, false, GroupReceiptDatabase.STATUS_UNKNOWN, null);
      database  = DatabaseFactory.getMmsDatabase(context);

      updateGroupReceiptStatus(message, messageId, recipient.requireGroupId());
    } else {
      OutgoingTextMessage outgoingTextMessage = new OutgoingEncryptedMessage(recipient, body, expiresInMillis);

      messageId = DatabaseFactory.getSmsDatabase(context).insertMessageOutbox(threadId, outgoingTextMessage, false, message.getTimestamp(), null);
      database  = DatabaseFactory.getSmsDatabase(context);
      database.markUnidentified(messageId, isUnidentified(message, recipient));
    }

    database.markAsSent(messageId, true);

    if (expiresInMillis > 0) {
      database.markExpireStarted(messageId, message.getExpirationStartTimestamp());
      ApplicationContext.getInstance(context)
                        .getExpiringMessageManager()
                        .scheduleDeletion(messageId, isGroup, message.getExpirationStartTimestamp(), expiresInMillis);
    }

    if (recipient.isLocalNumber()) {
      SyncMessageId id = new SyncMessageId(recipient.getId(), message.getTimestamp());
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

  private void handleUnsupportedDataMessage(@NonNull String sender,
                                            int senderDevice,
                                            @NonNull Optional<GroupId> groupId,
                                            long timestamp,
                                            @NonNull Optional<Long> smsMessageId)
  {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp, groupId);

      if (insertResult.isPresent()) {
        smsDatabase.markAsUnsupportedProtocolVersion(insertResult.get().getMessageId());
        MessageNotifier.updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsNoSession(smsMessageId.get());
    }
  }

  private void handleInvalidMessage(@NonNull SignalServiceAddress sender,
                                    int senderDevice,
                                    @NonNull Optional<GroupId> groupId,
                                    long timestamp,
                                    @NonNull Optional<Long> smsMessageId)
  {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender.getIdentifier(), senderDevice, timestamp, groupId);

      if (insertResult.isPresent()) {
        smsDatabase.markAsInvalidMessage(insertResult.get().getMessageId());
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
                                @NonNull byte[] messageProfileKeyBytes)
  {
    RecipientDatabase database          = DatabaseFactory.getRecipientDatabase(context);
    Recipient         recipient         = Recipient.externalPush(context, content.getSender());
    ProfileKey        messageProfileKey = ProfileKeyUtil.profileKeyOrNull(messageProfileKeyBytes);

    if (messageProfileKey != null) {
      if (database.setProfileKey(recipient.getId(), messageProfileKey)) {
        ApplicationDependencies.getJobManager().add(RetrieveProfileJob.forRecipient(recipient));
      }
    } else {
      Log.w(TAG, "Ignored invalid profile key seen in message");
    }
  }

  private void handleNeedsDeliveryReceipt(@NonNull SignalServiceContent content,
                                          @NonNull SignalServiceDataMessage message)
  {
    ApplicationDependencies.getJobManager().add(new SendDeliveryReceiptJob(Recipient.externalPush(context, content.getSender()).getId(), message.getTimestamp()));
  }

  @SuppressLint("DefaultLocale")
  private void handleDeliveryReceipt(@NonNull SignalServiceContent content,
                                     @NonNull SignalServiceReceiptMessage message)
  {
    for (long timestamp : message.getTimestamps()) {
      Log.i(TAG, String.format("Received encrypted delivery receipt: (XXXXX, %d)", timestamp));
      DatabaseFactory.getMmsSmsDatabase(context)
                     .incrementDeliveryReceiptCount(new SyncMessageId(Recipient.externalPush(context, content.getSender()).getId(), timestamp), System.currentTimeMillis());
    }
  }

  @SuppressLint("DefaultLocale")
  private void handleReadReceipt(@NonNull SignalServiceContent content,
                                 @NonNull SignalServiceReceiptMessage message)
  {
    if (TextSecurePreferences.isReadReceiptsEnabled(context)) {
      for (long timestamp : message.getTimestamps()) {
        Log.i(TAG, String.format("Received encrypted read receipt: (XXXXX, %d)", timestamp));

        Recipient     sender  = Recipient.externalPush(context, content.getSender());
        SyncMessageId id      = new SyncMessageId(sender.getId(), timestamp);
        boolean       handled = DatabaseFactory.getMmsSmsDatabase(context)
                                               .incrementReadReceiptCount(id, content.getTimestamp());

        if (!handled) {
          ApplicationDependencies.getEarlyMessageCache().store(sender.getId(), timestamp, content);
        }
      }
    }
  }

  private void handleTypingMessage(@NonNull SignalServiceContent content,
                                   @NonNull SignalServiceTypingMessage typingMessage)
      throws BadGroupIdException
  {
    if (!TextSecurePreferences.isTypingIndicatorsEnabled(context)) {
      return;
    }

    Recipient author = Recipient.externalPush(context, content.getSender());

    long threadId;

    if (typingMessage.getGroupId().isPresent()) {
      GroupId.Push groupId = GroupId.push(typingMessage.getGroupId().get());

      if (!DatabaseFactory.getGroupDatabase(context).isCurrentMember(groupId, author.getId())) {
        Log.w(TAG, "Seen typing indicator for non-member");
        return;
      }

      Recipient groupRecipient = Recipient.externalGroup(context, groupId);

      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
    } else {
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

  private static boolean isInvalidMessage(@NonNull SignalServiceDataMessage message) {
    if (message.isViewOnce()) {
      List<SignalServiceAttachment> attachments = message.getAttachments().or(Collections.emptyList());

      return attachments.size() != 1  ||
             !isViewOnceSupportedContentType(attachments.get(0).getContentType().toLowerCase());
    }

    return false;
  }

  private static boolean isViewOnceSupportedContentType(@NonNull String contentType) {
    return MediaUtil.isImageType(contentType) || MediaUtil.isVideoType(contentType);
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

    RecipientId   author  = Recipient.externalPush(context, quote.get().getAuthor()).getId();
    MessageRecord message = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(quote.get().getId(), author);

    if (message != null && !message.isRemoteDelete()) {
      Log.i(TAG, "Found matching message record...");

      List<Attachment> attachments = new LinkedList<>();

      if (message.isMms()) {
        MmsMessageRecord mmsMessage = (MmsMessageRecord) message;

        if (mmsMessage.isViewOnce()) {
          attachments.add(new TombstoneAttachment(MediaUtil.VIEW_ONCE, true));
        } else {
          attachments = mmsMessage.getSlideDeck().asAttachments();

          if (attachments.isEmpty()) {
            attachments.addAll(Stream.of(mmsMessage.getLinkPreviews())
                                     .filter(lp -> lp.getThumbnail().isPresent())
                                     .map(lp -> lp.getThumbnail().get())
                                     .toList());
          }
        }
      }

      return Optional.of(new QuoteModel(quote.get().getId(), author, message.getBody(), false, attachments));
    } else if (message != null) {
      Log.w(TAG, "Found the target for the quote, but it's flagged as remotely deleted.");
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
                                           stickerLocator,
                                           null,
                                           null));
    } else {
      return Optional.of(PointerAttachment.forPointer(Optional.of(sticker.get().getAttachment()), stickerLocator).get());
    }
  }

  private static Optional<List<Contact>> getContacts(Optional<List<SharedContact>> sharedContacts) {
    if (!sharedContacts.isPresent()) return Optional.absent();

    List<Contact> contacts = new ArrayList<>(sharedContacts.get().size());

    for (SharedContact sharedContact : sharedContacts.get()) {
      contacts.add(ContactModelMapper.remoteToLocal(sharedContact));
    }

    return Optional.of(contacts);
  }

  private static Optional<List<LinkPreview>> getLinkPreviews(Optional<List<Preview>> previews, @NonNull String message) {
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
    return insertPlaceholder(sender, senderDevice, timestamp, Optional.absent());
  }

  private Optional<InsertResult> insertPlaceholder(@NonNull String sender, int senderDevice, long timestamp, Optional<GroupId> groupId) {
    SmsDatabase         database    = DatabaseFactory.getSmsDatabase(context);
    IncomingTextMessage textMessage = new IncomingTextMessage(Recipient.external(context, sender).getId(),
                                                              senderDevice, timestamp, -1, "",
                                                              groupId, 0, false);

    textMessage = new IncomingEncryptedMessage(textMessage, "");
    return database.insertMessageInbox(textMessage);
  }

  private Recipient getSyncMessageDestination(@NonNull SentTranscriptMessage message)
      throws BadGroupIdException
  {
    return getGroupRecipient(message.getMessage().getGroupContext())
           .or(() -> Recipient.externalPush(context, message.getDestination().get()));
  }

  private Recipient getMessageDestination(@NonNull SignalServiceContent content,
                                          @NonNull SignalServiceDataMessage message)
      throws BadGroupIdException
  {
    return getGroupRecipient(message.getGroupContext())
           .or(() -> Recipient.externalPush(context, content.getSender()));
  }

  private Optional<Recipient> getGroupRecipient(Optional<SignalServiceGroupContext> message)
      throws BadGroupIdException
  {
    if (message.isPresent()) {
      return Optional.of(Recipient.externalGroup(context, GroupUtil.idFromGroupContext(message.get())));
    }
    return Optional.absent();
  }

  private void notifyTypingStoppedFromIncomingMessage(@NonNull Recipient conversationRecipient, @NonNull SignalServiceAddress sender, int device) {
    Recipient author   = Recipient.externalPush(context, sender);
    long      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(conversationRecipient);

    if (threadId > 0) {
      Log.d(TAG, "Typing stopped on thread " + threadId + " due to an incoming message.");
      ApplicationContext.getInstance(context).getTypingStatusRepository().onTypingStopped(context, threadId, author, device, true);
    }
  }

  private boolean shouldIgnore(@Nullable SignalServiceContent content)
      throws BadGroupIdException
  {
    if (content == null) {
      Log.w(TAG, "Got a message with null content.");
      return true;
    }

    Recipient sender = Recipient.externalPush(context, content.getSender());

    if (content.getDataMessage().isPresent()) {
      SignalServiceDataMessage message      = content.getDataMessage().get();
      Recipient                conversation = getMessageDestination(content, message);

      if (conversation.isGroup() && conversation.isBlocked()) {
        return true;
      } else if (conversation.isGroup()) {
        GroupDatabase     groupDatabase = DatabaseFactory.getGroupDatabase(context);
        Optional<GroupId> groupId       = GroupUtil.idFromGroupContext(message.getGroupContext());

        if (groupId.isPresent() && groupDatabase.isUnknownGroup(groupId.get())) {
          return false;
        }

        boolean isTextMessage    = message.getBody().isPresent();
        boolean isMediaMessage   = message.getAttachments().isPresent() || message.getQuote().isPresent() || message.getSharedContacts().isPresent();
        boolean isExpireMessage  = message.isExpirationUpdate();
        boolean isContentMessage = !message.isGroupV1Update() && !isExpireMessage && (isTextMessage || isMediaMessage);
        boolean isGroupActive    = groupId.isPresent() && groupDatabase.isActive(groupId.get());
        boolean isLeaveMessage   = message.getGroupContext().isPresent() && message.getGroupContext().get().getGroupV1Type() == SignalServiceGroup.Type.QUIT;

        return (isContentMessage && !isGroupActive) || (sender.isBlocked() && !isLeaveMessage);
      } else {
        return sender.isBlocked();
      }
    } else if (content.getCallMessage().isPresent() || content.getTypingMessage().isPresent()) {
      return sender.isBlocked();
    }

    return false;
  }

  private void resetRecipientToPush(@NonNull Recipient recipient) {
    if (recipient.isForceSmsSelection()) {
      DatabaseFactory.getRecipientDatabase(context).setForceSmsSelection(recipient.getId(), false);
    }
  }

  private void forceStickerDownloadIfNecessary(List<DatabaseAttachment> stickerAttachments) {
    if (stickerAttachments.isEmpty()) return;

    DatabaseAttachment stickerAttachment = stickerAttachments.get(0);

    if (stickerAttachment.getTransferState() != AttachmentDatabase.TRANSFER_PROGRESS_DONE) {
      AttachmentDownloadJob downloadJob = new AttachmentDownloadJob(messageId, stickerAttachment.getAttachmentId(), true);

      try {
        downloadJob.setContext(context);
        downloadJob.doWork();
      } catch (Exception e) {
        Log.w(TAG, "Failed to download sticker inline. Scheduling.");
        ApplicationDependencies.getJobManager().add(downloadJob);
      }
    }
  }

  private static boolean isUnidentified(@NonNull SentTranscriptMessage message, @NonNull Recipient recipient) {
    boolean unidentified = false;

    if (recipient.hasE164()) {
      unidentified |= message.isUnidentified(recipient.requireE164());
    }
    if (recipient.hasUuid()) {
      unidentified |= message.isUnidentified(recipient.requireUuid());
    }

    return unidentified;
  }

  @SuppressWarnings("WeakerAccess")
  private static class StorageFailedException extends Exception {
    private final String sender;
    private final int    senderDevice;

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

  public static final class Factory implements Job.Factory<PushProcessMessageJob> {
    @Override
    public @NonNull PushProcessMessageJob create(@NonNull Parameters parameters, @NonNull Data data) {
      try {
        MessageState state = MessageState.values()[data.getInt(KEY_MESSAGE_STATE)];

        if (state == MessageState.DECRYPTED_OK) {
          return new PushProcessMessageJob(parameters,
                                           state,
                                           Base64.decode(data.getString(KEY_MESSAGE_PLAINTEXT)),
                                           null,
                                           data.getLong(KEY_MESSAGE_ID),
                                           data.getLong(KEY_SMS_MESSAGE_ID),
                                           data.getLong(KEY_TIMESTAMP));
        } else {
          ExceptionMetadata exceptionMetadata = new ExceptionMetadata(data.getString(KEY_EXCEPTION_SENDER),
                                                                      data.getInt(KEY_EXCEPTION_DEVICE),
                                                                      GroupId.parseNullableOrThrow(data.getStringOrDefault(KEY_EXCEPTION_GROUP_ID, null)));

          return new PushProcessMessageJob(parameters,
                                           state,
                                           null,
                                           exceptionMetadata,
                                           data.getLong(KEY_MESSAGE_ID),
                                           data.getLong(KEY_SMS_MESSAGE_ID),
                                           data.getLong(KEY_TIMESTAMP));
        }
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }

  public enum MessageState {
    DECRYPTED_OK,
    INVALID_VERSION,
    CORRUPT_MESSAGE,
    NO_SESSION,
    LEGACY_MESSAGE,
    DUPLICATE_MESSAGE,
    UNSUPPORTED_DATA_MESSAGE
  }

  static class ExceptionMetadata {
    @NonNull  private final String  sender;
              private final int     senderDevice;
    @Nullable private final GroupId groupId;

    ExceptionMetadata(@NonNull String sender, int senderDevice, @Nullable GroupId groupId) {
      this.sender       = sender;
      this.senderDevice = senderDevice;
      this.groupId      = groupId;
    }

    ExceptionMetadata(@NonNull String sender, int senderDevice) {
      this(sender, senderDevice, null);
    }
  }
}
