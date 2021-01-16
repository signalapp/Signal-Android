package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.SessionUtil;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.DecryptionsDrainedConstraint;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;

/**
 * - Archives the session associated with the specified device
 * - Inserts an error message in the conversation
 * - Sends a new, empty message to trigger a fresh session with the specified device
 *
 * This will only be run when all decryptions have finished, and there can only be one enqueued
 * per websocket drain cycle.
 */
public class AutomaticSessionResetJob extends BaseJob {

  private static final String TAG = Log.tag(AutomaticSessionResetJob.class);

  public static final String KEY = "AutomaticSessionResetJob";

  private static final String KEY_RECIPIENT_ID   =  "recipient_id";
  private static final String KEY_DEVICE_ID      =  "device_id";
  private static final String KEY_SENT_TIMESTAMP =  "sent_timestamp";

  private final RecipientId recipientId;
  private final int         deviceId;
  private final long        sentTimestamp;

  public AutomaticSessionResetJob(@NonNull RecipientId recipientId, int deviceId, long sentTimestamp) {
    this(new Parameters.Builder()
                       .setQueue(PushProcessMessageJob.getQueueName(recipientId))
                       .addConstraint(DecryptionsDrainedConstraint.KEY)
                       .setMaxInstancesForQueue(1)
                       .build(),
         recipientId,
         deviceId,
         sentTimestamp);
  }

  private AutomaticSessionResetJob(@NonNull Parameters parameters,
                                   @NonNull RecipientId recipientId,
                                   int deviceId,
                                   long sentTimestamp)
  {
    super(parameters);
    this.recipientId   = recipientId;
    this.deviceId      = deviceId;
    this.sentTimestamp = sentTimestamp;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_RECIPIENT_ID, recipientId.serialize())
                             .putInt(KEY_DEVICE_ID, deviceId)
                             .putLong(KEY_SENT_TIMESTAMP, sentTimestamp)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    if (FeatureFlags.automaticSessionReset()) {
      SessionUtil.archiveSession(context, recipientId, deviceId);
      insertLocalMessage();
      sendNullMessage();
    } else {
      insertLocalMessage();
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
  }

  private void insertLocalMessage() {
    MessageDatabase.InsertResult result = DatabaseFactory.getSmsDatabase(context).insertDecryptionFailedMessage(recipientId, deviceId, sentTimestamp);
    ApplicationDependencies.getMessageNotifier().updateNotification(context, result.getThreadId());
  }

  private void sendNullMessage() throws IOException {
    Recipient                        recipient          = Recipient.resolved(recipientId);
    SignalServiceMessageSender       messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
    SignalServiceAddress             address            = RecipientUtil.toSignalServiceAddress(context, recipient);
    Optional<UnidentifiedAccessPair> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient);

    try {
      messageSender.sendNullMessage(address, unidentifiedAccess);
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, "Unable to send null message.");
    }
  }

  public static final class Factory implements Job.Factory<AutomaticSessionResetJob> {
    @Override
    public @NonNull AutomaticSessionResetJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new AutomaticSessionResetJob(parameters,
                                          RecipientId.from(data.getString(KEY_RECIPIENT_ID)),
                                          data.getInt(KEY_DEVICE_ID),
                                          data.getLong(KEY_SENT_TIMESTAMP));
    }
  }
}
