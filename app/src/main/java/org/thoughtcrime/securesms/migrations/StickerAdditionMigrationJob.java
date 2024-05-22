package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob;
import org.thoughtcrime.securesms.stickers.BlessedPacks;

import java.util.Arrays;
import java.util.List;

/**
 * Migration job for installing new blessed packs as references. This means that the packs will
 * show up in the list as available blessed packs, but they *won't* be auto-installed.
 */
public class StickerAdditionMigrationJob extends MigrationJob {

  public static final String KEY = "StickerInstallMigrationJob";

  private static String TAG = Log.tag(StickerAdditionMigrationJob.class);

  private static final String KEY_PACKS = "packs";

  private final List<BlessedPacks.Pack> packs;

  StickerAdditionMigrationJob(@NonNull BlessedPacks.Pack... packs) {
    this(new Parameters.Builder().build(), Arrays.asList(packs));
  }

  private StickerAdditionMigrationJob(@NonNull Parameters parameters, @NonNull List<BlessedPacks.Pack> packs) {
    super(parameters);
    this.packs = packs;
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
  public @Nullable byte[] serialize() {
    String[] packsRaw = Stream.of(packs).map(BlessedPacks.Pack::toJson).toArray(String[]::new);
    return new JsonJobData.Builder().putStringArray(KEY_PACKS, packsRaw).serialize();
  }

  @Override
  public void performMigration() {
    JobManager jobManager = AppDependencies.getJobManager();

    for (BlessedPacks.Pack pack : packs) {
      Log.i(TAG, "Installing reference for blessed pack: " + pack.getPackId());
      jobManager.add(StickerPackDownloadJob.forReference(pack.getPackId(), pack.getPackKey()));
    }
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<StickerAdditionMigrationJob> {
    @Override
    public @NonNull StickerAdditionMigrationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData             data  = JsonJobData.deserialize(serializedData);
      String[]                raw   = data.getStringArray(KEY_PACKS);
      List<BlessedPacks.Pack> packs = Stream.of(raw).map(BlessedPacks.Pack::fromJson).toList();

      return new StickerAdditionMigrationJob(parameters, packs);
    }
  }
}
