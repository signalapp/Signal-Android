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
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.MessageTable.SyncMessageId;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.AttachmentCompressionJob;
import org.thoughtcrime.securesms.jobs.AttachmentCopyJob;
import org.thoughtcrime.securesms.jobs.AttachmentMarkUploadedJob;
import org.thoughtcrime.securesms.jobs.AttachmentUploadJob;
import org.thoughtcrime.securesms.jobs.MmsSendJob;
import org.thoughtcrime.securesms.jobs.ProfileKeySendJob;
import org.thoughtcrime.securesms.jobs.PushDistributionListSendJob;
import org.thoughtcrime.securesms.jobs.PushGroupSendJob;
import org.thoughtcrime.securesms.jobs.IndividualSendJob;
import org.thoughtcrime.securesms.jobs.ReactionSendJob;
import org.thoughtcrime.securesms.jobs.RemoteDeleteSendJob;
import org.thoughtcrime.securesms.jobs.ResumableUploadSpecJob;
import org.thoughtcrime.securesms.jobs.SmsSendJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.util.ParcelUtil;
import org.thoughtcrime.securesms.util.SignalLocalMetrics;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MessageSender {

  private static final String TAG = Log.tag(MessageSender.class);

  /**
   * Suitable for a 1:1 conversation or a GV1 group only.
   */
  @WorkerThread
  public static void sendProfileKey(final long threadId) {
    ProfileKeySendJob job = ProfileKeySendJob.create(threadId, false);
    if (job != null) {
      ApplicationDependencies.getJobManager().add(job);
    }
  }

  public static void sendStories(@NonNull final Context context,
                                 @NonNull final List<OutgoingMessage> messages,
                                 @Nullable final String metricId,
                                 @Nullable final MessageTable.InsertListener insertListener)
  {
    Log.i(TAG, "Sending story messages to " + messages.size() + " targets.");
    ThreadTable  threadTable = SignalDatabase.threads();
    MessageTable database    = SignalDatabase.messages();
    List<Long>   messageIds  = new ArrayList<>(messages.size());
    List<Long>   threads     = new ArrayList<>(messages.size());
    UploadDependencyGraph dependencyGraph;

    try {
      database.beginTransaction();

      for (OutgoingMessage message : messages) {
        long allocatedThreadId = threadTable.getOrCreateValidThreadId(message.getRecipient(), -1L, message.getDistributionType());
        long messageId         = database.insertMessageOutbox(message.stripAttachments(), allocatedThreadId, false, insertListener);

        messageIds.add(messageId);
        threads.add(allocatedThreadId);

        if (message.getRecipient().isGroup() && message.getAttachments().isEmpty() && message.getLinkPreviews().isEmpty() && message.getSharedContacts().isEmpty()) {
          SignalLocalMetrics.GroupMessageSend.onInsertedIntoDatabase(messageId, metricId);
        } else {
          SignalLocalMetrics.GroupMessageSend.cancel(metricId);
        }
      }

      for (int i = 0; i < messageIds.size(); i++) {
        long            messageId = messageIds.get(i);
        OutgoingMessage message   = messages.get(i);
        Recipient       recipient = message.getRecipient();

        if (recipient.isDistributionList()) {
          DistributionId    distributionId = Objects.requireNonNull(SignalDatabase.distributionLists().getDistributionId(recipient.requireDistributionListId()));
          List<RecipientId> members        = SignalDatabase.distributionLists().getMembers(recipient.requireDistributionListId());
          SignalDatabase.storySends().insert(messageId, members, message.getSentTimeMillis(), message.getStoryType().isStoryWithReplies(), distributionId);
        }
      }

      dependencyGraph = UploadDependencyGraph.create(
          messages,
          ApplicationDependencies.getJobManager(),
          attachment -> {
            try {
              return SignalDatabase.attachments().insertAttachmentForPreUpload(attachment);
            } catch (MmsException e) {
              Log.e(TAG, e);
              throw new IllegalStateException(e);
            }
          }
      );

      for (int i = 0; i < messageIds.size(); i++) {
        long                             messageId = messageIds.get(i);
        OutgoingMessage                  message   = messages.get(i);
        List<UploadDependencyGraph.Node> nodes     = dependencyGraph.getDependencyMap().get(message);

        if (nodes == null || nodes.isEmpty()) {
          if (message.getStoryType().isTextStory()) {
            Log.d(TAG, "No attachments for given text story. Skipping.");
            continue;
          } else {
            Log.e(TAG, "No attachments for given media story. Aborting.");
            throw new MmsException("No attachment for story.");
          }
        }

        List<AttachmentId> attachmentIds = nodes.stream().map(UploadDependencyGraph.Node::getAttachmentId).collect(Collectors.toList());
        SignalDatabase.attachments().updateMessageId(attachmentIds, messageId, true);
        for (final AttachmentId attachmentId : attachmentIds) {
          SignalDatabase.attachments().updateAttachmentCaption(attachmentId, message.getBody());
        }
      }

      database.setTransactionSuccessful();
    } catch (MmsException e) {
      Log.w(TAG, "Failed to send stories.", e);
      return;
    } finally {
      database.endTransaction();
    }

    List<JobManager.Chain> chains = dependencyGraph.consumeDeferredQueue();
    for (final JobManager.Chain chain : chains) {
      chain.enqueue();
    }

    for (int i = 0; i < messageIds.size(); i++) {
      long            messageId = messageIds.get(i);
      OutgoingMessage message   = messages.get(i);
      Recipient       recipient = message.getRecipient();
      List<UploadDependencyGraph.Node> dependencies = dependencyGraph.getDependencyMap().get(message);

      List<String> jobDependencyIds = (dependencies != null) ? dependencies.stream().map(UploadDependencyGraph.Node::getJobId).collect(Collectors.toList())
                                                             : Collections.emptyList();

      sendMessageInternal(context,
                          recipient,
                          SendType.SIGNAL,
                          messageId,
                          jobDependencyIds,
                          false);
    }

    onMessageSent();

    for (long threadId : threads) {
      threadTable.update(threadId, true);
    }
  }

  public static long send(final Context context,
                          final OutgoingMessage message,
                          final long threadId,
                          @NonNull SendType sendType,
                          @Nullable final String metricId,
                          @Nullable final MessageTable.InsertListener insertListener)
  {
    Log.i(TAG, "Sending media message to " + message.getRecipient().getId() + ", thread: " + threadId);
    try {
      ThreadTable  threadTable = SignalDatabase.threads();
      MessageTable database    = SignalDatabase.messages();

      long      allocatedThreadId = threadTable.getOrCreateValidThreadId(message.getRecipient(), threadId, message.getDistributionType());
      Recipient recipient         = message.getRecipient();
      long      messageId         = database.insertMessageOutbox(applyUniversalExpireTimerIfNecessary(context, recipient, message, allocatedThreadId), allocatedThreadId, sendType != SendType.SIGNAL, insertListener);

      if (message.getRecipient().isGroup() && message.getAttachments().isEmpty() && message.getLinkPreviews().isEmpty() && message.getSharedContacts().isEmpty()) {
        SignalLocalMetrics.GroupMessageSend.onInsertedIntoDatabase(messageId, metricId);
      } else {
        SignalLocalMetrics.GroupMessageSend.cancel(metricId);
      }

      sendMessageInternal(context, recipient, sendType, messageId, Collections.emptyList(), message.getScheduledDate() > 0);
      onMessageSent();
      threadTable.update(allocatedThreadId, true);

      return allocatedThreadId;
    } catch (MmsException e) {
      Log.w(TAG, e);
      return threadId;
    }
  }

  public static long sendPushWithPreUploadedMedia(final Context context,
                                                  final OutgoingMessage message,
                                                  final Collection<PreUploadResult> preUploadResults,
                                                  final long threadId,
                                                  final MessageTable.InsertListener insertListener)
  {
    Log.i(TAG, "Sending media message with pre-uploads to " + message.getRecipient().getId() + ", thread: " + threadId + ", pre-uploads: " +  preUploadResults);
    Preconditions.checkArgument(message.getAttachments().isEmpty(), "If the media is pre-uploaded, there should be no attachments on the message.");

    try {
      ThreadTable     threadTable        = SignalDatabase.threads();
      MessageTable    mmsDatabase        = SignalDatabase.messages();
      AttachmentTable attachmentDatabase = SignalDatabase.attachments();

      Recipient recipient         = message.getRecipient();
      long      allocatedThreadId = threadTable.getOrCreateValidThreadId(message.getRecipient(), threadId);
      long      messageId         = mmsDatabase.insertMessageOutbox(applyUniversalExpireTimerIfNecessary(context, recipient, message, allocatedThreadId),
                                                                    allocatedThreadId,
                                                                    false,
                                                                    insertListener);

      List<AttachmentId> attachmentIds = Stream.of(preUploadResults).map(PreUploadResult::getAttachmentId).toList();
      List<String>       jobIds        = Stream.of(preUploadResults).map(PreUploadResult::getJobIds).flatMap(Stream::of).toList();

      attachmentDatabase.updateMessageId(attachmentIds, messageId, message.getStoryType().isStory());

      sendMessageInternal(context, recipient, SendType.SIGNAL, messageId, jobIds, false);
      onMessageSent();
      threadTable.update(allocatedThreadId, true);

      return allocatedThreadId;
    } catch (MmsException e) {
      Log.w(TAG, e);
      return threadId;
    }
  }

  public static void sendMediaBroadcast(@NonNull Context context,
                                        @NonNull List<OutgoingMessage> messages,
                                        @NonNull Collection<PreUploadResult> preUploadResults,
                                        boolean overwritePreUploadMessageIds)
  {
    Log.i(TAG, "Sending media broadcast (overwrite: " + overwritePreUploadMessageIds + ") to " + Stream.of(messages).map(m -> m.getRecipient().getId()).toList());
    Preconditions.checkArgument(messages.size() > 0, "No messages!");
    Preconditions.checkArgument(Stream.of(messages).allMatch(m -> m.getAttachments().isEmpty()), "Messages can't have attachments! They should be pre-uploaded.");

    JobManager         jobManager             = ApplicationDependencies.getJobManager();
    AttachmentTable    attachmentDatabase     = SignalDatabase.attachments();
    MessageTable       mmsDatabase            = SignalDatabase.messages();
    ThreadTable        threadTable            = SignalDatabase.threads();
    List<AttachmentId> preUploadAttachmentIds = Stream.of(preUploadResults).map(PreUploadResult::getAttachmentId).toList();
    List<String>       preUploadJobIds        = Stream.of(preUploadResults).map(PreUploadResult::getJobIds).flatMap(Stream::of).toList();
    List<Long>         messageIds             = new ArrayList<>(messages.size());
    List<String>       messageDependsOnIds    = new ArrayList<>(preUploadJobIds);
    OutgoingMessage    primaryMessage         = messages.get(0);

    mmsDatabase.beginTransaction();
    try {
      if (overwritePreUploadMessageIds) {
        long primaryThreadId  = threadTable.getOrCreateThreadIdFor(primaryMessage.getRecipient(), primaryMessage.getDistributionType());
        long primaryMessageId = mmsDatabase.insertMessageOutbox(applyUniversalExpireTimerIfNecessary(context, primaryMessage.getRecipient(), primaryMessage, primaryThreadId),
                                                                primaryThreadId,
                                                                false,
                                                                null);

        attachmentDatabase.updateMessageId(preUploadAttachmentIds, primaryMessageId, primaryMessage.getStoryType().isStory());
        if (primaryMessage.getStoryType() != StoryType.NONE) {
          for (final AttachmentId preUploadAttachmentId : preUploadAttachmentIds) {
            attachmentDatabase.updateAttachmentCaption(preUploadAttachmentId, primaryMessage.getBody());
          }
        }
        messageIds.add(primaryMessageId);
      }

      List<DatabaseAttachment> preUploadAttachments = Stream.of(preUploadAttachmentIds)
                                                            .map(attachmentDatabase::getAttachment)
                                                            .toList();

      if (messages.size() > 0) {
        List<OutgoingMessage>    secondaryMessages = overwritePreUploadMessageIds ? messages.subList(1, messages.size()) : messages;
        List<List<AttachmentId>> attachmentCopies  = new ArrayList<>();

        for (int i = 0; i < preUploadAttachmentIds.size(); i++) {
          attachmentCopies.add(new ArrayList<>(messages.size()));
        }

        for (OutgoingMessage secondaryMessage : secondaryMessages) {
          long               allocatedThreadId = threadTable.getOrCreateThreadIdFor(secondaryMessage.getRecipient(), secondaryMessage.getDistributionType());
          long               messageId         = mmsDatabase.insertMessageOutbox(applyUniversalExpireTimerIfNecessary(context, secondaryMessage.getRecipient(), secondaryMessage, allocatedThreadId),
                                                                                 allocatedThreadId,
                                                                                 false,
                                                                                 null);
          List<AttachmentId> attachmentIds     = new ArrayList<>(preUploadAttachmentIds.size());

          for (int i = 0; i < preUploadAttachments.size(); i++) {
            AttachmentId attachmentId = attachmentDatabase.insertAttachmentForPreUpload(preUploadAttachments.get(i)).getAttachmentId();
            attachmentCopies.get(i).add(attachmentId);
            attachmentIds.add(attachmentId);
          }

          attachmentDatabase.updateMessageId(attachmentIds, messageId, secondaryMessage.getStoryType().isStory());
          if (primaryMessage.getStoryType() != StoryType.NONE) {
            for (final AttachmentId preUploadAttachmentId : attachmentIds) {
              attachmentDatabase.updateAttachmentCaption(preUploadAttachmentId, primaryMessage.getBody());
            }
          }

          messageIds.add(messageId);
        }

        for (int i = 0; i < attachmentCopies.size(); i++) {
          Job copyJob = new AttachmentCopyJob(preUploadAttachmentIds.get(i), attachmentCopies.get(i));
          jobManager.add(copyJob, preUploadJobIds);
          messageDependsOnIds.add(copyJob.getId());
        }
      }

      for (int i = 0; i < messageIds.size(); i++) {
        long            messageId = messageIds.get(i);
        OutgoingMessage message   = messages.get(i);
        Recipient       recipient = message.getRecipient();

        if (recipient.isDistributionList()) {
          List<RecipientId> members        = SignalDatabase.distributionLists().getMembers(recipient.requireDistributionListId());
          DistributionId    distributionId = Objects.requireNonNull(SignalDatabase.distributionLists().getDistributionId(recipient.requireDistributionListId()));
          SignalDatabase.storySends().insert(messageId, members, message.getSentTimeMillis(), message.getStoryType().isStoryWithReplies(), distributionId);
        }
      }

      onMessageSent();
      mmsDatabase.setTransactionSuccessful();
    } catch (MmsException e) {
      Log.w(TAG, "Failed to send messages.", e);
      return;
    } finally {
      mmsDatabase.endTransaction();
    }

    for (int i = 0; i < messageIds.size(); i++) {
      long      messageId = messageIds.get(i);
      Recipient recipient = messages.get(i).getRecipient();

      if (isLocalSelfSend(context, recipient, SendType.SIGNAL)) {
        sendLocalMediaSelf(context, messageId);
      } else if (recipient.isPushGroup()) {
        jobManager.add(new PushGroupSendJob(messageId, recipient.getId(), Collections.emptySet(), true, false), messageDependsOnIds, recipient.getId().toQueueKey());
      } else if (recipient.isDistributionList()) {
        jobManager.add(new PushDistributionListSendJob(messageId, recipient.getId(), true, Collections.emptySet()), messageDependsOnIds, recipient.getId().toQueueKey());
      } else {
        jobManager.add(IndividualSendJob.create(messageId, recipient, true, false), messageDependsOnIds, recipient.getId().toQueueKey());
      }
    }
  }

  /**
   * @return A result if the attachment was enqueued, or null if it failed to enqueue or shouldn't
   *         be enqueued (like in the case of a local self-send).
   */
  public static @Nullable PreUploadResult preUploadPushAttachment(@NonNull Context context, @NonNull Attachment attachment, @Nullable Recipient recipient, @NonNull Media media) {
    if (isLocalSelfSend(context, recipient, SendType.SIGNAL)) {
      return null;
    }
    Log.i(TAG, "Pre-uploading attachment for " + (recipient != null ? recipient.getId() : "null"));

    try {
      AttachmentTable    attachmentDatabase = SignalDatabase.attachments();
      DatabaseAttachment databaseAttachment = attachmentDatabase.insertAttachmentForPreUpload(attachment);

      Job compressionJob         = AttachmentCompressionJob.fromAttachment(databaseAttachment, false, -1);
      Job resumableUploadSpecJob = new ResumableUploadSpecJob();
      Job uploadJob              = new AttachmentUploadJob(databaseAttachment.getAttachmentId());

      ApplicationDependencies.getJobManager()
                             .startChain(compressionJob)
                             .then(resumableUploadSpecJob)
                             .then(uploadJob)
                             .enqueue();

      return new PreUploadResult(media, databaseAttachment.getAttachmentId(), Arrays.asList(compressionJob.getId(), resumableUploadSpecJob.getId(), uploadJob.getId()));
    } catch (MmsException e) {
      Log.w(TAG, "preUploadPushAttachment() - Failed to upload!", e);
      return null;
    }
  }

  public static void sendNewReaction(@NonNull Context context, @NonNull MessageId messageId, @NonNull String emoji) {
    ReactionRecord reaction = new ReactionRecord(emoji, Recipient.self().getId(), System.currentTimeMillis(), System.currentTimeMillis());
    SignalDatabase.reactions().addReaction(messageId, reaction);

    try {
      ApplicationDependencies.getJobManager().add(ReactionSendJob.create(context, messageId, reaction, false));
      onMessageSent();
    } catch (NoSuchMessageException e) {
      Log.w(TAG, "[sendNewReaction] Could not find message! Ignoring.");
    }
  }

  public static void sendReactionRemoval(@NonNull Context context, @NonNull MessageId messageId, @NonNull ReactionRecord reaction) {
    SignalDatabase.reactions().deleteReaction(messageId, reaction.getAuthor());

    try {
      ApplicationDependencies.getJobManager().add(ReactionSendJob.create(context, messageId, reaction, true));
      onMessageSent();
    } catch (NoSuchMessageException e) {
      Log.w(TAG, "[sendReactionRemoval] Could not find message! Ignoring.");
    }
  }

  public static void sendRemoteDelete(long messageId) {
    MessageTable db = SignalDatabase.messages();
    db.markAsRemoteDelete(messageId);
    db.markAsSending(messageId);

    try {
      RemoteDeleteSendJob.create(messageId).enqueue();
      onMessageSent();
    } catch (NoSuchMessageException e) {
      Log.w(TAG, "[sendRemoteDelete] Could not find message! Ignoring.");
    }
  }

  public static void resendGroupMessage(@NonNull Context context, @NonNull MessageRecord messageRecord, @NonNull Set<RecipientId> filterRecipientIds) {
    if (!messageRecord.isMms()) throw new AssertionError("Not Group");
    sendGroupPush(context, messageRecord.getRecipient(), messageRecord.getId(), filterRecipientIds, Collections.emptyList());
    onMessageSent();
  }

  public static void resendDistributionList(@NonNull Context context, @NonNull MessageRecord messageRecord, @NonNull Set<RecipientId> filterRecipientIds) {
    if (!messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getStoryType().isStory()) {
      throw new AssertionError("Not a story");
    }
    sendDistributionList(context, messageRecord.getRecipient(), messageRecord.getId(), filterRecipientIds, Collections.emptyList());
    onMessageSent();
  }

  public static void resend(Context context, MessageRecord messageRecord) {
    long       messageId   = messageRecord.getId();
    boolean    forceSms    = messageRecord.isForcedSms();
    Recipient  recipient   = messageRecord.getRecipient();

    SendType sendType;

    if (forceSms) {
      Recipient threadRecipient = SignalDatabase.threads().getRecipientForThreadId(messageRecord.getThreadId());

      if ((threadRecipient != null && threadRecipient.isGroup()) || SignalDatabase.attachments().getAttachmentsForMessage(messageId).size() > 0) {
        sendType = SendType.MMS;
      } else {
        sendType = SendType.SMS;
      }
    } else {
      sendType = SendType.SIGNAL;
    }

    sendMessageInternal(context, recipient, sendType, messageId, Collections.emptyList(), false);

    onMessageSent();
  }

  public static void onMessageSent() {
    EventBus.getDefault().postSticky(MessageSentEvent.INSTANCE);
  }

  private static @NonNull OutgoingMessage applyUniversalExpireTimerIfNecessary(@NonNull Context context, @NonNull Recipient recipient, @NonNull OutgoingMessage outgoingMessage, long threadId) {
    if (!outgoingMessage.isExpirationUpdate() && outgoingMessage.getExpiresIn() == 0 && RecipientUtil.setAndSendUniversalExpireTimerIfNecessary(context, recipient, threadId)) {
      return outgoingMessage.withExpiry(TimeUnit.SECONDS.toMillis(SignalStore.settings().getUniversalExpireTimer()));
    }
    return outgoingMessage;
  }

  private static void sendMessageInternal(Context context,
                                          Recipient recipient,
                                          SendType sendType,
                                          long messageId,
                                          @NonNull Collection<String> uploadJobIds,
                                          boolean isScheduledSend)
  {
    if (isLocalSelfSend(context, recipient, sendType) && !isScheduledSend) {
      sendLocalMediaSelf(context, messageId);
    } else if (recipient.isPushGroup()) {
      sendGroupPush(context, recipient, messageId, Collections.emptySet(), uploadJobIds);
    } else if (recipient.isDistributionList()) {
      sendDistributionList(context, recipient, messageId, Collections.emptySet(), uploadJobIds);
    } else if (sendType == SendType.SIGNAL && isPushMediaSend(context, recipient)) {
      sendMediaPush(context, recipient, messageId, uploadJobIds);
    } else if (sendType == SendType.MMS) {
      sendMms(context, messageId);
    } else {
      sendSms(recipient, messageId);
    }
  }

  private static void sendMediaPush(Context context, Recipient recipient, long messageId, @NonNull Collection<String> uploadJobIds) {
    JobManager jobManager = ApplicationDependencies.getJobManager();

    if (uploadJobIds.size() > 0) {
      Job mediaSend = IndividualSendJob.create(messageId, recipient, true, false);
      jobManager.add(mediaSend, uploadJobIds);
    } else {
      IndividualSendJob.enqueue(context, jobManager, messageId, recipient, false);
    }
  }

  private static void sendGroupPush(@NonNull Context context, @NonNull Recipient recipient, long messageId, @NonNull Set<RecipientId> filterRecipientIds, @NonNull Collection<String> uploadJobIds) {
    JobManager jobManager = ApplicationDependencies.getJobManager();

    if (uploadJobIds.size() > 0) {
      Job groupSend = new PushGroupSendJob(messageId, recipient.getId(), filterRecipientIds, !uploadJobIds.isEmpty(), false);
      jobManager.add(groupSend, uploadJobIds, uploadJobIds.isEmpty() ? null : recipient.getId().toQueueKey());
    } else {
      PushGroupSendJob.enqueue(context, jobManager, messageId, recipient.getId(), filterRecipientIds, false);
    }
  }

  private static void sendDistributionList(@NonNull Context context, @NonNull Recipient recipient, long messageId, @NonNull Set<RecipientId> filterRecipientIds, @NonNull Collection<String> uploadJobIds) {
    JobManager jobManager = ApplicationDependencies.getJobManager();

    if (uploadJobIds.size() > 0) {
      Job groupSend = new PushDistributionListSendJob(messageId, recipient.getId(), !uploadJobIds.isEmpty(), filterRecipientIds);
      jobManager.add(groupSend, uploadJobIds, uploadJobIds.isEmpty() ? null : recipient.getId().toQueueKey());
    } else {
      PushDistributionListSendJob.enqueue(context, jobManager, messageId, recipient.getId(), filterRecipientIds);
    }
  }

  private static void sendMms(Context context, long messageId) {
    JobManager jobManager = ApplicationDependencies.getJobManager();
    MmsSendJob.enqueue(context, jobManager, messageId);
  }

  private static void sendSms(Recipient recipient, long messageId) {
    JobManager jobManager = ApplicationDependencies.getJobManager();
    jobManager.add(new SmsSendJob(messageId, recipient));
  }

  private static boolean isPushMediaSend(Context context, Recipient recipient) {
    if (!SignalStore.account().isRegistered()) {
      return false;
    }

    if (recipient.isGroup()) {
      return false;
    }

    return isPushDestination(context, recipient);
  }

  private static boolean isPushDestination(Context context, Recipient destination) {
    if (destination.resolve().getRegistered() == RecipientTable.RegisteredState.REGISTERED) {
      return true;
    } else if (destination.resolve().getRegistered() == RecipientTable.RegisteredState.NOT_REGISTERED) {
      return false;
    } else {
      try {
        RecipientTable.RegisteredState state = ContactDiscovery.refresh(context, destination, false);
        return state == RecipientTable.RegisteredState.REGISTERED;
      } catch (IOException e1) {
        Log.w(TAG, e1);
        return false;
      }
    }
  }

  public static boolean isLocalSelfSend(@NonNull Context context, @Nullable Recipient recipient, SendType sendType) {
    return recipient != null                    &&
           recipient.isSelf()                   &&
           sendType == SendType.SIGNAL          &&
           SignalStore.account().isRegistered() &&
           !TextSecurePreferences.isMultiDevice(context);
  }

  private static void sendLocalMediaSelf(Context context, long messageId) {
    try {
      ExpiringMessageManager expirationManager = ApplicationDependencies.getExpiringMessageManager();
      MessageTable           mmsDatabase    = SignalDatabase.messages();
      OutgoingMessage        message        = mmsDatabase.getOutgoingMessage(messageId);
      SyncMessageId          syncId         = new SyncMessageId(Recipient.self().getId(), message.getSentTimeMillis());
      List<Attachment>       attachments        = new LinkedList<>();


      attachments.addAll(message.getAttachments());

      attachments.addAll(Stream.of(message.getLinkPreviews())
                               .map(LinkPreview::getThumbnail)
                               .filter(Optional::isPresent)
                               .map(Optional::get)
                               .toList());

      attachments.addAll(Stream.of(message.getSharedContacts())
                               .map(Contact::getAvatar).withoutNulls()
                               .map(Contact.Avatar::getAttachment).withoutNulls()
                               .toList());

      List<AttachmentCompressionJob> compressionJobs = Stream.of(attachments)
                                                             .map(a -> AttachmentCompressionJob.fromAttachment((DatabaseAttachment) a, false, -1))
                                                             .toList();

      List<AttachmentMarkUploadedJob> fakeUploadJobs = Stream.of(attachments)
                                                             .map(a -> new AttachmentMarkUploadedJob(messageId, ((DatabaseAttachment) a).getAttachmentId()))
                                                             .toList();

      ApplicationDependencies.getJobManager().startChain(compressionJobs)
                                             .then(fakeUploadJobs)
                                             .enqueue();

      mmsDatabase.markAsSent(messageId, true);
      mmsDatabase.markUnidentified(messageId, true);

      mmsDatabase.incrementDeliveryReceiptCount(syncId, System.currentTimeMillis());
      mmsDatabase.incrementReadReceiptCount(syncId, System.currentTimeMillis());
      mmsDatabase.incrementViewedReceiptCount(syncId, System.currentTimeMillis());

      if (message.getExpiresIn() > 0 && !message.isExpirationUpdate()) {
        mmsDatabase.markExpireStarted(messageId);
        expirationManager.scheduleDeletion(messageId, true, message.getExpiresIn());
      }
    } catch (NoSuchMessageException | MmsException e) {
      Log.w(TAG, "Failed to update self-sent message.", e);
    }
  }

  public static class PreUploadResult implements Parcelable {
    private final Media              media;
    private final AttachmentId       attachmentId;
    private final Collection<String> jobIds;

    PreUploadResult(@NonNull Media media, @NonNull AttachmentId attachmentId, @NonNull Collection<String> jobIds) {
      this.media        = media;
      this.attachmentId = attachmentId;
      this.jobIds       = jobIds;
    }

    private PreUploadResult(Parcel in) {
      this.attachmentId = in.readParcelable(AttachmentId.class.getClassLoader());
      this.jobIds       = ParcelUtil.readStringCollection(in);
      this.media        = in.readParcelable(Media.class.getClassLoader());
    }

    public @NonNull AttachmentId getAttachmentId() {
      return attachmentId;
    }

    public @NonNull Collection<String> getJobIds() {
      return jobIds;
    }

    public @NonNull Media getMedia() {
      return media;
    }

    public static final Creator<PreUploadResult> CREATOR = new Creator<PreUploadResult>() {
      @Override
      public PreUploadResult createFromParcel(Parcel in) {
        return new PreUploadResult(in);
      }

      @Override
      public PreUploadResult[] newArray(int size) {
        return new PreUploadResult[size];
      }
    };

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeParcelable(attachmentId, flags);
      ParcelUtil.writeStringCollection(dest, jobIds);
      dest.writeParcelable(media, flags);
    }

    @Override
    public @NonNull String toString() {
      return "{ID: " + attachmentId.getRowId() + ", URI: " + media.getUri() + ", Jobs: " + jobIds.stream().map(j -> "JOB::" + j).collect(Collectors.toList()) + "}";
    }
  }

  public enum MessageSentEvent {
    INSTANCE
  }

  public enum SendType {
    SIGNAL, SMS, MMS
  }
}
