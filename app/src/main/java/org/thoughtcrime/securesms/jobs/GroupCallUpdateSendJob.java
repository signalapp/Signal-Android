package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
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

/**
 * Send a group call update message to every one in a V2 group. Used to indicate you
 * have joined or left a call.
 */
public class GroupCallUpdateSendJob extends BaseJob {

  public static final String KEY = "GroupCallUpdateSendJob";

  private static final String TAG = Log.tag(GroupCallUpdateSendJob.class);

  private static final String KEY_RECIPIENT_ID            = "recipient_id";
  private static final String KEY_ERA_ID                  = "era_id";
  private static final String KEY_RECIPIENTS              = "recipients";
  private static final String KEY_INITIAL_RECIPIENT_COUNT = "initial_recipient_count";

  private final RecipientId       recipientId;
  private final String            eraId;
  private final List<RecipientId> recipients;
  private final int               initialRecipientCount;

  @WorkerThread
  public static @NonNull GroupCallUpdateSendJob create(@NonNull RecipientId recipientId, @Nullable String eraId) {
    Recipient conversationRecipient = Recipient.resolved(recipientId);

    if (!conversationRecipient.isPushV2Group()) {
      throw new AssertionError("We have a recipient, but it's not a V2 Group");
    }

    List<RecipientId> recipients = Stream.of(RecipientUtil.getEligibleForSending(conversationRecipient.getParticipants()))
                                         .filterNot(Recipient::isSelf)
                                         .filterNot(Recipient::isBlocked)
                                         .map(Recipient::getId)
                                         .toList();

    return new GroupCallUpdateSendJob(recipientId,
                                      eraId,
                                      recipients,
                                      recipients.size(),
                                      new Parameters.Builder()
                                                    .setQueue(conversationRecipient.getId().toQueueKey())
                                                    .setLifespan(TimeUnit.MINUTES.toMillis(5))
                                                    .setMaxAttempts(3)
                                                    .build());
  }

  private GroupCallUpdateSendJob(@NonNull RecipientId recipientId,
                                 @NonNull String eraId,
                                 @NonNull List<RecipientId> recipients,
                                 int initialRecipientCount,
                                 @NonNull Parameters parameters)
  {
    super(parameters);

    this.recipientId           = recipientId;
    this.eraId                 = eraId;
    this.recipients            = recipients;
    this.initialRecipientCount = initialRecipientCount;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_RECIPIENT_ID, recipientId.serialize())
                             .putString(KEY_ERA_ID, eraId)
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
    Recipient conversationRecipient = Recipient.resolved(recipientId);

    if (!conversationRecipient.isPushV2Group()) {
      throw new AssertionError("We have a recipient, but it's not a V2 Group");
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
    if (e instanceof ServerRejectedException) return false;
    return e instanceof IOException ||
           e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
    if (recipients.size() < initialRecipientCount) {
      Log.w(TAG, "Only sent a group update to " + recipients.size() + "/" + initialRecipientCount + " recipients. Still, it sent to someone, so it stays.");
      return;
    }

    Log.w(TAG, "Failed to send the group update to all recipients!");
  }

  private @NonNull List<Recipient> deliver(@NonNull Recipient conversationRecipient, @NonNull List<Recipient> destinations)
      throws IOException, UntrustedIdentityException
  {
    SignalServiceMessageSender             messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
    List<SignalServiceAddress>             addresses          = RecipientUtil.toSignalServiceAddressesFromResolved(context, destinations);
    List<Optional<UnidentifiedAccessPair>> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, destinations);;
    SignalServiceDataMessage.Builder       dataMessage        = SignalServiceDataMessage.newBuilder()
                                                                                        .withTimestamp(System.currentTimeMillis())
                                                                                        .withGroupCallUpdate(new SignalServiceDataMessage.GroupCallUpdate(eraId));

    if (conversationRecipient.isGroup()) {
      GroupUtil.setDataMessageGroupContext(context, dataMessage, conversationRecipient.requireGroupId().requirePush());
    }

    List<SendMessageResult> results = messageSender.sendMessage(addresses, unidentifiedAccess, false, dataMessage.build());

    return GroupSendJobHelper.getCompletedSends(context, results);
  }

  public static class Factory implements Job.Factory<GroupCallUpdateSendJob> {

    @Override
    public @NonNull
    GroupCallUpdateSendJob create(@NonNull Parameters parameters, @NonNull Data data) {
      RecipientId       recipientId           = RecipientId.from(data.getString(KEY_RECIPIENT_ID));
      String            eraId                 = data.getString(KEY_ERA_ID);
      List<RecipientId> recipients            = RecipientId.fromSerializedList(data.getString(KEY_RECIPIENTS));
      int               initialRecipientCount = data.getInt(KEY_INITIAL_RECIPIENT_COUNT);

      return new GroupCallUpdateSendJob(recipientId, eraId, recipients, initialRecipientCount, parameters);
    }
  }
}
