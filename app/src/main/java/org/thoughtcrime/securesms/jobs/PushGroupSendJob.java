package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.google.protobuf.ByteString;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase.GroupReceiptInfo;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobLogger;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.messages.GroupSendUtil;
import org.thoughtcrime.securesms.mms.MessageGroupContext;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingGroupUpdateMessage;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.RecipientAccessList;
import org.thoughtcrime.securesms.util.SetUtil;
import org.thoughtcrime.securesms.util.SignalLocalMetrics;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.messages.SignalServicePreview;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContextV2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class PushGroupSendJob extends PushSendJob {

  public static final String KEY = "PushGroupSendJob";

  private static final String TAG = Log.tag(PushGroupSendJob.class);

  private static final String KEY_MESSAGE_ID       = "message_id";
  private static final String KEY_FILTER_RECIPIENT = "filter_recipient";

  private final long        messageId;
  private final RecipientId filterRecipient;

  public PushGroupSendJob(long messageId, @NonNull RecipientId destination, @Nullable RecipientId filterRecipient, boolean hasMedia) {
    this(new Job.Parameters.Builder()
                           .setQueue(destination.toQueueKey(hasMedia))
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
         messageId, filterRecipient);

  }

  private PushGroupSendJob(@NonNull Job.Parameters parameters, long messageId, @Nullable RecipientId filterRecipient) {
    super(parameters);

    this.messageId       = messageId;
    this.filterRecipient = filterRecipient;
  }

  @WorkerThread
  public static void enqueue(@NonNull Context context,
                             @NonNull JobManager jobManager,
                             long messageId,
                             @NonNull RecipientId destination,
                             @Nullable RecipientId filterAddress)
  {
    try {
      Recipient group = Recipient.resolved(destination);
      if (!group.isPushGroup()) {
        throw new AssertionError("Not a group!");
      }

      MessageDatabase      database            = SignalDatabase.mms();
      OutgoingMediaMessage message             = database.getOutgoingMessage(messageId);
      Set<String>          attachmentUploadIds = enqueueCompressingAndUploadAttachmentsChains(jobManager, message);

      if (!SignalDatabase.groups().isActive(group.requireGroupId()) && !isGv2UpdateMessage(message)) {
        throw new MmsException("Inactive group!");
      }

      jobManager.add(new PushGroupSendJob(messageId, destination, filterAddress, !attachmentUploadIds.isEmpty()), attachmentUploadIds, attachmentUploadIds.isEmpty() ? null : destination.toQueueKey());

    } catch (NoSuchMessageException | MmsException e) {
      Log.w(TAG, "Failed to enqueue message.", e);
      SignalDatabase.mms().markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId)
                             .putString(KEY_FILTER_RECIPIENT, filterRecipient != null ? filterRecipient.serialize() : null)
                             .build();
  }

  private static boolean isGv2UpdateMessage(@NonNull OutgoingMediaMessage message) {
    return (message instanceof OutgoingGroupUpdateMessage && ((OutgoingGroupUpdateMessage) message).isV2Group());
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {
    SignalDatabase.mms().markAsSending(messageId);
  }

  @Override
  public void onPushSend()
      throws IOException, MmsException, NoSuchMessageException, RetryLaterException
  {
    SignalLocalMetrics.GroupMessageSend.onJobStarted(messageId);

    MessageDatabase          database                   = SignalDatabase.mms();
    OutgoingMediaMessage     message                    = database.getOutgoingMessage(messageId);
    long                     threadId                   = database.getMessageRecord(messageId).getThreadId();
    Set<NetworkFailure>      existingNetworkFailures    = message.getNetworkFailures();
    Set<IdentityKeyMismatch> existingIdentityMismatches = message.getIdentityKeyMismatches();

    ApplicationDependencies.getJobManager().cancelAllInQueue(TypingSendJob.getQueue(threadId));

    if (database.isSent(messageId)) {
      log(TAG, String.valueOf(message.getSentTimeMillis()),  "Message " + messageId + " was already sent. Ignoring.");
      return;
    }

    Recipient groupRecipient = message.getRecipient().resolve();

    if (!groupRecipient.isPushGroup()) {
      throw new MmsException("Message recipient isn't a group!");
    }

    if (groupRecipient.isPushV1Group()) {
      throw new MmsException("No GV1 messages can be sent anymore!");
    }

    try {
      log(TAG, String.valueOf(message.getSentTimeMillis()), "Sending message: " + messageId + ", Recipient: " + message.getRecipient().getId() + ", Thread: " + threadId + ", Attachments: " + buildAttachmentString(message.getAttachments()));

      if (!groupRecipient.resolve().isProfileSharing() && !database.isGroupQuitMessage(messageId)) {
        RecipientUtil.shareProfileIfFirstSecureMessage(context, groupRecipient);
      }

      List<Recipient>   target;
      List<RecipientId> skipped = new ArrayList<>();

      if (filterRecipient != null) {
        target = Collections.singletonList(Recipient.resolved(filterRecipient));
      } else if (!existingNetworkFailures.isEmpty()) {
        target = Stream.of(existingNetworkFailures).map(nf -> nf.getRecipientId(context)).distinct().map(Recipient::resolved).toList();
      } else {
        GroupRecipientResult result = getGroupMessageRecipients(groupRecipient.requireGroupId(), messageId);

        target  = result.target;
        skipped = result.skipped;
      }

      List<SendMessageResult> results = deliver(message, groupRecipient, target);
      processGroupMessageResults(context, messageId, threadId, groupRecipient, message, results, target, skipped, existingNetworkFailures, existingIdentityMismatches);
      Log.i(TAG, JobLogger.format(this, "Finished send."));

    } catch (UntrustedIdentityException | UndeliverableMessageException e) {
      warn(TAG, String.valueOf(message.getSentTimeMillis()), e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }

    SignalLocalMetrics.GroupMessageSend.onJobFinished(messageId);
  }

  @Override
  public void onRetry() {
    SignalLocalMetrics.GroupMessageSend.cancel(messageId);
    super.onRetry();
  }

  @Override
  public void onFailure() {
    SignalDatabase.mms().markAsSentFailed(messageId);
  }

  private List<SendMessageResult> deliver(OutgoingMediaMessage message, @NonNull Recipient groupRecipient, @NonNull List<Recipient> destinations)
      throws IOException, UntrustedIdentityException, UndeliverableMessageException
  {
    try {
      rotateSenderCertificateIfNecessary();

      GroupId.Push                               groupId            = groupRecipient.requireGroupId().requirePush();
      Optional<byte[]>                           profileKey         = getProfileKey(groupRecipient);
      Optional<SignalServiceDataMessage.Sticker> sticker            = getStickerFor(message);
      List<SharedContact>                        sharedContacts     = getSharedContactsFor(message);
      List<SignalServicePreview>                 previews           = getPreviewsFor(message);
      List<SignalServiceDataMessage.Mention>     mentions           = getMentionsFor(message.getMentions());
      List<Attachment>                           attachments        = Stream.of(message.getAttachments()).filterNot(Attachment::isSticker).toList();
      List<SignalServiceAttachment>              attachmentPointers = getAttachmentPointersFor(attachments);
      boolean                                    isRecipientUpdate  = Stream.of(SignalDatabase.groupReceipts().getGroupReceiptInfo(messageId))
                                                                            .anyMatch(info -> info.getStatus() > GroupReceiptDatabase.STATUS_UNDELIVERED);

      if (message.getStoryType().isStory()) {
        Optional<GroupDatabase.GroupRecord> groupRecord = SignalDatabase.groups().getGroup(groupId);

        if (groupRecord.isPresent()) {
          GroupDatabase.V2GroupProperties v2GroupProperties = groupRecord.get().requireV2GroupProperties();
          SignalServiceGroupV2            groupContext      = SignalServiceGroupV2.newBuilder(v2GroupProperties.getGroupMasterKey())
                                                                                  .withRevision(v2GroupProperties.getGroupRevision())
                                                                                  .build();

          SignalServiceStoryMessage storyMessage = SignalServiceStoryMessage.forFileAttachment(Recipient.self().getProfileKey(), groupContext, attachmentPointers.get(0), message.getStoryType().isStoryWithReplies());

          return GroupSendUtil.sendGroupStoryMessage(context, groupId.requireV2(), destinations, isRecipientUpdate, new MessageId(messageId, true), message.getSentTimeMillis(), storyMessage);
        } else {
          throw new UndeliverableMessageException("No group found! " + groupId);
        }
      } else if (message.isGroup()) {
        OutgoingGroupUpdateMessage groupMessage = (OutgoingGroupUpdateMessage) message;

        if (groupMessage.isV2Group()) {
          MessageGroupContext.GroupV2Properties properties   = groupMessage.requireGroupV2Properties();
          GroupContextV2                        groupContext = properties.getGroupContext();
          SignalServiceGroupV2.Builder          builder      = SignalServiceGroupV2.newBuilder(properties.getGroupMasterKey())
                                                                                   .withRevision(groupContext.getRevision());

          ByteString groupChange = groupContext.getGroupChange();
          if (groupChange != null) {
            builder.withSignedGroupChange(groupChange.toByteArray());
          }

          SignalServiceGroupV2     group            = builder.build();
          SignalServiceDataMessage groupDataMessage = SignalServiceDataMessage.newBuilder()
                                                                              .withTimestamp(message.getSentTimeMillis())
                                                                              .withExpiration(groupRecipient.getExpiresInSeconds())
                                                                              .asGroupMessage(group)
                                                                              .build();
          return GroupSendUtil.sendResendableDataMessage(context, groupRecipient.requireGroupId().requireV2(), destinations, isRecipientUpdate, ContentHint.IMPLICIT, new MessageId(messageId, true), groupDataMessage);
        } else {
          throw new UndeliverableMessageException("Messages can no longer be sent to V1 groups!");
        }
      } else {
        Optional<GroupDatabase.GroupRecord> groupRecord = SignalDatabase.groups().getGroup(groupRecipient.requireGroupId());

        if (groupRecord.isPresent() && groupRecord.get().isAnnouncementGroup() && !groupRecord.get().isAdmin(Recipient.self())) {
          throw new UndeliverableMessageException("Non-admins cannot send messages in announcement groups!");
        }

        SignalServiceDataMessage.Builder builder = SignalServiceDataMessage.newBuilder()
                                                                           .withTimestamp(message.getSentTimeMillis());

        GroupUtil.setDataMessageGroupContext(context, builder, groupId);

        SignalServiceDataMessage.Builder groupMessageBuilder = builder.withAttachments(attachmentPointers)
                                                                      .withBody(message.getBody())
                                                                      .withExpiration((int)(message.getExpiresIn() / 1000))
                                                                      .withViewOnce(message.isViewOnce())
                                                                      .asExpirationUpdate(message.isExpirationUpdate())
                                                                      .withProfileKey(profileKey.orNull())
                                                                      .withSticker(sticker.orNull())
                                                                      .withSharedContacts(sharedContacts)
                                                                      .withPreviews(previews)
                                                                      .withMentions(mentions);

        if (message.getParentStoryId() != null) {
          try {
            MessageRecord storyRecord = SignalDatabase.mms().getMessageRecord(message.getParentStoryId().asMessageId().getId());
            Recipient     recipient   = storyRecord.isOutgoing() ? Recipient.self() : storyRecord.getIndividualRecipient();

            destinations = destinations.stream()
                                       .filter(r -> r.getStoriesCapability() == Recipient.Capability.SUPPORTED)
                                       .collect(java.util.stream.Collectors.toList());

            groupMessageBuilder.withStoryContext(new SignalServiceDataMessage.StoryContext(recipient.requireServiceId(), storyRecord.getDateSent()));
          } catch (NoSuchMessageException e) {
            // The story has probably expired
            // TODO [stories] check what should happen in this case
            throw new UndeliverableMessageException(e);
          }
        } else {
          groupMessageBuilder.withQuote(getQuoteFor(message).orNull());
        }

        Log.i(TAG, JobLogger.format(this, "Beginning message send."));

        return GroupSendUtil.sendResendableDataMessage(context,
                                                       groupRecipient.getGroupId().transform(GroupId::requireV2).orNull(),
                                                       destinations,
                                                       isRecipientUpdate,
                                                       ContentHint.RESENDABLE,
                                                       new MessageId(messageId, true),
                                                       groupMessageBuilder.build());
      }
    } catch (ServerRejectedException e) {
      throw new UndeliverableMessageException(e);
    }
  }

  public static long getMessageId(@NonNull Data data) {
    return data.getLong(KEY_MESSAGE_ID);
  }

  static void processGroupMessageResults(@NonNull Context context,
                                         long messageId,
                                         long threadId,
                                         @Nullable Recipient groupRecipient,
                                         @NonNull OutgoingMediaMessage message,
                                         @NonNull List<SendMessageResult> results,
                                         @NonNull List<Recipient> target,
                                         @NonNull List<RecipientId> skipped,
                                         @NonNull Set<NetworkFailure> existingNetworkFailures,
                                         @NonNull Set<IdentityKeyMismatch> existingIdentityMismatches)
      throws RetryLaterException, ProofRequiredException
  {
    MmsDatabase         database   = SignalDatabase.mms();
    RecipientAccessList accessList = new RecipientAccessList(target);

    List<NetworkFailure>             networkFailures           = Stream.of(results).filter(SendMessageResult::isNetworkFailure).map(result -> new NetworkFailure(accessList.requireIdByAddress(result.getAddress()))).toList();
    List<IdentityKeyMismatch>        identityMismatches        = Stream.of(results).filter(result -> result.getIdentityFailure() != null).map(result -> new IdentityKeyMismatch(accessList.requireIdByAddress(result.getAddress()), result.getIdentityFailure().getIdentityKey())).toList();
    ProofRequiredException           proofRequired             = Stream.of(results).filter(r -> r.getProofRequiredFailure() != null).findLast().map(SendMessageResult::getProofRequiredFailure).orElse(null);
    List<SendMessageResult>          successes                 = Stream.of(results).filter(result -> result.getSuccess() != null).toList();
    List<Pair<RecipientId, Boolean>> successUnidentifiedStatus = Stream.of(successes).map(result -> new Pair<>(accessList.requireIdByAddress(result.getAddress()), result.getSuccess().isUnidentified())).toList();
    Set<RecipientId>                 successIds                = Stream.of(successUnidentifiedStatus).map(Pair::first).collect(Collectors.toSet());
    List<NetworkFailure>             resolvedNetworkFailures   = Stream.of(existingNetworkFailures).filter(failure -> successIds.contains(failure.getRecipientId(context))).toList();
    List<IdentityKeyMismatch>        resolvedIdentityFailures  = Stream.of(existingIdentityMismatches).filter(failure -> successIds.contains(failure.getRecipientId(context))).toList();
    List<RecipientId>                unregisteredRecipients    = Stream.of(results).filter(SendMessageResult::isUnregisteredFailure).map(result -> RecipientId.from(result.getAddress())).toList();
    List<RecipientId>                skippedRecipients         = new ArrayList<>(unregisteredRecipients);

    skippedRecipients.addAll(skipped);

    if (networkFailures.size() > 0 || identityMismatches.size() > 0 || proofRequired != null || unregisteredRecipients.size() > 0) {
      Log.w(TAG,  String.format(Locale.US, "Failed to send to some recipients. Network: %d, Identity: %d, ProofRequired: %s, Unregistered: %d",
                                networkFailures.size(), identityMismatches.size(), proofRequired != null, unregisteredRecipients.size()));
    }

    RecipientDatabase recipientDatabase = SignalDatabase.recipients();
    for (RecipientId unregistered : unregisteredRecipients) {
      recipientDatabase.markUnregistered(unregistered);
    }

    existingNetworkFailures.removeAll(resolvedNetworkFailures);
    existingNetworkFailures.addAll(networkFailures);
    database.setNetworkFailures(messageId, existingNetworkFailures);

    existingIdentityMismatches.removeAll(resolvedIdentityFailures);
    existingIdentityMismatches.addAll(identityMismatches);
    database.setMismatchedIdentities(messageId, existingIdentityMismatches);

    SignalDatabase.groupReceipts().setUnidentified(successUnidentifiedStatus, messageId);

    if (proofRequired != null) {
      handleProofRequiredException(context, proofRequired, groupRecipient, threadId, messageId, true);
    }

    if (existingNetworkFailures.isEmpty() && networkFailures.isEmpty() && identityMismatches.isEmpty() && existingIdentityMismatches.isEmpty()) {
      database.markAsSent(messageId, true);

      markAttachmentsUploaded(messageId, message);

      if (skippedRecipients.size() > 0) {
        SignalDatabase.groupReceipts().setSkipped(skippedRecipients, messageId);
      }

      if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
        database.markExpireStarted(messageId);
        ApplicationDependencies.getExpiringMessageManager()
                               .scheduleDeletion(messageId, true, message.getExpiresIn());
      }

      if (message.isViewOnce()) {
        SignalDatabase.attachments().deleteAttachmentFilesForViewOnceMessage(messageId);
      }

      if (message.getStoryType().isStory()) {
        ApplicationDependencies.getExpireStoriesManager().scheduleIfNecessary();
      }
    } else if (!identityMismatches.isEmpty()) {
      Log.w(TAG, "Failing because there were " + identityMismatches.size() + " identity mismatches.");
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);

      Set<RecipientId> mismatchRecipientIds = Stream.of(identityMismatches)
                                                    .map(mismatch -> mismatch.getRecipientId(context))
                                                    .collect(Collectors.toSet());

      RetrieveProfileJob.enqueue(mismatchRecipientIds);
    } else if (!networkFailures.isEmpty()) {
      long retryAfter = results.stream()
                               .filter(r -> r.getRateLimitFailure() != null)
                               .map(r -> r.getRateLimitFailure().getRetryAfterMilliseconds().or(-1L))
                               .max(Long::compare)
                               .orElse(-1L);
      Log.w(TAG, "Retrying because there were " + networkFailures.size() + " network failures. retryAfter: " + retryAfter);
      throw new RetryLaterException(retryAfter);
    }
  }

  private static @NonNull GroupRecipientResult getGroupMessageRecipients(@NonNull GroupId groupId, long messageId) {
    List<GroupReceiptInfo> destinations = SignalDatabase.groupReceipts().getGroupReceiptInfo(messageId);

    List<Recipient>   possible;

    if (!destinations.isEmpty()) {
      possible = Stream.of(destinations)
                       .map(GroupReceiptInfo::getRecipientId)
                       .map(Recipient::resolved)
                       .distinctBy(Recipient::getId)
                       .toList();
    } else {
      Log.w(TAG, "No destinations found for group message " + groupId + " using current group membership");
      possible = Stream.of(SignalDatabase.groups()
                                         .getGroupMembers(groupId, GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF))
                                         .map(Recipient::resolve)
                                         .distinctBy(Recipient::getId)
                                         .toList();
    }

    List<Recipient>   eligible = RecipientUtil.getEligibleForSending(possible);
    List<RecipientId> skipped  = Stream.of(SetUtil.difference(possible, eligible)).map(Recipient::getId).toList();

    return new GroupRecipientResult(eligible, skipped);
  }

  private static class GroupRecipientResult {
    private final List<Recipient>   target;
    private final List<RecipientId> skipped;

    private GroupRecipientResult(@NonNull List<Recipient> target, @NonNull List<RecipientId> skipped) {
      this.target  = target;
      this.skipped = skipped;
    }
  }

  public static class Factory implements Job.Factory<PushGroupSendJob> {
    @Override
    public @NonNull PushGroupSendJob create(@NonNull Parameters parameters, @NonNull org.thoughtcrime.securesms.jobmanager.Data data) {
      String      raw    = data.getString(KEY_FILTER_RECIPIENT);
      RecipientId filter = raw != null ? RecipientId.from(raw) : null;

      return new PushGroupSendJob(parameters, data.getLong(KEY_MESSAGE_ID), filter);
    }
  }
}
