package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.databaseprotos.DeviceLastResetTime;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.DecryptionsDrainedConstraint;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
    ApplicationDependencies.getProtocolStore().aci().sessions().archiveSession(recipientId, deviceId);
    SignalDatabase.senderKeyShared().deleteAllFor(recipientId);
    insertLocalMessage();

    if (FeatureFlags.automaticSessionReset()) {
      long                resetInterval      = TimeUnit.SECONDS.toMillis(FeatureFlags.automaticSessionResetIntervalSeconds());
      DeviceLastResetTime resetTimes         = SignalDatabase.recipients().getLastSessionResetTimes(recipientId);
      long                timeSinceLastReset = System.currentTimeMillis() - getLastResetTime(resetTimes, deviceId);

      Log.i(TAG, "DeviceId: " + deviceId + ", Reset interval: " + resetInterval + ", Time since last reset: " + timeSinceLastReset, true);

      if (timeSinceLastReset > resetInterval) {
        Log.i(TAG, "We're good! Sending a null message.", true);

        SignalDatabase.recipients().setLastSessionResetTime(recipientId, setLastResetTime(resetTimes, deviceId, System.currentTimeMillis()));
        Log.i(TAG, "Marked last reset time: " + System.currentTimeMillis(), true);

        sendNullMessage();
        Log.i(TAG, "Successfully sent!", true);
      } else {
        Log.w(TAG, "Too soon! Time since last reset: " + timeSinceLastReset, true);
      }
    } else {
      Log.w(TAG, "Automatic session reset send disabled!", true);
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public void onFailure() {
  }

  private void insertLocalMessage() {
    MessageTable.InsertResult result = SignalDatabase.sms().insertChatSessionRefreshedMessage(recipientId, deviceId, sentTimestamp);
    ApplicationDependencies.getMessageNotifier().updateNotification(context, ConversationId.forConversation(result.getThreadId()));
  }

  private void sendNullMessage() throws IOException {
    Recipient recipient = Recipient.resolved(recipientId);

    if (recipient.isUnregistered()) {
      Log.w(TAG, recipient.getId() + " not registered!");
      return;
    }

    SignalServiceMessageSender       messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
    SignalServiceAddress             address            = RecipientUtil.toSignalServiceAddress(context, recipient);
    Optional<UnidentifiedAccessPair> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient);

    try {
      messageSender.sendNullMessage(address, unidentifiedAccess);
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, "Unable to send null message.");
    }
  }

  private long getLastResetTime(@NonNull DeviceLastResetTime resetTimes, int deviceId) {
    for (DeviceLastResetTime.Pair pair : resetTimes.getResetTimeList()) {
      if (pair.getDeviceId() == deviceId) {
        return pair.getLastResetTime();
      }
    }
    return 0;
  }

  private @NonNull DeviceLastResetTime setLastResetTime(@NonNull DeviceLastResetTime resetTimes, int deviceId, long time) {
    DeviceLastResetTime.Builder builder = DeviceLastResetTime.newBuilder();

    for (DeviceLastResetTime.Pair pair : resetTimes.getResetTimeList()) {
      if (pair.getDeviceId() != deviceId) {
        builder.addResetTime(pair);
      }
    }

    builder.addResetTime(DeviceLastResetTime.Pair.newBuilder().setDeviceId(deviceId).setLastResetTime(time));

    return builder.build();
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
