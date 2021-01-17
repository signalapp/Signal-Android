package org.thoughtcrime.securesms.jobs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.zkgroup.VerificationFailedException;
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
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.MessageDatabase.InsertResult;
import org.thoughtcrime.securesms.database.MessageDatabase.SyncMessageId;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.StickerDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.BadGroupIdException;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupNotAMemberException;
import org.thoughtcrime.securesms.groups.GroupV1MessageProcessor;
import org.thoughtcrime.securesms.groups.GroupsV1MigrationUtil;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil;
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
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.RemoteDeleteUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.groupsv2.NoCredentialForRedemptionTimeException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Preview;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.OpaqueMessage;
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
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class PushProcessMessageJob extends BaseJob {

  public static final String KEY          = "PushProcessJob";
  public static final String QUEUE_PREFIX = "__PUSH_PROCESS_JOB__";

  public static final String TAG = Log.tag(PushProcessMessageJob.class);

  private static final String KEY_MESSAGE_STATE      = "message_state";
  private static final String KEY_MESSAGE_PLAINTEXT  = "message_content";
  private static final String KEY_MESSAGE_ID         = "message_id";
  private static final String KEY_SMS_MESSAGE_ID     = "sms_message_id";
  private static final String KEY_TIMESTAMP          = "timestamp";
  private static final String KEY_EXCEPTION_SENDER   = "exception_sender";
  private static final String KEY_EXCEPTION_DEVICE   = "exception_device";
  private static final String KEY_EXCEPTION_GROUP_ID = "exception_groupId";

  @NonNull  private final MessageState         messageState;
  @Nullable private final SignalServiceContent content;
  @Nullable private final ExceptionMetadata    exceptionMetadata;
            private final long                 messageId;
            private final long                 smsMessageId;
            private final long                 timestamp;

  @WorkerThread
  PushProcessMessageJob(@NonNull SignalServiceContent content,
                        long pushMessageId,
                        long smsMessageId,
                        long timestamp)
  {
    this(MessageState.DECRYPTED_OK,
         content,
         null,
         pushMessageId,
         smsMessageId,
         timestamp);
  }

  @WorkerThread
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

  @WorkerThread
  private PushProcessMessageJob(@NonNull MessageState messageState,
                                @Nullable SignalServiceContent content,
                                @Nullable ExceptionMetadata exceptionMetadata,
                                long pushMessageId,
                                long smsMessageId,
                                long timestamp)
  {
    this(createParameters(content, exceptionMetadata),
         messageState,
         content,
         exceptionMetadata,
         pushMessageId,
         smsMessageId,
         timestamp);
  }

  private PushProcessMessageJob(@NonNull Parameters parameters,
                                @NonNull MessageState messageState,
                                @Nullable SignalServiceContent content,
                                @Nullable ExceptionMetadata exceptionMetadata,
                                long pushMessageId,
                                long smsMessageId,
                                long timestamp)
  {
    super(parameters);

    this.messageState      = messageState;
    this.exceptionMetadata = exceptionMetadata;
    this.content           = content;
    this.messageId         = pushMessageId;
    this.smsMessageId      = smsMessageId;
    this.timestamp         = timestamp;
  }

  static @NonNull String getQueueName(@NonNull RecipientId recipientId) {
    return QUEUE_PREFIX + recipientId.toQueueKey();
  }

  @WorkerThread
  private static @NonNull Parameters createParameters(@Nullable SignalServiceContent content, @Nullable ExceptionMetadata exceptionMetadata) {
    Context            context   = ApplicationDependencies.getApplication();
    String             queueName = QUEUE_PREFIX;
    Parameters.Builder builder   = new Parameters.Builder()
                                                 .setMaxAttempts(Parameters.UNLIMITED);

    if (content != null) {
      if (content.getDataMessage().isPresent() && content.getDataMessage().get().getGroupContext().isPresent()) {
        try {
          SignalServiceGroupContext signalServiceGroupContext = content.getDataMessage().get().getGroupContext().get();
          GroupId                   groupId                   = GroupUtil.idFromGroupContext(signalServiceGroupContext);

          queueName = getQueueName(Recipient.externalPossiblyMigratedGroup(context, groupId).getId());

          if (groupId.isV2()) {
            int localRevision = DatabaseFactory.getGroupDatabase(context)
                                               .getGroupV2Revision(groupId.requireV2());

            if (signalServiceGroupContext.getGroupV2().get().getRevision() > localRevision ||
                DatabaseFactory.getGroupDatabase(context).getGroupV1ByExpectedV2(groupId.requireV2()).isPresent())
            {
              Log.i(TAG, "Adding network constraint to group-related job.");
              builder.addConstraint(NetworkConstraint.KEY)
                     .setLifespan(TimeUnit.DAYS.toMillis(30));
            }
          }
        } catch (BadGroupIdException e) {
          Log.w(TAG, "Bad groupId! Using default queue. ID: " + content.getTimestamp());
        }
      } else {
        queueName = getQueueName(RecipientId.fromHighTrust(content.getSender()));
      }
    } else if (exceptionMetadata != null) {
      Recipient recipient = exceptionMetadata.groupId != null ? Recipient.externalPossiblyMigratedGroup(context, exceptionMetadata.groupId)
                                                              : Recipient.external(context, exceptionMetadata.sender);
      queueName = getQueueName(recipient.getId());
    }

    builder.setQueue(queueName);

    return builder.build();
  }

  @Override
  public @NonNull Data serialize() {
    Data.Builder dataBuilder = new Data.Builder()
                                       .putInt(KEY_MESSAGE_STATE, messageState.ordinal())
                                       .putLong(KEY_MESSAGE_ID, messageId)
                                       .putLong(KEY_SMS_MESSAGE_ID, smsMessageId)
                                       .putLong(KEY_TIMESTAMP, timestamp);

    if (messageState == MessageState.DECRYPTED_OK) {
      dataBuilder.putString(KEY_MESSAGE_PLAINTEXT, Base64.encodeBytes(Objects.requireNonNull(content).serialize()));
    } else {
      Objects.requireNonNull(exceptionMetadata);
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
  public void onRun() throws Exception {
    Optional<Long> optionalSmsMessageId = smsMessageId > 0 ? Optional.of(smsMessageId) : Optional.absent();

    if (messageState == MessageState.DECRYPTED_OK) {
      handleMessage(content, optionalSmsMessageId);

      Optional<List<SignalServiceContent>> earlyContent = ApplicationDependencies.getEarlyMessageCache()
                                                                                 .retrieve(Recipient.externalPush(context, content.getSender()).getId(),
                                                                                           content.getTimestamp());
      if (earlyContent.isPresent()) {
        log(TAG, String.valueOf(content.getTimestamp()), "Found " + earlyContent.get().size() + " dependent item(s) that were retrieved earlier. Processing.");

        for (SignalServiceContent earlyItem : earlyContent.get()) {
          handleMessage(earlyItem, Optional.absent());
        }
      }
    } else if (exceptionMetadata != null) {
      handleExceptionMessage(exceptionMetadata, optionalSmsMessageId);
    } else {
      warn(TAG, "Bad state! messageState: " + messageState);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException ||
           e instanceof NoCredentialForRedemptionTimeException ||
           e instanceof GroupChangeBusyException;
  }

  @Override
  public void onFailure() {
  }

  private void handleMessage(@Nullable SignalServiceContent content, @NonNull Optional<Long> smsMessageId)
      throws VerificationFailedException, IOException, InvalidGroupStateException, GroupChangeBusyException
  {
    try {
      GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);

      if (content == null || shouldIgnore(content)) {
        log(TAG, content != null ? String.valueOf(content.getTimestamp()) : "null", "Ignoring message.");
        return;
      }

      log(TAG, String.valueOf(content.getTimestamp()), "Beginning message processing.");

      if (content.getDataMessage().isPresent()) {
        SignalServiceDataMessage message        = content.getDataMessage().get();
        boolean                  isMediaMessage = message.getAttachments().isPresent() || message.getQuote().isPresent() || message.getSharedContacts().isPresent() || message.getPreviews().isPresent() || message.getSticker().isPresent() || message.getMentions().isPresent();
        Optional<GroupId>        groupId        = GroupUtil.idFromGroupContext(message.getGroupContext());
        boolean                  isGv2Message   = groupId.isPresent() && groupId.get().isV2();

        if (isGv2Message) {
          GroupId.V2 groupIdV2 = groupId.get().requireV2();

          Optional<GroupDatabase.GroupRecord> possibleGv1 = groupDatabase.getGroupV1ByExpectedV2(groupIdV2);
          if (possibleGv1.isPresent()) {
            GroupsV1MigrationUtil.performLocalMigration(context, possibleGv1.get().getId().requireV1());
          }

          if (!updateGv2GroupFromServerOrP2PChange(content, message.getGroupContext().get().getGroupV2().get())) {
            log(TAG, String.valueOf(content.getTimestamp()), "Ignoring GV2 message for group we are not currently in " + groupIdV2);
            return;
          }

          Recipient sender = Recipient.externalPush(context, content.getSender());
          if (!groupDatabase.isCurrentMember(groupIdV2, sender.getId())) {
            log(TAG, String.valueOf(content.getTimestamp()), "Ignoring GV2 message from member not in group " + groupIdV2);
            return;
          }
        }

        if      (isInvalidMessage(message))                                               handleInvalidMessage(content.getSender(), content.getSenderDevice(), groupId, content.getTimestamp(), smsMessageId);
        else if (message.isEndSession())                                                  handleEndSessionMessage(content, smsMessageId);
        else if (message.isGroupV1Update())                                               handleGroupV1Message(content, message, smsMessageId, groupId.get().requireV1());
        else if (message.isExpirationUpdate())                                            handleExpirationUpdate(content, message, smsMessageId, groupId);
        else if (message.getReaction().isPresent())                                       handleReaction(content, message);
        else if (message.getRemoteDelete().isPresent())                                   handleRemoteDelete(content, message);
        else if (isMediaMessage)                                                          handleMediaMessage(content, message, smsMessageId);
        else if (message.getBody().isPresent())                                           handleTextMessage(content, message, smsMessageId, groupId);
        else if (FeatureFlags.groupCalling() && message.getGroupCallUpdate().isPresent()) handleGroupCallUpdateMessage(content, message, groupId);

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
        else                                                          warn(TAG, String.valueOf(content.getTimestamp()), "Contains no known sync types...");
      } else if (content.getCallMessage().isPresent()) {
        log(TAG, String.valueOf(content.getTimestamp()), "Got call message...");

        SignalServiceCallMessage message             = content.getCallMessage().get();
        Optional<Integer>        destinationDeviceId = message.getDestinationDeviceId();

        if (destinationDeviceId.isPresent() && destinationDeviceId.get() != 1) {
          log(TAG, String.valueOf(content.getTimestamp()), String.format(Locale.US, "Ignoring call message that is not for this device! intended: %d, this: %d", destinationDeviceId.get(), 1));
          return;
        }

        if      (message.getOfferMessage().isPresent())      handleCallOfferMessage(content, message.getOfferMessage().get(), smsMessageId);
        else if (message.getAnswerMessage().isPresent())     handleCallAnswerMessage(content, message.getAnswerMessage().get());
        else if (message.getIceUpdateMessages().isPresent()) handleCallIceUpdateMessage(content, message.getIceUpdateMessages().get());
        else if (message.getHangupMessage().isPresent())     handleCallHangupMessage(content, message.getHangupMessage().get(), smsMessageId);
        else if (message.getBusyMessage().isPresent())       handleCallBusyMessage(content, message.getBusyMessage().get());
        else if (message.getOpaqueMessage().isPresent())     handleCallOpaqueMessage(content, message.getOpaqueMessage().get());
      } else if (content.getReceiptMessage().isPresent()) {
        SignalServiceReceiptMessage message = content.getReceiptMessage().get();

        if      (message.isReadReceipt())     handleReadReceipt(content, message);
        else if (message.isDeliveryReceipt()) handleDeliveryReceipt(content, message);
        else if (message.isViewedReceipt())   handleViewedReceipt(content, message);
      } else if (content.getTypingMessage().isPresent()) {
        handleTypingMessage(content, content.getTypingMessage().get());
      } else {
        warn(TAG, String.valueOf(content.getTimestamp()), "Got unrecognized message!");
      }

      resetRecipientToPush(Recipient.externalPush(context, content.getSender()));
    } catch (StorageFailedException e) {
      warn(TAG, String.valueOf(content.getTimestamp()), e);
      handleCorruptMessage(e.getSender(), e.getSenderDevice(), timestamp, smsMessageId);
    } catch (BadGroupIdException e) {
      warn(TAG, String.valueOf(content.getTimestamp()), "Ignoring message with bad group id", e);
    }
  }

  /**
   * Attempts to update the group to the revision mentioned in the message.
   * If the local version is at least the revision in the message it will not query the server.
   * If the message includes a signed change proto that is sufficient (i.e. local revision is only
   * 1 revision behind), it will also not query the server in this case.
   *
   * @return false iff needed to query the server and was not able to because self is not a current
   * member of the group.
   */
  private boolean updateGv2GroupFromServerOrP2PChange(@NonNull SignalServiceContent content,
                                                      @NonNull SignalServiceGroupV2 groupV2)
      throws IOException, GroupChangeBusyException
  {
    try {
      GroupManager.updateGroupFromServer(context, groupV2.getMasterKey(), groupV2.getRevision(), content.getTimestamp(), groupV2.getSignedGroupChange());
      return true;
    } catch (GroupNotAMemberException e) {
      warn(TAG, String.valueOf(content.getTimestamp()), "Ignoring message for a group we're not in");
      return false;
    }
  }

  private void handleExceptionMessage(@NonNull ExceptionMetadata e, @NonNull Optional<Long> smsMessageId) {
    Recipient sender = Recipient.external(context, e.sender);

    if (sender.isBlocked()) {
      warn(TAG, "Ignoring exception content from blocked sender, message state:" + messageState);
      return;
    }

    switch (messageState) {

      case INVALID_VERSION:
        warn(TAG, String.valueOf(timestamp), "Handling invalid version.");
        handleInvalidVersionMessage(e.sender, e.senderDevice, timestamp, smsMessageId);
        break;

      case LEGACY_MESSAGE:
        warn(TAG, String.valueOf(timestamp), "Handling legacy message.");
        handleLegacyMessage(e.sender, e.senderDevice, timestamp, smsMessageId);
        break;

      case DUPLICATE_MESSAGE:
        warn(TAG, String.valueOf(timestamp), "Duplicate message. Dropping.");
        break;

      case UNSUPPORTED_DATA_MESSAGE:
        warn(TAG, String.valueOf(timestamp), "Handling unsupported data message.");
        handleUnsupportedDataMessage(e.sender, e.senderDevice, Optional.fromNullable(e.groupId), timestamp, smsMessageId);
        break;

      case CORRUPT_MESSAGE:
      case NO_SESSION:
        warn(TAG, String.valueOf(timestamp), "Discovered old enqueued bad encrypted message. Scheduling reset.");
        ApplicationDependencies.getJobManager().add(new AutomaticSessionResetJob(Recipient.external(context, e.sender).getId(), e.senderDevice, timestamp));
        break;

      default:
        throw new AssertionError("Not handled " + messageState + ". (" + timestamp + ")");
    }
  }

  private void handleCallOfferMessage(@NonNull SignalServiceContent content,
                                      @NonNull OfferMessage message,
                                      @NonNull Optional<Long> smsMessageId)
  {
    log(TAG, String.valueOf(content.getTimestamp()), "handleCallOfferMessage...");

    if (smsMessageId.isPresent()) {
      MessageDatabase database = DatabaseFactory.getSmsDatabase(context);
      database.markAsMissedCall(smsMessageId.get(), message.getType() == OfferMessage.Type.VIDEO_CALL);
    } else {
      Intent     intent            = new Intent(context, WebRtcCallService.class);
      Recipient  recipient         = Recipient.externalHighTrustPush(context, content.getSender());
      RemotePeer remotePeer        = new RemotePeer(recipient.getId());
      byte[]     remoteIdentityKey = DatabaseFactory.getIdentityDatabase(context).getIdentity(recipient.getId()).transform(record -> record.getIdentityKey().serialize()).orNull();

      intent.setAction(WebRtcCallService.ACTION_RECEIVE_OFFER)
            .putExtra(WebRtcCallService.EXTRA_CALL_ID,                    message.getId())
            .putExtra(WebRtcCallService.EXTRA_REMOTE_PEER,                remotePeer)
            .putExtra(WebRtcCallService.EXTRA_REMOTE_IDENTITY_KEY,        remoteIdentityKey)
            .putExtra(WebRtcCallService.EXTRA_REMOTE_DEVICE,              content.getSenderDevice())
            .putExtra(WebRtcCallService.EXTRA_OFFER_OPAQUE,               message.getOpaque())
            .putExtra(WebRtcCallService.EXTRA_OFFER_SDP,                  message.getSdp())
            .putExtra(WebRtcCallService.EXTRA_SERVER_RECEIVED_TIMESTAMP,  content.getServerReceivedTimestamp())
            .putExtra(WebRtcCallService.EXTRA_SERVER_DELIVERED_TIMESTAMP, content.getServerDeliveredTimestamp())
            .putExtra(WebRtcCallService.EXTRA_OFFER_TYPE,                 message.getType().getCode())
            .putExtra(WebRtcCallService.EXTRA_MULTI_RING,                 content.getCallMessage().get().isMultiRing());

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
      else                                                context.startService(intent);
    }
  }

  private void handleCallAnswerMessage(@NonNull SignalServiceContent content,
                                       @NonNull AnswerMessage message)
  {
    log(TAG, String.valueOf(content), "handleCallAnswerMessage...");
    Intent     intent            = new Intent(context, WebRtcCallService.class);
    Recipient  recipient         = Recipient.externalHighTrustPush(context, content.getSender());
    RemotePeer remotePeer        = new RemotePeer(recipient.getId());
    byte[]     remoteIdentityKey = DatabaseFactory.getIdentityDatabase(context).getIdentity(recipient.getId()).transform(record -> record.getIdentityKey().serialize()).orNull();

    intent.setAction(WebRtcCallService.ACTION_RECEIVE_ANSWER)
          .putExtra(WebRtcCallService.EXTRA_CALL_ID,             message.getId())
          .putExtra(WebRtcCallService.EXTRA_REMOTE_PEER,         remotePeer)
          .putExtra(WebRtcCallService.EXTRA_REMOTE_IDENTITY_KEY, remoteIdentityKey)
          .putExtra(WebRtcCallService.EXTRA_REMOTE_DEVICE,       content.getSenderDevice())
          .putExtra(WebRtcCallService.EXTRA_ANSWER_OPAQUE,       message.getOpaque())
          .putExtra(WebRtcCallService.EXTRA_ANSWER_SDP,          message.getSdp())
          .putExtra(WebRtcCallService.EXTRA_MULTI_RING,          content.getCallMessage().get().isMultiRing());

    context.startService(intent);
  }

  private void handleCallIceUpdateMessage(@NonNull SignalServiceContent content,
                                          @NonNull List<IceUpdateMessage> messages)
  {
    log(TAG, String.valueOf(content), "handleCallIceUpdateMessage... " + messages.size());

    ArrayList<IceCandidateParcel> iceCandidates = new ArrayList<>(messages.size());
    long callId = -1;
    for (IceUpdateMessage iceMessage : messages) {
      iceCandidates.add(new IceCandidateParcel(iceMessage));
      callId = iceMessage.getId();
    }

    Intent     intent     = new Intent(context, WebRtcCallService.class);
    RemotePeer remotePeer = new RemotePeer(Recipient.externalHighTrustPush(context, content.getSender()).getId());

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
    log(TAG, String.valueOf(content), "handleCallHangupMessage");
    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).markAsMissedCall(smsMessageId.get(), false);
    } else {
      Intent     intent     = new Intent(context, WebRtcCallService.class);
      RemotePeer remotePeer = new RemotePeer(Recipient.externalHighTrustPush(context, content.getSender()).getId());

      intent.setAction(WebRtcCallService.ACTION_RECEIVE_HANGUP)
            .putExtra(WebRtcCallService.EXTRA_CALL_ID,          message.getId())
            .putExtra(WebRtcCallService.EXTRA_REMOTE_PEER,      remotePeer)
            .putExtra(WebRtcCallService.EXTRA_REMOTE_DEVICE,    content.getSenderDevice())
            .putExtra(WebRtcCallService.EXTRA_HANGUP_IS_LEGACY, message.isLegacy())
            .putExtra(WebRtcCallService.EXTRA_HANGUP_DEVICE_ID, message.getDeviceId())
            .putExtra(WebRtcCallService.EXTRA_HANGUP_TYPE,      message.getType().getCode());

      context.startService(intent);
    }
  }

  private void handleCallBusyMessage(@NonNull SignalServiceContent content,
                                     @NonNull BusyMessage message)
  {
    log(TAG, String.valueOf(content.getTimestamp()), "handleCallBusyMessage");

    Intent     intent     = new Intent(context, WebRtcCallService.class);
    RemotePeer remotePeer = new RemotePeer(Recipient.externalHighTrustPush(context, content.getSender()).getId());

    intent.setAction(WebRtcCallService.ACTION_RECEIVE_BUSY)
          .putExtra(WebRtcCallService.EXTRA_CALL_ID,       message.getId())
          .putExtra(WebRtcCallService.EXTRA_REMOTE_PEER,   remotePeer)
          .putExtra(WebRtcCallService.EXTRA_REMOTE_DEVICE, content.getSenderDevice());

    context.startService(intent);
  }

  private void handleCallOpaqueMessage(@NonNull SignalServiceContent content,
                                       @NonNull OpaqueMessage message)
  {
    log(TAG, String.valueOf(content.getTimestamp()), "handleCallOpaqueMessage");

    Intent intent = new Intent(context, WebRtcCallService.class);

    long messageAgeSeconds = 0;
    if (content.getServerReceivedTimestamp() > 0 && content.getServerDeliveredTimestamp() >= content.getServerReceivedTimestamp()) {
      messageAgeSeconds = (content.getServerDeliveredTimestamp() - content.getServerReceivedTimestamp()) / 1000;
    }

    intent.setAction(WebRtcCallService.ACTION_RECEIVE_OPAQUE_MESSAGE)
          .putExtra(WebRtcCallService.EXTRA_OPAQUE_MESSAGE, message.getOpaque())
          .putExtra(WebRtcCallService.EXTRA_UUID, Recipient.externalHighTrustPush(context, content.getSender()).requireUuid().toString())
          .putExtra(WebRtcCallService.EXTRA_REMOTE_DEVICE, content.getSenderDevice())
          .putExtra(WebRtcCallService.EXTRA_MESSAGE_AGE_SECONDS, messageAgeSeconds);

    context.startService(intent);
  }

  private void handleGroupCallUpdateMessage(@NonNull SignalServiceContent content,
                                            @NonNull SignalServiceDataMessage message,
                                            @NonNull Optional<GroupId> groupId)
  {
    if (!groupId.isPresent() || !groupId.get().isV2()) {
      Log.w(TAG, "Invalid group for group call update message");
      return;
    }

    RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromPossiblyMigratedGroupId(groupId.get());

    DatabaseFactory.getSmsDatabase(context).insertOrUpdateGroupCall(groupRecipientId,
                                                                    RecipientId.from(content.getSender()),
                                                                    content.getServerReceivedTimestamp(),
                                                                    message.getGroupCallUpdate().get().getEraId());

    GroupCallPeekJob.enqueue(groupRecipientId);
  }

  private void handleEndSessionMessage(@NonNull SignalServiceContent content,
                                       @NonNull Optional<Long>       smsMessageId)
  {
    MessageDatabase     smsDatabase         = DatabaseFactory.getSmsDatabase(context);
    IncomingTextMessage incomingTextMessage = new IncomingTextMessage(Recipient.externalHighTrustPush(context, content.getSender()).getId(),
                                                                      content.getSenderDevice(),
                                                                      content.getTimestamp(),
                                                                      content.getServerReceivedTimestamp(),
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
      ApplicationDependencies.getMessageNotifier().updateNotification(context, threadId);
    }
  }

  private long handleSynchronizeSentEndSessionMessage(@NonNull SentTranscriptMessage message)
      throws BadGroupIdException
  {
    MessageDatabase           database                  = DatabaseFactory.getSmsDatabase(context);
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
                                    @NonNull Optional<Long> smsMessageId,
                                    @NonNull GroupId.V1 groupId)
      throws StorageFailedException, BadGroupIdException
  {
    GroupV1MessageProcessor.process(context, content, message, false);

    if (message.getExpiresInSeconds() != 0 && message.getExpiresInSeconds() != getMessageDestination(content, message).getExpireMessages()) {
      handleExpirationUpdate(content, message, Optional.absent(), Optional.of(groupId));
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
        ApplicationDependencies.getJobManager().add(new RequestGroupInfoJob(Recipient.externalHighTrustPush(context, content.getSender()).getId(), GroupId.v1(groupV1.getGroupId())));
      } else {
        warn(TAG, String.valueOf(content.getTimestamp()), "Received a REQUEST_INFO message for a group we don't know about. Ignoring.");
      }
    } else {
      warn(TAG, String.valueOf(content.getTimestamp()), "Received a message for a group we don't know about without a GV1 context. Ignoring.");
    }
  }

  private void handleExpirationUpdate(@NonNull SignalServiceContent content,
                                      @NonNull SignalServiceDataMessage message,
                                      @NonNull Optional<Long> smsMessageId,
                                      @NonNull Optional<GroupId> groupId)
      throws StorageFailedException, BadGroupIdException
  {
    if (groupId.isPresent() && groupId.get().isV2()) {
      warn(TAG, String.valueOf(content.getTimestamp()), "Expiration update received for GV2. Ignoring.");
      return;
    }

    int                                 expiresInSeconds = message.getExpiresInSeconds();
    Optional<SignalServiceGroupContext> groupContext     = message.getGroupContext();
    Recipient                           recipient        = getMessageDestination(content, groupContext);

    if (recipient.getExpireMessages() == expiresInSeconds) {
      log(TAG, String.valueOf(content.getTimestamp()), "No change in message expiry for group. Ignoring.");
      return;
    }

    try {
      MessageDatabase      database     = DatabaseFactory.getMmsDatabase(context);
      Recipient            sender       = Recipient.externalHighTrustPush(context, content.getSender());
      IncomingMediaMessage mediaMessage = new IncomingMediaMessage(sender.getId(),
                                                                   content.getTimestamp(),
                                                                   content.getServerReceivedTimestamp(),
                                                                   -1,
                                                                   expiresInSeconds * 1000L,
                                                                   true,
                                                                   false,
                                                                   content.isNeedsReceipt(),
                                                                   Optional.absent(),
                                                                   groupContext,
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent());

      database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

      DatabaseFactory.getRecipientDatabase(context).setExpireMessages(recipient.getId(), expiresInSeconds);

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
      Recipient       reactionAuthor = Recipient.externalHighTrustPush(context, content.getSender());
      MessageDatabase db             = targetMessage.isMms() ? DatabaseFactory.getMmsDatabase(context) : DatabaseFactory.getSmsDatabase(context);

      if (reaction.isRemove()) {
        db.deleteReaction(targetMessage.getId(), reactionAuthor.getId());
        ApplicationDependencies.getMessageNotifier().updateNotification(context);
      } else {
        ReactionRecord reactionRecord = new ReactionRecord(reaction.getEmoji(), reactionAuthor.getId(), message.getTimestamp(), System.currentTimeMillis());
        db.addReaction(targetMessage.getId(), reactionRecord);
        ApplicationDependencies.getMessageNotifier().updateNotification(context, targetMessage.getThreadId(), false);
      }
    } else if (targetMessage != null) {
      warn(TAG, String.valueOf(content.getTimestamp()), "[handleReaction] Found a matching message, but it's flagged as remotely deleted. timestamp: " + reaction.getTargetSentTimestamp() + "  author: " + targetAuthor.getId());
    } else {
      warn(TAG, String.valueOf(content.getTimestamp()), "[handleReaction] Could not find matching message! timestamp: " + reaction.getTargetSentTimestamp() + "  author: " + targetAuthor.getId());
      ApplicationDependencies.getEarlyMessageCache().store(targetAuthor.getId(), reaction.getTargetSentTimestamp(), content);
    }
  }

  private void handleRemoteDelete(@NonNull SignalServiceContent content, @NonNull SignalServiceDataMessage message) {
    SignalServiceDataMessage.RemoteDelete delete = message.getRemoteDelete().get();

    Recipient     sender        = Recipient.externalHighTrustPush(context, content.getSender());
    MessageRecord targetMessage = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(delete.getTargetSentTimestamp(), sender.getId());

    if (targetMessage != null && RemoteDeleteUtil.isValidReceive(targetMessage, sender, content.getServerReceivedTimestamp())) {
      MessageDatabase db = targetMessage.isMms() ? DatabaseFactory.getMmsDatabase(context) : DatabaseFactory.getSmsDatabase(context);
      db.markAsRemoteDelete(targetMessage.getId());
      ApplicationDependencies.getMessageNotifier().updateNotification(context, targetMessage.getThreadId(), false);
    } else if (targetMessage == null) {
      warn(TAG, String.valueOf(content.getTimestamp()), "[handleRemoteDelete] Could not find matching message! timestamp: " + delete.getTargetSentTimestamp() + "  author: " + sender.getId());
      ApplicationDependencies.getEarlyMessageCache().store(sender.getId(), delete.getTargetSentTimestamp(), content);
    } else {
      warn(TAG, String.valueOf(content.getTimestamp()), String.format(Locale.ENGLISH, "[handleRemoteDelete] Invalid remote delete! deleteTime: %d, targetTime: %d, deleteAuthor: %s, targetAuthor: %s",
                                                                      content.getServerReceivedTimestamp(), targetMessage.getServerTimestamp(), sender.getId(), targetMessage.getRecipient().getId()));
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
        warn(TAG, "Received incomplete sticker pack operation sync.");
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
      SignalStore.settings().setLinkPreviewsEnabled(configurationMessage.getReadReceipts().get());
    }
  }

  private void handleSynchronizeBlockedListMessage(@NonNull BlockedListMessage blockMessage) {
    DatabaseFactory.getRecipientDatabase(context).applyBlockedUpdate(blockMessage.getAddresses(), blockMessage.getGroupIds());
  }

  private void handleSynchronizeFetchMessage(@NonNull SignalServiceSyncMessage.FetchType fetchType) {
    log(TAG, "Received fetch request with type: " + fetchType);

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
      recipient = Recipient.externalPossiblyMigratedGroup(context, groupId);
    } else {
      warn(TAG, "Message request response was missing a thread recipient! Skipping.");
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
        warn(TAG, "Got an unknown response type! Skipping");
        break;
    }
  }

  private void handleSynchronizeSentMessage(@NonNull SignalServiceContent content,
                                            @NonNull SentTranscriptMessage message)
      throws StorageFailedException, BadGroupIdException, IOException, GroupChangeBusyException
  {
    log(TAG, String.valueOf(content.getTimestamp()), "Processing sent transcript for message with ID " + message.getTimestamp());

    try {
      GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);

      if (message.getMessage().isGroupV2Message()) {
        Optional<GroupDatabase.GroupRecord> possibleGv1 = groupDatabase.getGroupV1ByExpectedV2(GroupId.v2(message.getMessage().getGroupContext().get().getGroupV2().get().getMasterKey()));
        if (possibleGv1.isPresent()) {
          GroupsV1MigrationUtil.performLocalMigration(context, possibleGv1.get().getId().requireV1());
        }
      }

      long threadId = -1;

      if (message.isRecipientUpdate()) {
        handleGroupRecipientUpdate(message);
      } else if (message.getMessage().isEndSession()) {
        threadId = handleSynchronizeSentEndSessionMessage(message);
      } else if (message.getMessage().isGroupV1Update()) {
        Long gv1ThreadId = GroupV1MessageProcessor.process(context, content, message.getMessage(), true);
        threadId = gv1ThreadId == null ? -1 : gv1ThreadId;
      } else if (message.getMessage().isGroupV2Update()) {
        handleSynchronizeSentGv2Update(content, message);
        threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(getSyncMessageDestination(message));
      } else if (FeatureFlags.groupCalling() && message.getMessage().getGroupCallUpdate().isPresent()) {
        handleGroupCallUpdateMessage(content, message.getMessage(), GroupUtil.idFromGroupContext(message.getMessage().getGroupContext()));
      } else if (message.getMessage().isEmptyGroupV2Message()) {
        // Do nothing
      } else if (message.getMessage().isExpirationUpdate()) {
        threadId = handleSynchronizeSentExpirationUpdate(message);
      } else if (message.getMessage().getReaction().isPresent()) {
        handleReaction(content, message.getMessage());
        threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(getSyncMessageDestination(message));
      } else if (message.getMessage().getRemoteDelete().isPresent()) {
        handleRemoteDelete(content, message.getMessage());
      } else if (message.getMessage().getAttachments().isPresent() || message.getMessage().getQuote().isPresent() || message.getMessage().getPreviews().isPresent() || message.getMessage().getSticker().isPresent() || message.getMessage().isViewOnce() || message.getMessage().getMentions().isPresent()) {
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

      if (threadId != -1) {
        DatabaseFactory.getThreadDatabase(context).setRead(threadId, true);
        ApplicationDependencies.getMessageNotifier().updateNotification(context);
      }

      ApplicationDependencies.getMessageNotifier().setLastDesktopActivityTimestamp(message.getTimestamp());
    } catch (MmsException e) {
      throw new StorageFailedException(e, content.getSender().getIdentifier(), content.getSenderDevice());
    }
  }

  private void handleSynchronizeSentGv2Update(@NonNull SignalServiceContent content,
                                              @NonNull SentTranscriptMessage message)
      throws IOException, GroupChangeBusyException
  {
    SignalServiceGroupV2 signalServiceGroupV2 = message.getMessage().getGroupContext().get().getGroupV2().get();
    GroupId.V2           groupIdV2            = GroupId.v2(signalServiceGroupV2.getMasterKey());

    if (!updateGv2GroupFromServerOrP2PChange(content, signalServiceGroupV2)) {
      log(TAG, String.valueOf(content.getTimestamp()), "Ignoring GV2 message for group we are not currently in " + groupIdV2);
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
                                                                                        SignalStore.settings().isLinkPreviewsEnabled()));
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
                          .scheduleDeletion(expiringMessage.first(), false, envelopeTimestamp, expiringMessage.second());
      }

      for (Pair<Long, Long> expiringMessage : expiringMedia) {
        ApplicationContext.getInstance(context)
                          .getExpiringMessageManager()
                          .scheduleDeletion(expiringMessage.first(), true, envelopeTimestamp, expiringMessage.second());
      }
    }

    MessageNotifier messageNotifier = ApplicationDependencies.getMessageNotifier();
    messageNotifier.setLastDesktopActivityTimestamp(envelopeTimestamp);
    messageNotifier.cancelDelayedNotifications();
    messageNotifier.updateNotification(context);
  }

  private void handleSynchronizeViewOnceOpenMessage(@NonNull ViewOnceOpenMessage openMessage, long envelopeTimestamp) {
    log(TAG, String.valueOf(envelopeTimestamp), "Handling a view-once open for message: " + openMessage.getTimestamp());

    RecipientId   author    = Recipient.externalPush(context, openMessage.getSender()).getId();
    long          timestamp = openMessage.getTimestamp();
    MessageRecord record    = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(timestamp, author);

    if (record != null && record.isMms()) {
      DatabaseFactory.getAttachmentDatabase(context).deleteAttachmentFilesForViewOnceMessage(record.getId());
    } else {
      warn(TAG, String.valueOf(envelopeTimestamp), "Got a view-once open message for a message we don't have!");
    }

    MessageNotifier messageNotifier = ApplicationDependencies.getMessageNotifier();
    messageNotifier.setLastDesktopActivityTimestamp(envelopeTimestamp);
    messageNotifier.cancelDelayedNotifications();
    messageNotifier.updateNotification(context);
  }

  private void handleMediaMessage(@NonNull SignalServiceContent content,
                                  @NonNull SignalServiceDataMessage message,
                                  @NonNull Optional<Long> smsMessageId)
      throws StorageFailedException, BadGroupIdException
  {
    notifyTypingStoppedFromIncomingMessage(getMessageDestination(content, message), content.getSender(), content.getSenderDevice());

    Optional<InsertResult> insertResult;

    MessageDatabase database = DatabaseFactory.getMmsDatabase(context);
    database.beginTransaction();

    try {
      Optional<QuoteModel>        quote          = getValidatedQuote(message.getQuote());
      Optional<List<Contact>>     sharedContacts = getContacts(message.getSharedContacts());
      Optional<List<LinkPreview>> linkPreviews   = getLinkPreviews(message.getPreviews(), message.getBody().or(""));
      Optional<List<Mention>>     mentions       = getMentions(message.getMentions());
      Optional<Attachment>        sticker        = getStickerAttachment(message.getSticker());
      IncomingMediaMessage        mediaMessage   = new IncomingMediaMessage(RecipientId.fromHighTrust(content.getSender()),
                                                                            message.getTimestamp(),
                                                                            content.getServerReceivedTimestamp(),
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
                                                                            mentions,
                                                                            sticker);

      insertResult = database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

      if (insertResult.isPresent()) {
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
      List<DatabaseAttachment> allAttachments     = DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(insertResult.get().getMessageId());
      List<DatabaseAttachment> stickerAttachments = Stream.of(allAttachments).filter(Attachment::isSticker).toList();
      List<DatabaseAttachment> attachments        = Stream.of(allAttachments).filterNot(Attachment::isSticker).toList();

      forceStickerDownloadIfNecessary(stickerAttachments);

      for (DatabaseAttachment attachment : attachments) {
        ApplicationDependencies.getJobManager().add(new AttachmentDownloadJob(insertResult.get().getMessageId(), attachment.getAttachmentId(), false));
      }

      ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
      ApplicationDependencies.getJobManager().add(new TrimThreadJob(insertResult.get().getThreadId()));

      if (message.isViewOnce()) {
        ApplicationContext.getInstance(context).getViewOnceMessageManager().scheduleIfNecessary();
      }
    }
  }

  private long handleSynchronizeSentExpirationUpdate(@NonNull SentTranscriptMessage message)
      throws MmsException, BadGroupIdException
  {
    MessageDatabase database   = DatabaseFactory.getMmsDatabase(context);
    Recipient       recipient  = getSyncMessageDestination(message);

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
    MessageDatabase             database        = DatabaseFactory.getMmsDatabase(context);
    Recipient                   recipients      = getSyncMessageDestination(message);
    Optional<QuoteModel>        quote           = getValidatedQuote(message.getMessage().getQuote());
    Optional<Attachment>        sticker         = getStickerAttachment(message.getMessage().getSticker());
    Optional<List<Contact>>     sharedContacts  = getContacts(message.getMessage().getSharedContacts());
    Optional<List<LinkPreview>> previews        = getLinkPreviews(message.getMessage().getPreviews(), message.getMessage().getBody().or(""));
    Optional<List<Mention>>     mentions        = getMentions(message.getMessage().getMentions());
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
                                                                 mentions.or(Collections.emptyList()),
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

      if (recipients.isSelf()) {
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
      warn(TAG, "Got recipient update for a non-group message! Skipping.");
      return;
    }

    MmsSmsDatabase database = DatabaseFactory.getMmsSmsDatabase(context);
    MessageRecord  record   = database.getMessageFor(message.getTimestamp(), Recipient.self().getId());

    if (record == null) {
      warn(TAG, "Got recipient update for non-existing message! Skipping.");
      return;
    }

    if (!record.isMms()) {
      warn(TAG, "Recipient update matched a non-MMS message! Skipping.");
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

    List<org.whispersystems.libsignal.util.Pair<RecipientId, Boolean>> unidentifiedStatus = Stream.of(members)
                                                                                                  .map(m -> new org.whispersystems.libsignal.util.Pair<>(m.getId(), message.isUnidentified(m.requireServiceId())))
                                                                                                  .toList();
    receiptDatabase.setUnidentified(unidentifiedStatus, messageId);
  }

  private void handleTextMessage(@NonNull SignalServiceContent content,
                                 @NonNull SignalServiceDataMessage message,
                                 @NonNull Optional<Long> smsMessageId,
                                 @NonNull Optional<GroupId> groupId)
      throws StorageFailedException, BadGroupIdException
  {
    MessageDatabase database  = DatabaseFactory.getSmsDatabase(context);
    String          body      = message.getBody().isPresent() ? message.getBody().get() : "";
    Recipient       recipient = getMessageDestination(content, message);

    if (message.getExpiresInSeconds() != recipient.getExpireMessages()) {
      handleExpirationUpdate(content, message, Optional.absent(), groupId);
    }

    Long threadId;

    if (smsMessageId.isPresent() && !message.getGroupContext().isPresent()) {
      threadId = database.updateBundleMessageBody(smsMessageId.get(), body).second();
    } else {
      notifyTypingStoppedFromIncomingMessage(recipient, content.getSender(), content.getSenderDevice());

      IncomingTextMessage textMessage = new IncomingTextMessage(RecipientId.fromHighTrust(content.getSender()),
                                                                content.getSenderDevice(),
                                                                message.getTimestamp(),
                                                                content.getServerReceivedTimestamp(),
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
      ApplicationDependencies.getMessageNotifier().updateNotification(context, threadId);
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

    MessageDatabase database;
    long            messageId;

    if (isGroup) {
      OutgoingMediaMessage outgoingMediaMessage = new OutgoingMediaMessage(recipient,
                                                                           new SlideDeck(),
                                                                           body,
                                                                           message.getTimestamp(),
                                                                           -1,
                                                                           expiresInMillis,
                                                                           false,
                                                                           ThreadDatabase.DistributionTypes.DEFAULT,
                                                                           null,
                                                                           Collections.emptyList(),
                                                                           Collections.emptyList(),
                                                                           Collections.emptyList());
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

    if (recipient.isSelf()) {
      SyncMessageId id = new SyncMessageId(recipient.getId(), message.getTimestamp());
      DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(id, System.currentTimeMillis());
      DatabaseFactory.getMmsSmsDatabase(context).incrementReadReceiptCount(id, System.currentTimeMillis());
    }

    return threadId;
  }

  private void handleInvalidVersionMessage(@NonNull String sender, int senderDevice, long timestamp,
                                           @NonNull Optional<Long> smsMessageId)
  {
    MessageDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp);

      if (insertResult.isPresent()) {
        smsDatabase.markAsInvalidVersionKeyExchange(insertResult.get().getMessageId());
        ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsInvalidVersionKeyExchange(smsMessageId.get());
    }
  }

  private void handleCorruptMessage(@NonNull String sender, int senderDevice, long timestamp,
                                    @NonNull Optional<Long> smsMessageId)
  {
    MessageDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp);

      if (insertResult.isPresent()) {
        smsDatabase.markAsDecryptFailed(insertResult.get().getMessageId());
        ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsDecryptFailed(smsMessageId.get());
    }
  }

  private void handleUnsupportedDataMessage(@NonNull String sender,
                                            int senderDevice,
                                            @NonNull Optional<GroupId> groupId,
                                            long timestamp,
                                            @NonNull Optional<Long> smsMessageId)
  {
    MessageDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp, groupId);

      if (insertResult.isPresent()) {
        smsDatabase.markAsUnsupportedProtocolVersion(insertResult.get().getMessageId());
        ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
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
    MessageDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender.getIdentifier(), senderDevice, timestamp, groupId);

      if (insertResult.isPresent()) {
        smsDatabase.markAsInvalidMessage(insertResult.get().getMessageId());
        ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsNoSession(smsMessageId.get());
    }
  }

  private void handleLegacyMessage(@NonNull String sender, int senderDevice, long timestamp,
                                   @NonNull Optional<Long> smsMessageId)
  {
    MessageDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(sender, senderDevice, timestamp);

      if (insertResult.isPresent()) {
        smsDatabase.markAsLegacyVersion(insertResult.get().getMessageId());
        ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsLegacyVersion(smsMessageId.get());
    }
  }

  private void handleProfileKey(@NonNull SignalServiceContent content,
                                @NonNull byte[] messageProfileKeyBytes)
  {
    RecipientDatabase database          = DatabaseFactory.getRecipientDatabase(context);
    Recipient         recipient         = Recipient.externalHighTrustPush(context, content.getSender());
    ProfileKey        messageProfileKey = ProfileKeyUtil.profileKeyOrNull(messageProfileKeyBytes);

    if (messageProfileKey != null) {
      if (database.setProfileKey(recipient.getId(), messageProfileKey)) {
        ApplicationDependencies.getJobManager().add(RetrieveProfileJob.forRecipient(recipient.getId()));
      }
    } else {
      warn(TAG, String.valueOf(content.getTimestamp()), "Ignored invalid profile key seen in message");
    }
  }

  private void handleNeedsDeliveryReceipt(@NonNull SignalServiceContent content,
                                          @NonNull SignalServiceDataMessage message)
  {
    ApplicationDependencies.getJobManager().add(new SendDeliveryReceiptJob(RecipientId.fromHighTrust(content.getSender()), message.getTimestamp()));
  }

  private void handleViewedReceipt(@NonNull SignalServiceContent content,
                                   @NonNull SignalServiceReceiptMessage message)
  {
    if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
      log(TAG, "Ignoring viewed receipts for IDs: " + Util.join(message.getTimestamps(), ", "));
      return;
    }

    log(TAG, "Processing viewed reciepts for IDs: " + Util.join(message.getTimestamps(), ","));

    Recipient                 sender    = Recipient.externalHighTrustPush(context, content.getSender());
    List<SyncMessageId>       ids       = Stream.of(message.getTimestamps())
                                                .map(t -> new SyncMessageId(sender.getId(), t))
                                                .toList();
    Collection<SyncMessageId> unhandled = DatabaseFactory.getMmsSmsDatabase(context)
                                                         .incrementViewedReceiptCounts(ids, content.getTimestamp());

    for (SyncMessageId id : unhandled) {
      warn(TAG, String.valueOf(content.getTimestamp()), "[handleViewedReceipt] Could not find matching message! timestamp: " + id.getTimetamp() + "  author: " + sender.getId());
      ApplicationDependencies.getEarlyMessageCache().store(sender.getId(), id.getTimetamp(), content);
    }
  }

  @SuppressLint("DefaultLocale")
  private void handleDeliveryReceipt(@NonNull SignalServiceContent content,
                                     @NonNull SignalServiceReceiptMessage message)
  {
    log(TAG, "Processing delivery receipts for IDs: " + Util.join(message.getTimestamps(), ", "));

    Recipient           sender  = Recipient.externalHighTrustPush(context, content.getSender());
    List<SyncMessageId> ids     = Stream.of(message.getTimestamps())
                                        .map(t -> new SyncMessageId(sender.getId(), t))
                                        .toList();

    DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCounts(ids, System.currentTimeMillis());
  }

  @SuppressLint("DefaultLocale")
  private void handleReadReceipt(@NonNull SignalServiceContent content,
                                 @NonNull SignalServiceReceiptMessage message)
  {
    if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
      log(TAG, "Ignoring read receipts for IDs: " + Util.join(message.getTimestamps(), ", "));
      return;
    }

    log(TAG, "Processing read receipts for IDs: " + Util.join(message.getTimestamps(), ", "));

    Recipient           sender  = Recipient.externalHighTrustPush(context, content.getSender());
    List<SyncMessageId> ids     = Stream.of(message.getTimestamps())
                                        .map(t -> new SyncMessageId(sender.getId(), t))
                                        .toList();

    Collection<SyncMessageId> unhandled = DatabaseFactory.getMmsSmsDatabase(context).incrementReadReceiptCounts(ids, content.getTimestamp());

    for (SyncMessageId id : unhandled) {
      warn(TAG, String.valueOf(content.getTimestamp()), "[handleReadReceipt] Could not find matching message! timestamp: " + id.getTimetamp() + "  author: " + sender.getId());
      ApplicationDependencies.getEarlyMessageCache().store(sender.getId(), id.getTimetamp(), content);
    }
  }

  private void handleTypingMessage(@NonNull SignalServiceContent content,
                                   @NonNull SignalServiceTypingMessage typingMessage)
      throws BadGroupIdException
  {
    if (!TextSecurePreferences.isTypingIndicatorsEnabled(context)) {
      return;
    }

    Recipient author = Recipient.externalHighTrustPush(context, content.getSender());

    long threadId;

    if (typingMessage.getGroupId().isPresent()) {
      GroupId.Push groupId = GroupId.push(typingMessage.getGroupId().get());

      if (!DatabaseFactory.getGroupDatabase(context).isCurrentMember(groupId, author.getId())) {
        warn(TAG, String.valueOf(content.getTimestamp()), "Seen typing indicator for non-member");
        return;
      }

      Recipient groupRecipient = Recipient.externalPossiblyMigratedGroup(context, groupId);

      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
    } else {
      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(author);
    }

    if (threadId <= 0) {
      warn(TAG, String.valueOf(content.getTimestamp()), "Couldn't find a matching thread for a typing message.");
      return;
    }

    if (typingMessage.isTypingStarted()) {
      Log.d(TAG, "Typing started on thread " + threadId);
      ApplicationDependencies.getTypingStatusRepository().onTypingStarted(context,threadId, author, content.getSenderDevice());
    } else {
      Log.d(TAG, "Typing stopped on thread " + threadId);
      ApplicationDependencies.getTypingStatusRepository().onTypingStopped(context, threadId, author, content.getSenderDevice(), false);
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
      warn(TAG, "Received quote without an ID! Ignoring...");
      return Optional.absent();
    }

    if (quote.get().getAuthor() == null) {
      warn(TAG, "Received quote without an author! Ignoring...");
      return Optional.absent();
    }

    RecipientId   author  = Recipient.externalPush(context, quote.get().getAuthor()).getId();
    MessageRecord message = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(quote.get().getId(), author);

    if (message != null && !message.isRemoteDelete()) {
      log(TAG, "Found matching message record...");

      List<Attachment> attachments = new LinkedList<>();
      List<Mention>    mentions    = new LinkedList<>();

      if (message.isMms()) {
        MmsMessageRecord mmsMessage = (MmsMessageRecord) message;

        mentions.addAll(DatabaseFactory.getMentionDatabase(context).getMentionsForMessage(mmsMessage.getId()));

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

      return Optional.of(new QuoteModel(quote.get().getId(), author, message.getBody(), false, attachments, mentions));
    } else if (message != null) {
      warn(TAG, "Found the target for the quote, but it's flagged as remotely deleted.");
    }

    warn(TAG, "Didn't find matching message record...");

    return Optional.of(new QuoteModel(quote.get().getId(),
                                      author,
                                      quote.get().getText(),
                                      true,
                                      PointerAttachment.forPointers(quote.get().getAttachments()),
                                      getMentions(quote.get().getMentions())));
  }

  private Optional<Attachment> getStickerAttachment(Optional<SignalServiceDataMessage.Sticker> sticker) {
    if (!sticker.isPresent()) {
      return Optional.absent();
    }

    if (sticker.get().getPackId() == null || sticker.get().getPackKey() == null || sticker.get().getAttachment() == null) {
      warn(TAG, "Malformed sticker!");
      return Optional.absent();
    }

    String          packId          = Hex.toStringCondensed(sticker.get().getPackId());
    String          packKey         = Hex.toStringCondensed(sticker.get().getPackKey());
    int             stickerId       = sticker.get().getStickerId();
    String          emoji           = sticker.get().getEmoji();
    StickerLocator  stickerLocator  = new StickerLocator(packId, packKey, stickerId, emoji);
    StickerDatabase stickerDatabase = DatabaseFactory.getStickerDatabase(context);
    StickerRecord   stickerRecord   = stickerDatabase.getSticker(stickerLocator.getPackId(), stickerLocator.getStickerId(), false);

    if (stickerRecord != null) {
      return Optional.of(new UriAttachment(stickerRecord.getUri(),
                                           stickerRecord.getContentType(),
                                           AttachmentDatabase.TRANSFER_PROGRESS_DONE,
                                           stickerRecord.getSize(),
                                           StickerSlide.WIDTH,
                                           StickerSlide.HEIGHT,
                                           null,
                                           String.valueOf(new SecureRandom().nextLong()),
                                           false,
                                           false,
                                           false,
                                           null,
                                           stickerLocator,
                                           null,
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

  private Optional<List<LinkPreview>> getLinkPreviews(Optional<List<Preview>> previews, @NonNull String message) {
    if (!previews.isPresent() || previews.get().isEmpty()) return Optional.absent();

    List<LinkPreview>     linkPreviews  = new ArrayList<>(previews.get().size());
    LinkPreviewUtil.Links urlsInMessage = LinkPreviewUtil.findValidPreviewUrls(message);

    for (Preview preview : previews.get()) {
      Optional<Attachment> thumbnail     = PointerAttachment.forPointer(preview.getImage());
      Optional<String>     url           = Optional.fromNullable(preview.getUrl());
      Optional<String>     title         = Optional.fromNullable(preview.getTitle());
      Optional<String>     description   = Optional.fromNullable(preview.getDescription());
      boolean              hasTitle      = !TextUtils.isEmpty(title.or(""));
      boolean              presentInBody = url.isPresent() && urlsInMessage.containsUrl(url.get());
      boolean              validDomain   = url.isPresent() && LinkPreviewUtil.isValidPreviewUrl(url.get());

      if (hasTitle && presentInBody && validDomain) {
        LinkPreview linkPreview = new LinkPreview(url.get(), title.or(""), description.or(""), preview.getDate(), thumbnail);
        linkPreviews.add(linkPreview);
      } else {
        warn(TAG, String.format("Discarding an invalid link preview. hasTitle: %b presentInBody: %b validDomain: %b", hasTitle, presentInBody, validDomain));
      }
    }

    return Optional.of(linkPreviews);
  }

  private Optional<List<Mention>> getMentions(Optional<List<SignalServiceDataMessage.Mention>> signalServiceMentions) {
    if (!signalServiceMentions.isPresent()) return Optional.absent();

    return Optional.of(getMentions(signalServiceMentions.get()));
  }

  private @NonNull List<Mention> getMentions(@Nullable List<SignalServiceDataMessage.Mention> signalServiceMentions) {
    if (signalServiceMentions == null || signalServiceMentions.isEmpty()) {
      return Collections.emptyList();
    }

    List<Mention> mentions = new ArrayList<>(signalServiceMentions.size());

    for (SignalServiceDataMessage.Mention mention : signalServiceMentions) {
      mentions.add(new Mention(Recipient.externalPush(context, mention.getUuid(), null, false).getId(), mention.getStart(), mention.getLength()));
    }

    return mentions;
  }

  private Optional<InsertResult> insertPlaceholder(@NonNull String sender, int senderDevice, long timestamp) {
    return insertPlaceholder(sender, senderDevice, timestamp, Optional.absent());
  }

  private Optional<InsertResult> insertPlaceholder(@NonNull String sender, int senderDevice, long timestamp, Optional<GroupId> groupId) {
    MessageDatabase     database    = DatabaseFactory.getSmsDatabase(context);
    IncomingTextMessage textMessage = new IncomingTextMessage(Recipient.external(context, sender).getId(),
                                                              senderDevice, timestamp, -1, "",
                                                              groupId, 0, false);

    textMessage = new IncomingEncryptedMessage(textMessage, "");
    return database.insertMessageInbox(textMessage);
  }

  private Recipient getSyncMessageDestination(@NonNull SentTranscriptMessage message)
      throws BadGroupIdException
  {
    return getGroupRecipient(message.getMessage().getGroupContext()).or(() -> Recipient.externalPush(context, message.getDestination().get()));
  }

  private Recipient getMessageDestination(@NonNull SignalServiceContent content,
                                          @NonNull SignalServiceDataMessage message)
      throws BadGroupIdException
  {
    return getGroupRecipient(message.getGroupContext()).or(() -> Recipient.externalHighTrustPush(context, content.getSender()));
  }

  private Recipient getMessageDestination(@NonNull SignalServiceContent content,
                                          @NonNull Optional<SignalServiceGroupContext> groupContext)
      throws BadGroupIdException
  {
    return getGroupRecipient(groupContext).or(() -> Recipient.externalPush(context, content.getSender()));
  }

  private Optional<Recipient> getGroupRecipient(Optional<SignalServiceGroupContext> message)
      throws BadGroupIdException
  {
    if (message.isPresent()) {
      return Optional.of(Recipient.externalPossiblyMigratedGroup(context, GroupUtil.idFromGroupContext(message.get())));
    }
    return Optional.absent();
  }

  private void notifyTypingStoppedFromIncomingMessage(@NonNull Recipient conversationRecipient, @NonNull SignalServiceAddress sender, int device) {
    Recipient author   = Recipient.externalPush(context, sender);
    long      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(conversationRecipient);

    if (threadId > 0) {
      Log.d(TAG, "Typing stopped on thread " + threadId + " due to an incoming message.");
      ApplicationDependencies.getTypingStatusRepository().onTypingStopped(context, threadId, author, device, true);
    }
  }

  private boolean shouldIgnore(@Nullable SignalServiceContent content)
      throws BadGroupIdException
  {
    if (content == null) {
      warn(TAG, "Got a message with null content.");
      return true;
    }

    Recipient sender = Recipient.externalHighTrustPush(context, content.getSender());

    if (content.getDataMessage().isPresent()) {
      SignalServiceDataMessage message      = content.getDataMessage().get();
      Recipient                conversation = getMessageDestination(content, message);

      if (conversation.isGroup() && conversation.isBlocked()) {
        return true;
      } else if (conversation.isGroup()) {
        GroupDatabase     groupDatabase = DatabaseFactory.getGroupDatabase(context);
        Optional<GroupId> groupId       = GroupUtil.idFromGroupContext(message.getGroupContext());

        if (groupId.isPresent()       &&
            groupId.get().isV1()      &&
            message.isGroupV1Update() &&
            groupDatabase.groupExists(groupId.get().requireV1().deriveV2MigrationGroupId()))
        {
          warn(TAG, String.valueOf(content.getTimestamp()), "Ignoring V1 update for a group we've already migrated to V2.");
          return true;
        }

        if (groupId.isPresent() && groupDatabase.isUnknownGroup(groupId.get())) {
          return sender.isBlocked();
        }

        boolean isTextMessage    = message.getBody().isPresent();
        boolean isMediaMessage   = message.getAttachments().isPresent() || message.getQuote().isPresent() || message.getSharedContacts().isPresent();
        boolean isExpireMessage  = message.isExpirationUpdate();
        boolean isGv2Update      = message.isGroupV2Update();
        boolean isContentMessage = !message.isGroupV1Update() && !isGv2Update && !isExpireMessage && (isTextMessage || isMediaMessage);
        boolean isGroupActive    = groupId.isPresent() && groupDatabase.isActive(groupId.get());
        boolean isLeaveMessage   = message.getGroupContext().isPresent() && message.getGroupContext().get().getGroupV1Type() == SignalServiceGroup.Type.QUIT;

        return (isContentMessage && !isGroupActive) || (sender.isBlocked() && !isLeaveMessage && !isGv2Update);
      } else {
        return sender.isBlocked();
      }
    } else if (content.getCallMessage().isPresent()) {
      return sender.isBlocked();
    } else if (content.getTypingMessage().isPresent()) {
      if (sender.isBlocked()) {
        return true;
      }

      if (content.getTypingMessage().get().getGroupId().isPresent()) {
        GroupId   groupId        = GroupId.push(content.getTypingMessage().get().getGroupId().get());
        Recipient groupRecipient = Recipient.externalPossiblyMigratedGroup(context, groupId);
        return groupRecipient.isBlocked() || !groupRecipient.isActiveGroup();
      }
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
        warn(TAG, "Failed to download sticker inline. Scheduling.");
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
                                           SignalServiceContent.deserialize(Base64.decode(data.getString(KEY_MESSAGE_PLAINTEXT))),
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
    CORRUPT_MESSAGE, // Not used, but can't remove due to serialization
    NO_SESSION,      // Not used, but can't remove due to serialization
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
