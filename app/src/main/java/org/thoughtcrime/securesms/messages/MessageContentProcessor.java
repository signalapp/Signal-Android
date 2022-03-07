package org.thoughtcrime.securesms.messages;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.mobilecoin.lib.exceptions.SerializationException;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallId;
import org.signal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.attachments.PointerAttachment;
import org.thoughtcrime.securesms.attachments.TombstoneAttachment;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.components.emoji.EmojiUtil;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactModelMapper;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase.GroupReceiptInfo;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.MessageDatabase.InsertResult;
import org.thoughtcrime.securesms.database.MessageDatabase.SyncMessageId;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.PaymentDatabase;
import org.thoughtcrime.securesms.database.PaymentMetaDataUtil;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.StickerDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageLogEntry;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.ParentStoryId;
import org.thoughtcrime.securesms.database.model.PendingRetryReceiptModel;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.BadGroupIdException;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupNotAMemberException;
import org.thoughtcrime.securesms.groups.GroupV1MessageProcessor;
import org.thoughtcrime.securesms.groups.GroupsV1MigrationUtil;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob;
import org.thoughtcrime.securesms.jobs.AutomaticSessionResetJob;
import org.thoughtcrime.securesms.jobs.GroupCallPeekJob;
import org.thoughtcrime.securesms.jobs.GroupV2UpdateSelfProfileKeyJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceBlockedUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceContactSyncJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceGroupUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceKeysUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDevicePniIdentityUpdateJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceStickerPackSyncJob;
import org.thoughtcrime.securesms.jobs.NullMessageSendJob;
import org.thoughtcrime.securesms.jobs.PaymentLedgerUpdateJob;
import org.thoughtcrime.securesms.jobs.PaymentTransactionCheckJob;
import org.thoughtcrime.securesms.jobs.ProfileKeySendJob;
import org.thoughtcrime.securesms.jobs.PushProcessMessageJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob;
import org.thoughtcrime.securesms.jobs.RequestGroupInfoJob;
import org.thoughtcrime.securesms.jobs.ResendMessageJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.jobs.SendDeliveryReceiptJob;
import org.thoughtcrime.securesms.jobs.SenderKeyDistributionSendJob;
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob;
import org.thoughtcrime.securesms.jobs.TrimThreadJob;
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
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.payments.MobileCoinPublicAddress;
import org.thoughtcrime.securesms.ratelimit.RateLimitUtil;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.WebRtcData;
import org.thoughtcrime.securesms.sms.IncomingEncryptedMessage;
import org.thoughtcrime.securesms.sms.IncomingEndSessionMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingEndSessionMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.stories.Stories;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.RemoteDeleteUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.protocol.DecryptionErrorMessage;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.messages.SignalServicePreview;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessage;
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
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.KeysMessage;
import org.whispersystems.signalservice.api.messages.multidevice.MessageRequestResponseMessage;
import org.whispersystems.signalservice.api.messages.multidevice.OutgoingPaymentMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewOnceOpenMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewedMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.payments.Money;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Takes data about a decrypted message, transforms it into user-presentable data, and writes that
 * data to our data stores.
 */
public final class MessageContentProcessor {

  private static final String TAG = Log.tag(MessageContentProcessor.class);

  private final Context context;

  public MessageContentProcessor(@NonNull Context context) {
    this.context = context;
  }

  /**
   * Given the details about a message decryption, this will insert the proper message content into
   * the database.
   *
   * This is super-stateful, and it's recommended that this be run in a transaction so that no
   * intermediate results are persisted to the database if the app were to crash.
   */
  public void process(MessageState messageState, @Nullable SignalServiceContent content, @Nullable ExceptionMetadata exceptionMetadata, long timestamp, long smsMessageId)
      throws IOException, GroupChangeBusyException
  {
    Optional<Long> optionalSmsMessageId = smsMessageId > 0 ? Optional.of(smsMessageId) : Optional.absent();

    if (messageState == MessageState.DECRYPTED_OK) {

      if (content != null) {
        Recipient senderRecipient = Recipient.externalHighTrustPush(context, content.getSender());

        handleMessage(content, timestamp, senderRecipient, optionalSmsMessageId);

        Optional<List<SignalServiceContent>> earlyContent = ApplicationDependencies.getEarlyMessageCache()
                                                                                   .retrieve(senderRecipient.getId(), content.getTimestamp());
        if (earlyContent.isPresent()) {
          log(String.valueOf(content.getTimestamp()), "Found " + earlyContent.get().size() + " dependent item(s) that were retrieved earlier. Processing.");

          for (SignalServiceContent earlyItem : earlyContent.get()) {
            handleMessage(earlyItem, timestamp, senderRecipient, Optional.absent());
          }
        }
      } else {
        warn("null", "Null content. Ignoring message.");
      }
    } else if (exceptionMetadata != null) {
      handleExceptionMessage(messageState, exceptionMetadata, timestamp, optionalSmsMessageId);
    } else if (messageState == MessageState.NOOP) {
      Log.d(TAG, "Nothing to do: " + messageState.name());
    } else {
      warn("Bad state! messageState: " + messageState);
    }
  }

  private void handleMessage(@NonNull SignalServiceContent content, long timestamp, @NonNull Recipient senderRecipient, @NonNull Optional<Long> smsMessageId)
      throws IOException, GroupChangeBusyException
  {
    try {
      Recipient threadRecipient = getMessageDestination(content);

      if (shouldIgnore(content, senderRecipient, threadRecipient)) {
        log(content.getTimestamp(), "Ignoring message.");
        return;
      }

      PendingRetryReceiptModel pending      = ApplicationDependencies.getPendingRetryReceiptCache().get(senderRecipient.getId(), content.getTimestamp());
      long                     receivedTime = handlePendingRetry(pending, content, threadRecipient);

      log(String.valueOf(content.getTimestamp()), "Beginning message processing. Sender: " + formatSender(senderRecipient, content));

      if (content.getDataMessage().isPresent()) {
        GroupDatabase            groupDatabase  = SignalDatabase.groups();
        SignalServiceDataMessage message        = content.getDataMessage().get();
        boolean                  isMediaMessage = message.getAttachments().isPresent() || message.getQuote().isPresent() || message.getSharedContacts().isPresent() || message.getPreviews().isPresent() || message.getSticker().isPresent() || message.getMentions().isPresent();
        Optional<GroupId>        groupId        = GroupUtil.idFromGroupContext(message.getGroupContext());
        boolean                  isGv2Message   = groupId.isPresent() && groupId.get().isV2();

        if (isGv2Message) {
          if (handleGv2PreProcessing(groupId.orNull().requireV2(), content, content.getDataMessage().get().getGroupContext().get().getGroupV2().get(), senderRecipient)) {
            return;
          }
        }

        MessageId messageId = null;

        if      (isInvalidMessage(message))                                               handleInvalidMessage(content.getSender(), content.getSenderDevice(), groupId, content.getTimestamp(), smsMessageId);
        else if (message.isEndSession())                                                  messageId = handleEndSessionMessage(content, smsMessageId, senderRecipient);
        else if (message.isGroupV1Update())                                               handleGroupV1Message(content, message, smsMessageId, groupId.get().requireV1(), senderRecipient, threadRecipient, receivedTime);
        else if (message.isExpirationUpdate())                                            messageId = handleExpirationUpdate(content, message, smsMessageId, groupId, senderRecipient, threadRecipient, receivedTime);
        else if (message.getReaction().isPresent())                                       messageId = handleReaction(content, message, senderRecipient);
        else if (message.getRemoteDelete().isPresent())                                   messageId = handleRemoteDelete(content, message, senderRecipient);
        else if (message.getPayment().isPresent())                                        handlePayment(content, message, senderRecipient);
        else if (message.getStoryContext().isPresent())                                   handleStoryReply(content, message, senderRecipient);
        else if (isMediaMessage)                                                          messageId = handleMediaMessage(content, message, smsMessageId, senderRecipient, threadRecipient, receivedTime);
        else if (message.getBody().isPresent())                                           messageId = handleTextMessage(content, message, smsMessageId, groupId, senderRecipient, threadRecipient, receivedTime);
        else if (Build.VERSION.SDK_INT > 19 && message.getGroupCallUpdate().isPresent())  handleGroupCallUpdateMessage(content, message, groupId, senderRecipient);

        if (groupId.isPresent() && groupDatabase.isUnknownGroup(groupId.get())) {
          handleUnknownGroupMessage(content, message.getGroupContext().get(), senderRecipient);
        }

        if (message.getProfileKey().isPresent()) {
          handleProfileKey(content, message.getProfileKey().get(), senderRecipient);
        }

        if (content.isNeedsReceipt() && messageId != null) {
          handleNeedsDeliveryReceipt(content, message, messageId);
        } else if (!content.isNeedsReceipt()) {
          if (RecipientUtil.shouldHaveProfileKey(context, threadRecipient)) {
            Log.w(TAG, "Received an unsealed sender message from " + senderRecipient.getId() + ", but they should already have our profile key. Correcting.");

            if (groupId.isPresent() && groupId.get().isV2()) {
              Log.i(TAG, "Message was to a GV2 group. Ensuring our group profile keys are up to date.");
              ApplicationDependencies.getJobManager().startChain(new RefreshAttributesJob(false))
                                                     .then(GroupV2UpdateSelfProfileKeyJob.withQueueLimits(groupId.get().requireV2()))
                                                     .enqueue();
            } else if (!threadRecipient.isGroup()) {
              Log.i(TAG, "Message was to a 1:1. Ensuring this user has our profile key.");
              ApplicationDependencies.getJobManager()
                                     .startChain(new RefreshAttributesJob(false))
                                     .then(ProfileKeySendJob.create(context, SignalDatabase.threads().getOrCreateThreadIdFor(threadRecipient), true))
                                     .enqueue();
            }
          }
        }
      } else if (content.getSyncMessage().isPresent()) {
        TextSecurePreferences.setMultiDevice(context, true);

        SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();

        if      (syncMessage.getSent().isPresent())                   handleSynchronizeSentMessage(content, syncMessage.getSent().get(), senderRecipient);
        else if (syncMessage.getRequest().isPresent())                handleSynchronizeRequestMessage(syncMessage.getRequest().get(), content.getTimestamp());
        else if (syncMessage.getRead().isPresent())                   handleSynchronizeReadMessage(syncMessage.getRead().get(), content.getTimestamp(), senderRecipient);
        else if (syncMessage.getViewed().isPresent())                 handleSynchronizeViewedMessage(syncMessage.getViewed().get(), content.getTimestamp());
        else if (syncMessage.getViewOnceOpen().isPresent())           handleSynchronizeViewOnceOpenMessage(syncMessage.getViewOnceOpen().get(), content.getTimestamp());
        else if (syncMessage.getVerified().isPresent())               handleSynchronizeVerifiedMessage(syncMessage.getVerified().get());
        else if (syncMessage.getStickerPackOperations().isPresent())  handleSynchronizeStickerPackOperation(syncMessage.getStickerPackOperations().get(), content.getTimestamp());
        else if (syncMessage.getConfiguration().isPresent())          handleSynchronizeConfigurationMessage(syncMessage.getConfiguration().get(), content.getTimestamp());
        else if (syncMessage.getBlockedList().isPresent())            handleSynchronizeBlockedListMessage(syncMessage.getBlockedList().get());
        else if (syncMessage.getFetchType().isPresent())              handleSynchronizeFetchMessage(syncMessage.getFetchType().get(), content.getTimestamp());
        else if (syncMessage.getMessageRequestResponse().isPresent()) handleSynchronizeMessageRequestResponse(syncMessage.getMessageRequestResponse().get(), content.getTimestamp());
        else if (syncMessage.getOutgoingPaymentMessage().isPresent()) handleSynchronizeOutgoingPayment(content, syncMessage.getOutgoingPaymentMessage().get());
        else if (syncMessage.getKeys().isPresent())                   handleSynchronizeKeys(syncMessage.getKeys().get(), content.getTimestamp());
        else if (syncMessage.getContacts().isPresent())               handleSynchronizeContacts(syncMessage.getContacts().get(), content.getTimestamp());
        else                                                          warn(String.valueOf(content.getTimestamp()), "Contains no known sync types...");
      } else if (content.getCallMessage().isPresent()) {
        log(String.valueOf(content.getTimestamp()), "Got call message...");

        SignalServiceCallMessage message             = content.getCallMessage().get();
        Optional<Integer>        destinationDeviceId = message.getDestinationDeviceId();

        if (destinationDeviceId.isPresent() && destinationDeviceId.get() != SignalStore.account().getDeviceId()) {
          log(String.valueOf(content.getTimestamp()), String.format(Locale.US, "Ignoring call message that is not for this device! intended: %d, this: %d", destinationDeviceId.get(), SignalStore.account().getDeviceId()));
          return;
        }

        if      (message.getOfferMessage().isPresent())      handleCallOfferMessage(content, message.getOfferMessage().get(), smsMessageId, senderRecipient);
        else if (message.getAnswerMessage().isPresent())     handleCallAnswerMessage(content, message.getAnswerMessage().get(), senderRecipient);
        else if (message.getIceUpdateMessages().isPresent()) handleCallIceUpdateMessage(content, message.getIceUpdateMessages().get(), senderRecipient);
        else if (message.getHangupMessage().isPresent())     handleCallHangupMessage(content, message.getHangupMessage().get(), smsMessageId, senderRecipient);
        else if (message.getBusyMessage().isPresent())       handleCallBusyMessage(content, message.getBusyMessage().get(), senderRecipient);
        else if (message.getOpaqueMessage().isPresent())     handleCallOpaqueMessage(content, message.getOpaqueMessage().get(), senderRecipient);
      } else if (content.getReceiptMessage().isPresent()) {
        SignalServiceReceiptMessage message = content.getReceiptMessage().get();

        if      (message.isReadReceipt())     handleReadReceipt(content, message, senderRecipient);
        else if (message.isDeliveryReceipt()) handleDeliveryReceipt(content, message, senderRecipient);
        else if (message.isViewedReceipt())   handleViewedReceipt(content, message, senderRecipient);
      } else if (content.getTypingMessage().isPresent()) {
        handleTypingMessage(content, content.getTypingMessage().get(), senderRecipient);
      } else if (content.getStoryMessage().isPresent()) {
        handleStoryMessage(content, content.getStoryMessage().get(), senderRecipient);
      } else if (content.getDecryptionErrorMessage().isPresent()) {
        handleRetryReceipt(content, content.getDecryptionErrorMessage().get(), senderRecipient);
      } else if (content.getSenderKeyDistributionMessage().isPresent()) {
        // Already handled, here in order to prevent unrecognized message log
      } else {
        warn(String.valueOf(content.getTimestamp()), "Got unrecognized message!");
      }

      resetRecipientToPush(senderRecipient);

      if (pending != null) {
        warn(content.getTimestamp(), "Pending retry was processed. Deleting.");
        ApplicationDependencies.getPendingRetryReceiptCache().delete(pending);
      }
    } catch (StorageFailedException e) {
      warn(String.valueOf(content.getTimestamp()), e);
      handleCorruptMessage(e.getSender(), e.getSenderDevice(), timestamp, smsMessageId);
    } catch (BadGroupIdException e) {
      warn(String.valueOf(content.getTimestamp()), "Ignoring message with bad group id", e);
    }
  }

