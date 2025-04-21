package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.SetUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.DistributionListId;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.messages.GroupSendUtil;
import org.thoughtcrime.securesms.net.NotPushRegisteredException;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RemoteDeleteSendJob extends BaseJob {

  public static final String KEY = "RemoteDeleteSendJob";

  private static final String TAG = Log.tag(RemoteDeleteSendJob.class);

  private static final String KEY_MESSAGE_ID              = "message_id";
  private static final String KEY_RECIPIENTS              = "recipients";
  private static final String KEY_INITIAL_RECIPIENT_COUNT = "initial_recipient_count";

  private final long              messageId;
  private final List<RecipientId> recipients;
  private final int               initialRecipientCount;


  @WorkerThread
  public static @NonNull JobManager.Chain create(long messageId)
      throws NoSuchMessageException
  {
    MessageRecord message = SignalDatabase.messages().getMessageRecord(messageId);

    Recipient conversationRecipient = SignalDatabase.threads().getRecipientForThreadId(message.getThreadId());

    if (conversationRecipient == null) {
      throw new AssertionError("We have a message, but couldn't find the thread!");
    }

    List<RecipientId> recipients;
    if (conversationRecipient.isDistributionList()) {
      recipients = SignalDatabase.storySends().getRemoteDeleteRecipients(message.getId(), message.getTimestamp());
      if (recipients.isEmpty()) {
        return AppDependencies.getJobManager().startChain(MultiDeviceStorySendSyncJob.create(message.getDateSent(), messageId));
      }
    } else {
      recipients = conversationRecipient.isGroup() ? Stream.of(conversationRecipient.getParticipantIds()).toList()
                                                   : Stream.of(conversationRecipient.getId()).toList();
    }

    recipients.remove(Recipient.self().getId());

    RemoteDeleteSendJob sendJob = new RemoteDeleteSendJob(messageId,
                                                          recipients,
                                                          recipients.size(),
                                                          new Parameters.Builder()
                                                                        .setQueue(conversationRecipient.getId().toQueueKey())
                                                                        .setLifespan(TimeUnit.DAYS.toMillis(1))
                                                                        .setMaxAttempts(Parameters.UNLIMITED)
                                                                        .build());

    if (conversationRecipient.isDistributionList()) {
      return AppDependencies.getJobManager()
                            .startChain(sendJob)
                            .then(MultiDeviceStorySendSyncJob.create(message.getDateSent(), messageId));
    } else {
      return AppDependencies.getJobManager().startChain(sendJob);
    }
  }

  private RemoteDeleteSendJob(long messageId,
                              @NonNull List<RecipientId> recipients,
                              int initialRecipientCount,
                              @NonNull Parameters parameters)
  {
    super(parameters);

    this.messageId             = messageId;
    this.recipients            = recipients;
    this.initialRecipientCount = initialRecipientCount;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putLong(KEY_MESSAGE_ID, messageId)
                                    .putString(KEY_RECIPIENTS, RecipientId.toSerializedList(recipients))
                                    .putInt(KEY_INITIAL_RECIPIENT_COUNT, initialRecipientCount)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    if (!Recipient.self().isRegistered()) {
      throw new NotPushRegisteredException();
    }

    MessageTable  db      = SignalDatabase.messages();
    MessageRecord message = SignalDatabase.messages().getMessageRecord(messageId);

    long      targetSentTimestamp   = message.getDateSent();
    Recipient conversationRecipient = SignalDatabase.threads().getRecipientForThreadId(message.getThreadId());

    if (conversationRecipient == null) {
      throw new AssertionError("We have a message, but couldn't find the thread!");
    }

    if (!message.isOutgoing()) {
      throw new IllegalStateException("Cannot delete a message that isn't yours!");
    }

    if (!conversationRecipient.isRegistered() || conversationRecipient.isMmsGroup()) {
      Log.w(TAG, "Unable to remote delete non-push messages");
      return;
    }

    if (conversationRecipient.isPushV1Group()) {
      Log.w(TAG, "Unable to remote delete messages in GV1 groups");
      return;
    }

    List<Recipient>   possible = Stream.of(recipients).map(Recipient::resolved).toList();
    List<Recipient>   eligible = RecipientUtil.getEligibleForSending(Stream.of(recipients).map(Recipient::resolved).toList());
    List<RecipientId> skipped  = Stream.of(SetUtil.difference(possible, eligible)).map(Recipient::getId).toList();

    boolean            isForStory         = message.isMms() && (((MmsMessageRecord) message).getStoryType().isStory() || ((MmsMessageRecord) message).getParentStoryId() != null);
    DistributionListId distributionListId = isForStory ? message.getToRecipient().getDistributionListId().orElse(null) : null;

    GroupSendJobHelper.SendResult sendResult = deliver(conversationRecipient, eligible, targetSentTimestamp, isForStory, distributionListId);

    for (Recipient completion : sendResult.completed) {
      recipients.remove(completion.getId());
    }

    for (RecipientId unregistered : sendResult.unregistered) {
      SignalDatabase.recipients().markUnregistered(unregistered);
    }

    for (RecipientId skip : skipped) {
      recipients.remove(skip);
    }

    List<RecipientId> totalSkips = Util.join(skipped, sendResult.skipped);

    Log.i(TAG, "Completed now: " + sendResult.completed.size() + ", Skipped: " + totalSkips.size() + ", Remaining: " + recipients.size());

    if (totalSkips.size() > 0 && message.getToRecipient().isGroup()) {
      SignalDatabase.groupReceipts().setSkipped(totalSkips, messageId);
    }

    if (recipients.isEmpty()) {
      db.markAsSent(messageId, true);
    } else {
      Log.w(TAG, "Still need to send to " + recipients.size() + " recipients. Retrying.");
      throw new RetryLaterException();
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof ServerRejectedException) return false;
    if (e instanceof NotPushRegisteredException) return false;
    return e instanceof IOException ||
           e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to send remote delete to all recipients! (" + (initialRecipientCount - recipients.size() + "/" + initialRecipientCount + ")") );
  }

  private @NonNull GroupSendJobHelper.SendResult deliver(@NonNull Recipient conversationRecipient,
                                                         @NonNull List<Recipient> destinations,
                                                         long targetSentTimestamp,
                                                         boolean isForStory,
                                                         @Nullable DistributionListId distributionListId)
      throws IOException, UntrustedIdentityException
  {
    SignalServiceDataMessage.Builder dataMessageBuilder = SignalServiceDataMessage.newBuilder()
                                                                                  .withTimestamp(System.currentTimeMillis())
                                                                                  .withRemoteDelete(new SignalServiceDataMessage.RemoteDelete(targetSentTimestamp));

    if (conversationRecipient.isGroup()) {
      GroupUtil.setDataMessageGroupContext(context, dataMessageBuilder, conversationRecipient.requireGroupId().requirePush());
    }

    SignalServiceDataMessage dataMessage = dataMessageBuilder.build();
    List<SendMessageResult>  results     = GroupSendUtil.sendResendableDataMessage(context,
                                                                                   conversationRecipient.getGroupId().map(GroupId::requireV2).orElse(null),
                                                                                   distributionListId,
                                                                                   destinations,
                                                                                   false,
                                                                                   ContentHint.RESENDABLE,
                                                                                   new MessageId(messageId),
                                                                                   dataMessage,
                                                                                   true,
                                                                                   isForStory,
                                                                                   null);

    if (conversationRecipient.isSelf()) {
      AppDependencies.getSignalServiceMessageSender().sendSyncMessage(dataMessage);
    }

    return GroupSendJobHelper.getCompletedSends(destinations, results);
  }

  public static class Factory implements Job.Factory<RemoteDeleteSendJob> {

    @Override
    public @NonNull RemoteDeleteSendJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      long              messageId             = data.getLong(KEY_MESSAGE_ID);
      List<RecipientId> recipients            = RecipientId.fromSerializedList(data.getString(KEY_RECIPIENTS));
      int               initialRecipientCount = data.getInt(KEY_INITIAL_RECIPIENT_COUNT);

      return new RemoteDeleteSendJob(messageId,  recipients, initialRecipientCount, parameters);
    }
  }
}