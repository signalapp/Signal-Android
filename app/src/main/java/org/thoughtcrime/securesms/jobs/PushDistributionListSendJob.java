package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase.GroupReceiptInfo;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.DistributionListId;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobLogger;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.messages.GroupSendUtil;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessage;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A job that lets us send a message to a distribution list. Currently the only supported message type is a story.
 */
public final class PushDistributionListSendJob extends PushSendJob {

  public static final String KEY = "PushDistributionListSendJob";

  private static final String TAG = Log.tag(PushDistributionListSendJob.class);

  private static final String KEY_MESSAGE_ID = "message_id";

  private final long messageId;

  public PushDistributionListSendJob(long messageId, @NonNull RecipientId destination, boolean hasMedia) {
    this(new Parameters.Builder()
                       .setQueue(destination.toQueueKey(hasMedia))
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build(),
         messageId);

  }

  private PushDistributionListSendJob(@NonNull Parameters parameters, long messageId) {
    super(parameters);
    this.messageId = messageId;
  }

  @WorkerThread
  public static void enqueue(@NonNull Context context,
                             @NonNull JobManager jobManager,
                             long messageId,
                             @NonNull RecipientId destination)
  {
    try {
      Recipient listRecipient = Recipient.resolved(destination);

      if (!listRecipient.isDistributionList()) {
        throw new AssertionError("Not a distribution list! MessageId: " + messageId);
      }

      OutgoingMediaMessage message = SignalDatabase.mms().getOutgoingMessage(messageId);

      if (!message.getStoryType().isStory()) {
        throw new AssertionError("Only story messages are currently supported! MessageId: " + messageId);
      }

      Set<String> attachmentUploadIds = enqueueCompressingAndUploadAttachmentsChains(jobManager, message);

      jobManager.add(new PushDistributionListSendJob(messageId, destination, !attachmentUploadIds.isEmpty()), attachmentUploadIds, attachmentUploadIds.isEmpty() ? null : destination.toQueueKey());
    } catch (NoSuchMessageException | MmsException e) {
      Log.w(TAG, "Failed to enqueue message.", e);
      SignalDatabase.mms().markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId).build();
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
    MessageDatabase          database                   = SignalDatabase.mms();
    OutgoingMediaMessage     message                    = database.getOutgoingMessage(messageId);
    Set<NetworkFailure>      existingNetworkFailures    = message.getNetworkFailures();
    Set<IdentityKeyMismatch> existingIdentityMismatches = message.getIdentityKeyMismatches();

    if (!message.getStoryType().isStory()) {
      throw new MmsException("Only story sends are currently supported!");
    }

    if (database.isSent(messageId)) {
      log(TAG, String.valueOf(message.getSentTimeMillis()), "Message " + messageId + " was already sent. Ignoring.");
      return;
    }

    Recipient listRecipient = message.getRecipient().resolve();

    if (!listRecipient.isDistributionList()) {
      throw new MmsException("Message recipient isn't a distribution list!");
    }

    try {
      log(TAG, String.valueOf(message.getSentTimeMillis()), "Sending message: " + messageId + ", Recipient: " + message.getRecipient().getId() + ", Attachments: " + buildAttachmentString(message.getAttachments()));

      List<Recipient> target;

      if (!existingNetworkFailures.isEmpty()) target = Stream.of(existingNetworkFailures).map(nf -> nf.getRecipientId(context)).distinct().map(Recipient::resolved).toList();
      else                                    target = Stream.of(getFullRecipients(listRecipient.requireDistributionListId(), messageId)).distinctBy(Recipient::getId).toList();

      List<SendMessageResult> results = deliver(message, target);
      Log.i(TAG, JobLogger.format(this, "Finished send."));

      PushGroupSendJob.processGroupMessageResults(context, messageId, -1, null, message, results, target, Collections.emptyList(), existingNetworkFailures, existingIdentityMismatches);
    } catch (UntrustedIdentityException | UndeliverableMessageException e) {
      warn(TAG, String.valueOf(message.getSentTimeMillis()), e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public void onFailure() {
    SignalDatabase.mms().markAsSentFailed(messageId);
  }

  private List<SendMessageResult> deliver(@NonNull OutgoingMediaMessage message, @NonNull List<Recipient> destinations)
      throws IOException, UntrustedIdentityException, UndeliverableMessageException
  {
    try {
      rotateSenderCertificateIfNecessary();

      List<Attachment>              attachments        = Stream.of(message.getAttachments()).filterNot(Attachment::isSticker).toList();
      List<SignalServiceAttachment> attachmentPointers = getAttachmentPointersFor(attachments);
      boolean                       isRecipientUpdate  = Stream.of(SignalDatabase.groupReceipts().getGroupReceiptInfo(messageId))
                                                               .anyMatch(info -> info.getStatus() > GroupReceiptDatabase.STATUS_UNDELIVERED);

      SignalServiceStoryMessage storyMessage = SignalServiceStoryMessage.forFileAttachment(Recipient.self().getProfileKey(), null, attachmentPointers.get(0), message.getStoryType().isStoryWithReplies());
      return GroupSendUtil.sendStoryMessage(context, message.getRecipient().requireDistributionListId(), destinations, isRecipientUpdate, new MessageId(messageId, true), message.getSentTimeMillis(), storyMessage);
    } catch (ServerRejectedException e) {
      throw new UndeliverableMessageException(e);
    }
  }

  private static List<Recipient> getFullRecipients(@NonNull DistributionListId distributionListId, long messageId) {
    List<GroupReceiptInfo> destinations = SignalDatabase.groupReceipts().getGroupReceiptInfo(messageId);

    if (!destinations.isEmpty()) {
      return RecipientUtil.getEligibleForSending(destinations.stream()
                                                             .map(GroupReceiptInfo::getRecipientId)
                                                             .map(Recipient::resolved)
                                                             .collect(Collectors.toList()));
    } else {
      return RecipientUtil.getEligibleForSending(SignalDatabase.distributionLists()
                                                               .getMembers(distributionListId)
                                                               .stream()
                                                               .map(Recipient::resolved)
                                                               .collect(Collectors.toList()));
    }
  }

  public static class Factory implements Job.Factory<PushDistributionListSendJob> {
    @Override
    public @NonNull PushDistributionListSendJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new PushDistributionListSendJob(parameters, data.getLong(KEY_MESSAGE_ID));
    }
  }
}
