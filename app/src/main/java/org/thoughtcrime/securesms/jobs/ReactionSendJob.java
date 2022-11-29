package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.ReactionTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.messages.GroupSendUtil;
import org.thoughtcrime.securesms.net.NotPushRegisteredException;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ReactionSendJob extends BaseJob {

  public static final String KEY = "ReactionSendJob";

  private static final String TAG = Log.tag(ReactionSendJob.class);

  private static final String KEY_MESSAGE_ID              = "message_id";
  private static final String KEY_IS_MMS                  = "is_mms";
  private static final String KEY_REACTION_EMOJI          = "reaction_emoji";
  private static final String KEY_REACTION_AUTHOR         = "reaction_author";
  private static final String KEY_REACTION_DATE_SENT      = "reaction_date_sent";
  private static final String KEY_REACTION_DATE_RECEIVED  = "reaction_date_received";
  private static final String KEY_REMOVE                  = "remove";
  private static final String KEY_RECIPIENTS              = "recipients";
  private static final String KEY_INITIAL_RECIPIENT_COUNT = "initial_recipient_count";

  private final MessageId         messageId;
  private final List<RecipientId> recipients;
  private final int               initialRecipientCount;
  private final ReactionRecord    reaction;
  private final boolean           remove;


  @WorkerThread
  public static @NonNull ReactionSendJob create(@NonNull Context context,
                                                @NonNull MessageId messageId,
                                                @NonNull ReactionRecord reaction,
                                                boolean remove)
      throws NoSuchMessageException
  {
    MessageRecord message = messageId.isMms() ? SignalDatabase.mms().getMessageRecord(messageId.getId())
                                              : SignalDatabase.sms().getSmsMessage(messageId.getId());

    Recipient conversationRecipient = SignalDatabase.threads().getRecipientForThreadId(message.getThreadId());

    if (conversationRecipient == null) {
      throw new AssertionError("We have a message, but couldn't find the thread!");
    }

    RecipientId       selfId     = Recipient.self().getId();
    List<RecipientId> recipients = conversationRecipient.isGroup() ? RecipientUtil.getEligibleForSending(Recipient.resolvedList(conversationRecipient.getParticipantIds()))
                                                                                  .stream()
                                                                                  .map(Recipient::getId)
                                                                                  .filter(r -> !r.equals(selfId))
                                                                                  .collect(Collectors.toList())
                                                                   : Collections.singletonList(conversationRecipient.getId());

    return new ReactionSendJob(messageId,
                               recipients,
                               recipients.size(),
                               reaction,
                               remove,
                               new Parameters.Builder()
                                             .setQueue(conversationRecipient.getId().toQueueKey())
                                             .addConstraint(NetworkConstraint.KEY)
                                             .setLifespan(TimeUnit.DAYS.toMillis(1))
                                             .setMaxAttempts(Parameters.UNLIMITED)
                                             .build());
  }

  private ReactionSendJob(@NonNull MessageId messageId,
                          @NonNull List<RecipientId> recipients,
                          int initialRecipientCount,
                          @NonNull ReactionRecord reaction,
                          boolean remove,
                          @NonNull Parameters parameters)
  {
    super(parameters);

    this.messageId             = messageId;
    this.recipients            = recipients;
    this.initialRecipientCount = initialRecipientCount;
    this.reaction              = reaction;
    this.remove                = remove;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId.getId())
                             .putBoolean(KEY_IS_MMS, messageId.isMms())
                             .putString(KEY_REACTION_EMOJI, reaction.getEmoji())
                             .putString(KEY_REACTION_AUTHOR, reaction.getAuthor().serialize())
                             .putLong(KEY_REACTION_DATE_SENT, reaction.getDateSent())
                             .putLong(KEY_REACTION_DATE_RECEIVED, reaction.getDateReceived())
                             .putBoolean(KEY_REMOVE, remove)
                             .putString(KEY_RECIPIENTS, RecipientId.toSerializedList(recipients))
                             .putInt(KEY_INITIAL_RECIPIENT_COUNT, initialRecipientCount)
                             .build();
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

    ReactionTable reactionTable = SignalDatabase.reactions();

    MessageRecord  message;

    if (messageId.isMms()) {
      message = SignalDatabase.mms().getMessageRecord(messageId.getId());
    } else {
      message = SignalDatabase.sms().getSmsMessage(messageId.getId());
    }

    Recipient targetAuthor        = message.isOutgoing() ? Recipient.self() : message.getIndividualRecipient();
    long      targetSentTimestamp = message.getDateSent();

    if (targetAuthor.getId().equals(SignalStore.releaseChannelValues().getReleaseChannelRecipientId())) {
      return;
    }

    if (!remove && !reactionTable.hasReaction(messageId, reaction)) {
      Log.w(TAG, "Went to add a reaction, but it's no longer present on the message!");
      return;
    }

    if (remove && reactionTable.hasReaction(messageId, reaction)) {
      Log.w(TAG, "Went to remove a reaction, but it's still there!");
      return;
    }

    Recipient conversationRecipient = SignalDatabase.threads().getRecipientForThreadId(message.getThreadId());

    if (conversationRecipient == null) {
      throw new AssertionError("We have a message, but couldn't find the thread!");
    }

    if (conversationRecipient.isPushV1Group() || conversationRecipient.isMmsGroup()) {
      Log.w(TAG, "Cannot send reactions to legacy groups.");
      return;
    }

    List<Recipient>   resolved     = recipients.stream().map(Recipient::resolved).collect(Collectors.toList());
    List<RecipientId> unregistered = resolved.stream().filter(Recipient::isUnregistered).map(Recipient::getId).collect(Collectors.toList());
    List<Recipient>   destinations = resolved.stream().filter(Recipient::isMaybeRegistered).collect(Collectors.toList());
    List<Recipient>   completions  = deliver(conversationRecipient, destinations, targetAuthor, targetSentTimestamp);

    recipients.removeAll(unregistered);
    recipients.removeAll(completions.stream().map(Recipient::getId).collect(Collectors.toList()));

    Log.i(TAG, "Completed now: " + completions.size() + ", Remaining: " + recipients.size());

    if (!recipients.isEmpty()) {
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
    if (recipients.size() < initialRecipientCount) {
      Log.w(TAG, "Only sent a reaction to " + recipients.size() + "/" + initialRecipientCount + " recipients. Still, it sent to someone, so it stays.");
      return;
    }

    Log.w(TAG, "Failed to send the reaction to all recipients!");

    ReactionTable reactionTable = SignalDatabase.reactions();

    if (remove && !reactionTable.hasReaction(messageId, reaction)) {
      Log.w(TAG, "Reaction removal failed, so adding the reaction back.");
      reactionTable.addReaction(messageId, reaction);
    } else if (!remove && reactionTable.hasReaction(messageId, reaction)){
      Log.w(TAG, "Reaction addition failed, so removing the reaction.");
      reactionTable.deleteReaction(messageId, reaction.getAuthor());
    } else {
      Log.w(TAG, "Reaction state didn't match what we'd expect to revert it, so we're just leaving it alone.");
    }
  }

  private @NonNull List<Recipient> deliver(@NonNull Recipient conversationRecipient, @NonNull List<Recipient> destinations, @NonNull Recipient targetAuthor, long targetSentTimestamp)
      throws IOException, UntrustedIdentityException
  {
    SignalServiceDataMessage.Builder dataMessageBuilder = SignalServiceDataMessage.newBuilder()
                                                                                  .withTimestamp(System.currentTimeMillis())
                                                                                  .withReaction(buildReaction(context, reaction, remove, targetAuthor, targetSentTimestamp));

    if (conversationRecipient.isGroup()) {
      GroupUtil.setDataMessageGroupContext(context, dataMessageBuilder, conversationRecipient.requireGroupId().requirePush());
    }

    SignalServiceDataMessage dataMessage         = dataMessageBuilder.build();
    List<Recipient>          nonSelfDestinations = destinations.stream().filter(r -> !r.isSelf()).collect(Collectors.toList());
    boolean                  includesSelf        = nonSelfDestinations.size() != destinations.size();
    List<SendMessageResult>  results             = GroupSendUtil.sendResendableDataMessage(context,
                                                                                           conversationRecipient.getGroupId().map(GroupId::requireV2).orElse(null),
                                                                                           null,
                                                                                           nonSelfDestinations,
                                                                                           false,
                                                                                           ContentHint.RESENDABLE,
                                                                                           messageId,
                                                                                           dataMessage,
                                                                                           true,
                                                                                           false);

    if (includesSelf) {
      results.add(ApplicationDependencies.getSignalServiceMessageSender().sendSyncMessage(dataMessage));
    }

    return GroupSendJobHelper.getCompletedSends(destinations, results).completed;
  }

  private static SignalServiceDataMessage.Reaction buildReaction(@NonNull Context context,
                                                                 @NonNull ReactionRecord reaction,
                                                                 boolean remove,
                                                                 @NonNull Recipient targetAuthor,
                                                                 long targetSentTimestamp)
      throws IOException
  {
    return new SignalServiceDataMessage.Reaction(reaction.getEmoji(),
                                                 remove,
                                                 RecipientUtil.getOrFetchServiceId(context, targetAuthor),
                                                 targetSentTimestamp);
  }

  public static class Factory implements Job.Factory<ReactionSendJob> {

    @Override
    public @NonNull
    ReactionSendJob create(@NonNull Parameters parameters, @NonNull Data data) {
      long              messageId             = data.getLong(KEY_MESSAGE_ID);
      boolean           isMms                 = data.getBoolean(KEY_IS_MMS);
      List<RecipientId> recipients            = RecipientId.fromSerializedList(data.getString(KEY_RECIPIENTS));
      int               initialRecipientCount = data.getInt(KEY_INITIAL_RECIPIENT_COUNT);
      ReactionRecord    reaction              = new ReactionRecord(data.getString(KEY_REACTION_EMOJI),
                                                                   RecipientId.from(data.getString(KEY_REACTION_AUTHOR)),
                                                                   data.getLong(KEY_REACTION_DATE_SENT),
                                                                   data.getLong(KEY_REACTION_DATE_RECEIVED));
      boolean           remove                = data.getBoolean(KEY_REMOVE);

      return new ReactionSendJob(new MessageId(messageId, isMms), recipients, initialRecipientCount, reaction, remove, parameters);
    }
  }
}
