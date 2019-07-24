package org.thoughtcrime.securesms.stickers;

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.StickerDatabase;
import org.thoughtcrime.securesms.database.StickerDatabase.StickerPackRecordReader;
import org.thoughtcrime.securesms.database.model.StickerPackRecord;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.MultiDeviceStickerPackOperationJob;
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.ArrayList;
import java.util.List;

final class StickerManagementRepository {

  private final Context            context;
  private final StickerDatabase    stickerDatabase;
  private final AttachmentDatabase attachmentDatabase;

  StickerManagementRepository(@NonNull Context context) {
    this.context            = context.getApplicationContext();
    this.stickerDatabase    = DatabaseFactory.getStickerDatabase(context);
    this.attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context);
  }

  void deleteOrphanedStickerPacks() {
    SignalExecutors.SERIAL.execute(stickerDatabase::deleteOrphanedPacks);
  }

  void fetchUnretrievedReferencePacks() {
    SignalExecutors.SERIAL.execute(() -> {
      JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();

      try (Cursor cursor = attachmentDatabase.getUnavailableStickerPacks()) {
        while (cursor != null && cursor.moveToNext()) {
          String packId  = cursor.getString(cursor.getColumnIndexOrThrow(AttachmentDatabase.STICKER_PACK_ID));
          String packKey = cursor.getString(cursor.getColumnIndexOrThrow(AttachmentDatabase.STICKER_PACK_KEY));

          jobManager.add(new StickerPackDownloadJob(packId, packKey, true));
        }
      }
    });
  }

  void getStickerPacks(@NonNull Callback<PackResult> callback) {
    SignalExecutors.SERIAL.execute(() -> {
      List<StickerPackRecord> installedPacks = new ArrayList<>();
      List<StickerPackRecord> availablePacks = new ArrayList<>();
      List<StickerPackRecord> blessedPacks   = new ArrayList<>();

      try (StickerPackRecordReader reader = new StickerPackRecordReader(stickerDatabase.getAllStickerPacks())) {
        StickerPackRecord record;
        while ((record = reader.getNext()) != null) {
          if (record.isInstalled()) {
            installedPacks.add(record);
          } else if (BlessedPacks.contains(record.getPackId())) {
            blessedPacks.add(record);
          } else {
            availablePacks.add(record);
          }
        }
      }

      callback.onComplete(new PackResult(installedPacks, availablePacks, blessedPacks));
    });
  }

  void uninstallStickerPack(@NonNull String packId, @NonNull String packKey) {
    SignalExecutors.SERIAL.execute(() -> {
      stickerDatabase.uninstallPack(packId);

      if (TextSecurePreferences.isMultiDevice(context)) {
        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new MultiDeviceStickerPackOperationJob(packId, packKey, MultiDeviceStickerPackOperationJob.Type.REMOVE));
      }
    });
  }

  void installStickerPack(@NonNull String packId, @NonNull String packKey) {
    SignalExecutors.SERIAL.execute(() -> {
      JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();

      if (stickerDatabase.isPackAvailableAsReference(packId)) {
        stickerDatabase.markPackAsInstalled(packId);
      }

      jobManager.add(new StickerPackDownloadJob(packId, packKey, false));

      if (TextSecurePreferences.isMultiDevice(context)) {
        jobManager.add(new MultiDeviceStickerPackOperationJob(packId, packKey, MultiDeviceStickerPackOperationJob.Type.INSTALL));
      }
    });
  }

  static class PackResult {

    private final List<StickerPackRecord> installedPacks;
    private final List<StickerPackRecord> availablePacks;
    private final List<StickerPackRecord> blessedPacks;

    PackResult(@NonNull List<StickerPackRecord> installedPacks,
               @NonNull List<StickerPackRecord> availablePacks,
               @NonNull List<StickerPackRecord> blessedPacks)
    {
      this.installedPacks = installedPacks;
      this.availablePacks = availablePacks;
      this.blessedPacks   = blessedPacks;
    }

    @NonNull List<StickerPackRecord> getInstalledPacks() {
      return installedPacks;
    }

    @NonNull List<StickerPackRecord> getAvailablePacks() {
      return availablePacks;
    }

    @NonNull List<StickerPackRecord> getBlessedPacks() {
      return blessedPacks;
    }
  }

  interface Callback<T> {
    void onComplete(T result);
  }
}
