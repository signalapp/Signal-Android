package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobLogger;
import org.thoughtcrime.securesms.logging.Log;

/**
 * A base class for jobs that are intended to be used in {@link ApplicationMigrations}. Some
 * sensible defaults are provided, as well as enforcement that jobs have the correct queue key,
 * never expire, and have at most one instance (to avoid double-migrating).
 *
 * These jobs can never fail, or else the JobManager will skip over them. As a result, if they are
 * neither successful nor retryable, they will crash the app.
 */
abstract class MigrationJob extends Job {

  private static final String TAG = Log.tag(MigrationJob.class);

  MigrationJob(@NonNull Parameters parameters) {
    super(parameters.toBuilder()
                    .setQueue(Parameters.MIGRATION_QUEUE_KEY)
                    .setMaxInstances(1)
                    .setLifespan(Parameters.IMMORTAL)
                    .setMaxAttempts(Parameters.UNLIMITED)
                    .build());
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull Result run() {
    try {
      Log.i(TAG, "About to run " + getClass().getSimpleName());
      performMigration();
      return Result.success();
    } catch (RuntimeException e) {
      Log.w(TAG, JobLogger.format(this, "Encountered a runtime exception."), e);
      throw new FailedMigrationError(e);
    } catch (Exception e) {
      if (shouldRetry(e)) {
        Log.w(TAG, JobLogger.format(this, "Encountered a retryable exception."), e);
        return Result.retry();
      } else {
        Log.w(TAG, JobLogger.format(this, "Encountered a non-runtime fatal exception."), e);
        throw new FailedMigrationError(e);
      }
    }
  }

  @Override
  public void onCanceled() {
    throw new AssertionError("This job should never fail. " + getClass().getSimpleName());
  }

  /**
   * @return True if you want the UI to be blocked by a spinner if the user opens the application
   *         during the migration, otherwise false.
   */
  abstract boolean isUiBlocking();

  /**
   * Do the actual work of your migration.
   */
  abstract void performMigration() throws Exception;

  /**
   * @return True if you should retry this job based on the exception type, otherwise false.
   *         Returning false will result in a crash and your job being re-run upon app start.
   *         This could result in a crash loop, but considering that this is for an application
   *         migration, this is likely preferable to skipping it.
   */
  abstract boolean shouldRetry(@NonNull Exception e);

  private static class FailedMigrationError extends Error {
    FailedMigrationError(Throwable t) {
      super(t);
    }
  }
}
