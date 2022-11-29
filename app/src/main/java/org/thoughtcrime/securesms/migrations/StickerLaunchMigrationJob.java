package org.thoughtcrime.securesms.migrations;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.StickerTable;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.MultiDeviceStickerPackOperationJob;
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob;
import org.thoughtcrime.securesms.stickers.BlessedPacks;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class StickerLaunchMigrationJob extends MigrationJob {

  public static final String KEY = "StickerLaunchMigrationJob";

  StickerLaunchMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private StickerLaunchMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public boolean isUiBlocking() {
    return false;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void performMigration() {
    installPack(context, BlessedPacks.ZOZO);
    installPack(context, BlessedPacks.BANDIT);
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  private static void installPack(@NonNull Context context, @NonNull BlessedPacks.Pack pack) {
    JobManager   jobManager      = ApplicationDependencies.getJobManager();
    StickerTable stickerDatabase = SignalDatabase.stickers();

    if (stickerDatabase.isPackAvailableAsReference(pack.getPackId())) {
      stickerDatabase.markPackAsInstalled(pack.getPackId(), false);
    }

    jobManager.add(StickerPackDownloadJob.forInstall(pack.getPackId(), pack.getPackKey(), false));

    if (TextSecurePreferences.isMultiDevice(context)) {
      jobManager.add(new MultiDeviceStickerPackOperationJob(pack.getPackId(), pack.getPackKey(), MultiDeviceStickerPackOperationJob.Type.INSTALL));
    }
  }

  public static class Factory implements Job.Factory<StickerLaunchMigrationJob> {
    @Override
    public @NonNull
    StickerLaunchMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new StickerLaunchMigrationJob(parameters);
    }
  }
}
