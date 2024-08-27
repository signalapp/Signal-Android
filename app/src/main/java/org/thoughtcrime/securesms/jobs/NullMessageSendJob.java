package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.util.concurrent.TimeUnit;

/**
 * Just sends an empty message to a target recipient. Only suitable for individuals, NOT groups.
 */
public class NullMessageSendJob extends BaseJob {

  public static final String KEY = "NullMessageSendJob";

  private static final String TAG = Log.tag(NullMessageSendJob.class);

  private final RecipientId recipientId;

  private static final String KEY_RECIPIENT_ID = "recipient_id";

  public NullMessageSendJob(@NonNull RecipientId recipientId) {
    this(recipientId,
         new Parameters.Builder()
                       .setQueue(recipientId.toQueueKey())
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build());
  }

  private NullMessageSendJob(@NonNull RecipientId recipientId, @NonNull Parameters parameters) {
    super(parameters);
    this.recipientId = recipientId;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_RECIPIENT_ID, recipientId.serialize()).serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    Recipient recipient = Recipient.resolved(recipientId);

    if (recipient.isGroup()) {
      Log.w(TAG, "Groups are not supported!");
      return;
    }

    if (recipient.isUnregistered()) {
      Log.w(TAG, recipient.getId() + " not registered!");
    }

    SignalServiceMessageSender messageSender = AppDependencies.getSignalServiceMessageSender();
    SignalServiceAddress       address       = RecipientUtil.toSignalServiceAddress(context, recipient);

    try {
      messageSender.sendNullMessage(address, SealedSenderAccessUtil.getSealedSenderAccessFor(recipient));
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, "Unable to send null message.");
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<NullMessageSendJob> {

    @Override
    public @NonNull NullMessageSendJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new NullMessageSendJob(RecipientId.from(data.getString(KEY_RECIPIENT_ID)),
                                    parameters);
    }
  }
}
