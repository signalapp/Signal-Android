package org.thoughtcrime.securesms.jobmanager;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.JobMigration.JobData;
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.JobStorage;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
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
  int migrate(@NonNull JobStorage jobStorage, @NonNull Data.Serializer dataSerializer) {
    List<JobSpec> jobSpecs = jobStorage.getAllJobSpecs();

    for (int i = lastSeenVersion; i < currentVersion; i++) {
      Log.i(TAG, "Migrating from " + i + " to " + (i + 1));

      ListIterator<JobSpec> iter      = jobSpecs.listIterator();
      JobMigration          migration = migrations.get(i + 1);

      assert migration != null;

      while (iter.hasNext()) {
        JobSpec jobSpec         = iter.next();
        Data    data            = dataSerializer.deserialize(jobSpec.getSerializedData());
        JobData originalJobData = new JobData(jobSpec.getFactoryKey(), jobSpec.getQueueKey(), data);
        JobData updatedJobData  = migration.migrate(originalJobData);
        JobSpec updatedJobSpec  = new JobSpec(jobSpec.getId(),
                                              updatedJobData.getFactoryKey(),
                                              updatedJobData.getQueueKey(),
                                              jobSpec.getCreateTime(),
                                              jobSpec.getNextRunAttemptTime(),
                                              jobSpec.getRunAttempt(),
                                              jobSpec.getMaxAttempts(),
                                              jobSpec.getMaxBackoff(),
                                              jobSpec.getLifespan(),
                                              dataSerializer.serialize(updatedJobData.getData()),
                                              jobSpec.getSerializedInputData(),
                                              jobSpec.isRunning(),
                                              jobSpec.isMemoryOnly());

        iter.set(updatedJobSpec);
      }
    }

    jobStorage.updateJobs(jobSpecs);

    return currentVersion;
  }
}
