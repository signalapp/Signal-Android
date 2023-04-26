package org.thoughtcrime.securesms.jobmanager.migrations;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.JobMigration;

import java.util.SortedSet;
import java.util.TreeSet;

public class SendReadReceiptsJobMigration extends JobMigration {

  private final MessageTable messageTable;

  public SendReadReceiptsJobMigration(@NonNull MessageTable messageTable) {
    super(5);
    this.messageTable = messageTable;
  }

  @Override
  protected @NonNull JobData migrate(@NonNull JobData jobData) {
    if ("SendReadReceiptJob".equals(jobData.getFactoryKey())) {
      return migrateSendReadReceiptJob(messageTable, jobData);
    }
    return jobData;
  }

  private static @NonNull JobData migrateSendReadReceiptJob(@NonNull MessageTable messageTable, @NonNull JobData jobData) {
    JsonJobData data = JsonJobData.deserialize(jobData.getData());

    if (!data.hasLong("thread")) {
      long[]          messageIds = data.getLongArray("message_ids");
      SortedSet<Long> threadIds  = new TreeSet<>();

      for (long id : messageIds) {
        long threadForMessageId = messageTable.getThreadIdForMessage(id);
        if (id != -1) {
          threadIds.add(threadForMessageId);
        }
      }

      if (threadIds.size() != 1) {
        return new JobData("FailingJob", null, null);
      } else {
        return jobData.withData(data.buildUpon().putLong("thread", threadIds.first()).serialize());
      }

    } else {
      return jobData;
    }
  }

}
