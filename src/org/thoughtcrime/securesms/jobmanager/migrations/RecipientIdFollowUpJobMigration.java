package org.thoughtcrime.securesms.jobmanager.migrations;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.JobMigration;

/**
 * Fixes things that went wrong in {@link RecipientIdJobMigration}. In particular, some jobs didn't
 * have some necessary data fields carried over. Thankfully they're relatively non-critical, so
 * we'll just swap them out with failing jobs if they're missing something.
 */
public class RecipientIdFollowUpJobMigration extends JobMigration {

  public RecipientIdFollowUpJobMigration() {
    this(3);
  }

  RecipientIdFollowUpJobMigration(int endVersion) {
    super(endVersion);
  }

  @Override
  protected @NonNull JobData migrate(@NonNull JobData jobData) {
    switch(jobData.getFactoryKey()) {
      case "RequestGroupInfoJob": return migrateRequestGroupInfoJob(jobData);
      case "SendDeliveryReceiptJob": return migrateSendDeliveryReceiptJob(jobData);
      default:
        return jobData;
    }
  }

  private static @NonNull JobData migrateRequestGroupInfoJob(@NonNull JobData jobData) {
    if (!jobData.getData().hasString("source") || !jobData.getData().hasString("group_id")) {
      return failingJobData();
    } else {
      return jobData;
    }
  }

  private static @NonNull JobData migrateSendDeliveryReceiptJob(@NonNull JobData jobData) {
    if (!jobData.getData().hasString("recipient") ||
        !jobData.getData().hasLong("message_id")  ||
        !jobData.getData().hasLong("timestamp"))
    {
      return failingJobData();
    } else {
      return jobData;
    }
  }

  private static JobData failingJobData() {
    return new JobData("FailingJob", null, new Data.Builder().build());
  }
}
