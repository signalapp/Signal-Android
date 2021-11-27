package org.thoughtcrime.securesms.wallpaper;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.util.storage.FileStorage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Manages the storage of custom wallpaper files.
 */
public final class WallpaperStorage {

  private static final String TAG = Log.tag(WallpaperStorage.class);

  private static final String DIRECTORY     = "wallpapers";
  private static final String FILENAME_BASE = "wallpaper";

  /**
   * Saves the provided input stream as a new wallpaper file.
   */
  @WorkerThread
  public static @NonNull ChatWallpaper save(@NonNull Context context, @NonNull InputStream wallpaperStream, @NonNull String extension) throws IOException {
    String name = FileStorage.save(context, wallpaperStream, DIRECTORY, FILENAME_BASE, extension);

    return ChatWallpaperFactory.create(PartAuthority.getWallpaperUri(name));
  }

  @WorkerThread
  public static @NonNull InputStream read(@NonNull Context context, String filename) throws IOException {
    return FileStorage.read(context, DIRECTORY, filename);
  }

  @WorkerThread
  public static @NonNull List<ChatWallpaper> getAll(@NonNull Context context) {
    return FileStorage.getAll(context, DIRECTORY, FILENAME_BASE)
                      .stream()
                      .map(PartAuthority::getWallpaperUri)
                      .map(ChatWallpaperFactory::create)
                      .collect(Collectors.toList());
  }

  /**
   * Called when wallpaper is deselected. This will check anywhere the wallpaper could be used, and
   * if we discover it's unused, we'll delete the file.
   */
  @WorkerThread
  public static void onWallpaperDeselected(@NonNull Context context, @NonNull Uri uri) {
    Uri globalUri = SignalStore.wallpaper().getWallpaperUri();
    if (Objects.equals(uri, globalUri)) {
      return;
    }

    int recipientCount = SignalDatabase.recipients().getWallpaperUriUsageCount(uri);
    if (recipientCount > 0) {
      return;
    }

    String filename      = PartAuthority.getWallpaperFilename(uri);
    File   directory     = context.getDir(DIRECTORY, Context.MODE_PRIVATE);
    File   wallpaperFile = new File(directory, filename);

    if (!wallpaperFile.delete()) {
      Log.w(TAG, "Failed to delete " + filename + "!");
    }
  }
}
