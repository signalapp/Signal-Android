package org.thoughtcrime.securesms.database;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import net.sqlcipher.database.SQLiteOpenHelper;
import net.sqlcipher.database.SQLiteDatabase;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.DatabaseSecret;
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider;
import org.thoughtcrime.securesms.jobmanager.persistence.ConstraintSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.DependencySpec;
import org.thoughtcrime.securesms.jobmanager.persistence.FullSpec;
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec;
import org.thoughtcrime.securesms.util.CursorUtil;

import java.util.LinkedList;
import java.util.List;

public class JobDatabase extends SQLiteOpenHelper implements SignalDatabase {

  private static final String TAG = Log.tag(JobDatabase.class);

  private static final int    DATABASE_VERSION = 1;
  private static final String DATABASE_NAME    = "signal-jobmanager.db";

  private static final class Jobs {
    private static final String TABLE_NAME            = "job_spec";
    private static final String ID                    = "_id";
    private static final String JOB_SPEC_ID           = "job_spec_id";
    private static final String FACTORY_KEY           = "factory_key";
    private static final String QUEUE_KEY             = "queue_key";
    private static final String CREATE_TIME           = "create_time";
    private static final String NEXT_RUN_ATTEMPT_TIME = "next_run_attempt_time";
    private static final String RUN_ATTEMPT           = "run_attempt";
    private static final String MAX_ATTEMPTS          = "max_attempts";
    private static final String MAX_BACKOFF           = "max_backoff";
    private static final String LIFESPAN              = "lifespan";
    private static final String SERIALIZED_DATA       = "serialized_data";
    private static final String SERIALIZED_INPUT_DATA = "serialized_input_data";
    private static final String IS_RUNNING            = "is_running";

    private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID                    + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                    JOB_SPEC_ID           + " TEXT UNIQUE, " +
                                                                                    FACTORY_KEY           + " TEXT, " +
                                                                                    QUEUE_KEY             + " TEXT, " +
                                                                                    CREATE_TIME           + " INTEGER, " +
                                                                                    NEXT_RUN_ATTEMPT_TIME + " INTEGER, " +
                                                                                    RUN_ATTEMPT           + " INTEGER, " +
                                                                                    MAX_ATTEMPTS          + " INTEGER, " +
                                                                                    MAX_BACKOFF           + " INTEGER, " +
                                                                                    LIFESPAN              + " INTEGER, " +
                                                                                    SERIALIZED_DATA       + " TEXT, " +
                                                                                    SERIALIZED_INPUT_DATA + " TEXT DEFAULT NULL, " +
                                                                                    IS_RUNNING            + " INTEGER)";
  }

  private static final class Constraints {
    private static final String TABLE_NAME  = "constraint_spec";
    private static final String ID          = "_id";
    private static final String JOB_SPEC_ID = "job_spec_id";
    private static final String FACTORY_KEY = "factory_key";

    private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID          + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                    JOB_SPEC_ID + " TEXT, " +
                                                                                    FACTORY_KEY + " TEXT, " +
                                                                                    "UNIQUE(" + JOB_SPEC_ID + ", " + FACTORY_KEY + "))";
  }

  private static final class Dependencies {
    private static final String TABLE_NAME             = "dependency_spec";
    private static final String ID                     = "_id";
    private static final String JOB_SPEC_ID            = "job_spec_id";
    private static final String DEPENDS_ON_JOB_SPEC_ID = "depends_on_job_spec_id";

    private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID                     + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                    JOB_SPEC_ID            + " TEXT, " +
                                                                                    DEPENDS_ON_JOB_SPEC_ID + " TEXT, " +
                                                                                    "UNIQUE(" + JOB_SPEC_ID + ", " + DEPENDS_ON_JOB_SPEC_ID + "))";
  }


  private static volatile JobDatabase instance;

  private final Application    application;
  private final DatabaseSecret databaseSecret;

  public static @NonNull JobDatabase getInstance(@NonNull Application context) {
    if (instance == null) {
      synchronized (JobDatabase.class) {
        if (instance == null) {
          instance = new JobDatabase(context, DatabaseSecretProvider.getOrCreateDatabaseSecret(context));
        }
      }
    }
    return instance;
  }

