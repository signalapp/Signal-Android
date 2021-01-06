package org.thoughtcrime.securesms.jobmanager.workmanager;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.jobmanager.persistence.ConstraintSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.FullSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class WorkManagerDatabase extends SQLiteOpenHelper {

  private static final String TAG = WorkManagerDatabase.class.getSimpleName();

  static final String DB_NAME = "androidx.work.workdb";

  WorkManagerDatabase(@NonNull Context context) {
    super(context, DB_NAME, null, 5);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    throw new UnsupportedOperationException("We should never be creating this database, only migrating an existing one!");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    // There's a chance that a user who hasn't upgraded in > 6 months could hit this onUpgrade path,
    // but we don't use any of the columns that were added in any migrations they could hit, so we
    // can ignore this.
    Log.w(TAG, "Hit onUpgrade path from " + oldVersion + " to " + newVersion);
  }

  @NonNull List<FullSpec> getAllJobs(@NonNull Data.Serializer dataSerializer) {
    SQLiteDatabase  db         = getReadableDatabase();
    String[]        columns    = new String[] { "id", "worker_class_name", "input", "required_network_type"};
    List<FullSpec>  fullSpecs  = new LinkedList<>();

    try (Cursor cursor = db.query("WorkSpec", columns, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        String factoryName = WorkManagerFactoryMappings.getFactoryKey(cursor.getString(cursor.getColumnIndexOrThrow("worker_class_name")));

        if (factoryName != null) {
          String id   = cursor.getString(cursor.getColumnIndexOrThrow("id"));
          byte[] data = cursor.getBlob(cursor.getColumnIndexOrThrow("input"));

          List<ConstraintSpec> constraints = new LinkedList<>();
          JobSpec              jobSpec     = new JobSpec(id,
                                                         factoryName,
                                                         getQueueKey(id),
                                                         System.currentTimeMillis(),
                                                         0,
                                                         0,
                                                         Job.Parameters.UNLIMITED,
                                                         TimeUnit.SECONDS.toMillis(30),
                                                         TimeUnit.DAYS.toMillis(1),
                                                         dataSerializer.serialize(DataMigrator.convert(data)),
                                                         null,
                                                         false,
                                                         false);



          if (cursor.getInt(cursor.getColumnIndexOrThrow("required_network_type")) != 0) {
            constraints.add(new ConstraintSpec(id, NetworkConstraint.KEY, false));
          }

          fullSpecs.add(new FullSpec(jobSpec, constraints, Collections.emptyList()));
        } else {
          Log.w(TAG, "Failed to find a matching factory for worker class: " + factoryName);
        }
      }
    }

    return fullSpecs;
  }

  private @Nullable String getQueueKey(@NonNull String jobId) {
    String   query = "work_spec_id = ?";
    String[] args  = new String[] { jobId };

    try (Cursor cursor = getReadableDatabase().query("WorkName", null, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getString(cursor.getColumnIndexOrThrow("name"));
      }
    }

    return null;
  }
}