  private long handlePendingRetry(@Nullable PendingRetryReceiptModel pending, @NonNull SignalServiceContent content, @NonNull Recipient destination) throws BadGroupIdException {
    long receivedTime = System.currentTimeMillis();

    if (pending != null) {
      warn(content.getTimestamp(), "Incoming message matches a pending retry we were expecting.");

      Long threadId = SignalDatabase.threads().getThreadIdFor(destination.getId());

      if (threadId != null) {
        ThreadDatabase.ConversationMetadata metadata = SignalDatabase.threads().getConversationMetadata(threadId);
        long visibleThread = ApplicationDependencies.getMessageNotifier().getVisibleThread();

        if (threadId != visibleThread && metadata.getLastSeen() > 0 && metadata.getLastSeen() < pending.getReceivedTimestamp()) {
          receivedTime = pending.getReceivedTimestamp();
          warn(content.getTimestamp(), "Thread has not been opened yet. Using received timestamp of " + receivedTime);
        } else {
          warn(content.getTimestamp(), "Thread was opened after receiving the original message. Using the current time for received time. (Last seen: " + metadata.getLastSeen() + ", ThreadVisible: " + (threadId == visibleThread) + ")");
        }
      } else {
        warn(content.getTimestamp(), "Could not find a thread for the pending message. Using current time for received time.");
      }
    }

    return receivedTime;
  }

  private void handlePayment(@NonNull SignalServiceContent content, @NonNull SignalServiceDataMessage message, @NonNull Recipient senderRecipient) {
    log(content.getTimestamp(), "Payment message.");

    if (!message.getPayment().isPresent()) {
      throw new AssertionError();
    }

    if (!message.getPayment().get().getPaymentNotification().isPresent()) {
      warn(content.getTimestamp(), "Ignoring payment message without notification");
      return;
    }

    SignalServiceDataMessage.PaymentNotification paymentNotification = message.getPayment().get().getPaymentNotification().get();
    PaymentDatabase                              paymentDatabase     = SignalDatabase.payments();
    UUID                                         uuid                = UUID.randomUUID();
    String                                       queue               = "Payment_" + PushProcessMessageJob.getQueueName(senderRecipient.getId());

    try {
      paymentDatabase.createIncomingPayment(uuid,
                                            senderRecipient.getId(),
                                            message.getTimestamp(),
                                            paymentNotification.getNote(),
                                            Money.MobileCoin.ZERO,
                                            Money.MobileCoin.ZERO,
                                            paymentNotification.getReceipt());
    } catch (PaymentDatabase.PublicKeyConflictException e) {
      warn(content.getTimestamp(), "Ignoring payment with public key already in database");
      return;
    } catch (SerializationException e) {
      warn(content.getTimestamp(), "Ignoring payment with bad data.", e);
    }

    ApplicationDependencies.getJobManager()
                           .startChain(new PaymentTransactionCheckJob(uuid, queue))
                           .then(PaymentLedgerUpdateJob.updateLedger())
                           .enqueue();
  }

  /**
   * @return True if the content should be ignored, otherwise false.
   */
  private boolean handleGv2PreProcessing(@NonNull GroupId.V2 groupId, @NonNull SignalServiceContent content, @NonNull SignalServiceGroupV2 groupV2, @NonNull Recipient senderRecipient)
      throws IOException, GroupChangeBusyException
  {
    GroupDatabase         groupDatabase = SignalDatabase.groups();
    Optional<GroupRecord> possibleGv1   = groupDatabase.getGroupV1ByExpectedV2(groupId);

    if (possibleGv1.isPresent()) {
      GroupsV1MigrationUtil.performLocalMigration(context, possibleGv1.get().getId().requireV1());
    }

    if (!updateGv2GroupFromServerOrP2PChange(content, groupV2)) {
      log(String.valueOf(content.getTimestamp()), "Ignoring GV2 message for group we are not currently in " + groupId);
      return true;
    }

    Optional<GroupRecord> groupRecord = groupDatabase.getGroup(groupId);

    if (groupRecord.isPresent() && !groupRecord.get().getMembers().contains(senderRecipient.getId())) {
      log(String.valueOf(content.getTimestamp()), "Ignoring GV2 message from member not in group " + groupId + ". Sender: " + senderRecipient.getId() + " | " + senderRecipient.requireServiceId());
      return true;
    }

    if (groupRecord.isPresent() && groupRecord.get().isAnnouncementGroup() && !groupRecord.get().getAdmins().contains(senderRecipient)) {
      if (content.getDataMessage().isPresent()) {
        SignalServiceDataMessage data = content.getDataMessage().get();
        if (data.getBody().isPresent()        ||
            data.getAttachments().isPresent() ||
            data.getQuote().isPresent()       ||
            data.getPreviews().isPresent()    ||
            data.getMentions().isPresent()    ||
            data.getSticker().isPresent())
        {
          Log.w(TAG, "Ignoring message from " + senderRecipient.getId() + " because it has disallowed content, and they're not an admin in an announcement-only group.");
          return true;
        }
      } else if (content.getTypingMessage().isPresent()) {
        Log.w(TAG, "Ignoring typing indicator from " + senderRecipient.getId() + " because they're not an admin in an announcement-only group.");
        return true;
      }
    }

    return false;
  }


  private static @Nullable SignalServiceGroupContext getGroupContextIfPresent(@NonNull SignalServiceContent content) {
    if (content.getDataMessage().isPresent() && content.getDataMessage().get().getGroupContext().isPresent()) {
      return content.getDataMessage().get().getGroupContext().get();
    } else if (content.getSyncMessage().isPresent()                 &&
        content.getSyncMessage().get().getSent().isPresent() &&
        content.getSyncMessage().get().getSent().get().getMessage().getGroupContext().isPresent())
    {
      return content.getSyncMessage().get().getSent().get().getMessage().getGroupContext().get();
    } else {
      return null;
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
      warn(String.valueOf(content.getTimestamp()), "Ignoring message for a group we're not in");
      return false;
    }
  }

  private void handleExceptionMessage(@NonNull MessageState messageState, @NonNull ExceptionMetadata e, long timestamp, @NonNull Optional<Long> smsMessageId) {
    Recipient sender = Recipient.external(context, e.sender);

    if (sender.isBlocked()) {
      warn("Ignoring exception content from blocked sender, message state:" + messageState);
      return;
    }

    switch (messageState) {
      case INVALID_VERSION:
        warn(String.valueOf(timestamp), "Handling invalid version.");
        handleInvalidVersionMessage(e.sender, e.senderDevice, timestamp, smsMessageId);
        break;

      case LEGACY_MESSAGE:
        warn(String.valueOf(timestamp), "Handling legacy message.");
        handleLegacyMessage(e.sender, e.senderDevice, timestamp, smsMessageId);
        break;

      case DUPLICATE_MESSAGE:
        warn(String.valueOf(timestamp), "Duplicate message. Dropping.");
        break;

      case UNSUPPORTED_DATA_MESSAGE:
        warn(String.valueOf(timestamp), "Handling unsupported data message.");
        handleUnsupportedDataMessage(e.sender, e.senderDevice, Optional.fromNullable(e.groupId), timestamp, smsMessageId);
        break;

      case CORRUPT_MESSAGE:
      case NO_SESSION:
        warn(String.valueOf(timestamp), "Discovered old enqueued bad encrypted message. Scheduling reset.");
        ApplicationDependencies.getJobManager().add(new AutomaticSessionResetJob(sender.getId(), e.senderDevice, timestamp));
        break;

      default:
        throw new AssertionError("Not handled " + messageState + ". (" + timestamp + ")");
    }
  }

  private void handleCallOfferMessage(@NonNull SignalServiceContent content,
                                      @NonNull OfferMessage message,
                                      @NonNull Optional<Long> smsMessageId,
                                      @NonNull Recipient senderRecipient)
  {
    log(String.valueOf(content.getTimestamp()), "handleCallOfferMessage...");

    if (smsMessageId.isPresent()) {
      MessageDatabase database = SignalDatabase.sms();
      database.markAsMissedCall(smsMessageId.get(), message.getType() == OfferMessage.Type.VIDEO_CALL);
    } else {
      RemotePeer remotePeer        = new RemotePeer(senderRecipient.getId(), new CallId(message.getId()));
      byte[]     remoteIdentityKey = ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecord(senderRecipient.getId()).transform(record -> record.getIdentityKey().serialize()).orNull();

      ApplicationDependencies.getSignalCallManager()
                             .receivedOffer(new WebRtcData.CallMetadata(remotePeer, content.getSenderDevice()),
                                            new WebRtcData.OfferMetadata(message.getOpaque(), message.getSdp(), message.getType()),
                                            new WebRtcData.ReceivedOfferMetadata(remoteIdentityKey,
                                                                                 content.getServerReceivedTimestamp(),
                                                                                 content.getServerDeliveredTimestamp(),
                                                                                 content.getCallMessage().get().isMultiRing()));
    }
  }

  private void handleCallAnswerMessage(@NonNull SignalServiceContent content,
                                       @NonNull AnswerMessage message,
                                       @NonNull Recipient senderRecipient)
  {
    log(String.valueOf(content), "handleCallAnswerMessage...");
    RemotePeer remotePeer        = new RemotePeer(senderRecipient.getId(), new CallId(message.getId()));
    byte[]     remoteIdentityKey = ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecord(senderRecipient.getId()).transform(record -> record.getIdentityKey().serialize()).orNull();

    ApplicationDependencies.getSignalCallManager()
                           .receivedAnswer(new WebRtcData.CallMetadata(remotePeer, content.getSenderDevice()),
                                           new WebRtcData.AnswerMetadata(message.getOpaque(), message.getSdp()),
                                           new WebRtcData.ReceivedAnswerMetadata(remoteIdentityKey, content.getCallMessage().get().isMultiRing()));
  }

