package org.thoughtcrime.securesms.stickers;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.StickerTable;
import org.thoughtcrime.securesms.database.model.StickerPackRecord;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.signal.core.util.Hex;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SignalServiceStickerManifest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class StickerPackPreviewRepository {

  private static final String TAG = Log.tag(StickerPackPreviewRepository.class);

  private final StickerTable                 stickerDatabase;
  private final SignalServiceMessageReceiver receiver;

  public StickerPackPreviewRepository(@NonNull Context context) {
    this.receiver        = ApplicationDependencies.getSignalServiceMessageReceiver();
    this.stickerDatabase = SignalDatabase.stickers();
  }

  public void getStickerManifest(@NonNull String packId,
                                 @NonNull String packKey,
                                 @NonNull Callback<Optional<StickerManifestResult>> callback)
  {
    SignalExecutors.UNBOUNDED.execute(() -> {
      Optional<StickerManifestResult> localManifest = getManifestFromDatabase(packId);

      if (localManifest.isPresent()) {
        Log.d(TAG, "Found manifest locally.");
        callback.onComplete(localManifest);
      } else {
        Log.d(TAG, "Looking for manifest remotely.");
        callback.onComplete(getManifestRemote(packId, packKey));
      }
    });
  }

  @WorkerThread
  private Optional<StickerManifestResult> getManifestFromDatabase(@NonNull String packId) {
    StickerPackRecord record = stickerDatabase.getStickerPack(packId);

    if (record != null && record.isInstalled()) {
      StickerManifest.Sticker       cover    = toSticker(record.getCover());
      List<StickerManifest.Sticker> stickers = getStickersFromDatabase(packId);

      StickerManifest manifest = new StickerManifest(record.getPackId(),
                                                     record.getPackKey(),
                                                     record.getTitle(),
                                                     record.getAuthor(),
                                                     Optional.of(cover),
                                                     stickers);

      return Optional.of(new StickerManifestResult(manifest, record.isInstalled()));
    }

    return Optional.empty();
  }

  @WorkerThread
  private Optional<StickerManifestResult> getManifestRemote(@NonNull String packId, @NonNull String packKey) {
    try {
      byte[]                       packIdBytes    = Hex.fromStringCondensed(packId);
      byte[]                       packKeyBytes   = Hex.fromStringCondensed(packKey);
      SignalServiceStickerManifest remoteManifest = receiver.retrieveStickerManifest(packIdBytes, packKeyBytes);
      StickerManifest              localManifest  = new StickerManifest(packId,
                                                                        packKey,
                                                                        remoteManifest.getTitle(),
                                                                        remoteManifest.getAuthor(),
                                                                        toOptionalSticker(packId, packKey, remoteManifest.getCover()),
                                                                        Stream.of(remoteManifest.getStickers())
                                                                              .map(s -> toSticker(packId, packKey, s))
                                                                              .toList());

      return Optional.of(new StickerManifestResult(localManifest, false));
    } catch (IOException | InvalidMessageException e) {
      Log.w(TAG, "Failed to retrieve pack manifest.", e);
    }

    return Optional.empty();
  }

  @WorkerThread
  private List<StickerManifest.Sticker> getStickersFromDatabase(@NonNull String packId) {
    List<StickerManifest.Sticker> stickers = new ArrayList<>();

    try (Cursor cursor = stickerDatabase.getStickersForPack(packId)) {
      StickerTable.StickerRecordReader reader = new StickerTable.StickerRecordReader(cursor);

      StickerRecord record;
      while ((record = reader.getNext()) != null) {
        stickers.add(toSticker(record));
      }
    }

    return stickers;
  }


  private Optional<StickerManifest.Sticker> toOptionalSticker(@NonNull String packId,
                                                              @NonNull String packKey,
                                                              @NonNull Optional<SignalServiceStickerManifest.StickerInfo> remoteSticker)
  {
    return remoteSticker.isPresent() ? Optional.of(toSticker(packId, packKey, remoteSticker.get()))
                                     : Optional.empty();
  }

  private StickerManifest.Sticker toSticker(@NonNull String packId,
                                            @NonNull String packKey,
                                            @NonNull SignalServiceStickerManifest.StickerInfo remoteSticker)
  {
    return new StickerManifest.Sticker(packId, packKey, remoteSticker.getId(), remoteSticker.getEmoji(), remoteSticker.getContentType());
  }

  private StickerManifest.Sticker toSticker(@NonNull StickerRecord record) {
    return new StickerManifest.Sticker(record.getPackId(), record.getPackKey(), record.getStickerId(), record.getEmoji(), record.getContentType(), record.getUri());
  }

  static class StickerManifestResult {
    private final StickerManifest manifest;
    private final boolean         isInstalled;

    StickerManifestResult(StickerManifest manifest, boolean isInstalled) {
      this.manifest    = manifest;
      this.isInstalled = isInstalled;
    }

    public StickerManifest getManifest() {
      return manifest;
    }

    public boolean isInstalled() {
      return isInstalled;
    }
  }

  interface Callback<T> {
    void onComplete(T result);
  }
}
