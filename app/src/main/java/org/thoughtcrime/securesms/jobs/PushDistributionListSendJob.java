package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.database.GroupReceiptTable;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SentStorySyncManifest;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobLogger;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.messages.GroupSendUtil;
import org.thoughtcrime.securesms.messages.StorySendUtil;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.stories.Stories;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessageRecipient;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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

  private static final String KEY_MESSAGE_ID             = "message_id";
  private static final String KEY_FILTERED_RECIPIENT_IDS = "filtered_recipient_ids";

  private final long             messageId;
  private final Set<RecipientId> filterRecipientIds;

  public PushDistributionListSendJob(long messageId, @NonNull RecipientId destination, boolean hasMedia, @NonNull Set<RecipientId> filterRecipientIds) {
    this(new Parameters.Builder()
             .setQueue(destination.toQueueKey(hasMedia))
             .addConstraint(NetworkConstraint.KEY)
             .setLifespan(TimeUnit.DAYS.toMillis(1))
             .setMaxAttempts(Parameters.UNLIMITED)
             .build(),
         messageId,
         filterRecipientIds
    );
  }

  private PushDistributionListSendJob(@NonNull Parameters parameters, long messageId, @NonNull Set<RecipientId> filterRecipientIds) {
    super(parameters);
    this.messageId          = messageId;
    this.filterRecipientIds = filterRecipientIds;
  }

  @WorkerThread
  public static void enqueue(@NonNull Context context,
                             @NonNull JobManager jobManager,
                             long messageId,
                             @NonNull RecipientId destination,
                             @NonNull Set<RecipientId> filterRecipientIds)
  {
    try {
      Recipient listRecipient = Recipient.resolved(destination);

      if (!listRecipient.isDistributionList()) {
        throw new AssertionError("Not a distribution list! MessageId: " + messageId);
      }

      OutgoingMessage message = SignalDatabase.messages().getOutgoingMessage(messageId);

      if (!message.getStoryType().isStory()) {
        throw new AssertionError("Only story messages are currently supported! MessageId: " + messageId);
      }

      if (!message.getStoryType().isTextStory()) {
        DatabaseAttachment storyAttachment = (DatabaseAttachment) message.getAttachments().get(0);
        SignalDatabase.attachments().updateAttachmentCaption(storyAttachment.getAttachmentId(), message.getBody());
      }

      Set<String> attachmentUploadIds = enqueueCompressingAndUploadAttachmentsChains(jobManager, message);

      jobManager.add(new PushDistributionListSendJob(messageId, destination, !attachmentUploadIds.isEmpty(), filterRecipientIds), attachmentUploadIds, attachmentUploadIds.isEmpty() ? null : destination.toQueueKey());
    } catch (NoSuchMessageException | MmsException e) {
      Log.w(TAG, "Failed to enqueue message.", e);
      SignalDatabase.messages().markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putLong(KEY_MESSAGE_ID, messageId)
                                    .putString(KEY_FILTERED_RECIPIENT_IDS, RecipientId.toSerializedList(filterRecipientIds))
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {
    SignalDatabase.messages().markAsSending(messageId);
  }

  @Override
  public void onPushSend()
      throws IOException, MmsException, NoSuchMessageException, RetryLaterException
  {
    MessageTable             database                   = SignalDatabase.messages();
    OutgoingMessage          message                    = database.getOutgoingMessage(messageId);
    Set<NetworkFailure>      existingNetworkFailures    = new HashSet<>(message.getNetworkFailures());
    Set<IdentityKeyMismatch> existingIdentityMismatches = new HashSet<>(message.getIdentityKeyMismatches());

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

      List<Recipient> targets;
      List<RecipientId> skipped = Collections.emptyList();

      if (Util.hasItems(filterRecipientIds)) {
        targets = new ArrayList<>(filterRecipientIds.size() + existingNetworkFailures.size());
        targets.addAll(filterRecipientIds.stream().map(Recipient::resolved).collect(Collectors.toList()));
        targets.addAll(existingNetworkFailures.stream().map(nf -> nf.getRecipientId(context)).distinct().map(Recipient::resolved).collect(Collectors.toList()));
      } else if (!existingNetworkFailures.isEmpty()) {
        targets = Stream.of(existingNetworkFailures).map(nf -> nf.getRecipientId(context)).distinct().map(Recipient::resolved).toList();
      } else {
        Stories.SendData data = Stories.getRecipientsToSendTo(messageId, message.getSentTimeMillis(), message.getStoryType().isStoryWithReplies());
        targets = data.getTargets();
        skipped = data.getSkipped();
      }

      List<SendMessageResult> results = deliver(message, targets);
      Log.i(TAG, JobLogger.format(this, "Finished send."));

      PushGroupSendJob.processGroupMessageResults(context, messageId, -1, null, message, results, targets, skipped, existingNetworkFailures, existingIdentityMismatches);

    } catch (UntrustedIdentityException | UndeliverableMessageException e) {
      warn(TAG, String.valueOf(message.getSentTimeMillis()), e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public void onFailure() {
    SignalDatabase.messages().markAsSentFailed(messageId);
  }

  private List<SendMessageResult> deliver(@NonNull OutgoingMessage message, @NonNull List<Recipient> destinations)
      throws IOException, UntrustedIdentityException, UndeliverableMessageException
  {
    try {
      rotateSenderCertificateIfNecessary();

      List<Attachment>                    attachments        = Stream.of(message.getAttachments()).filterNot(Attachment::isSticker).toList();
      List<SignalServiceAttachment>       attachmentPointers = getAttachmentPointersFor(attachments);
      List<SignalServiceProtos.BodyRange> bodyRanges         = getBodyRanges(message);
      boolean                             isRecipientUpdate  = Stream.of(SignalDatabase.groupReceipts().getGroupReceiptInfo(messageId))
                                                                     .anyMatch(info -> info.getStatus() > GroupReceiptTable.STATUS_UNDELIVERED);

      final SignalServiceStoryMessage storyMessage;
      if (message.getStoryType().isTextStory()) {
        storyMessage = SignalServiceStoryMessage.forTextAttachment(Recipient.self().getProfileKey(), null, StorySendUtil.deserializeBodyToStoryTextAttachment(message, this::getPreviewsFor), message.getStoryType().isStoryWithReplies(), bodyRanges);
      } else if (!attachmentPointers.isEmpty()) {
        storyMessage = SignalServiceStoryMessage.forFileAttachment(Recipient.self().getProfileKey(), null, attachmentPointers.get(0), message.getStoryType().isStoryWithReplies(), bodyRanges);
      } else {
        throw new UndeliverableMessageException("No attachment on non-text story.");
      }

      SentStorySyncManifest                   manifest           = SignalDatabase.storySends().getFullSentStorySyncManifest(messageId, message.getSentTimeMillis());
      Set<SignalServiceStoryMessageRecipient> manifestCollection = manifest != null ? manifest.toRecipientsSet() : Collections.emptySet();

      Log.d(TAG, "[" + messageId + "] Sending a story message with a manifest of size " + manifestCollection.size());

      return GroupSendUtil.sendStoryMessage(context, message.getRecipient().requireDistributionListId(), destinations, isRecipientUpdate, new MessageId(messageId), message.getSentTimeMillis(), storyMessage, manifestCollection);
    } catch (ServerRejectedException e) {
      throw new UndeliverableMessageException(e);
    }
  }

  public static class Factory implements Job.Factory<PushDistributionListSendJob> {
    @Override
    public @NonNull PushDistributionListSendJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      Set<RecipientId> recipientIds = new HashSet<>(RecipientId.fromSerializedList(data.getStringOrDefault(KEY_FILTERED_RECIPIENT_IDS, "")));
      return new PushDistributionListSendJob(parameters, data.getLong(KEY_MESSAGE_ID), recipientIds);
    }
  }
}
