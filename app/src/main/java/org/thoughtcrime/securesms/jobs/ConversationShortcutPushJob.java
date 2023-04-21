package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

public class ConversationShortcutPushJob extends BaseJob {

  private static final String TAG = Log.tag(ConversationShortcutPushJob.class);

  public static final String KEY = "ConversationShortcutPushJob";

  private static final String KEY_RECIPIENT           = "recipient";
  private static final String KEY_REPORTED_SIGNAL = "reported_signal";

  @NonNull private final Recipient recipient;
  private final int reportedSignal;

  public static void enqueue(@NonNull Recipient recipient, int reportedSignal) {
    ApplicationDependencies.getJobManager().add(new ConversationShortcutPushJob(recipient, reportedSignal));
  }

  private ConversationShortcutPushJob(@NonNull Recipient recipient, int reportedSignal) {
    this(new Parameters.Builder()
             .setQueue("ConversationShortcutPushJob")
             .setLifespan(TimeUnit.MINUTES.toMillis(15))
             .setMaxInstancesForFactory(1)
             .build(), recipient, reportedSignal);
  }

  private ConversationShortcutPushJob(@NonNull Parameters parameters, @NonNull Recipient recipient, int reportedSignal) {
    super(parameters);
    this.recipient = recipient;
    this.reportedSignal = reportedSignal;
  }

  @Nullable @Override public byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_RECIPIENT, recipient.getId().serialize())
                                    .putInt(KEY_REPORTED_SIGNAL, reportedSignal)
                                    .serialize();
  }

  @NonNull @Override public String getFactoryKey() {
    return KEY;
  }

  @Override protected void onRun() throws Exception {
    if (TextSecurePreferences.isScreenLockEnabled(context)) {
      Log.i(TAG, "Screen lock enabled. Clearing shortcuts.");
      ConversationUtil.clearAllShortcuts(context);
      return;
    }

    ConversationUtil.pushShortcutForRecipientSync(context, recipient, reportedSignal);
  }

  @Override protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override public void onFailure() {
  }

  public static class Factory implements Job.Factory<ConversationShortcutPushJob> {
    @Override
    public @NonNull ConversationShortcutPushJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);
      String recipientId = data.getString(KEY_RECIPIENT);
      Recipient recipient = Recipient.resolved(RecipientId.from(recipientId));
      int reportedSignal = data.getInt(KEY_REPORTED_SIGNAL);
      return new ConversationShortcutPushJob(parameters, recipient, reportedSignal);
    }
  }
}