  private void handleCallIceUpdateMessage(@NonNull SignalServiceContent content,
                                          @NonNull List<IceUpdateMessage> messages,
                                          @NonNull Recipient senderRecipient)
  {
    log(String.valueOf(content), "handleCallIceUpdateMessage... " + messages.size());

    List<byte[]> iceCandidates = new ArrayList<>(messages.size());
    long         callId        = -1;

    for (IceUpdateMessage iceMessage : messages) {
      iceCandidates.add(iceMessage.getOpaque());
      callId = iceMessage.getId();
    }

    RemotePeer remotePeer = new RemotePeer(senderRecipient.getId(), new CallId(callId));

    ApplicationDependencies.getSignalCallManager()
                           .receivedIceCandidates(new WebRtcData.CallMetadata(remotePeer, content.getSenderDevice()),
                                                  iceCandidates);
  }

  private void handleCallHangupMessage(@NonNull SignalServiceContent content,
                                       @NonNull HangupMessage message,
                                       @NonNull Optional<Long> smsMessageId,
                                       @NonNull Recipient senderRecipient)
  {
    log(String.valueOf(content), "handleCallHangupMessage");
    if (smsMessageId.isPresent()) {
      SignalDatabase.sms().markAsMissedCall(smsMessageId.get(), false);
    } else {
      RemotePeer remotePeer = new RemotePeer(senderRecipient.getId(), new CallId(message.getId()));

      ApplicationDependencies.getSignalCallManager()
                             .receivedCallHangup(new WebRtcData.CallMetadata(remotePeer, content.getSenderDevice()),
                                                 new WebRtcData.HangupMetadata(message.getType(), message.isLegacy(), message.getDeviceId()));
    }
  }

  private void handleCallBusyMessage(@NonNull SignalServiceContent content,
                                     @NonNull BusyMessage message,
                                     @NonNull Recipient senderRecipient)
  {
    log(String.valueOf(content.getTimestamp()), "handleCallBusyMessage");

    RemotePeer remotePeer = new RemotePeer(senderRecipient.getId(), new CallId(message.getId()));

    ApplicationDependencies.getSignalCallManager()
                           .receivedCallBusy(new WebRtcData.CallMetadata(remotePeer, content.getSenderDevice()));
  }

  private void handleCallOpaqueMessage(@NonNull SignalServiceContent content,
                                       @NonNull OpaqueMessage message,
                                       @NonNull Recipient senderRecipient)
  {
    log(String.valueOf(content.getTimestamp()), "handleCallOpaqueMessage");

    long messageAgeSeconds = 0;
    if (content.getServerReceivedTimestamp() > 0 && content.getServerDeliveredTimestamp() >= content.getServerReceivedTimestamp()) {
      messageAgeSeconds = (content.getServerDeliveredTimestamp() - content.getServerReceivedTimestamp()) / 1000;
    }

    ApplicationDependencies.getSignalCallManager()
                           .receivedOpaqueMessage(new WebRtcData.OpaqueMessageMetadata(senderRecipient.requireServiceId().uuid(),
                                                                                       message.getOpaque(),
                                                                                       content.getSenderDevice(),
                                                                                       messageAgeSeconds));
  }

  private void handleGroupCallUpdateMessage(@NonNull SignalServiceContent content,
                                            @NonNull SignalServiceDataMessage message,
                                            @NonNull Optional<GroupId> groupId,
                                            @NonNull Recipient senderRecipient)
  {
    log(content.getTimestamp(), "Group call update message.");

    if (!groupId.isPresent() || !groupId.get().isV2()) {
      Log.w(TAG, "Invalid group for group call update message");
      return;
    }

    RecipientId groupRecipientId = SignalDatabase.recipients().getOrInsertFromPossiblyMigratedGroupId(groupId.get());

    SignalDatabase.sms().insertOrUpdateGroupCall(groupRecipientId,
                                                                    senderRecipient.getId(),
                                                                    content.getServerReceivedTimestamp(),
                                                                    message.getGroupCallUpdate().get().getEraId());

    GroupCallPeekJob.enqueue(groupRecipientId);
  }

