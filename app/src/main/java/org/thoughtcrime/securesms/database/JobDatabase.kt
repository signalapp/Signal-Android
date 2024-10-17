package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.database.Cursor
import androidx.core.content.contentValuesOf
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.forEach
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.updateAll
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.crypto.DatabaseSecret
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider
import org.thoughtcrime.securesms.jobmanager.persistence.ConstraintSpec
import org.thoughtcrime.securesms.jobmanager.persistence.DependencySpec
import org.thoughtcrime.securesms.jobmanager.persistence.FullSpec
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec
import org.thoughtcrime.securesms.jobs.MinimalJobSpec
import java.util.function.Predicate

class JobDatabase(
  application: Application,
  databaseSecret: DatabaseSecret
) : SQLiteOpenHelper(
  application,
  DATABASE_NAME,
  databaseSecret.asString(),
  null,
  DATABASE_VERSION,
  0,
  SqlCipherErrorHandler(DATABASE_NAME),
  SqlCipherDatabaseHook(),
  true
),
  SignalDatabaseOpenHelper {
  private object Jobs {
    const val TABLE_NAME = "job_spec"
    const val ID = "_id"
    const val JOB_SPEC_ID = "job_spec_id"
    const val FACTORY_KEY = "factory_key"
    const val QUEUE_KEY = "queue_key"
    const val CREATE_TIME = "create_time"
    const val LAST_RUN_ATTEMPT_TIME = "last_run_attempt_time"
    const val NEXT_BACKOFF_INTERVAL = "next_backoff_interval"
    const val RUN_ATTEMPT = "run_attempt"
    const val MAX_ATTEMPTS = "max_attempts"
    const val LIFESPAN = "lifespan"
    const val SERIALIZED_DATA = "serialized_data"
    const val SERIALIZED_INPUT_DATA = "serialized_input_data"
    const val IS_RUNNING = "is_running"
    const val GLOBAL_PRIORITY = "global_priority"
    const val QUEUE_PRIORITY = "queue_priority"

    val CREATE_TABLE =
      """
        CREATE TABLE $TABLE_NAME(
          $ID INTEGER PRIMARY KEY AUTOINCREMENT, 
          $JOB_SPEC_ID TEXT UNIQUE, 
          $FACTORY_KEY TEXT,
          $QUEUE_KEY TEXT,
          $CREATE_TIME INTEGER, 
          $LAST_RUN_ATTEMPT_TIME INTEGER, 
          $RUN_ATTEMPT INTEGER, 
          $MAX_ATTEMPTS INTEGER, 
          $LIFESPAN INTEGER, 
          $SERIALIZED_DATA TEXT, 
          $SERIALIZED_INPUT_DATA TEXT DEFAULT NULL, 
          $IS_RUNNING INTEGER,
          $NEXT_BACKOFF_INTERVAL INTEGER,
          $GLOBAL_PRIORITY INTEGER DEFAULT 0,
          $QUEUE_PRIORITY INTEGER DEFAULT 0
        )
      """.trimIndent()
  }

  private object Constraints {
    const val TABLE_NAME = "constraint_spec"
    const val ID = "_id"
    const val JOB_SPEC_ID = "job_spec_id"
    const val FACTORY_KEY = "factory_key"

    val CREATE_TABLE =
      """
        CREATE TABLE $TABLE_NAME(
          $ID INTEGER PRIMARY KEY AUTOINCREMENT, 
          $JOB_SPEC_ID TEXT, 
          $FACTORY_KEY TEXT, 
          UNIQUE($JOB_SPEC_ID, $FACTORY_KEY)
        )
      """.trimIndent()
  }

  private object Dependencies {
    const val TABLE_NAME = "dependency_spec"
    private const val ID = "_id"
    const val JOB_SPEC_ID = "job_spec_id"
    const val DEPENDS_ON_JOB_SPEC_ID = "depends_on_job_spec_id"

    val CREATE_TABLE =
      """
        CREATE TABLE $TABLE_NAME(
          $ID INTEGER PRIMARY KEY AUTOINCREMENT, 
          $JOB_SPEC_ID TEXT, 
          $DEPENDS_ON_JOB_SPEC_ID TEXT, 
          UNIQUE($JOB_SPEC_ID, $DEPENDS_ON_JOB_SPEC_ID)
        )
      """.trimIndent()
  }

  override fun onCreate(db: SQLiteDatabase) {
    Log.i(TAG, "onCreate()")

    db.execSQL(Jobs.CREATE_TABLE)
    db.execSQL(Constraints.CREATE_TABLE)
    db.execSQL(Dependencies.CREATE_TABLE)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    Log.i(TAG, "onUpgrade($oldVersion, $newVersion)")

    if (oldVersion < 2) {
      db.execSQL("ALTER TABLE job_spec RENAME COLUMN next_run_attempt_time TO last_run_attempt_time")
      db.execSQL("ALTER TABLE job_spec ADD COLUMN next_backoff_interval INTEGER")
      db.execSQL("UPDATE job_spec SET last_run_attempt_time = 0")
    }

    if (oldVersion < 3) {
      db.execSQL("ALTER TABLE job_spec ADD COLUMN priority INTEGER DEFAULT 0")
    }

    if (oldVersion < 4) {
      db.execSQL("ALTER TABLE job_spec RENAME COLUMN priority TO global_priority")
      db.execSQL("ALTER TABLE job_spec ADD COLUMN queue_priority INTEGER DEFAULT 0")
    }
  }

  override fun onOpen(db: SQLiteDatabase) {
    Log.i(TAG, "onOpen()")
    db.setForeignKeyConstraintsEnabled(true)
  }

  @Synchronized
  fun insertJobs(fullSpecs: List<FullSpec>) {
    if (fullSpecs.all { it.jobSpec.isMemoryOnly }) {
      return
    }

    writableDatabase.withinTransaction { db ->
      for ((jobSpec, constraintSpecs, dependencySpecs) in fullSpecs) {
        insertJobSpec(db, jobSpec)
        insertConstraintSpecs(db, constraintSpecs)
        insertDependencySpecs(db, dependencySpecs)
      }
    }
  }

  @Synchronized
  fun getJobSpecs(limit: Int): List<JobSpec> {
    return readableDatabase
      .select()
      .from(Jobs.TABLE_NAME)
      .orderBy("${Jobs.CREATE_TIME}, ${Jobs.ID} ASC")
      .limit(limit)
      .run()
      .readToList { it.toJobSpec() }
  }

  @Synchronized
  fun getMostEligibleJobInQueue(queue: String): JobSpec? {
    return readableDatabase
      .select()
      .from(Jobs.TABLE_NAME)
      .where("${Jobs.QUEUE_KEY} = ?", queue)
      .orderBy("${Jobs.GLOBAL_PRIORITY} DESC, ${Jobs.QUEUE_PRIORITY} DESC, ${Jobs.CREATE_TIME} ASC, ${Jobs.ID} ASC")
      .limit(1)
      .run()
      .readToSingleObject { it.toJobSpec() }
  }

  @Synchronized
  fun getAllMatchingFilter(predicate: Predicate<JobSpec>): List<JobSpec> {
    val output: MutableList<JobSpec> = mutableListOf()

    readableDatabase
      .select()
      .from(Jobs.TABLE_NAME)
      .run()
      .readToList { cursor ->
        val jobSpec = cursor.toJobSpec()
        if (predicate.test(jobSpec)) {
          output += jobSpec
        }
      }

    return output
  }

  @Synchronized
  fun getJobSpec(id: String): JobSpec? {
    return readableDatabase
      .select()
      .from(Jobs.TABLE_NAME)
      .where("${Jobs.JOB_SPEC_ID} = ?", id)
      .run()
      .readToSingleObject { it.toJobSpec() }
  }

  @Synchronized
  fun getAllMinimalJobSpecs(): List<MinimalJobSpec> {
    val columns = arrayOf(
      Jobs.ID,
      Jobs.JOB_SPEC_ID,
      Jobs.FACTORY_KEY,
      Jobs.QUEUE_KEY,
      Jobs.CREATE_TIME,
      Jobs.LAST_RUN_ATTEMPT_TIME,
      Jobs.NEXT_BACKOFF_INTERVAL,
      Jobs.IS_RUNNING,
      Jobs.GLOBAL_PRIORITY,
      Jobs.QUEUE_PRIORITY
    )
    return readableDatabase
      .query(Jobs.TABLE_NAME, columns, null, null, null, null, "${Jobs.CREATE_TIME}, ${Jobs.ID} ASC")
      .readToList { cursor ->
        MinimalJobSpec(
          id = cursor.requireNonNullString(Jobs.JOB_SPEC_ID),
          factoryKey = cursor.requireNonNullString(Jobs.FACTORY_KEY),
          queueKey = cursor.requireString(Jobs.QUEUE_KEY),
          createTime = cursor.requireLong(Jobs.CREATE_TIME),
          lastRunAttemptTime = cursor.requireLong(Jobs.LAST_RUN_ATTEMPT_TIME),
          nextBackoffInterval = cursor.requireLong(Jobs.NEXT_BACKOFF_INTERVAL),
          globalPriority = cursor.requireInt(Jobs.GLOBAL_PRIORITY),
          queuePriority = cursor.requireInt(Jobs.QUEUE_PRIORITY),
          isRunning = cursor.requireBoolean(Jobs.IS_RUNNING),
          isMemoryOnly = false
        )
      }
  }

  @Synchronized
  fun markJobAsRunning(id: String, currentTime: Long) {
    writableDatabase
      .update(Jobs.TABLE_NAME)
      .values(
        Jobs.IS_RUNNING to 1,
        Jobs.LAST_RUN_ATTEMPT_TIME to currentTime
      )
      .where("${Jobs.JOB_SPEC_ID} = ?", id)
      .run()
  }

  @Synchronized
  fun updateJobAfterRetry(id: String, currentTime: Long, runAttempt: Int, nextBackoffInterval: Long, serializedData: ByteArray?) {
    writableDatabase
      .update(Jobs.TABLE_NAME)
      .values(
        Jobs.IS_RUNNING to 0,
        Jobs.RUN_ATTEMPT to runAttempt,
        Jobs.LAST_RUN_ATTEMPT_TIME to currentTime,
        Jobs.NEXT_BACKOFF_INTERVAL to nextBackoffInterval,
        Jobs.SERIALIZED_DATA to serializedData
      )
      .where("${Jobs.JOB_SPEC_ID} = ?", id)
      .run()
  }

  @Synchronized
  fun updateAllJobsToBePending() {
    writableDatabase
      .updateAll(Jobs.TABLE_NAME)
      .values(Jobs.IS_RUNNING to 0)
      .run()
  }

  @Synchronized
  fun updateJobs(jobs: List<JobSpec>) {
    if (jobs.all { it.isMemoryOnly }) {
      return
    }

    writableDatabase.withinTransaction { db ->
      jobs
        .filterNot { it.isMemoryOnly }
        .forEach { job ->
          db.update(Jobs.TABLE_NAME)
            .values(job.toContentValues())
            .where("${Jobs.JOB_SPEC_ID} = ?", job.id)
            .run()
        }
    }
  }

  @Synchronized
  fun transformJobs(transformer: (JobSpec) -> JobSpec): List<JobSpec> {
    val transformed: MutableList<JobSpec> = mutableListOf()

    writableDatabase.withinTransaction { db ->
      readableDatabase
        .select()
        .from(Jobs.TABLE_NAME)
        .run()
        .forEach { cursor ->
          val jobSpec = cursor.toJobSpec()
          val updated = transformer(jobSpec)
          if (updated != jobSpec) {
            transformed += updated
          }
        }

      for (job in transformed) {
        db.update(Jobs.TABLE_NAME)
          .values(job.toContentValues())
          .where("${Jobs.JOB_SPEC_ID} = ?", job.id)
          .run()
      }
    }

    return transformed
  }

  @Synchronized
  fun deleteJobs(jobIds: List<String>) {
    writableDatabase.withinTransaction { db ->
      for (jobId in jobIds) {
        db.delete(Jobs.TABLE_NAME)
          .where("${Jobs.JOB_SPEC_ID} = ?", jobId)
          .run()

        db.delete(Constraints.TABLE_NAME)
          .where("${Constraints.JOB_SPEC_ID} = ?", jobId)
          .run()

        db.delete(Dependencies.TABLE_NAME)
          .where("${Dependencies.JOB_SPEC_ID} = ?", jobId)
          .run()

        db.delete(Dependencies.TABLE_NAME)
          .where("${Dependencies.DEPENDS_ON_JOB_SPEC_ID} = ?", jobId)
          .run()
      }
    }
  }

  @Synchronized
  fun getConstraintSpecs(limit: Int): List<ConstraintSpec> {
    return readableDatabase
      .select()
      .from(Constraints.TABLE_NAME)
      .limit(limit)
      .run()
      .readToList { it.toConstraintSpec() }
  }

  fun getConstraintSpecsForJobs(jobIds: Collection<String>): List<ConstraintSpec> {
    val output: MutableList<ConstraintSpec> = mutableListOf()

    for (query in SqlUtil.buildCollectionQuery(Constraints.JOB_SPEC_ID, jobIds)) {
      readableDatabase
        .select()
        .from(Constraints.TABLE_NAME)
        .where(query.where, query.whereArgs)
        .run()
        .forEach {
          output += it.toConstraintSpec()
        }
    }

    return output
  }

  @Synchronized
  fun getAllDependencySpecs(): List<DependencySpec> {
    return readableDatabase
      .select()
      .from(Dependencies.TABLE_NAME)
      .run()
      .readToList { it.toDependencySpec() }
  }

  private fun insertJobSpec(db: SQLiteDatabase, job: JobSpec) {
    if (job.isMemoryOnly) {
      return
    }

    check(db.inTransaction())

    db.insertInto(Jobs.TABLE_NAME)
      .values(job.toContentValues())
      .run(SQLiteDatabase.CONFLICT_IGNORE)
  }

  private fun insertConstraintSpecs(db: SQLiteDatabase, constraints: List<ConstraintSpec>) {
    check(db.inTransaction())

    constraints
      .filterNot { it.isMemoryOnly }
      .forEach { constraint ->
        db.insertInto(Constraints.TABLE_NAME)
          .values(
            Constraints.JOB_SPEC_ID to constraint.jobSpecId,
            Constraints.FACTORY_KEY to constraint.factoryKey
          )
          .run(SQLiteDatabase.CONFLICT_IGNORE)
      }
  }

  private fun insertDependencySpecs(db: SQLiteDatabase, dependencies: List<DependencySpec>) {
    check(db.inTransaction())

    dependencies
      .filterNot { it.isMemoryOnly }
      .forEach { dependency ->
        db.insertInto(Dependencies.TABLE_NAME)
          .values(
            Dependencies.JOB_SPEC_ID to dependency.jobId,
            Dependencies.DEPENDS_ON_JOB_SPEC_ID to dependency.dependsOnJobId
          )
          .run(SQLiteDatabase.CONFLICT_IGNORE)
      }
  }

  private fun Cursor.toJobSpec(): JobSpec {
    return JobSpec(
      id = this.requireNonNullString(Jobs.JOB_SPEC_ID),
      factoryKey = this.requireNonNullString(Jobs.FACTORY_KEY),
      queueKey = this.requireString(Jobs.QUEUE_KEY),
      createTime = this.requireLong(Jobs.CREATE_TIME),
      lastRunAttemptTime = this.requireLong(Jobs.LAST_RUN_ATTEMPT_TIME),
      nextBackoffInterval = this.requireLong(Jobs.NEXT_BACKOFF_INTERVAL),
      runAttempt = this.requireInt(Jobs.RUN_ATTEMPT),
      maxAttempts = this.requireInt(Jobs.MAX_ATTEMPTS),
      lifespan = this.requireLong(Jobs.LIFESPAN),
      serializedData = this.requireBlob(Jobs.SERIALIZED_DATA),
      serializedInputData = this.requireBlob(Jobs.SERIALIZED_INPUT_DATA),
      isRunning = this.requireBoolean(Jobs.IS_RUNNING),
      isMemoryOnly = false,
      globalPriority = this.requireInt(Jobs.GLOBAL_PRIORITY),
      queuePriority = this.requireInt(Jobs.QUEUE_PRIORITY)
    )
  }

  private fun Cursor.toConstraintSpec(): ConstraintSpec {
    return ConstraintSpec(
      jobSpecId = this.requireNonNullString(Constraints.JOB_SPEC_ID),
      factoryKey = this.requireNonNullString(Constraints.FACTORY_KEY),
      isMemoryOnly = false
    )
  }

  private fun Cursor.toDependencySpec(): DependencySpec {
    return DependencySpec(
      jobId = this.requireNonNullString(Dependencies.JOB_SPEC_ID),
      dependsOnJobId = this.requireNonNullString(Dependencies.DEPENDS_ON_JOB_SPEC_ID),
      isMemoryOnly = false
    )
  }

  override fun getSqlCipherDatabase(): SQLiteDatabase {
    return writableDatabase
  }

  /** Should only be used for debugging! */
  fun debugResetBackoffInterval() {
    writableDatabase.update(Jobs.TABLE_NAME, contentValuesOf(Jobs.NEXT_BACKOFF_INTERVAL to 0), null, null)
  }

  private fun JobSpec.toContentValues(): ContentValues {
    return contentValuesOf(
      Jobs.JOB_SPEC_ID to this.id,
      Jobs.FACTORY_KEY to this.factoryKey,
      Jobs.QUEUE_KEY to this.queueKey,
      Jobs.CREATE_TIME to this.createTime,
      Jobs.LAST_RUN_ATTEMPT_TIME to this.lastRunAttemptTime,
      Jobs.NEXT_BACKOFF_INTERVAL to this.nextBackoffInterval,
      Jobs.RUN_ATTEMPT to this.runAttempt,
      Jobs.MAX_ATTEMPTS to this.maxAttempts,
      Jobs.LIFESPAN to this.lifespan,
      Jobs.SERIALIZED_DATA to this.serializedData,
      Jobs.SERIALIZED_INPUT_DATA to this.serializedInputData,
      Jobs.IS_RUNNING to if (this.isRunning) 1 else 0,
      Jobs.GLOBAL_PRIORITY to this.globalPriority,
      Jobs.QUEUE_PRIORITY to this.queuePriority
    )
  }

  companion object {
    private val TAG = Log.tag(JobDatabase::class.java)
    private const val DATABASE_VERSION = 4
    private const val DATABASE_NAME = "signal-jobmanager.db"

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var instance: JobDatabase? = null

    @JvmStatic
    fun getInstance(context: Application): JobDatabase {
      if (instance == null) {
        synchronized(JobDatabase::class.java) {
          if (instance == null) {
            SqlCipherLibraryLoader.load()
            instance = JobDatabase(context, DatabaseSecretProvider.getOrCreateDatabaseSecret(context))
          }
        }
      }
      return instance!!
    }
  }
}
