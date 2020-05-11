package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ProfileKeySendJob extends BaseJob {

  private static final String TAG            = Log.tag(ProfileKeySendJob.class);
  private static final String KEY_RECIPIENTS = "recipients";
  private static final String KEY_THREAD     = "thread";

  public static final String KEY = "ProfileKeySendJob";

  private final long              threadId;
  private final List<RecipientId> recipients;

  @WorkerThread
  public static ProfileKeySendJob create(@NonNull Context context, long threadId) {
    Recipient conversationRecipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId);

    if (conversationRecipient == null) {
      throw new AssertionError("We have a thread but no recipient!");
    }

    List<RecipientId> recipients = conversationRecipient.isGroup() ? Stream.of(conversationRecipient.getParticipants()).map(Recipient::getId).toList()
                                                                   : Stream.of(conversationRecipient.getId()).toList();

    recipients.remove(Recipient.self().getId());

    return new ProfileKeySendJob(new Parameters.Builder()
                                               .setQueue(conversationRecipient.getId().toQueueKey())
                                               .setLifespan(TimeUnit.DAYS.toMillis(1))
                                               .setMaxAttempts(Parameters.UNLIMITED)
                                               .build(), threadId, recipients);
  }

  private ProfileKeySendJob(@NonNull Parameters parameters, long threadId, @NonNull List<RecipientId> recipients) {
    super(parameters);
    this.threadId   = threadId;
    this.recipients = recipients;
  }

  @Override
  protected void onRun() throws Exception {
    Recipient conversationRecipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId);

    if (conversationRecipient == null) {
      Log.w(TAG, "Thread no longer present");
      return;
    }

    List<Recipient> destinations = Stream.of(recipients).map(Recipient::resolved).toList();
    List<Recipient> completions  = deliver(conversationRecipient, destinations);

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
    return e instanceof IOException ||
           e instanceof RetryLaterException;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder()
                   .putLong(KEY_THREAD, threadId)
                   .putString(KEY_RECIPIENTS, RecipientId.toSerializedList(recipients))
                   .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {

  }

  private List<Recipient> deliver(@NonNull Recipient conversationRecipient, @NonNull List<Recipient> destinations) throws IOException, UntrustedIdentityException {
    SignalServiceMessageSender             messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
    List<SignalServiceAddress>             addresses          = Stream.of(destinations).map(t -> RecipientUtil.toSignalServiceAddress(context, t)).toList();
    List<Optional<UnidentifiedAccessPair>> unidentifiedAccess = Stream.of(destinations).map(recipient -> UnidentifiedAccessUtil.getAccessFor(context, recipient)).toList();
    SignalServiceDataMessage.Builder       dataMessage        = SignalServiceDataMessage.newBuilder()
                                                                                        .withTimestamp(System.currentTimeMillis())
                                                                                        .withProfileKey(Recipient.self().resolve().getProfileKey());

    if (conversationRecipient.isGroup()) {
      dataMessage.asGroupMessage(new SignalServiceGroup(conversationRecipient.requireGroupId().getDecodedId()));
    }

    List<SendMessageResult> results = messageSender.sendMessage(addresses, unidentifiedAccess, false, dataMessage.build());

    Stream.of(results)
          .filter(r -> r.getIdentityFailure() != null)
          .map(SendMessageResult::getAddress)
          .map(a -> Recipient.externalPush(context, a))
          .forEach(r -> Log.w(TAG, "Identity failure for " + r.getId()));

    Stream.of(results)
          .filter(SendMessageResult::isUnregisteredFailure)
          .map(SendMessageResult::getAddress)
          .map(a -> Recipient.externalPush(context, a))
          .forEach(r -> Log.w(TAG, "Unregistered failure for " + r.getId()));


    return Stream.of(results)
                 .filter(r -> r.getSuccess() != null || r.getIdentityFailure() != null || r.isUnregisteredFailure())
                 .map(SendMessageResult::getAddress)
                 .map(a -> Recipient.externalPush(context, a))
                 .toList();
  }

  public static class Factory implements Job.Factory<ProfileKeySendJob> {

    @Override
    public @NonNull ProfileKeySendJob create(@NonNull Parameters parameters, @NonNull Data data) {
      long              threadId   = data.getLong(KEY_THREAD);
      List<RecipientId> recipients = RecipientId.fromSerializedList(data.getString(KEY_RECIPIENTS));

      return new ProfileKeySendJob(parameters, threadId, recipients);
    }
  }
}
