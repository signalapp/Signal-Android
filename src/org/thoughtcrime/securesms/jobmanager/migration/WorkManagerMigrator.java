package org.thoughtcrime.securesms.jobmanager.migration;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.persistence.FullSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.JobStorage;
import org.thoughtcrime.securesms.logging.Log;

import java.util.List;

public class WorkManagerMigrator {

  private static final String TAG = Log.tag(WorkManagerMigrator.class);

  @SuppressLint("DefaultLocale")
  @WorkerThread
  public static synchronized void migrate(@NonNull Context context,
                                          @NonNull JobStorage jobStorage,
                                          @NonNull Data.Serializer dataSerializer)
  {
    long startTime = System.currentTimeMillis();
    Log.i(TAG, "Beginning WorkManager migration.");

    WorkManagerDatabase database  = new WorkManagerDatabase(context);
    List<FullSpec>      fullSpecs = database.getAllJobs(dataSerializer);

    for (FullSpec fullSpec : fullSpecs) {
      Log.i(TAG, String.format("Migrating job with key '%s' and %d constraint(s).", fullSpec.getJobSpec().getFactoryKey(), fullSpec.getConstraintSpecs().size()));
    }

    jobStorage.insertJobs(fullSpecs);

    context.deleteDatabase(WorkManagerDatabase.DB_NAME);
    Log.i(TAG, String.format("WorkManager migration finished. Migrated %d job(s) in %d ms.", fullSpecs.size(), System.currentTimeMillis() - startTime));
  }

  @WorkerThread
  public static synchronized boolean needsMigration(@NonNull Context context) {
    return context.getDatabasePath(WorkManagerDatabase.DB_NAME).exists();
  }
}
