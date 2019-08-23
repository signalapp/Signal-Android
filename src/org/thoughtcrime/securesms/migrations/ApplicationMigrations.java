package org.thoughtcrime.securesms.migrations;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.VersionTracker;

import java.util.LinkedList;
import java.util.List;

/**
 * Manages application-level migrations.
 *
 * Migrations can be slotted to occur based on changes in the canonical version code
 * (see {@link Util#getCanonicalVersionCode()}).
 *
 * Migrations are performed via {@link MigrationJob}s. These jobs are durable and are run before any
 * other job, allowing you to schedule safe migrations. Furthermore, you may specify that a
 * migration is UI-blocking, at which point we will show a spinner via
 * {@link ApplicationMigrationActivity} if the user opens the app while the migration is in
 * progress.
 */
public class ApplicationMigrations {

  private static final String TAG = Log.tag(ApplicationMigrations.class);

  private static final MutableLiveData<Boolean> UI_BLOCKING_MIGRATION_RUNNING = new MutableLiveData<>();

  private static final class Version {
    static final int LEGACY = 455;
  }

  /**
   * This *must* be called after the {@link JobManager} has been instantiated, but *before* the call
   * to {@link JobManager#beginJobLoop()}. Otherwise, other non-migration jobs may have started
   * executing before we add the migration jobs.
   */
  public static void onApplicationCreate(@NonNull Context context, @NonNull JobManager jobManager) {
    if (!isUpdate(context)) {
      Log.d(TAG, "Not an update. Skipping.");
      return;
    }

    final int currentVersion  = Util.getCanonicalVersionCode();
    final int lastSeenVersion = VersionTracker.getLastSeenVersion(context);

    Log.d(TAG, "currentVersion: " + currentVersion + "  lastSeenVersion: " + lastSeenVersion);

    List<MigrationJob> migrationJobs = getMigrationJobs(context, lastSeenVersion);

    if (migrationJobs.size() > 0) {
      Log.i(TAG, "About to enqueue " + migrationJobs.size() + " migration(s).");

      boolean uiBlocking = Stream.of(migrationJobs).reduce(false, (existing, job) -> existing || job.isUiBlocking());
      UI_BLOCKING_MIGRATION_RUNNING.postValue(uiBlocking);

      if (uiBlocking) {
        Log.i(TAG, "Migration set is UI-blocking.");
      } else {
        Log.i(TAG, "Migration set is non-UI-blocking.");
      }

      for (MigrationJob job : migrationJobs) {
        jobManager.add(job);
      }

      jobManager.add(new MigrationCompleteJob(currentVersion));

      final long startTime = System.currentTimeMillis();

      EventBus.getDefault().register(new Object() {
        @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
        public void onMigrationComplete(MigrationCompleteEvent event) {
          Log.i(TAG, "Received MigrationCompleteEvent for version " + event.getVersion() + ".");

          if (event.getVersion() == currentVersion) {
            Log.i(TAG, "Migration complete. Took " + (System.currentTimeMillis() - startTime) + " ms.");
            EventBus.getDefault().unregister(this);

            VersionTracker.updateLastSeenVersion(context);
            UI_BLOCKING_MIGRATION_RUNNING.postValue(false);
          } else {
            Log.i(TAG, "Version doesn't match. Looking for " + currentVersion + ", but received " + event.getVersion() + ".");
          }
        }
      });
    } else {
      Log.d(TAG, "No migrations.");
      VersionTracker.updateLastSeenVersion(context);
      UI_BLOCKING_MIGRATION_RUNNING.postValue(false);
    }
  }

  /**
   * @return A {@link LiveData} object that will update with whether or not a UI blocking migration
   * is in progress.
   */
  public static LiveData<Boolean> isUiBlockingMigrationRunning() {
    return UI_BLOCKING_MIGRATION_RUNNING;
  }

  /**
   * @return Whether or not we're in the middle of an update, as determined by the last seen and
   * current version.
   */
  public static boolean isUpdate(Context context) {
    int currentVersionCode  = Util.getCanonicalVersionCode();
    int previousVersionCode = VersionTracker.getLastSeenVersion(context);

    return previousVersionCode < currentVersionCode;
  }

  private static List<MigrationJob> getMigrationJobs(@NonNull Context context, int lastSeenVersion) {
    List<MigrationJob> jobs = new LinkedList<>();

    if (lastSeenVersion < Version.LEGACY) {
      jobs.add(new LegacyMigrationJob());
    }

    return jobs;
  }
}
