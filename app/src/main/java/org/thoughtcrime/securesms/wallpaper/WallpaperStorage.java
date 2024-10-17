package org.thoughtcrime.securesms.wallpaper;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.UploadAttachmentToArchiveJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.PartUriParser;

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

  /**
   * Saves the provided input stream as a new wallpaper file.
   */
  @WorkerThread
  public static @NonNull ChatWallpaper save(@NonNull InputStream wallpaperStream) throws IOException {
    AttachmentId attachmentId = SignalDatabase.attachments().insertWallpaper(wallpaperStream);

    if (SignalStore.backup().backsUpMedia()) {
      AppDependencies.getJobManager().add(new UploadAttachmentToArchiveJob(attachmentId));
    }

    return ChatWallpaperFactory.create(PartAuthority.getAttachmentDataUri(attachmentId));
  }

  @WorkerThread
  public static @NonNull List<ChatWallpaper> getAll() {
    return SignalDatabase.attachments()
                         .getAllWallpapers()
                         .stream()
                         .map(PartAuthority::getAttachmentDataUri)
                         .map(ChatWallpaperFactory::create)
                         .collect(Collectors.toList());
  }

  /**
   * Called when wallpaper is deselected. This will check anywhere the wallpaper could be used, and
   * if we discover it's unused, we'll delete the file.
   */
  @WorkerThread
  public static void onWallpaperDeselected(@NonNull Uri uri) {
    Uri globalUri = SignalStore.wallpaper().getWallpaperUri();
    if (Objects.equals(uri, globalUri)) {
      return;
    }

    int recipientCount = SignalDatabase.recipients().getWallpaperUriUsageCount(uri);
    if (recipientCount > 0) {
      return;
    }

    AttachmentId attachmentId = new PartUriParser(uri).getPartId();
    SignalDatabase.attachments().deleteAttachment(attachmentId);
  }
}