  private @Nullable MessageId handleEndSessionMessage(@NonNull SignalServiceContent content,
                                                      @NonNull Optional<Long> smsMessageId,
                                                      @NonNull Recipient senderRecipient)
  {
    log(content.getTimestamp(), "End session message.");

    MessageDatabase     smsDatabase         = SignalDatabase.sms();
    IncomingTextMessage incomingTextMessage = new IncomingTextMessage(senderRecipient.getId(),
                                                                      content.getSenderDevice(),
                                                                      content.getTimestamp(),
                                                                      content.getServerReceivedTimestamp(),
                                                                      System.currentTimeMillis(),
                                                                      "",
                                                                      Optional.absent(),
                                                                      0,
                                                                      content.isNeedsReceipt(),
                                                                      content.getServerUuid());

    Optional<InsertResult> insertResult;

    if (!smsMessageId.isPresent()) {
      IncomingEndSessionMessage incomingEndSessionMessage = new IncomingEndSessionMessage(incomingTextMessage);

      insertResult = smsDatabase.insertMessageInbox(incomingEndSessionMessage);
    } else {
      smsDatabase.markAsEndSession(smsMessageId.get());
      insertResult = Optional.of(new InsertResult(smsMessageId.get(), smsDatabase.getThreadIdForMessage(smsMessageId.get())));
    }

    if (insertResult.isPresent()) {
      ApplicationDependencies.getProtocolStore().aci().deleteAllSessions(content.getSender().getIdentifier());

      SecurityEvent.broadcastSecurityUpdateEvent(context);
      ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());

      return new MessageId(insertResult.get().getMessageId(), true);
    } else {
      return null;
    }
  }

  private long handleSynchronizeSentEndSessionMessage(@NonNull SentTranscriptMessage message, long envelopeTimestamp)
      throws BadGroupIdException
  {
    log(envelopeTimestamp, "Synchronize end session message.");

    MessageDatabase           database                  = SignalDatabase.sms();
    Recipient                 recipient                 = getSyncMessageDestination(message);
    OutgoingTextMessage outgoingTextMessage       = new OutgoingTextMessage(recipient, "", -1);
    OutgoingEndSessionMessage outgoingEndSessionMessage = new OutgoingEndSessionMessage(outgoingTextMessage);

    long threadId = SignalDatabase.threads().getOrCreateThreadIdFor(recipient);

    if (!recipient.isGroup()) {
      ApplicationDependencies.getProtocolStore().aci().deleteAllSessions(recipient.requireServiceId().toString());

      SecurityEvent.broadcastSecurityUpdateEvent(context);

      long messageId = database.insertMessageOutbox(threadId,
                                                    outgoingEndSessionMessage,
                                                    false,
                                                    message.getTimestamp(),
                                                    null);
      database.markAsSent(messageId, true);
      SignalDatabase.threads().update(threadId, true);
    }

    return threadId;
  }

  private void handleGroupV1Message(@NonNull SignalServiceContent content,
                                    @NonNull SignalServiceDataMessage message,
                                    @NonNull Optional<Long> smsMessageId,
                                    @NonNull GroupId.V1 groupId,
                                    @NonNull Recipient senderRecipient,
                                    @NonNull Recipient threadRecipient,
                                    long receivedTime)
      throws StorageFailedException, BadGroupIdException
  {
    log(content.getTimestamp(), "GroupV1 message.");

    GroupV1MessageProcessor.process(context, content, message, false);

    if (message.getExpiresInSeconds() != 0 && message.getExpiresInSeconds() != threadRecipient.getExpiresInSeconds()) {
      handleExpirationUpdate(content, message, Optional.absent(), Optional.of(groupId), senderRecipient, threadRecipient, receivedTime);
    }

    if (smsMessageId.isPresent()) {
      SignalDatabase.sms().deleteMessage(smsMessageId.get());
    }
  }

  private void handleUnknownGroupMessage(@NonNull SignalServiceContent content,
                                         @NonNull SignalServiceGroupContext group,
                                         @NonNull Recipient senderRecipient)
      throws BadGroupIdException
  {
    log(content.getTimestamp(), "Unknown group message.");

    if (group.getGroupV1().isPresent()) {
      SignalServiceGroup groupV1 = group.getGroupV1().get();
      if (groupV1.getType() != SignalServiceGroup.Type.REQUEST_INFO) {
        ApplicationDependencies.getJobManager().add(new RequestGroupInfoJob(senderRecipient.getId(), GroupId.v1(groupV1.getGroupId())));
      } else {
        warn(content.getTimestamp(), "Received a REQUEST_INFO message for a group we don't know about. Ignoring.");
      }
    } else if (group.getGroupV2().isPresent()) {
      warn(content.getTimestamp(), "Received a GV2 message for a group we have no knowledge of -- attempting to fix this state.");
      SignalDatabase.groups().fixMissingMasterKey(group.getGroupV2().get().getMasterKey());
    } else {
      warn(content.getTimestamp(), "Received a message for a group we don't know about without a group context. Ignoring.");
    }
  }

  private @Nullable MessageId handleExpirationUpdate(@NonNull SignalServiceContent content,
                                                     @NonNull SignalServiceDataMessage message,
                                                     @NonNull Optional<Long> smsMessageId,
                                                     @NonNull Optional<GroupId> groupId,
                                                     @NonNull Recipient senderRecipient,
                                                     @NonNull Recipient threadRecipient,
                                                     long receivedTime)
      throws StorageFailedException
  {
    log(content.getTimestamp(), "Expiration update.");

    if (groupId.isPresent() && groupId.get().isV2()) {
      warn(String.valueOf(content.getTimestamp()), "Expiration update received for GV2. Ignoring.");
      return null;
    }

    int                                 expiresInSeconds = message.getExpiresInSeconds();
    Optional<SignalServiceGroupContext> groupContext     = message.getGroupContext();

    if (threadRecipient.getExpiresInSeconds() == expiresInSeconds) {
      log(String.valueOf(content.getTimestamp()), "No change in message expiry for group. Ignoring.");
      return null;
    }

    try {
      MessageDatabase      database     = SignalDatabase.mms();
      IncomingMediaMessage mediaMessage = new IncomingMediaMessage(senderRecipient.getId(),
                                                                   content.getTimestamp(),
                                                                   content.getServerReceivedTimestamp(),
                                                                   receivedTime,
                                                                   StoryType.NONE,
                                                                   null,
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
                                                                   Optional.absent(),
                                                                   content.getServerUuid());

      Optional<InsertResult> insertResult = database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

      SignalDatabase.recipients().setExpireMessages(threadRecipient.getId(), expiresInSeconds);

      if (smsMessageId.isPresent()) {
        SignalDatabase.sms().deleteMessage(smsMessageId.get());
      }

      if (insertResult.isPresent()) {
        return new MessageId(insertResult.get().getMessageId(), true);
      }
    } catch (MmsException e) {
      throw new StorageFailedException(e, content.getSender().getIdentifier(), content.getSenderDevice());
    }

    return null;
  }

  private @Nullable MessageId handleReaction(@NonNull SignalServiceContent content, @NonNull SignalServiceDataMessage message, @NonNull Recipient senderRecipient) {
    log(content.getTimestamp(), "Handle reaction for message " + message.getReaction().get().getTargetSentTimestamp());

    SignalServiceDataMessage.Reaction reaction = message.getReaction().get();

    if (!EmojiUtil.isEmoji(reaction.getEmoji())) {
      Log.w(TAG, "Reaction text is not a valid emoji! Ignoring the message.");
      return null;
    }

    Recipient     targetAuthor   = Recipient.externalPush(reaction.getTargetAuthor());
    MessageRecord targetMessage  = SignalDatabase.mmsSms().getMessageFor(reaction.getTargetSentTimestamp(), targetAuthor.getId());

    if (targetMessage == null) {
      warn(String.valueOf(content.getTimestamp()), "[handleReaction] Could not find matching message! Putting it in the early message cache. timestamp: " + reaction.getTargetSentTimestamp() + "  author: " + targetAuthor.getId());
      ApplicationDependencies.getEarlyMessageCache().store(targetAuthor.getId(), reaction.getTargetSentTimestamp(), content);
      return null;
    }

    if (targetMessage.isRemoteDelete()) {
      warn(String.valueOf(content.getTimestamp()), "[handleReaction] Found a matching message, but it's flagged as remotely deleted. timestamp: " + reaction.getTargetSentTimestamp() + "  author: " + targetAuthor.getId());
      return null;
    }

    ThreadRecord targetThread = SignalDatabase.threads().getThreadRecord(targetMessage.getThreadId());

    if (targetThread == null) {
      warn(String.valueOf(content.getTimestamp()), "[handleReaction] Could not find a thread for the message! timestamp: " + reaction.getTargetSentTimestamp() + "  author: " + targetAuthor.getId());
      return null;
    }

    Recipient threadRecipient = targetThread.getRecipient().resolve();

    if (threadRecipient.isGroup() && !threadRecipient.getParticipants().contains(senderRecipient)) {
      warn(String.valueOf(content.getTimestamp()), "[handleReaction] Reaction author is not in the group! timestamp: " + reaction.getTargetSentTimestamp() + "  author: " + targetAuthor.getId());
      return null;
    }

    if (!threadRecipient.isGroup() && !senderRecipient.equals(threadRecipient) && !senderRecipient.isSelf()) {
      warn(String.valueOf(content.getTimestamp()), "[handleReaction] Reaction author is not a part of the 1:1 thread! timestamp: " + reaction.getTargetSentTimestamp() + "  author: " + targetAuthor.getId());
      return null;
    }

    MessageId targetMessageId = new MessageId(targetMessage.getId(), targetMessage.isMms());

    if (reaction.isRemove()) {
      SignalDatabase.reactions().deleteReaction(targetMessageId, senderRecipient.getId());
      ApplicationDependencies.getMessageNotifier().updateNotification(context);
    } else {
      ReactionRecord reactionRecord = new ReactionRecord(reaction.getEmoji(), senderRecipient.getId(), message.getTimestamp(), System.currentTimeMillis());
      SignalDatabase.reactions().addReaction(targetMessageId, reactionRecord);
      ApplicationDependencies.getMessageNotifier().updateNotification(context, targetMessage.getThreadId(), false);
    }

    return new MessageId(targetMessage.getId(), targetMessage.isMms());
  }

  private @Nullable MessageId handleRemoteDelete(@NonNull SignalServiceContent content, @NonNull SignalServiceDataMessage message, @NonNull Recipient senderRecipient) {
    log(content.getTimestamp(), "Remote delete for message " + message.getRemoteDelete().get().getTargetSentTimestamp());

    SignalServiceDataMessage.RemoteDelete delete = message.getRemoteDelete().get();

    MessageRecord targetMessage = SignalDatabase.mmsSms().getMessageFor(delete.getTargetSentTimestamp(), senderRecipient.getId());

    if (targetMessage != null && RemoteDeleteUtil.isValidReceive(targetMessage, senderRecipient, content.getServerReceivedTimestamp())) {
      MessageDatabase db = targetMessage.isMms() ? SignalDatabase.mms() : SignalDatabase.sms();
      db.markAsRemoteDelete(targetMessage.getId());
      ApplicationDependencies.getMessageNotifier().updateNotification(context, targetMessage.getThreadId(), false);
      return new MessageId(targetMessage.getId(), targetMessage.isMms());
    } else if (targetMessage == null) {
      warn(String.valueOf(content.getTimestamp()), "[handleRemoteDelete] Could not find matching message! timestamp: " + delete.getTargetSentTimestamp() + "  author: " + senderRecipient.getId());
      ApplicationDependencies.getEarlyMessageCache().store(senderRecipient.getId(), delete.getTargetSentTimestamp(), content);
      return null;
    } else {
      warn(String.valueOf(content.getTimestamp()), String.format(Locale.ENGLISH, "[handleRemoteDelete] Invalid remote delete! deleteTime: %d, targetTime: %d, deleteAuthor: %s, targetAuthor: %s",
          content.getServerReceivedTimestamp(), targetMessage.getServerTimestamp(), senderRecipient.getId(), targetMessage.getRecipient().getId()));
      return null;
    }
  }

  private void handleSynchronizeVerifiedMessage(@NonNull VerifiedMessage verifiedMessage) {
    log(verifiedMessage.getTimestamp(), "Synchronize verified message.");
    IdentityUtil.processVerifiedMessage(context, verifiedMessage);
  }

  private void handleSynchronizeStickerPackOperation(@NonNull List<StickerPackOperationMessage> stickerPackOperations, long envelopeTimestamp) {
    log(envelopeTimestamp, "Synchronize sticker pack operation.");

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
            SignalDatabase.stickers().uninstallPack(packId);
            break;
        }
      } else {
        warn("Received incomplete sticker pack operation sync.");
      }
    }
  }

  private void handleSynchronizeConfigurationMessage(@NonNull ConfigurationMessage configurationMessage, long envelopeTimestamp) {
    log(envelopeTimestamp, "Synchronize configuration message.");

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
    SignalDatabase.recipients().applyBlockedUpdate(blockMessage.getAddresses(), blockMessage.getGroupIds());
  }

  private void handleSynchronizeFetchMessage(@NonNull SignalServiceSyncMessage.FetchType fetchType, long envelopeTimestamp) {
    log(envelopeTimestamp, "Received fetch request with type: " + fetchType);

    switch (fetchType) {
      case LOCAL_PROFILE:
        ApplicationDependencies.getJobManager().add(new RefreshOwnProfileJob());
        break;
      case STORAGE_MANIFEST:
        StorageSyncHelper.scheduleSyncForDataChange();
        break;
      case SUBSCRIPTION_STATUS:
        warn(TAG, "Dropping subscription status fetch message.");
        break;
      default:
        warn(TAG, "Received a fetch message for an unknown type.");
    }
  }

  private void handleSynchronizeMessageRequestResponse(@NonNull MessageRequestResponseMessage response, long envelopeTimestamp)
      throws BadGroupIdException
  {
    log(envelopeTimestamp, "Synchronize message request response.");

    RecipientDatabase recipientDatabase = SignalDatabase.recipients();
    ThreadDatabase    threadDatabase    = SignalDatabase.threads();

    Recipient recipient;

    if (response.getPerson().isPresent()) {
      recipient = Recipient.externalPush(response.getPerson().get());
    } else if (response.getGroupId().isPresent()) {
      GroupId groupId = GroupId.v1(response.getGroupId().get());
      recipient = Recipient.externalPossiblyMigratedGroup(context, groupId);
    } else {
      warn("Message request response was missing a thread recipient! Skipping.");
      return;
    }

    long threadId = threadDatabase.getOrCreateThreadIdFor(recipient);

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
        warn("Got an unknown response type! Skipping");
        break;
    }
  }

  private void handleSynchronizeOutgoingPayment(@NonNull SignalServiceContent content, @NonNull OutgoingPaymentMessage outgoingPaymentMessage) {
    RecipientId recipientId = outgoingPaymentMessage.getRecipient()
                                                    .transform(RecipientId::from)
                                                    .orNull();
    long timestamp = outgoingPaymentMessage.getBlockTimestamp();
    if (timestamp == 0) {
      timestamp = System.currentTimeMillis();
    }

    Optional<MobileCoinPublicAddress> address = outgoingPaymentMessage.getAddress().transform(MobileCoinPublicAddress::fromBytes);
    if (!address.isPresent() && recipientId == null) {
      log(content.getTimestamp(), "Inserting defrag");
      address     = Optional.of(ApplicationDependencies.getPayments().getWallet().getMobileCoinPublicAddress());
      recipientId = Recipient.self().getId();
    }

    UUID uuid = UUID.randomUUID();
    try {
      SignalDatabase.payments()
                     .createSuccessfulPayment(uuid,
                                              recipientId,
                                              address.get(),
                                              timestamp,
                                              outgoingPaymentMessage.getBlockIndex(),
                                              outgoingPaymentMessage.getNote().or(""),
                                              outgoingPaymentMessage.getAmount(),
                                              outgoingPaymentMessage.getFee(),
                                              outgoingPaymentMessage.getReceipt().toByteArray(),
                                              PaymentMetaDataUtil.fromKeysAndImages(outgoingPaymentMessage.getPublicKeys(), outgoingPaymentMessage.getKeyImages()));
   } catch (SerializationException e) {
      warn(content.getTimestamp(), "Ignoring synchronized outgoing payment with bad data.", e);
    }

    log("Inserted synchronized payment " + uuid);
  }

  private void handleSynchronizeKeys(@NonNull KeysMessage keysMessage, long envelopeTimestamp) {
    if (SignalStore.account().isLinkedDevice()) {
      log(envelopeTimestamp, "Synchronize keys.");
    } else {
      log(envelopeTimestamp, "Primary device ignores synchronize keys.");
      return;
    }

    SignalStore.storageService().setStorageKeyFromPrimary(keysMessage.getStorageService().get());
  }

  private void handleSynchronizeContacts(@NonNull ContactsMessage contactsMessage, long envelopeTimestamp) throws IOException {
    if (SignalStore.account().isLinkedDevice()) {
      log(envelopeTimestamp, "Synchronize contacts.");
    } else {
      log(envelopeTimestamp, "Primary device ignores synchronize contacts.");
      return;
    }

    if (!(contactsMessage.getContactsStream() instanceof SignalServiceAttachmentPointer)) {
      warn(envelopeTimestamp, "No contact stream available.");
      return;
    }

    SignalServiceAttachmentPointer contactsAttachment = (SignalServiceAttachmentPointer) contactsMessage.getContactsStream();

    ApplicationDependencies.getJobManager().add(new MultiDeviceContactSyncJob(contactsAttachment));
  }

  private void handleSynchronizeSentMessage(@NonNull SignalServiceContent content,
                                            @NonNull SentTranscriptMessage message,
                                            @NonNull Recipient senderRecipient)
      throws StorageFailedException, BadGroupIdException, IOException, GroupChangeBusyException
  {
    log(String.valueOf(content.getTimestamp()), "Processing sent transcript for message with ID " + message.getTimestamp());

    try {
      GroupDatabase groupDatabase = SignalDatabase.groups();

      if (message.getMessage().isGroupV2Message()) {
        GroupId.V2 groupId = GroupId.v2(message.getMessage().getGroupContext().get().getGroupV2().get().getMasterKey());
        if (handleGv2PreProcessing(groupId, content, message.getMessage().getGroupContext().get().getGroupV2().get(), senderRecipient)) {
          return;
        }
      }

      long threadId = -1;

      if (message.isRecipientUpdate()) {
        handleGroupRecipientUpdate(message, content.getTimestamp());
      } else if (message.getMessage().isEndSession()) {
        threadId = handleSynchronizeSentEndSessionMessage(message, content.getTimestamp());
      } else if (message.getMessage().isGroupV1Update()) {
        Long gv1ThreadId = GroupV1MessageProcessor.process(context, content, message.getMessage(), true);
        threadId = gv1ThreadId == null ? -1 : gv1ThreadId;
      } else if (message.getMessage().isGroupV2Update()) {
        handleSynchronizeSentGv2Update(content, message);
        threadId = SignalDatabase.threads().getOrCreateThreadIdFor(getSyncMessageDestination(message));
      } else if (Build.VERSION.SDK_INT > 19 && message.getMessage().getGroupCallUpdate().isPresent()) {
        handleGroupCallUpdateMessage(content, message.getMessage(), GroupUtil.idFromGroupContext(message.getMessage().getGroupContext()), senderRecipient);
      } else if (message.getMessage().isEmptyGroupV2Message()) {
        warn(content.getTimestamp(), "Empty GV2 message! Doing nothing.");
      } else if (message.getMessage().isExpirationUpdate()) {
        threadId = handleSynchronizeSentExpirationUpdate(message);
      } else if (message.getMessage().getReaction().isPresent()) {
        handleReaction(content, message.getMessage(), senderRecipient);
        threadId = SignalDatabase.threads().getOrCreateThreadIdFor(getSyncMessageDestination(message));
      } else if (message.getMessage().getRemoteDelete().isPresent()) {
        handleRemoteDelete(content, message.getMessage(), senderRecipient);
      } else if (message.getMessage().getAttachments().isPresent() || message.getMessage().getQuote().isPresent() || message.getMessage().getPreviews().isPresent() || message.getMessage().getSticker().isPresent() || message.getMessage().isViewOnce() || message.getMessage().getMentions().isPresent()) {
        threadId = handleSynchronizeSentMediaMessage(message, content.getTimestamp());
      } else if (message.getMessage().getStoryContext().isPresent()) {
        handleStoryReply(content, message.getMessage(), senderRecipient);
      } else {
        threadId = handleSynchronizeSentTextMessage(message, content.getTimestamp());
      }

      if (message.getMessage().getGroupContext().isPresent() && groupDatabase.isUnknownGroup(GroupUtil.idFromGroupContext(message.getMessage().getGroupContext().get()))) {
        handleUnknownGroupMessage(content, message.getMessage().getGroupContext().get(), senderRecipient);
      }

      if (message.getMessage().getProfileKey().isPresent()) {
        Recipient recipient = getSyncMessageDestination(message);

        if (recipient != null && !recipient.isSystemContact() && !recipient.isProfileSharing()) {
          SignalDatabase.recipients().setProfileSharing(recipient.getId(), true);
        }
      }

      if (threadId != -1) {
        SignalDatabase.threads().setRead(threadId, true);
        ApplicationDependencies.getMessageNotifier().updateNotification(context);
      }

      if (SignalStore.rateLimit().needsRecaptcha()) {
        log(content.getTimestamp(), "Got a sent transcript while in reCAPTCHA mode. Assuming we're good to message again.");
        RateLimitUtil.retryAllRateLimitedMessages(context);
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
    log(content.getTimestamp(), "Synchronize sent GV2 update for message with timestamp " + message.getTimestamp());

    SignalServiceGroupV2 signalServiceGroupV2 = message.getMessage().getGroupContext().get().getGroupV2().get();
    GroupId.V2           groupIdV2            = GroupId.v2(signalServiceGroupV2.getMasterKey());

    if (!updateGv2GroupFromServerOrP2PChange(content, signalServiceGroupV2)) {
      log(String.valueOf(content.getTimestamp()), "Ignoring GV2 message for group we are not currently in " + groupIdV2);
    }
  }

  private void handleSynchronizeRequestMessage(@NonNull RequestMessage message, long envelopeTimestamp)
  {
    if (SignalStore.account().isPrimaryDevice()) {
      log(envelopeTimestamp, "Synchronize request message.");
    } else {
      log(envelopeTimestamp, "Linked device ignoring synchronize request message.");
      return;
    }

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

    if (message.isPniIdentityRequest()) {
      ApplicationDependencies.getJobManager().add(new MultiDevicePniIdentityUpdateJob());
    }
  }

  private void handleSynchronizeReadMessage(@NonNull List<ReadMessage> readMessages, long envelopeTimestamp, @NonNull Recipient senderRecipient)
  {
    log(envelopeTimestamp, "Synchronize read message. Count: " + readMessages.size() + ", Timestamps: " + Stream.of(readMessages).map(ReadMessage::getTimestamp).toList());

    Map<Long, Long> threadToLatestRead = new HashMap<>();

    SignalDatabase.mmsSms().setTimestampRead(senderRecipient, readMessages, envelopeTimestamp, threadToLatestRead);

    List<MessageDatabase.MarkedMessageInfo> markedMessages = SignalDatabase.threads().setReadSince(threadToLatestRead, false);

    if (Util.hasItems(markedMessages)) {
      Log.i(TAG, "Updating past messages: " + markedMessages.size());
      MarkReadReceiver.process(context, markedMessages);
    }

    MessageNotifier messageNotifier = ApplicationDependencies.getMessageNotifier();
    messageNotifier.setLastDesktopActivityTimestamp(envelopeTimestamp);
    messageNotifier.cancelDelayedNotifications();
    messageNotifier.updateNotification(context);
  }

  private void handleSynchronizeViewedMessage(@NonNull List<ViewedMessage> viewedMessages, long envelopeTimestamp) {
    log(envelopeTimestamp, "Synchronize view message. Count: " + viewedMessages.size() + ", Timestamps: " + Stream.of(viewedMessages).map(ViewedMessage::getTimestamp).toList());

    List<Long> toMarkViewed = Stream.of(viewedMessages)
                                    .map(message -> {
                                      RecipientId author = Recipient.externalPush(message.getSender()).getId();
                                      return SignalDatabase.mmsSms().getMessageFor(message.getTimestamp(), author);
                                    })
                                    .filter(message -> message != null && message.isMms())
                                    .map(MessageRecord::getId)
                                    .toList();

    SignalDatabase.mms().setIncomingMessagesViewed(toMarkViewed);

    MessageNotifier messageNotifier = ApplicationDependencies.getMessageNotifier();
    messageNotifier.setLastDesktopActivityTimestamp(envelopeTimestamp);
    messageNotifier.cancelDelayedNotifications();
    messageNotifier.updateNotification(context);
  }

  private void handleSynchronizeViewOnceOpenMessage(@NonNull ViewOnceOpenMessage openMessage, long envelopeTimestamp) {
    log(envelopeTimestamp, "Handling a view-once open for message: " + openMessage.getTimestamp());

    RecipientId   author    = Recipient.externalPush(openMessage.getSender()).getId();
    long          timestamp = openMessage.getTimestamp();
    MessageRecord record    = SignalDatabase.mmsSms().getMessageFor(timestamp, author);

    if (record != null && record.isMms()) {
      SignalDatabase.attachments().deleteAttachmentFilesForViewOnceMessage(record.getId());
    } else {
      warn(String.valueOf(envelopeTimestamp), "Got a view-once open message for a message we don't have!");
    }

    MessageNotifier messageNotifier = ApplicationDependencies.getMessageNotifier();
    messageNotifier.setLastDesktopActivityTimestamp(envelopeTimestamp);
    messageNotifier.cancelDelayedNotifications();
    messageNotifier.updateNotification(context);
  }

  private void handleStoryMessage(@NonNull SignalServiceContent content, @NonNull SignalServiceStoryMessage message, @NonNull Recipient senderRecipient) throws StorageFailedException {
    log(content.getTimestamp(), "Story message.");

    if (!Stories.isFeatureAvailable()) {
      warn(content.getTimestamp(), "Dropping unsupported story.");
      return;
    }

    Optional<InsertResult> insertResult;

    MessageDatabase database = SignalDatabase.mms();
    database.beginTransaction();

    try {
      final StoryType storyType;
      if (message.getAllowsReplies().or(false)) {
        storyType = StoryType.STORY_WITH_REPLIES;
      } else {
        storyType = StoryType.STORY_WITHOUT_REPLIES;
      }

      IncomingMediaMessage mediaMessage = new IncomingMediaMessage(senderRecipient.getId(),
                                                                   content.getTimestamp(),
                                                                   content.getServerReceivedTimestamp(),
                                                                   System.currentTimeMillis(),
                                                                   storyType,
                                                                   null,
                                                                   -1,
                                                                   0,
                                                                   false,
                                                                   false,
                                                                   content.isNeedsReceipt(),
                                                                   Optional.absent(),
                                                                   Optional.fromNullable(GroupUtil.getGroupContextIfPresent(content)),
                                                                   message.getFileAttachment().transform(Collections::singletonList),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   content.getServerUuid());

      insertResult = database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

      if (insertResult.isPresent()) {
        database.setTransactionSuccessful();
      }
    } catch (MmsException e) {
      throw new StorageFailedException(e, content.getSender().getIdentifier(), content.getSenderDevice());
    } finally {
      database.endTransaction();
    }

    if (insertResult.isPresent()) {
      List<DatabaseAttachment> allAttachments = SignalDatabase.attachments().getAttachmentsForMessage(insertResult.get().getMessageId());
      List<DatabaseAttachment> attachments    = Stream.of(allAttachments).filterNot(Attachment::isSticker).toList();

      for (DatabaseAttachment attachment : attachments) {
        ApplicationDependencies.getJobManager().add(new AttachmentDownloadJob(insertResult.get().getMessageId(), attachment.getAttachmentId(), false));
      }

      ApplicationDependencies.getExpireStoriesManager().scheduleIfNecessary();
    }
  }

  private void handleStoryReply(@NonNull SignalServiceContent content, @NonNull SignalServiceDataMessage message, @NonNull Recipient senderRecipient) throws StorageFailedException {
    log(content.getTimestamp(), "Story reply.");

    if (!Stories.isFeatureAvailable()) {
      warn(content.getTimestamp(), "Dropping unsupported story reply.");
      return;
    }

    SignalServiceDataMessage.StoryContext storyContext = message.getStoryContext().get();

    MessageDatabase database = SignalDatabase.mms();
    database.beginTransaction();

    try {
      RecipientId   storyAuthorRecipient = RecipientId.from(storyContext.getAuthorServiceId(), null);
      ParentStoryId parentStoryId;
      QuoteModel    quoteModel = null;
      try {
        MessageId storyMessageId = database.getStoryId(storyAuthorRecipient, storyContext.getSentTimestamp());

        if (message.getGroupContext().isPresent()) {
          parentStoryId = new ParentStoryId.GroupReply(storyMessageId.getId());
        } else {
          MmsMessageRecord story = (MmsMessageRecord) database.getMessageRecord(storyMessageId.getId());

          if (!story.getStoryType().isStoryWithReplies()) {
            warn(content.getTimestamp(), "Story has replies disabled. Dropping reply.");
            return;
          }

          parentStoryId = new ParentStoryId.DirectReply(storyMessageId.getId());
          quoteModel    = new QuoteModel(storyContext.getSentTimestamp(), storyAuthorRecipient, "", false, story.getSlideDeck().asAttachments(), Collections.emptyList());
        }
      } catch (NoSuchMessageException e) {
        warn(content.getTimestamp(), "Couldn't find story for reply.", e);
        return;
      }

      IncomingMediaMessage mediaMessage = new IncomingMediaMessage(senderRecipient.getId(),
                                                                   content.getTimestamp(),
                                                                   content.getServerReceivedTimestamp(),
                                                                   System.currentTimeMillis(),
                                                                   StoryType.NONE,
                                                                   parentStoryId,
                                                                   -1,
                                                                   0,
                                                                   false,
                                                                   false,
                                                                   content.isNeedsReceipt(),
                                                                   message.getBody(),
                                                                   Optional.fromNullable(GroupUtil.getGroupContextIfPresent(content)),
                                                                   Optional.absent(),
                                                                   Optional.fromNullable(quoteModel),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   Optional.absent(),
                                                                   content.getServerUuid());

      Optional<InsertResult> insertResult = database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

      if (insertResult.isPresent()) {
        database.setTransactionSuccessful();
      }
    } catch (MmsException e) {
      throw new StorageFailedException(e, content.getSender().getIdentifier(), content.getSenderDevice());
    } finally {
      database.endTransaction();
    }
  }

  private @Nullable MessageId handleMediaMessage(@NonNull SignalServiceContent content,
                                                @NonNull SignalServiceDataMessage message,
                                                @NonNull Optional<Long> smsMessageId,
                                                @NonNull Recipient senderRecipient,
                                                @NonNull Recipient threadRecipient,
                                                long receivedTime)
      throws StorageFailedException
  {
    log(message.getTimestamp(), "Media message.");

    notifyTypingStoppedFromIncomingMessage(senderRecipient, threadRecipient, content.getSenderDevice());

    Optional<InsertResult> insertResult;

    MessageDatabase database = SignalDatabase.mms();
    database.beginTransaction();

    try {
      Optional<QuoteModel>        quote          = getValidatedQuote(message.getQuote());
      Optional<List<Contact>>     sharedContacts = getContacts(message.getSharedContacts());
      Optional<List<LinkPreview>> linkPreviews   = getLinkPreviews(message.getPreviews(), message.getBody().or(""));
      Optional<List<Mention>>     mentions       = getMentions(message.getMentions());
      Optional<Attachment>        sticker        = getStickerAttachment(message.getSticker());

      IncomingMediaMessage mediaMessage = new IncomingMediaMessage(senderRecipient.getId(),
                                                                   message.getTimestamp(),
                                                                   content.getServerReceivedTimestamp(),
                                                                   receivedTime,
                                                                   StoryType.NONE,
                                                                   null,
                                                                   -1,
                                                                   TimeUnit.SECONDS.toMillis(message.getExpiresInSeconds()),
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
                                                                   sticker,
                                                                   content.getServerUuid());

      insertResult = database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

      if (insertResult.isPresent()) {
        if (smsMessageId.isPresent()) {
          SignalDatabase.sms().deleteMessage(smsMessageId.get());
        }

        database.setTransactionSuccessful();
      }
    } catch (MmsException e) {
      throw new StorageFailedException(e, content.getSender().getIdentifier(), content.getSenderDevice());
    } finally {
      database.endTransaction();
    }

    if (insertResult.isPresent()) {
      List<DatabaseAttachment> allAttachments     = SignalDatabase.attachments().getAttachmentsForMessage(insertResult.get().getMessageId());
      List<DatabaseAttachment> stickerAttachments = Stream.of(allAttachments).filter(Attachment::isSticker).toList();
      List<DatabaseAttachment> attachments        = Stream.of(allAttachments).filterNot(Attachment::isSticker).toList();

      forceStickerDownloadIfNecessary(insertResult.get().getMessageId(), stickerAttachments);

      for (DatabaseAttachment attachment : attachments) {
        ApplicationDependencies.getJobManager().add(new AttachmentDownloadJob(insertResult.get().getMessageId(), attachment.getAttachmentId(), false));
      }

      ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
      TrimThreadJob.enqueueAsync(insertResult.get().getThreadId());

      if (message.isViewOnce()) {
        ApplicationDependencies.getViewOnceMessageManager().scheduleIfNecessary();
      }

      return new MessageId(insertResult.get().getMessageId(), true);
    } else {
      return null;
    }
  }

  private long handleSynchronizeSentExpirationUpdate(@NonNull SentTranscriptMessage message)
      throws MmsException, BadGroupIdException
  {
    log(message.getTimestamp(), "Synchronize sent expiration update.");

    MessageDatabase database   = SignalDatabase.mms();
    Recipient       recipient  = getSyncMessageDestination(message);

    OutgoingExpirationUpdateMessage expirationUpdateMessage = new OutgoingExpirationUpdateMessage(recipient,
        message.getTimestamp(),
        TimeUnit.SECONDS.toMillis(message.getMessage().getExpiresInSeconds()));

    long threadId  = SignalDatabase.threads().getOrCreateThreadIdFor(recipient);
    long messageId = database.insertMessageOutbox(expirationUpdateMessage, threadId, false, null);

    database.markAsSent(messageId, true);

    SignalDatabase.recipients().setExpireMessages(recipient.getId(), message.getMessage().getExpiresInSeconds());

    return threadId;
  }

  private long handleSynchronizeSentMediaMessage(@NonNull SentTranscriptMessage message, long envelopeTimestamp)
      throws MmsException, BadGroupIdException
  {
    log(envelopeTimestamp, "Synchronize sent media message for " + message.getTimestamp());

    MessageDatabase             database        = SignalDatabase.mms();
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

    OutgoingMediaMessage mediaMessage = new OutgoingMediaMessage(recipients,
                                                                 message.getMessage().getBody().orNull(),
                                                                 syncAttachments,
                                                                 message.getTimestamp(),
                                                                 -1,
                                                                 TimeUnit.SECONDS.toMillis(message.getMessage().getExpiresInSeconds()),
                                                                 viewOnce,
                                                                 ThreadDatabase.DistributionTypes.DEFAULT,
                                                                 StoryType.NONE,
                                                                 null,
                                                                 quote.orNull(),
                                                                 sharedContacts.or(Collections.emptyList()),
                                                                 previews.or(Collections.emptyList()),
                                                                 mentions.or(Collections.emptyList()),
                                                                 Collections.emptySet(),
                                                                 Collections.emptySet());

    mediaMessage = new OutgoingSecureMediaMessage(mediaMessage);

    if (recipients.getExpiresInSeconds() != message.getMessage().getExpiresInSeconds()) {
      handleSynchronizeSentExpirationUpdate(message);
    }

    long threadId = SignalDatabase.threads().getOrCreateThreadIdFor(recipients);

    long                     messageId;
    List<DatabaseAttachment> attachments;
    List<DatabaseAttachment> stickerAttachments;

    database.beginTransaction();
    try {
      messageId = database.insertMessageOutbox(mediaMessage, threadId, false, GroupReceiptDatabase.STATUS_UNKNOWN, null);

      if (recipients.isGroup()) {
        updateGroupReceiptStatus(message, messageId, recipients.requireGroupId());
      } else {
        database.markUnidentified(messageId, isUnidentified(message, recipients));
      }

      database.markAsSent(messageId, true);

      List<DatabaseAttachment> allAttachments = SignalDatabase.attachments().getAttachmentsForMessage(messageId);

      stickerAttachments = Stream.of(allAttachments).filter(Attachment::isSticker).toList();
      attachments        = Stream.of(allAttachments).filterNot(Attachment::isSticker).toList();

      if (message.getMessage().getExpiresInSeconds() > 0) {
        database.markExpireStarted(messageId, message.getExpirationStartTimestamp());
        ApplicationDependencies.getExpiringMessageManager()
                               .scheduleDeletion(messageId,
                                                 true,
                                                 message.getExpirationStartTimestamp(),
                                                 TimeUnit.SECONDS.toMillis(message.getMessage().getExpiresInSeconds()));
      }

      if (recipients.isSelf()) {
        SyncMessageId id = new SyncMessageId(recipients.getId(), message.getTimestamp());
        SignalDatabase.mmsSms().incrementDeliveryReceiptCount(id, System.currentTimeMillis());
        SignalDatabase.mmsSms().incrementReadReceiptCount(id, System.currentTimeMillis());
      }

      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }

    for (DatabaseAttachment attachment : attachments) {
      ApplicationDependencies.getJobManager().add(new AttachmentDownloadJob(messageId, attachment.getAttachmentId(), false));
    }

    forceStickerDownloadIfNecessary(messageId, stickerAttachments);

    return threadId;
  }

  private void handleGroupRecipientUpdate(@NonNull SentTranscriptMessage message, long envelopeTimestamp)
      throws BadGroupIdException
  {
    log(envelopeTimestamp, "Group recipient update.");

    Recipient recipient = getSyncMessageDestination(message);

    if (!recipient.isGroup()) {
      warn("Got recipient update for a non-group message! Skipping.");
      return;
    }

    MmsSmsDatabase database = SignalDatabase.mmsSms();
    MessageRecord  record   = database.getMessageFor(message.getTimestamp(), Recipient.self().getId());

    if (record == null) {
      warn("Got recipient update for non-existing message! Skipping.");
      return;
    }

    if (!record.isMms()) {
      warn("Recipient update matched a non-MMS message! Skipping.");
      return;
    }

    updateGroupReceiptStatus(message, record.getId(), recipient.requireGroupId());
  }

  private void updateGroupReceiptStatus(@NonNull SentTranscriptMessage message, long messageId, @NonNull GroupId groupString) {
    GroupReceiptDatabase      receiptDatabase     = SignalDatabase.groupReceipts();
    List<RecipientId>         messageRecipientIds = Stream.of(message.getRecipients()).map(RecipientId::from).toList();
    List<Recipient>           members             = SignalDatabase.groups().getGroupMembers(groupString, GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
    Map<RecipientId, Integer> localReceipts       = Stream.of(receiptDatabase.getGroupReceiptInfo(messageId))
                                                          .collect(Collectors.toMap(GroupReceiptInfo::getRecipientId, GroupReceiptInfo::getStatus));

    for (RecipientId messageRecipientId : messageRecipientIds) {
      //noinspection ConstantConditions
      if (localReceipts.containsKey(messageRecipientId) && localReceipts.get(messageRecipientId) < GroupReceiptDatabase.STATUS_UNDELIVERED) {
        receiptDatabase.update(messageRecipientId, messageId, GroupReceiptDatabase.STATUS_UNDELIVERED, message.getTimestamp());
      } else if (!localReceipts.containsKey(messageRecipientId)) {
        receiptDatabase.insert(Collections.singletonList(messageRecipientId), messageId, GroupReceiptDatabase.STATUS_UNDELIVERED, message.getTimestamp());
      }
    }

    List<org.whispersystems.libsignal.util.Pair<RecipientId, Boolean>> unidentifiedStatus = Stream.of(members)
                                                                                                  .map(m -> new org.whispersystems.libsignal.util.Pair<>(m.getId(), message.isUnidentified(m.requireServiceId().toString())))
                                                                                                  .toList();
    receiptDatabase.setUnidentified(unidentifiedStatus, messageId);
  }

  private @Nullable MessageId handleTextMessage(@NonNull SignalServiceContent content,
                                                @NonNull SignalServiceDataMessage message,
                                                @NonNull Optional<Long> smsMessageId,
                                                @NonNull Optional<GroupId> groupId,
                                                @NonNull Recipient senderRecipient,
                                                @NonNull Recipient threadRecipient,
                                                long receivedTime)
      throws StorageFailedException
  {
    log(message.getTimestamp(), "Text message.");
    MessageDatabase database = SignalDatabase.sms();
    String          body     = message.getBody().isPresent() ? message.getBody().get() : "";

    if (message.getExpiresInSeconds() != threadRecipient.getExpiresInSeconds()) {
      handleExpirationUpdate(content, message, Optional.absent(), groupId, senderRecipient, threadRecipient, receivedTime);
    }

    Optional<InsertResult> insertResult;

    if (smsMessageId.isPresent() && !message.getGroupContext().isPresent()) {
      insertResult = Optional.of(database.updateBundleMessageBody(smsMessageId.get(), body));
    } else {
      notifyTypingStoppedFromIncomingMessage(senderRecipient, threadRecipient, content.getSenderDevice());

      IncomingTextMessage textMessage = new IncomingTextMessage(senderRecipient.getId(),
                                                                content.getSenderDevice(),
                                                                message.getTimestamp(),
                                                                content.getServerReceivedTimestamp(),
                                                                receivedTime,
                                                                body,
                                                                groupId,
                                                                TimeUnit.SECONDS.toMillis(message.getExpiresInSeconds()),
                                                                content.isNeedsReceipt(),
                                                                content.getServerUuid());

      textMessage = new IncomingEncryptedMessage(textMessage, body);
      insertResult = database.insertMessageInbox(textMessage);

      if (smsMessageId.isPresent()) database.deleteMessage(smsMessageId.get());
    }

    if (insertResult.isPresent()) {
      ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
      return new MessageId(insertResult.get().getMessageId(), false);
    } else {
      return null;
    }
  }

  private long handleSynchronizeSentTextMessage(@NonNull SentTranscriptMessage message, long envelopeTimestamp)
      throws MmsException, BadGroupIdException
  {
    log(envelopeTimestamp, "Synchronize sent text message for " + message.getTimestamp());

    Recipient recipient       = getSyncMessageDestination(message);
    String    body            = message.getMessage().getBody().or("");
    long      expiresInMillis = TimeUnit.SECONDS.toMillis(message.getMessage().getExpiresInSeconds());

    if (recipient.getExpiresInSeconds() != message.getMessage().getExpiresInSeconds()) {
      handleSynchronizeSentExpirationUpdate(message);
    }

    long    threadId  = SignalDatabase.threads().getOrCreateThreadIdFor(recipient);
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
                                                                           StoryType.NONE,
                                                                           null,
                                                                           null,
                                                                           Collections.emptyList(),
                                                                           Collections.emptyList(),
                                                                           Collections.emptyList());
      outgoingMediaMessage = new OutgoingSecureMediaMessage(outgoingMediaMessage);

      messageId = SignalDatabase.mms().insertMessageOutbox(outgoingMediaMessage, threadId, false, GroupReceiptDatabase.STATUS_UNKNOWN, null);
      database  = SignalDatabase.mms();

      updateGroupReceiptStatus(message, messageId, recipient.requireGroupId());
    } else {
      OutgoingTextMessage outgoingTextMessage = new OutgoingEncryptedMessage(recipient, body, expiresInMillis);

      messageId = SignalDatabase.sms().insertMessageOutbox(threadId, outgoingTextMessage, false, message.getTimestamp(), null);
      database  = SignalDatabase.sms();
      database.markUnidentified(messageId, isUnidentified(message, recipient));
      SignalDatabase.threads().update(threadId, true);
    }

    database.markAsSent(messageId, true);

    if (expiresInMillis > 0) {
      database.markExpireStarted(messageId, message.getExpirationStartTimestamp());
      ApplicationDependencies.getExpiringMessageManager()
                             .scheduleDeletion(messageId, isGroup, message.getExpirationStartTimestamp(), expiresInMillis);
    }

    if (recipient.isSelf()) {
      SyncMessageId id = new SyncMessageId(recipient.getId(), message.getTimestamp());
      SignalDatabase.mmsSms().incrementDeliveryReceiptCount(id, System.currentTimeMillis());
      SignalDatabase.mmsSms().incrementReadReceiptCount(id, System.currentTimeMillis());
    }

    return threadId;
  }

  private void handleInvalidVersionMessage(@NonNull String sender, int senderDevice, long timestamp,
                                           @NonNull Optional<Long> smsMessageId)
  {
    log(timestamp, "Invalid version message.");

    MessageDatabase smsDatabase = SignalDatabase.sms();

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
    log(timestamp, "Corrupt message.");

    MessageDatabase smsDatabase = SignalDatabase.sms();

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
    log(timestamp, "Unsupported data message.");

    MessageDatabase smsDatabase = SignalDatabase.sms();

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
    log(timestamp, "Invalid message.");

    MessageDatabase smsDatabase = SignalDatabase.sms();

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
    log(timestamp, "Legacy message.");

    MessageDatabase smsDatabase = SignalDatabase.sms();

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
                                @NonNull byte[] messageProfileKeyBytes,
                                @NonNull Recipient senderRecipient)
  {
    RecipientDatabase database          = SignalDatabase.recipients();
    ProfileKey        messageProfileKey = ProfileKeyUtil.profileKeyOrNull(messageProfileKeyBytes);

    if (senderRecipient.isSelf()) {
      if (!Objects.equals(ProfileKeyUtil.getSelfProfileKey(), messageProfileKey)) {
        warn(content.getTimestamp(), "Saw a sync message whose profile key doesn't match our records. Scheduling a storage sync to check.");
        StorageSyncHelper.scheduleSyncForDataChange();
      }
    } else if (messageProfileKey != null) {
      if (database.setProfileKey(senderRecipient.getId(), messageProfileKey)) {
        log(content.getTimestamp(), "Profile key on message from " + senderRecipient.getId() + " didn't match our local store. It has been updated.");
        ApplicationDependencies.getJobManager().add(RetrieveProfileJob.forRecipient(senderRecipient.getId()));
      }
    } else {
      warn(String.valueOf(content.getTimestamp()), "Ignored invalid profile key seen in message");
    }
  }

  private void handleNeedsDeliveryReceipt(@NonNull SignalServiceContent content,
                                          @NonNull SignalServiceDataMessage message,
                                          @NonNull MessageId messageId)
  {
    ApplicationDependencies.getJobManager().add(new SendDeliveryReceiptJob(RecipientId.fromHighTrust(content.getSender()), message.getTimestamp(), messageId));
  }

  private void handleViewedReceipt(@NonNull SignalServiceContent content,
                                   @NonNull SignalServiceReceiptMessage message,
                                   @NonNull Recipient senderRecipient)
  {
    if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
      log("Ignoring viewed receipts for IDs: " + Util.join(message.getTimestamps(), ", "));
      return;
    }

    log(TAG, "Processing viewed receipts. Sender: " +  senderRecipient.getId() + ", Device: " + content.getSenderDevice() + ", Timestamps: " + Util.join(message.getTimestamps(), ", "));

    List<SyncMessageId> ids = Stream.of(message.getTimestamps())
                                    .map(t -> new SyncMessageId(senderRecipient.getId(), t))
                                    .toList();

    Collection<SyncMessageId> unhandled = SignalDatabase.mmsSms()
                                                         .incrementViewedReceiptCounts(ids, content.getTimestamp());

    for (SyncMessageId id : unhandled) {
      warn(String.valueOf(content.getTimestamp()), "[handleViewedReceipt] Could not find matching message! timestamp: " + id.getTimetamp() + "  author: " + senderRecipient.getId());
      ApplicationDependencies.getEarlyMessageCache().store(senderRecipient.getId(), id.getTimetamp(), content);
    }
  }

  @SuppressLint("DefaultLocale")
  private void handleDeliveryReceipt(@NonNull SignalServiceContent content,
                                     @NonNull SignalServiceReceiptMessage message,
                                     @NonNull Recipient senderRecipient)
  {
    log(TAG, "Processing delivery receipts. Sender: " +  senderRecipient.getId() + ", Device: " + content.getSenderDevice() + ", Timestamps: " + Util.join(message.getTimestamps(), ", "));

    List<SyncMessageId> ids = Stream.of(message.getTimestamps())
                                    .map(t -> new SyncMessageId(senderRecipient.getId(), t))
                                    .toList();

    SignalDatabase.mmsSms().incrementDeliveryReceiptCounts(ids, System.currentTimeMillis());
    SignalDatabase.messageLog().deleteEntriesForRecipient(message.getTimestamps(), senderRecipient.getId(), content.getSenderDevice());
  }

  @SuppressLint("DefaultLocale")
  private void handleReadReceipt(@NonNull SignalServiceContent content,
                                 @NonNull SignalServiceReceiptMessage message,
                                 @NonNull Recipient senderRecipient)
  {
    if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
      log("Ignoring read receipts for IDs: " + Util.join(message.getTimestamps(), ", "));
      return;
    }

    log(TAG, "Processing read receipts. Sender: " +  senderRecipient.getId() + ", Device: " + content.getSenderDevice() + ", Timestamps: " + Util.join(message.getTimestamps(), ", "));

    List<SyncMessageId> ids = Stream.of(message.getTimestamps())
                                    .map(t -> new SyncMessageId(senderRecipient.getId(), t))
                                    .toList();

    Collection<SyncMessageId> unhandled = SignalDatabase.mmsSms().incrementReadReceiptCounts(ids, content.getTimestamp());

    for (SyncMessageId id : unhandled) {
      warn(String.valueOf(content.getTimestamp()), "[handleReadReceipt] Could not find matching message! timestamp: " + id.getTimetamp() + "  author: " + senderRecipient.getId());
      ApplicationDependencies.getEarlyMessageCache().store(senderRecipient.getId(), id.getTimetamp(), content);
    }
  }

  private void handleTypingMessage(@NonNull SignalServiceContent content,
                                   @NonNull SignalServiceTypingMessage typingMessage,
                                   @NonNull Recipient senderRecipient)
      throws BadGroupIdException
  {
    if (!TextSecurePreferences.isTypingIndicatorsEnabled(context)) {
      return;
    }

    long threadId;

    if (typingMessage.getGroupId().isPresent()) {
      GroupId.Push groupId = GroupId.push(typingMessage.getGroupId().get());

      if (!SignalDatabase.groups().isCurrentMember(groupId, senderRecipient.getId())) {
        warn(String.valueOf(content.getTimestamp()), "Seen typing indicator for non-member " + senderRecipient.getId());
        return;
      }

      Recipient groupRecipient = Recipient.externalPossiblyMigratedGroup(context, groupId);

      threadId = SignalDatabase.threads().getOrCreateThreadIdFor(groupRecipient);
    } else {
      threadId = SignalDatabase.threads().getOrCreateThreadIdFor(senderRecipient);
    }

    if (threadId <= 0) {
      warn(String.valueOf(content.getTimestamp()), "Couldn't find a matching thread for a typing message.");
      return;
    }

    if (typingMessage.isTypingStarted()) {
      Log.d(TAG, "Typing started on thread " + threadId);
      ApplicationDependencies.getTypingStatusRepository().onTypingStarted(context,threadId, senderRecipient, content.getSenderDevice());
    } else {
      Log.d(TAG, "Typing stopped on thread " + threadId);
      ApplicationDependencies.getTypingStatusRepository().onTypingStopped(context, threadId, senderRecipient, content.getSenderDevice(), false);
    }
  }

  private void handleRetryReceipt(@NonNull SignalServiceContent content, @NonNull DecryptionErrorMessage decryptionErrorMessage, @NonNull Recipient senderRecipient) {
    if (!FeatureFlags.retryReceipts()) {
      warn(String.valueOf(content.getTimestamp()), "[RetryReceipt] Feature flag disabled, skipping retry receipt.");
      return;
    }

    if (decryptionErrorMessage.getDeviceId() != SignalStore.account().getDeviceId()) {
      log(String.valueOf(content.getTimestamp()), "[RetryReceipt] Received a DecryptionErrorMessage targeting a linked device. Ignoring.");
      return;
    }

    long sentTimestamp = decryptionErrorMessage.getTimestamp();

    warn(content.getTimestamp(), "[RetryReceipt] Received a retry receipt from " + formatSender(senderRecipient, content) + " for message with timestamp " + sentTimestamp + ".");

    if (!senderRecipient.hasServiceId()) {
      warn(content.getTimestamp(), "[RetryReceipt] Requester " + senderRecipient.getId() + " somehow has no UUID! timestamp: " + sentTimestamp);
      return;
    }

    MessageLogEntry messageLogEntry = SignalDatabase.messageLog().getLogEntry(senderRecipient.getId(), content.getSenderDevice(), sentTimestamp);

    if (decryptionErrorMessage.getRatchetKey().isPresent()) {
      handleIndividualRetryReceipt(senderRecipient, messageLogEntry, content, decryptionErrorMessage);
    } else {
      handleSenderKeyRetryReceipt(senderRecipient, messageLogEntry, content, decryptionErrorMessage);
    }
  }

  private void handleSenderKeyRetryReceipt(@NonNull Recipient requester,
                                           @Nullable MessageLogEntry messageLogEntry,
                                           @NonNull SignalServiceContent content,
                                           @NonNull DecryptionErrorMessage decryptionErrorMessage)
  {
    long          sentTimestamp  = decryptionErrorMessage.getTimestamp();
    MessageRecord relatedMessage = findRetryReceiptRelatedMessage(context, messageLogEntry, sentTimestamp);

    if (relatedMessage == null) {
      warn(content.getTimestamp(), "[RetryReceipt-SK] The related message could not be found! There shouldn't be any sender key resends where we can't find the related message. Skipping.");
      return;
    }

    Recipient threadRecipient = SignalDatabase.threads().getRecipientForThreadId(relatedMessage.getThreadId());
    if (threadRecipient == null) {
      warn(content.getTimestamp(), "[RetryReceipt-SK] Could not find a thread recipient! Skipping.");
      return;
    }

    if (!threadRecipient.isPushV2Group()) {
      warn(content.getTimestamp(), "[RetryReceipt-SK] Thread recipient is not a v2 group! Skipping.");
      return;
    }

    GroupId.V2            groupId          = threadRecipient.requireGroupId().requireV2();
    DistributionId        distributionId   = SignalDatabase.groups().getOrCreateDistributionId(groupId);
    SignalProtocolAddress requesterAddress = new SignalProtocolAddress(requester.requireServiceId().toString(), content.getSenderDevice());

    SignalDatabase.senderKeyShared().delete(distributionId, Collections.singleton(requesterAddress));

    if (messageLogEntry != null) {
      warn(content.getTimestamp(), "[RetryReceipt-SK] Found MSL entry for " + requester.getId() + " (" + requesterAddress + ") with timestamp " + sentTimestamp + ". Scheduling a resend.");

      ApplicationDependencies.getJobManager().add(new ResendMessageJob(messageLogEntry.getRecipientId(),
                                                                       messageLogEntry.getDateSent(),
                                                                       messageLogEntry.getContent(),
                                                                       messageLogEntry.getContentHint(),
                                                                       groupId,
                                                                       distributionId));
    } else {
      warn(content.getTimestamp(), "[RetryReceipt-SK] Unable to find MSL entry for " + requester.getId() + " (" + requesterAddress + ") with timestamp " + sentTimestamp + ".");

      Optional<GroupRecord> groupRecord = SignalDatabase.groups().getGroup(groupId);

      if (!groupRecord.isPresent()) {
        warn(content.getTimestamp(), "[RetryReceipt-SK] Could not find a record for the group!");
        return;
      }

      if (!groupRecord.get().getMembers().contains(requester.getId())) {
        warn(content.getTimestamp(), "[RetryReceipt-SK] The requester is not in the group, so we cannot send them a SenderKeyDistributionMessage.");
        return;
      }

      warn(content.getTimestamp(), "[RetryReceipt-SK] The requester is in the group, so we'll send them a SenderKeyDistributionMessage.");
      ApplicationDependencies.getJobManager().add(new SenderKeyDistributionSendJob(requester.getId(), groupRecord.get().getId().requireV2()));
    }
  }

  private void handleIndividualRetryReceipt(@NonNull Recipient requester, @Nullable MessageLogEntry messageLogEntry, @NonNull SignalServiceContent content, @NonNull DecryptionErrorMessage decryptionErrorMessage) {
    boolean archivedSession = false;

    // TODO [pnp] Ignore retry receipts that have a PNI destinationUuid

    if (decryptionErrorMessage.getRatchetKey().isPresent() &&
        ratchetKeyMatches(requester, content.getSenderDevice(), decryptionErrorMessage.getRatchetKey().get()))
    {
      warn(content.getTimestamp(), "[RetryReceipt-I] Ratchet key matches. Archiving the session.");
      ApplicationDependencies.getProtocolStore().aci().sessions().archiveSession(requester.getId(), content.getSenderDevice());
      archivedSession = true;
    }

    if (messageLogEntry != null) {
      warn(content.getTimestamp(), "[RetryReceipt-I] Found an entry in the MSL. Resending.");
      ApplicationDependencies.getJobManager().add(new ResendMessageJob(messageLogEntry.getRecipientId(),
                                                                       messageLogEntry.getDateSent(),
                                                                       messageLogEntry.getContent(),
                                                                       messageLogEntry.getContentHint(),
                                                                       null,
                                                                       null));
    } else if (archivedSession) {
      warn(content.getTimestamp(), "[RetryReceipt-I] Could not find an entry in the MSL, but we archived the session, so we're sending a null message to complete the reset.");
      ApplicationDependencies.getJobManager().add(new NullMessageSendJob(requester.getId()));
    } else {
      warn(content.getTimestamp(), "[RetryReceipt-I] Could not find an entry in the MSL. Skipping.");
    }
  }

  private @Nullable MessageRecord findRetryReceiptRelatedMessage(@NonNull Context context, @Nullable MessageLogEntry messageLogEntry, long sentTimestamp) {
    if (messageLogEntry != null && messageLogEntry.hasRelatedMessage()) {
      MessageId relatedMessage = messageLogEntry.getRelatedMessages().get(0);

      if (relatedMessage.isMms()) {
        return SignalDatabase.mms().getMessageRecordOrNull(relatedMessage.getId());
      } else {
        return SignalDatabase.sms().getMessageRecordOrNull(relatedMessage.getId());
      }
    } else {
      return SignalDatabase.mmsSms().getMessageFor(sentTimestamp, Recipient.self().getId());
    }
  }

  public static boolean ratchetKeyMatches(@NonNull Recipient recipient, int deviceId, @NonNull ECPublicKey ratchetKey) {
    SignalProtocolAddress address = recipient.resolve().requireServiceId().toProtocolAddress(deviceId);
    SessionRecord         session = ApplicationDependencies.getProtocolStore().aci().loadSession(address);

    return session.currentRatchetKeyMatches(ratchetKey);
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
      warn("Received quote without an ID! Ignoring...");
      return Optional.absent();
    }

    if (quote.get().getAuthor() == null) {
      warn("Received quote without an author! Ignoring...");
      return Optional.absent();
    }

    RecipientId   author  = Recipient.externalPush(quote.get().getAuthor()).getId();
    MessageRecord message = SignalDatabase.mmsSms().getMessageFor(quote.get().getId(), author);

    if (message != null && !message.isRemoteDelete()) {
      log("Found matching message record...");

      List<Attachment> attachments = new LinkedList<>();
      List<Mention>    mentions    = new LinkedList<>();

      if (message.isMms()) {
        MmsMessageRecord mmsMessage = (MmsMessageRecord) message;

        mentions.addAll(SignalDatabase.mentions().getMentionsForMessage(mmsMessage.getId()));

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
      warn("Found the target for the quote, but it's flagged as remotely deleted.");
    }

    warn("Didn't find matching message record...");

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
      warn("Malformed sticker!");
      return Optional.absent();
    }

    String          packId          = Hex.toStringCondensed(sticker.get().getPackId());
    String          packKey         = Hex.toStringCondensed(sticker.get().getPackKey());
    int             stickerId       = sticker.get().getStickerId();
    String          emoji           = sticker.get().getEmoji();
    StickerLocator stickerLocator  = new StickerLocator(packId, packKey, stickerId, emoji);
    StickerDatabase stickerDatabase = SignalDatabase.stickers();
    StickerRecord stickerRecord   = stickerDatabase.getSticker(stickerLocator.getPackId(), stickerLocator.getStickerId(), false);

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

  private Optional<List<LinkPreview>> getLinkPreviews(Optional<List<SignalServicePreview>> previews, @NonNull String message) {
    if (!previews.isPresent() || previews.get().isEmpty()) return Optional.absent();

    List<LinkPreview>     linkPreviews  = new ArrayList<>(previews.get().size());
    LinkPreviewUtil.Links urlsInMessage = LinkPreviewUtil.findValidPreviewUrls(message);

    for (SignalServicePreview preview : previews.get()) {
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
        warn(String.format("Discarding an invalid link preview. hasTitle: %b presentInBody: %b validDomain: %b", hasTitle, presentInBody, validDomain));
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
      mentions.add(new Mention(Recipient.externalPush(mention.getServiceId(), null, false).getId(), mention.getStart(), mention.getLength()));
    }

    return mentions;
  }

  private Optional<InsertResult> insertPlaceholder(@NonNull String sender, int senderDevice, long timestamp) {
    return insertPlaceholder(sender, senderDevice, timestamp, Optional.absent());
  }

  private Optional<InsertResult> insertPlaceholder(@NonNull String sender, int senderDevice, long timestamp, Optional<GroupId> groupId) {
    MessageDatabase     database    = SignalDatabase.sms();
    IncomingTextMessage textMessage = new IncomingTextMessage(Recipient.external(context, sender).getId(),
                                                              senderDevice, timestamp, -1, System.currentTimeMillis(), "",
                                                              groupId, 0, false, null);

    textMessage = new IncomingEncryptedMessage(textMessage, "");
    return database.insertMessageInbox(textMessage);
  }

  private Recipient getSyncMessageDestination(@NonNull SentTranscriptMessage message)
      throws BadGroupIdException
  {
    return getGroupRecipient(message.getMessage().getGroupContext()).or(() -> Recipient.externalPush(message.getDestination().get()));
  }

  private Recipient getMessageDestination(@NonNull SignalServiceContent content) throws BadGroupIdException {
    SignalServiceDataMessage message = content.getDataMessage().orNull();
    return getGroupRecipient(message != null ? message.getGroupContext() : Optional.absent()).or(() -> Recipient.externalHighTrustPush(context, content.getSender()));
  }

  private Optional<Recipient> getGroupRecipient(Optional<SignalServiceGroupContext> message)
      throws BadGroupIdException
  {
    if (message.isPresent()) {
      return Optional.of(Recipient.externalPossiblyMigratedGroup(context, GroupUtil.idFromGroupContext(message.get())));
    }
    return Optional.absent();
  }

  private void notifyTypingStoppedFromIncomingMessage(@NonNull Recipient senderRecipient, @NonNull Recipient conversationRecipient, int device) {
    long threadId = SignalDatabase.threads().getOrCreateThreadIdFor(conversationRecipient);

    if (threadId > 0 && TextSecurePreferences.isTypingIndicatorsEnabled(context)) {
      Log.d(TAG, "Typing stopped on thread " + threadId + " due to an incoming message.");
      ApplicationDependencies.getTypingStatusRepository().onTypingStopped(context, threadId, senderRecipient, device, true);
    }
  }

  private boolean shouldIgnore(@NonNull SignalServiceContent content, @NonNull Recipient sender, @NonNull Recipient conversation)
      throws BadGroupIdException
  {
    if (content.getDataMessage().isPresent()) {
      SignalServiceDataMessage message = content.getDataMessage().get();

      if (conversation.isGroup() && conversation.isBlocked()) {
        return true;
      } else if (conversation.isGroup()) {
        GroupDatabase     groupDatabase = SignalDatabase.groups();
        Optional<GroupId> groupId       = GroupUtil.idFromGroupContext(message.getGroupContext());

        if (groupId.isPresent()       &&
            groupId.get().isV1()      &&
            message.isGroupV1Update() &&
            groupDatabase.groupExists(groupId.get().requireV1().deriveV2MigrationGroupId()))
        {
          warn(String.valueOf(content.getTimestamp()), "Ignoring V1 update for a group we've already migrated to V2.");
          return true;
        }

        if (groupId.isPresent() && groupDatabase.isUnknownGroup(groupId.get())) {
          return sender.isBlocked();
        }

        boolean isTextMessage    = message.getBody().isPresent();
        boolean isMediaMessage   = message.getAttachments().isPresent() || message.getQuote().isPresent() || message.getSharedContacts().isPresent() || message.getSticker().isPresent();
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

        if (groupRecipient.isBlocked() || !groupRecipient.isActiveGroup()) {
          return true;
        } else {
          Optional<GroupRecord> groupRecord = SignalDatabase.groups().getGroup(groupId);
          return groupRecord.isPresent() && groupRecord.get().isAnnouncementGroup() && !groupRecord.get().getAdmins().contains(sender);
        }
      }
    }

    return false;
  }

  private void resetRecipientToPush(@NonNull Recipient recipient) {
    if (recipient.isForceSmsSelection()) {
      SignalDatabase.recipients().setForceSmsSelection(recipient.getId(), false);
    }
  }

  private void forceStickerDownloadIfNecessary(long messageId, List<DatabaseAttachment> stickerAttachments) {
    if (stickerAttachments.isEmpty()) return;

    DatabaseAttachment stickerAttachment = stickerAttachments.get(0);

    if (stickerAttachment.getTransferState() != AttachmentDatabase.TRANSFER_PROGRESS_DONE) {
      AttachmentDownloadJob downloadJob = new AttachmentDownloadJob(messageId, stickerAttachment.getAttachmentId(), true);

      try {
        downloadJob.setContext(context);
        downloadJob.doWork();
      } catch (Exception e) {
        warn("Failed to download sticker inline. Scheduling.");
        ApplicationDependencies.getJobManager().add(downloadJob);
      }
    }
  }

  private static boolean isUnidentified(@NonNull SentTranscriptMessage message, @NonNull Recipient recipient) {
    boolean unidentified = false;

    if (recipient.hasE164()) {
      unidentified |= message.isUnidentified(recipient.requireE164());
    }
    if (recipient.hasServiceId()) {
      unidentified |= message.isUnidentified(recipient.requireServiceId());
    }

    return unidentified;
  }

  private static void log(@NonNull String message) {
    Log.i(TAG, message);
  }

  private static void log(long timestamp, @NonNull String message) {
    log(String.valueOf(timestamp), message);
  }

  private static void log(@NonNull String extra, @NonNull String message) {
    String extraLog = Util.isEmpty(extra) ? "" : "[" + extra + "] ";
    Log.i(TAG, extraLog + message);
  }

  private static void warn(@NonNull String message) {
    warn("", message, null);
  }

  private static void warn(@NonNull String extra, @NonNull String message) {
    warn(extra, message, null);
  }

  private static void warn(long timestamp, @NonNull String message) {
    warn(String.valueOf(timestamp), message);
  }

  private static void warn(long timestamp, @NonNull String message, @Nullable Throwable t) {
    warn(String.valueOf(timestamp), message, t);
  }

  private static void warn(@NonNull String message, @Nullable Throwable t) {
    warn("", message, t);
  }

  private static void warn(@NonNull String extra, @NonNull String message, @Nullable Throwable t) {
    String extraLog = Util.isEmpty(extra) ? "" : "[" + extra + "] ";
    Log.w(TAG, extraLog + message, t);
  }

  private static String formatSender(@NonNull Recipient recipient, @Nullable SignalServiceContent content) {
    return formatSender(recipient.getId(), content);
  }

  private static String formatSender(@NonNull RecipientId recipientId, @Nullable SignalServiceContent content) {
    if (content != null) {
      return recipientId + " (" + content.getSender().getIdentifier() + "." + content.getSenderDevice() + ")";
    } else {
      return recipientId.toString();
    }
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

  public enum MessageState {
    DECRYPTED_OK,
    INVALID_VERSION,
    CORRUPT_MESSAGE, // Not used, but can't remove due to serialization
    NO_SESSION,      // Not used, but can't remove due to serialization
    LEGACY_MESSAGE,
    DUPLICATE_MESSAGE,
    UNSUPPORTED_DATA_MESSAGE,
    NOOP
  }

  public static final class ExceptionMetadata {
    @NonNull  private final String  sender;
              private final int     senderDevice;
    @Nullable private final GroupId groupId;

    public ExceptionMetadata(@NonNull String sender, int senderDevice, @Nullable GroupId groupId) {
      this.sender       = sender;
      this.senderDevice = senderDevice;
      this.groupId      = groupId;
    }

    public ExceptionMetadata(@NonNull String sender, int senderDevice) {
      this(sender, senderDevice, null);
    }

    @NonNull
    public String getSender() {
      return sender;
    }

    public int getSenderDevice() {
      return senderDevice;
    }

    @Nullable
    public GroupId getGroupId() {
      return groupId;
    }
  }
}
