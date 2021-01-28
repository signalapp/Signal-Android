package org.thoughtcrime.securesms.wallpaper;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mms.PartAuthority;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    File directory = context.getDir(DIRECTORY, Context.MODE_PRIVATE);
    File file      = File.createTempFile(FILENAME_BASE, "." + extension, directory);

    StreamUtil.copy(wallpaperStream, getOutputStream(context, file));

    return ChatWallpaperFactory.create(PartAuthority.getWallpaperUri(file.getName()));
  }

  @WorkerThread
  public static @NonNull InputStream read(@NonNull Context context, String filename) throws IOException {
    File directory     = context.getDir(DIRECTORY, Context.MODE_PRIVATE);
    File wallpaperFile = new File(directory, filename);

    return getInputStream(context, wallpaperFile);
  }

  @WorkerThread
  public static @NonNull List<ChatWallpaper> getAll(@NonNull Context context) {
    File   directory = context.getDir(DIRECTORY, Context.MODE_PRIVATE);
    File[] allFiles  = directory.listFiles(pathname -> pathname.getName().contains(FILENAME_BASE));

    if (allFiles != null) {
      return Stream.of(allFiles)
                   .map(File::getName)
                   .map(PartAuthority::getWallpaperUri)
                   .map(ChatWallpaperFactory::create)
                   .toList();
    } else {
      return Collections.emptyList();
    }
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

    int recipientCount = DatabaseFactory.getRecipientDatabase(context).getWallpaperUriUsageCount(uri);
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

  private static @NonNull OutputStream getOutputStream(@NonNull Context context, File outputFile) throws IOException {
    AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
    return ModernEncryptingPartOutputStream.createFor(attachmentSecret, outputFile, true).second;
  }

  private static @NonNull InputStream getInputStream(@NonNull Context context, File inputFile) throws IOException {
    AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
    return ModernDecryptingPartInputStream.createFor(attachmentSecret, inputFile, 0);
  }
}
