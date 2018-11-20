package org.thoughtcrime.securesms.mediasend;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Handles the retrieval of media present on the user's device.
 */
class MediaRepository {

  /**
   * Retrieves a list of folders that contain media.
   */
  void getFolders(@NonNull Context context, @NonNull Callback<List<MediaFolder>> callback) {
    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> callback.onComplete(getFolders(context)));
  }

  /**
   * Retrieves a list of media items (images and videos) that are present int he specified bucket.
   */
  void getMediaInBucket(@NonNull Context context, @NonNull String bucketId, @NonNull Callback<List<Media>> callback) {
    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> callback.onComplete(getMediaInBucket(context, bucketId)));
  }

  @WorkerThread
  private @NonNull List<MediaFolder> getFolders(@NonNull Context context) {
    Pair<String, Map<String, FolderData>> imageFolders = getFolders(context, Images.Media.EXTERNAL_CONTENT_URI);
    Pair<String, Map<String, FolderData>> videoFolders = getFolders(context, Video.Media.EXTERNAL_CONTENT_URI);
    Map<String, FolderData>               folders      = new HashMap<>(imageFolders.second());

    for (Map.Entry<String, FolderData> entry : videoFolders.second().entrySet()) {
      if (folders.containsKey(entry.getKey())) {
        folders.get(entry.getKey()).incrementCount(entry.getValue().getCount());
      } else {
        folders.put(entry.getKey(), entry.getValue());
      }
    }

    String            cameraBucketId = imageFolders.first() != null ? imageFolders.first() : videoFolders.first();
    FolderData        cameraFolder   = cameraBucketId != null ? folders.remove(cameraBucketId) : null;
    List<MediaFolder> mediaFolders   = Stream.of(folders.values()).map(folder -> new MediaFolder(folder.getThumbnail(),
                                                                                                 folder.getTitle(),
                                                                                                 folder.getCount(),
                                                                                                 folder.getBucketId(),
                                                                                                 MediaFolder.FolderType.NORMAL))
                                                                  .sorted((o1, o2) -> o1.getTitle().toLowerCase().compareTo(o2.getTitle().toLowerCase()))
                                                                  .toList();

    if (cameraFolder != null) {
      mediaFolders.add(0, new MediaFolder(cameraFolder.getThumbnail(), cameraFolder.getTitle(), cameraFolder.getCount(), cameraFolder.getBucketId(), MediaFolder.FolderType.CAMERA));
    }

    return mediaFolders;
  }

  @WorkerThread
  private @NonNull Pair<String, Map<String, FolderData>> getFolders(@NonNull Context context, @NonNull Uri contentUri) {
    String                  cameraPath     = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + File.separator + "Camera";
    String                  cameraBucketId = null;
    Map<String, FolderData> folders        = new HashMap<>();

    String[] projection = new String[] { Images.Media.DATA, Images.Media.BUCKET_ID, Images.Media.BUCKET_DISPLAY_NAME };
    String   selection  = Images.Media.DATA + " NOT NULL";
    String   sortBy     = Images.Media.BUCKET_DISPLAY_NAME + " COLLATE NOCASE ASC, " + Images.Media.DATE_TAKEN + " DESC";

    try (Cursor cursor = context.getContentResolver().query(contentUri, projection, selection, null, sortBy)) {
      while (cursor != null && cursor.moveToNext()) {
        String     path      = cursor.getString(cursor.getColumnIndexOrThrow(projection[0]));
        Uri        thumbnail = Uri.fromFile(new File(path));
        String     bucketId  = cursor.getString(cursor.getColumnIndexOrThrow(projection[1]));
        String     title     = cursor.getString(cursor.getColumnIndexOrThrow(projection[2]));
        FolderData folder    = Util.getOrDefault(folders, bucketId, new FolderData(thumbnail, title, bucketId));

        folder.incrementCount();
        folders.put(bucketId, folder);

        if (cameraBucketId == null && path.startsWith(cameraPath)) {
          cameraBucketId = bucketId;
        }
      }
    }

    return new Pair<>(cameraBucketId, folders);
  }

  @WorkerThread
  private @NonNull List<Media> getMediaInBucket(@NonNull Context context, @NonNull String bucketId) {
    List<Media> images = getMediaInBucket(context, bucketId, Images.Media.EXTERNAL_CONTENT_URI);
    List<Media> videos = getMediaInBucket(context, bucketId, Video.Media.EXTERNAL_CONTENT_URI);
    List<Media> media  = new ArrayList<>(images.size() + videos.size());

    media.addAll(images);
    media.addAll(videos);
    Collections.sort(media, (o1, o2) -> Long.compare(o2.getDate(), o1.getDate()));

    return media;
  }

  @WorkerThread
  private @NonNull List<Media> getMediaInBucket(@NonNull Context context, @NonNull String bucketId, @NonNull Uri contentUri) {
    List<Media> media      = new LinkedList<>();
    String      selection  = Images.Media.BUCKET_ID + " = ? AND " + Images.Media.DATA + " NOT NULL";
    String      sortBy     = Images.Media.DATE_TAKEN + " DESC";
    String[]    projection = Build.VERSION.SDK_INT >= 16 ? new String[] { Images.Media._ID, Images.Media.MIME_TYPE, Images.Media.DATE_TAKEN, Images.Media.WIDTH, Images.Media.HEIGHT }
                                                         : new String[] { Images.Media._ID, Images.Media.MIME_TYPE, Images.Media.DATE_TAKEN };

    try (Cursor cursor = context.getContentResolver().query(contentUri, projection, selection, new String[] { bucketId }, sortBy)) {
      while (cursor != null && cursor.moveToNext()) {
        Uri    uri       = Uri.withAppendedPath(contentUri, cursor.getString(cursor.getColumnIndexOrThrow(projection[0])));
        String mimetype  = cursor.getString(cursor.getColumnIndexOrThrow(projection[1]));
        long   dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(projection[2]));
        int    width     = 0;
        int    height    = 0;

        if (Build.VERSION.SDK_INT >= 16) {
          width  = cursor.getInt(cursor.getColumnIndexOrThrow(projection[3]));
          height = cursor.getInt(cursor.getColumnIndexOrThrow(projection[4]));
        }

        media.add(new Media(uri, mimetype, dateTaken, width, height, Optional.of(bucketId), Optional.absent()));
      }
    }

    return media;
  }

  private static class FolderData {
    private final Uri    thumbnail;
    private final String title;
    private final String bucketId;

    private int count;

    private FolderData(Uri thumbnail, String title, String bucketId) {
      this.thumbnail = thumbnail;
      this.title     = title;
      this.bucketId  = bucketId;
    }

    Uri getThumbnail() {
      return thumbnail;
    }

    String getTitle() {
      return title;
    }

    String getBucketId() {
      return bucketId;
    }

    int getCount() {
      return count;
    }

    void incrementCount() {
      incrementCount(1);
    }

    void incrementCount(int amount) {
      count += amount;
    }
  }

  interface Callback<E> {
    void onComplete(@NonNull E result);
  }
}