  public JobDatabase(@NonNull Application application, @NonNull DatabaseSecret databaseSecret) {
    super(application, DATABASE_NAME, null, DATABASE_VERSION, new SqlCipherDatabaseHook());

    this.application    = application;
    this.databaseSecret = databaseSecret;
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    Log.i(TAG, "onCreate()");

    db.execSQL(Jobs.CREATE_TABLE);
    db.execSQL(Constraints.CREATE_TABLE);
    db.execSQL(Dependencies.CREATE_TABLE);

    if (DatabaseFactory.getInstance(application).hasTable("job_spec")) {
      Log.i(TAG, "Found old job_spec table. Migrating data.");
      migrateJobSpecsFromPreviousDatabase(DatabaseFactory.getInstance(application).getRawDatabase(), db);
    }

    if (DatabaseFactory.getInstance(application).hasTable("constraint_spec")) {
      Log.i(TAG, "Found old constraint_spec table. Migrating data.");
      migrateConstraintSpecsFromPreviousDatabase(DatabaseFactory.getInstance(application).getRawDatabase(), db);
    }

    if (DatabaseFactory.getInstance(application).hasTable("dependency_spec")) {
      Log.i(TAG, "Found old dependency_spec table. Migrating data.");
      migrateDependencySpecsFromPreviousDatabase(DatabaseFactory.getInstance(application).getRawDatabase(), db);
    }
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    Log.i(TAG, "onUpgrade(" + oldVersion + ", " + newVersion + ")");
  }

  @Override
  public void onOpen(SQLiteDatabase db) {
    Log.i(TAG, "onOpen()");

    dropTableIfPresent("job_spec");
    dropTableIfPresent("constraint_spec");
    dropTableIfPresent("dependency_spec");
  }

