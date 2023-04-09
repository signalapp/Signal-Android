package org.thoughtcrime.securesms.jobmanager.migrations;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.PushTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.JobMigration;
import org.thoughtcrime.securesms.jobs.FailingJob;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

/**
 * We removed the messageId property from the job data and replaced it with a serialized envelope,
 * so we need to take jobs that referenced an ID and replace it with the envelope instead.
 */
public class PushDecryptMessageJobEnvelopeMigration extends JobMigration {

  private static final String TAG = Log.tag(PushDecryptMessageJobEnvelopeMigration.class);

  private final PushTable pushDatabase;

  public PushDecryptMessageJobEnvelopeMigration(@NonNull Context context) {
    super(8);
    this.pushDatabase = SignalDatabase.push();
  }

  @Override
  protected @NonNull JobData migrate(@NonNull JobData jobData) {
    if ("PushDecryptJob".equals(jobData.getFactoryKey())) {
      Log.i(TAG, "Found a PushDecryptJob to migrate.");
      return migratePushDecryptMessageJob(pushDatabase, jobData);
    } else {
      return jobData;
    }
  }

  private static @NonNull JobData migratePushDecryptMessageJob(@NonNull PushTable pushDatabase, @NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    if (data.hasLong("message_id")) {
      long messageId = data.getLong("message_id");
      try {
        SignalServiceEnvelope envelope = pushDatabase.get(messageId);
        return jobData.withData(data.buildUpon()
                                    .putBlobAsString("envelope", envelope.serialize())
                                    .serialize());
      } catch (NoSuchMessageException e) {
        Log.w(TAG, "Failed to find envelope in DB! Failing.");
        return jobData.withFactoryKey(FailingJob.KEY);
      }
    } else {
      Log.w(TAG, "No message_id property?");
      return jobData;
    }
  }
}
