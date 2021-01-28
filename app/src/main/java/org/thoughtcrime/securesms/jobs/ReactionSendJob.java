package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

  private final long              messageId;
  private final boolean           isMms;
  private final List<RecipientId> recipients;
  private final int               initialRecipientCount;
  private final ReactionRecord    reaction;
  private final boolean           remove;


  @WorkerThread
  public static @NonNull ReactionSendJob create(@NonNull Context context,
                                                long messageId,
                                                boolean isMms,
                                                @NonNull ReactionRecord reaction,
                                                boolean remove)
      throws NoSuchMessageException
  {
    MessageRecord message = isMms ? DatabaseFactory.getMmsDatabase(context).getMessageRecord(messageId)
                                  : DatabaseFactory.getSmsDatabase(context).getSmsMessage(messageId);

    Recipient conversationRecipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(message.getThreadId());

    if (conversationRecipient == null) {
      throw new AssertionError("We have a message, but couldn't find the thread!");
    }

    List<RecipientId> recipients = conversationRecipient.isGroup() ? Stream.of(RecipientUtil.getEligibleForSending(conversationRecipient.getParticipants())).map(Recipient::getId).toList()
                                                                   : Stream.of(conversationRecipient.getId()).toList();

    recipients.remove(Recipient.self().getId());

    return new ReactionSendJob(messageId,
                               isMms,
                               recipients,
                               recipients.size(),
                               reaction,
                               remove,
                               new Parameters.Builder()
                                             .setQueue(conversationRecipient.getId().toQueueKey())
                                             .setLifespan(TimeUnit.DAYS.toMillis(1))
                                             .setMaxAttempts(Parameters.UNLIMITED)
                                             .build());
  }

  private ReactionSendJob(long messageId,
                          boolean isMms,
                          @NonNull List<RecipientId> recipients,
                          int initialRecipientCount,
                          @NonNull ReactionRecord reaction,
                          boolean remove,
                          @NonNull Parameters parameters)
  {
    super(parameters);

    this.messageId             = messageId;
    this.isMms                 = isMms;
    this.recipients            = recipients;
    this.initialRecipientCount = initialRecipientCount;
    this.reaction              = reaction;
    this.remove                = remove;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId)
                             .putBoolean(KEY_IS_MMS, isMms)
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
    MessageDatabase db;
    MessageRecord     message;

    if (isMms) {
      db      = DatabaseFactory.getMmsDatabase(context);
      message = DatabaseFactory.getMmsDatabase(context).getMessageRecord(messageId);
    } else {
      db      = DatabaseFactory.getSmsDatabase(context);
      message = DatabaseFactory.getSmsDatabase(context).getSmsMessage(messageId);
    }

    Recipient targetAuthor        = message.isOutgoing() ? Recipient.self() : message.getIndividualRecipient();
    long      targetSentTimestamp = message.getDateSent();

    if (!remove && !db.hasReaction(messageId, reaction)) {
      Log.w(TAG, "Went to add a reaction, but it's no longer present on the message!");
      return;
    }

    if (remove && db.hasReaction(messageId, reaction)) {
      Log.w(TAG, "Went to remove a reaction, but it's still there!");
      return;
    }

    Recipient conversationRecipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(message.getThreadId());

    if (conversationRecipient == null) {
      throw new AssertionError("We have a message, but couldn't find the thread!");
    }

    List<Recipient> destinations = Stream.of(recipients).map(Recipient::resolved).toList();
    List<Recipient> completions  = deliver(conversationRecipient, destinations, targetAuthor, targetSentTimestamp);

    for (Recipient completion : completions) {
      recipients.remove(completion.getId());
    }

    Log.i(TAG, "Completed now: " + completions.size() + ", Remaining: " + recipients.size());

    if (!recipients.isEmpty()) {
      Log.w(TAG, "Still need to send to " + recipients.size() + " recipients. Retrying.");
      throw new RetryLaterException();
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof ServerRejectedException) return false;
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

    MessageDatabase db = isMms ? DatabaseFactory.getMmsDatabase(context) : DatabaseFactory.getSmsDatabase(context);

    if (remove && !db.hasReaction(messageId, reaction)) {
      Log.w(TAG, "Reaction removal failed, so adding the reaction back.");
      db.addReaction(messageId, reaction);
    } else if (!remove && db.hasReaction(messageId, reaction)){
      Log.w(TAG, "Reaction addition failed, so removing the reaction.");
      db.deleteReaction(messageId, reaction.getAuthor());
    } else {
      Log.w(TAG, "Reaction state didn't match what we'd expect to revert it, so we're just leaving it alone.");
    }
  }

  private @NonNull List<Recipient> deliver(@NonNull Recipient conversationRecipient, @NonNull List<Recipient> destinations, @NonNull Recipient targetAuthor, long targetSentTimestamp)
      throws IOException, UntrustedIdentityException
  {
    SignalServiceMessageSender             messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
    List<SignalServiceAddress>             addresses          = RecipientUtil.toSignalServiceAddressesFromResolved(context, destinations);
    List<Optional<UnidentifiedAccessPair>> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, destinations);;
    SignalServiceDataMessage.Builder       dataMessage        = SignalServiceDataMessage.newBuilder()
                                                                                        .withTimestamp(System.currentTimeMillis())
                                                                                        .withReaction(buildReaction(context, reaction, remove, targetAuthor, targetSentTimestamp));

    if (conversationRecipient.isGroup()) {
      GroupUtil.setDataMessageGroupContext(context, dataMessage, conversationRecipient.requireGroupId().requirePush());
    }

    List<SendMessageResult> results = messageSender.sendMessage(addresses, unidentifiedAccess, false, dataMessage.build());

    return GroupSendJobHelper.getCompletedSends(context, results);
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
                                                 RecipientUtil.toSignalServiceAddress(context, targetAuthor),
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

      return new ReactionSendJob(messageId, isMms, recipients, initialRecipientCount, reaction, remove, parameters);
    }
  }
}
