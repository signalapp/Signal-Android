package org.thoughtcrime.securesms.jobmanager;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.JobMigration.JobData;
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.JobStorage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("UseSparseArrays")
public class JobMigrator {

  private static final String TAG = Log.tag(JobMigrator.class);

  private final int                        lastSeenVersion;
  private final int                        currentVersion;
  private final Map<Integer, JobMigration> migrations;

  public JobMigrator(int lastSeenVersion, int currentVersion, @NonNull List<JobMigration> migrations) {
    this.lastSeenVersion = lastSeenVersion;
    this.currentVersion  = currentVersion;
    this.migrations      = new HashMap<>();

    if (migrations.size() != currentVersion - 1) {
      throw new AssertionError("You must have a migration for every version!");
    }

    for (int i = 0; i < migrations.size(); i++) {
      JobMigration migration = migrations.get(i);

      if (migration.getEndVersion() != i + 2) {
        throw new AssertionError("Missing migration for version " + (i + 2) + "!");
      }

      this.migrations.put(migration.getEndVersion(), migrations.get(i));
    }
  }

  /**
   * @return The version that has been migrated to.
   */
  int migrate(@NonNull JobStorage jobStorage) {
    for (int i = lastSeenVersion; i < currentVersion; i++) {
      Log.i(TAG, "Migrating from " + i + " to " + (i + 1));
      JobMigration migration = migrations.get(i + 1);
      assert migration != null;

      jobStorage.transformJobs(jobSpec -> {
        JobData originalJobData = new JobData(jobSpec.getFactoryKey(), jobSpec.getQueueKey(), jobSpec.getMaxAttempts(), jobSpec.getLifespan(), jobSpec.getSerializedData());
        JobData updatedJobData  = migration.migrate(originalJobData);

        if (updatedJobData == originalJobData) {
          return jobSpec;
        }

        return new JobSpec(jobSpec.getId(),
                           updatedJobData.getFactoryKey(),
                           updatedJobData.getQueueKey(),
                           jobSpec.getCreateTime(),
                           jobSpec.getLastRunAttemptTime(),
                           jobSpec.getNextBackoffInterval(),
                           jobSpec.getRunAttempt(),
                           updatedJobData.getMaxAttempts(),
                           updatedJobData.getLifespan(),
                           updatedJobData.getData(),
                           jobSpec.getSerializedInputData(),
                           jobSpec.isRunning(),
                           jobSpec.isMemoryOnly(),
                           jobSpec.getPriority());
      });
    }

    return currentVersion;
  }
}