  public synchronized void insertJobs(@NonNull List<FullSpec> fullSpecs) {
    if (Stream.of(fullSpecs).map(FullSpec::getJobSpec).allMatch(JobSpec::isMemoryOnly)) {
      return;
    }

    SQLiteDatabase db = getWritableDatabase();

    db.beginTransaction();

    try {
      for (FullSpec fullSpec : fullSpecs) {
        insertJobSpec(db, fullSpec.getJobSpec());
        insertConstraintSpecs(db, fullSpec.getConstraintSpecs());
        insertDependencySpecs(db, fullSpec.getDependencySpecs());
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized @NonNull List<JobSpec> getAllJobSpecs() {
    List<JobSpec> jobs = new LinkedList<>();

    try (Cursor cursor = getReadableDatabase().query(Jobs.TABLE_NAME, null, null, null, null, null, Jobs.CREATE_TIME + ", " + Jobs.ID + " ASC")) {
      while (cursor != null && cursor.moveToNext()) {
        jobs.add(jobSpecFromCursor(cursor));
      }
    }

    return jobs;
  }

  public synchronized void updateJobRunningState(@NonNull String id, boolean isRunning) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(Jobs.IS_RUNNING, isRunning ? 1 : 0);

    String   query = Jobs.JOB_SPEC_ID + " = ?";
    String[] args  = new String[]{ id };

    getWritableDatabase().update(Jobs.TABLE_NAME, contentValues, query, args);
  }

  public synchronized void updateJobAfterRetry(@NonNull String id, boolean isRunning, int runAttempt, long nextRunAttemptTime, @NonNull String serializedData) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(Jobs.IS_RUNNING, isRunning ? 1 : 0);
    contentValues.put(Jobs.RUN_ATTEMPT, runAttempt);
    contentValues.put(Jobs.NEXT_RUN_ATTEMPT_TIME, nextRunAttemptTime);
    contentValues.put(Jobs.SERIALIZED_DATA, serializedData);

    String   query = Jobs.JOB_SPEC_ID + " = ?";
    String[] args  = new String[]{ id };

    getWritableDatabase().update(Jobs.TABLE_NAME, contentValues, query, args);
  }

  public synchronized void updateAllJobsToBePending() {
    ContentValues contentValues = new ContentValues();
    contentValues.put(Jobs.IS_RUNNING, 0);

    getWritableDatabase().update(Jobs.TABLE_NAME, contentValues, null, null);
  }

  public synchronized void updateJobs(@NonNull List<JobSpec> jobs) {
    if (Stream.of(jobs).allMatch(JobSpec::isMemoryOnly)) {
      return;
    }

    SQLiteDatabase db = getWritableDatabase();

    db.beginTransaction();

    try {
      Stream.of(jobs)
            .filterNot(JobSpec::isMemoryOnly)
            .forEach(job -> {
              ContentValues values = new ContentValues();
              values.put(Jobs.JOB_SPEC_ID, job.getId());
              values.put(Jobs.FACTORY_KEY, job.getFactoryKey());
              values.put(Jobs.QUEUE_KEY, job.getQueueKey());
              values.put(Jobs.CREATE_TIME, job.getCreateTime());
              values.put(Jobs.NEXT_RUN_ATTEMPT_TIME, job.getNextRunAttemptTime());
              values.put(Jobs.RUN_ATTEMPT, job.getRunAttempt());
              values.put(Jobs.MAX_ATTEMPTS, job.getMaxAttempts());
              values.put(Jobs.MAX_BACKOFF, job.getMaxBackoff());
              values.put(Jobs.LIFESPAN, job.getLifespan());
              values.put(Jobs.SERIALIZED_DATA, job.getSerializedData());
              values.put(Jobs.SERIALIZED_INPUT_DATA, job.getSerializedInputData());
              values.put(Jobs.IS_RUNNING, job.isRunning() ? 1 : 0);

              String   query = Jobs.JOB_SPEC_ID + " = ?";
              String[] args  = new String[]{ job.getId() };

              db.update(Jobs.TABLE_NAME, values, query, args);
            });

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized void deleteJobs(@NonNull List<String> jobIds) {
    SQLiteDatabase db = getWritableDatabase();

    db.beginTransaction();

    try {
      for (String jobId : jobIds) {
        String[] arg = new String[]{jobId};

        db.delete(Jobs.TABLE_NAME, Jobs.JOB_SPEC_ID + " = ?", arg);
        db.delete(Constraints.TABLE_NAME, Constraints.JOB_SPEC_ID + " = ?", arg);
        db.delete(Dependencies.TABLE_NAME, Dependencies.JOB_SPEC_ID + " = ?", arg);
        db.delete(Dependencies.TABLE_NAME, Dependencies.DEPENDS_ON_JOB_SPEC_ID + " = ?", arg);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized @NonNull List<ConstraintSpec> getAllConstraintSpecs() {
    List<ConstraintSpec> constraints = new LinkedList<>();

    try (Cursor cursor = getReadableDatabase().query(Constraints.TABLE_NAME, null, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        constraints.add(constraintSpecFromCursor(cursor));
      }
    }

    return constraints;
  }

  public synchronized @NonNull List<DependencySpec> getAllDependencySpecs() {
    List<DependencySpec> dependencies = new LinkedList<>();

    try (Cursor cursor = getReadableDatabase().query(Dependencies.TABLE_NAME, null, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        dependencies.add(dependencySpecFromCursor(cursor));
      }
    }

    return dependencies;
  }

  private void insertJobSpec(@NonNull SQLiteDatabase db, @NonNull JobSpec job) {
    if (job.isMemoryOnly()) {
      return;
    }

    ContentValues contentValues = new ContentValues();
    contentValues.put(Jobs.JOB_SPEC_ID, job.getId());
    contentValues.put(Jobs.FACTORY_KEY, job.getFactoryKey());
    contentValues.put(Jobs.QUEUE_KEY, job.getQueueKey());
    contentValues.put(Jobs.CREATE_TIME, job.getCreateTime());
    contentValues.put(Jobs.NEXT_RUN_ATTEMPT_TIME, job.getNextRunAttemptTime());
    contentValues.put(Jobs.RUN_ATTEMPT, job.getRunAttempt());
    contentValues.put(Jobs.MAX_ATTEMPTS, job.getMaxAttempts());
    contentValues.put(Jobs.MAX_BACKOFF, job.getMaxBackoff());
    contentValues.put(Jobs.LIFESPAN, job.getLifespan());
    contentValues.put(Jobs.SERIALIZED_DATA, job.getSerializedData());
    contentValues.put(Jobs.SERIALIZED_INPUT_DATA, job.getSerializedInputData());
    contentValues.put(Jobs.IS_RUNNING, job.isRunning() ? 1 : 0);

    db.insertWithOnConflict(Jobs.TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_IGNORE);
  }

  private void insertConstraintSpecs(@NonNull SQLiteDatabase db, @NonNull List<ConstraintSpec> constraints) {
    Stream.of(constraints)
          .filterNot(ConstraintSpec::isMemoryOnly)
          .forEach(constraintSpec -> {
            ContentValues contentValues = new ContentValues();
            contentValues.put(Constraints.JOB_SPEC_ID, constraintSpec.getJobSpecId());
            contentValues.put(Constraints.FACTORY_KEY, constraintSpec.getFactoryKey());
            db.insertWithOnConflict(Constraints.TABLE_NAME, null ,contentValues, SQLiteDatabase.CONFLICT_IGNORE);
          });
  }

  private void insertDependencySpecs(@NonNull SQLiteDatabase db, @NonNull List<DependencySpec> dependencies) {
    Stream.of(dependencies)
          .filterNot(DependencySpec::isMemoryOnly)
          .forEach(dependencySpec -> {
            ContentValues contentValues = new ContentValues();
            contentValues.put(Dependencies.JOB_SPEC_ID, dependencySpec.getJobId());
            contentValues.put(Dependencies.DEPENDS_ON_JOB_SPEC_ID, dependencySpec.getDependsOnJobId());
            db.insertWithOnConflict(Dependencies.TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_IGNORE);
          });
  }

  private @NonNull JobSpec jobSpecFromCursor(@NonNull Cursor cursor) {
    return new JobSpec(cursor.getString(cursor.getColumnIndexOrThrow(Jobs.JOB_SPEC_ID)),
                       cursor.getString(cursor.getColumnIndexOrThrow(Jobs.FACTORY_KEY)),
                       cursor.getString(cursor.getColumnIndexOrThrow(Jobs.QUEUE_KEY)),
                       cursor.getLong(cursor.getColumnIndexOrThrow(Jobs.CREATE_TIME)),
                       cursor.getLong(cursor.getColumnIndexOrThrow(Jobs.NEXT_RUN_ATTEMPT_TIME)),
                       cursor.getInt(cursor.getColumnIndexOrThrow(Jobs.RUN_ATTEMPT)),
                       cursor.getInt(cursor.getColumnIndexOrThrow(Jobs.MAX_ATTEMPTS)),
                       cursor.getLong(cursor.getColumnIndexOrThrow(Jobs.MAX_BACKOFF)),
                       cursor.getLong(cursor.getColumnIndexOrThrow(Jobs.LIFESPAN)),
                       cursor.getString(cursor.getColumnIndexOrThrow(Jobs.SERIALIZED_DATA)),
                       cursor.getString(cursor.getColumnIndexOrThrow(Jobs.SERIALIZED_INPUT_DATA)),
                       cursor.getInt(cursor.getColumnIndexOrThrow(Jobs.IS_RUNNING)) == 1,
                       false);
  }

  private @NonNull ConstraintSpec constraintSpecFromCursor(@NonNull Cursor cursor) {
    return new ConstraintSpec(cursor.getString(cursor.getColumnIndexOrThrow(Constraints.JOB_SPEC_ID)),
                              cursor.getString(cursor.getColumnIndexOrThrow(Constraints.FACTORY_KEY)),
                              false);
  }

  private @NonNull DependencySpec dependencySpecFromCursor(@NonNull Cursor cursor) {
    return new DependencySpec(cursor.getString(cursor.getColumnIndexOrThrow(Dependencies.JOB_SPEC_ID)),
                              cursor.getString(cursor.getColumnIndexOrThrow(Dependencies.DEPENDS_ON_JOB_SPEC_ID)),
                              false);
  }

  private @NonNull SQLiteDatabase getReadableDatabase() {
    return getWritableDatabase(databaseSecret.asString());
  }

  private @NonNull SQLiteDatabase getWritableDatabase() {
    return getWritableDatabase(databaseSecret.asString());
  }

  @Override
  public @NonNull SQLiteDatabase getSqlCipherDatabase() {
    return getWritableDatabase();
  }

  private void dropTableIfPresent(@NonNull String table) {
    if (DatabaseFactory.getInstance(application).hasTable(table)) {
      Log.i(TAG, "Dropping original " + table + " table from the main database.");
      DatabaseFactory.getInstance(application).getRawDatabase().rawExecSQL("DROP TABLE " + table);
    }
  }

  private static void migrateJobSpecsFromPreviousDatabase(@NonNull SQLiteDatabase oldDb, @NonNull SQLiteDatabase newDb) {
    try (Cursor cursor = oldDb.rawQuery("SELECT * FROM job_spec", null)) {
      while (cursor.moveToNext()) {
        ContentValues values = new ContentValues();

        values.put(Jobs.JOB_SPEC_ID, CursorUtil.requireString(cursor, "job_spec_id"));
        values.put(Jobs.FACTORY_KEY, CursorUtil.requireString(cursor, "factory_key"));
        values.put(Jobs.QUEUE_KEY, CursorUtil.requireString(cursor, "queue_key"));
        values.put(Jobs.CREATE_TIME, CursorUtil.requireLong(cursor, "create_time"));
        values.put(Jobs.NEXT_RUN_ATTEMPT_TIME, CursorUtil.requireLong(cursor, "next_run_attempt_time"));
        values.put(Jobs.RUN_ATTEMPT, CursorUtil.requireInt(cursor, "run_attempt"));
        values.put(Jobs.MAX_ATTEMPTS, CursorUtil.requireInt(cursor, "max_attempts"));
        values.put(Jobs.MAX_BACKOFF, CursorUtil.requireLong(cursor, "max_backoff"));
        values.put(Jobs.LIFESPAN, CursorUtil.requireLong(cursor, "lifespan"));
        values.put(Jobs.SERIALIZED_DATA, CursorUtil.requireString(cursor, "serialized_data"));
        values.put(Jobs.SERIALIZED_INPUT_DATA, CursorUtil.requireString(cursor, "serialized_input_data"));
        values.put(Jobs.IS_RUNNING, CursorUtil.requireInt(cursor, "is_running"));

        newDb.insert(Jobs.TABLE_NAME, null, values);
      }
    }
  }

  private static void migrateConstraintSpecsFromPreviousDatabase(@NonNull SQLiteDatabase oldDb, @NonNull SQLiteDatabase newDb) {
    try (Cursor cursor = oldDb.rawQuery("SELECT * FROM constraint_spec", null)) {
      while (cursor.moveToNext()) {
        ContentValues values = new ContentValues();

        values.put(Constraints.JOB_SPEC_ID, CursorUtil.requireString(cursor, "job_spec_id"));
        values.put(Constraints.FACTORY_KEY, CursorUtil.requireString(cursor, "factory_key"));

        newDb.insert(Constraints.TABLE_NAME, null, values);
      }
    }
  }

  private static void migrateDependencySpecsFromPreviousDatabase(@NonNull SQLiteDatabase oldDb, @NonNull SQLiteDatabase newDb) {
    try (Cursor cursor = oldDb.rawQuery("SELECT * FROM dependency_spec", null)) {
      while (cursor.moveToNext()) {
        ContentValues values = new ContentValues();

        values.put(Dependencies.JOB_SPEC_ID, CursorUtil.requireString(cursor, "job_spec_id"));
        values.put(Dependencies.DEPENDS_ON_JOB_SPEC_ID, CursorUtil.requireString(cursor, "depends_on_job_spec_id"));

        newDb.insert(Dependencies.TABLE_NAME, null, values);
      }
    }
  }
}
