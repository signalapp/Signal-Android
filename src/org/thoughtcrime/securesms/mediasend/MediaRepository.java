package org.thoughtcrime.securesms.mediasend;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.OpenableColumns;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import android.util.Pair;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.File;
import java.io.IOException;
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

  /**
   * Given an existing list of {@link Media}, this will ensure that the media is populate with as
   * much data as we have, like width/height.
   */
  void getPopulatedMedia(@NonNull Context context, @NonNull List<Media> media, @NonNull Callback<List<Media>> callback) {
    if (Stream.of(media).allMatch(this::isPopulated)) {
      callback.onComplete(media);
      return;
    }

    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> callback.onComplete(getPopulatedMedia(context, media)));
  }

  @WorkerThread
  private @NonNull List<MediaFolder> getFolders(@NonNull Context context) {
    FolderResult imageFolders       = getFolders(context, Images.Media.EXTERNAL_CONTENT_URI);
    FolderResult videoFolders       = getFolders(context, Video.Media.EXTERNAL_CONTENT_URI);
    Map<String, FolderData> folders = new HashMap<>(imageFolders.getFolderData());

    for (Map.Entry<String, FolderData> entry : videoFolders.getFolderData().entrySet()) {
      if (folders.containsKey(entry.getKey())) {
        folders.get(entry.getKey()).incrementCount(entry.getValue().getCount());
      } else {
        folders.put(entry.getKey(), entry.getValue());
      }
    }

    String            cameraBucketId = imageFolders.getCameraBucketId() != null ? imageFolders.getCameraBucketId() : videoFolders.getCameraBucketId();
    FolderData        cameraFolder   = cameraBucketId != null ? folders.remove(cameraBucketId) : null;
    List<MediaFolder> mediaFolders   = Stream.of(folders.values()).map(folder -> new MediaFolder(folder.getThumbnail(),
                                                                                                 folder.getTitle(),
                                                                                                 folder.getCount(),
                                                                                                 folder.getBucketId(),
                                                                                                 MediaFolder.FolderType.NORMAL))
                                                                  .sorted((o1, o2) -> o1.getTitle().toLowerCase().compareTo(o2.getTitle().toLowerCase()))
                                                                  .toList();

    Uri allMediaThumbnail = imageFolders.getThumbnailTimestamp() > videoFolders.getThumbnailTimestamp() ? imageFolders.getThumbnail() : videoFolders.getThumbnail();

    if (allMediaThumbnail != null) {
      int allMediaCount = Stream.of(mediaFolders).reduce(0, (count, folder) -> count + folder.getItemCount());

      if (cameraFolder != null) {
        allMediaCount += cameraFolder.getCount();
      }

      mediaFolders.add(0, new MediaFolder(allMediaThumbnail, context.getString(R.string.MediaRepository_all_media), allMediaCount, Media.ALL_MEDIA_BUCKET_ID, MediaFolder.FolderType.NORMAL));
    }

    if (cameraFolder != null) {
      mediaFolders.add(0, new MediaFolder(cameraFolder.getThumbnail(), cameraFolder.getTitle(), cameraFolder.getCount(), cameraFolder.getBucketId(), MediaFolder.FolderType.CAMERA));
    }

    return mediaFolders;
  }

  @WorkerThread
  private @NonNull FolderResult getFolders(@NonNull Context context, @NonNull Uri contentUri) {
    String                  cameraPath         = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + File.separator + "Camera";
    String                  cameraBucketId     = null;
    Uri                     globalThumbnail    = null;
    long                    thumbnailTimestamp = 0;
    Map<String, FolderData> folders            = new HashMap<>();

    String[] projection = new String[] { Images.Media.DATA, Images.Media.BUCKET_ID, Images.Media.BUCKET_DISPLAY_NAME, Images.Media.DATE_TAKEN };
    String   selection  = Images.Media.DATA + " NOT NULL";
    String   sortBy     = Images.Media.BUCKET_DISPLAY_NAME + " COLLATE NOCASE ASC, " + Images.Media.DATE_TAKEN + " DESC";

    try (Cursor cursor = context.getContentResolver().query(contentUri, projection, selection, null, sortBy)) {
      while (cursor != null && cursor.moveToNext()) {
        String     path      = cursor.getString(cursor.getColumnIndexOrThrow(projection[0]));
        Uri        thumbnail = Uri.fromFile(new File(path));
        String     bucketId  = cursor.getString(cursor.getColumnIndexOrThrow(projection[1]));
        String     title     = cursor.getString(cursor.getColumnIndexOrThrow(projection[2]));
        long       timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(projection[3]));
        FolderData folder    = Util.getOrDefault(folders, bucketId, new FolderData(thumbnail, title, bucketId));

        folder.incrementCount();
        folders.put(bucketId, folder);

        if (cameraBucketId == null && path.startsWith(cameraPath)) {
          cameraBucketId = bucketId;
        }

        if (timestamp > thumbnailTimestamp) {
          globalThumbnail    = thumbnail;
          thumbnailTimestamp = timestamp;
        }
      }
    }

    return new FolderResult(cameraBucketId, globalThumbnail, thumbnailTimestamp, folders);
  }

  @WorkerThread
  private @NonNull List<Media> getMediaInBucket(@NonNull Context context, @NonNull String bucketId) {
    List<Media> images = getMediaInBucket(context, bucketId, Images.Media.EXTERNAL_CONTENT_URI, true);
    List<Media> videos = getMediaInBucket(context, bucketId, Video.Media.EXTERNAL_CONTENT_URI, false);
    List<Media> media  = new ArrayList<>(images.size() + videos.size());

    media.addAll(images);
    media.addAll(videos);
    Collections.sort(media, (o1, o2) -> Long.compare(o2.getDate(), o1.getDate()));

    return media;
  }

  @WorkerThread
  private @NonNull List<Media> getMediaInBucket(@NonNull Context context, @NonNull String bucketId, @NonNull Uri contentUri, boolean hasOrienation) {
    List<Media> media         = new LinkedList<>();
    String      selection     = Images.Media.BUCKET_ID + " = ? AND " + Images.Media.DATA + " NOT NULL";
    String[]    selectionArgs = new String[] { bucketId };
    String      sortBy        = Images.Media.DATE_TAKEN + " DESC";

    String[] projection;

    if (hasOrienation) {
      projection = new String[]{Images.Media._ID, Images.Media.MIME_TYPE, Images.Media.DATE_TAKEN, Images.Media.ORIENTATION, Images.Media.WIDTH, Images.Media.HEIGHT, Images.Media.SIZE};
    } else {
      projection = new String[]{Images.Media._ID, Images.Media.MIME_TYPE, Images.Media.DATE_TAKEN, Images.Media.WIDTH, Images.Media.HEIGHT, Images.Media.SIZE};
    }

    if (Media.ALL_MEDIA_BUCKET_ID.equals(bucketId)) {
      selection     = Images.Media.DATA + " NOT NULL";
      selectionArgs = null;
    }

    try (Cursor cursor = context.getContentResolver().query(contentUri, projection, selection, selectionArgs, sortBy)) {
      while (cursor != null && cursor.moveToNext()) {
        Uri    uri         = Uri.withAppendedPath(contentUri, cursor.getString(cursor.getColumnIndexOrThrow(Images.Media._ID)));
        String mimetype    = cursor.getString(cursor.getColumnIndexOrThrow(Images.Media.MIME_TYPE));
        long   dateTaken   = cursor.getLong(cursor.getColumnIndexOrThrow(Images.Media.DATE_TAKEN));
        int    orientation = hasOrienation ? cursor.getInt(cursor.getColumnIndexOrThrow(Images.Media.ORIENTATION)) : 0;
        int    width       = cursor.getInt(cursor.getColumnIndexOrThrow(getWidthColumn(orientation)));
        int    height      = cursor.getInt(cursor.getColumnIndexOrThrow(getHeightColumn(orientation)));
        long   size        = cursor.getLong(cursor.getColumnIndexOrThrow(Images.Media.SIZE));

        media.add(new Media(uri, mimetype, dateTaken, width, height, size, Optional.of(bucketId), Optional.absent()));
      }
    }

    return media;
  }

  @WorkerThread
  private List<Media> getPopulatedMedia(@NonNull Context context, @NonNull List<Media> media) {
    return Stream.of(media).map(m -> {
      try {
        if (isPopulated(m)) {
          return m;
        } else if (PartAuthority.isLocalUri(m.getUri())) {
          return getLocallyPopulatedMedia(context, m);
        } else {
          return getContentResolverPopulatedMedia(context, m);
        }
      } catch (IOException e) {
        return m;
      }
    }).toList();
  }

  @TargetApi(16)
  @SuppressWarnings("SuspiciousNameCombination")
  private String getWidthColumn(int orientation) {
    if (orientation == 0 || orientation == 180) return Images.Media.WIDTH;
    else                                        return Images.Media.HEIGHT;
  }

  @TargetApi(16)
  @SuppressWarnings("SuspiciousNameCombination")
  private String getHeightColumn(int orientation) {
    if (orientation == 0 || orientation == 180) return Images.Media.HEIGHT;
    else                                        return Images.Media.WIDTH;
  }

  private boolean isPopulated(@NonNull Media media) {
    return media.getWidth() > 0 && media.getHeight() > 0 && media.getSize() > 0;
  }

  private Media getLocallyPopulatedMedia(@NonNull Context context, @NonNull Media media) throws IOException {
    int  width  = media.getWidth();
    int  height = media.getHeight();
    long size   = media.getSize();

    if (size <= 0) {
      Optional<Long> optionalSize = Optional.fromNullable(PartAuthority.getAttachmentSize(context, media.getUri()));
      size = optionalSize.isPresent() ? optionalSize.get() : 0;
    }

    if (size <= 0) {
      size = MediaUtil.getMediaSize(context, media.getUri());
    }

    if (width == 0 || height == 0) {
      Pair<Integer, Integer> dimens = MediaUtil.getDimensions(context, media.getMimeType(), media.getUri());
      width  = dimens.first;
      height = dimens.second;
    }

    return new Media(media.getUri(), media.getMimeType(), media.getDate(), width, height, size, media.getBucketId(), media.getCaption());
  }

  private Media getContentResolverPopulatedMedia(@NonNull Context context, @NonNull Media media) throws IOException {
    int  width  = media.getWidth();
    int  height = media.getHeight();
    long size   = media.getSize();

    if (size <= 0) {
      try (Cursor cursor = context.getContentResolver().query(media.getUri(), null, null, null, null)) {
        if (cursor != null && cursor.moveToFirst() && cursor.getColumnIndex(OpenableColumns.SIZE) >= 0) {
          size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
        }
      }
    }

    if (size <= 0) {
      size = MediaUtil.getMediaSize(context, media.getUri());
    }

    if (width == 0 || height == 0) {
      Pair<Integer, Integer> dimens = MediaUtil.getDimensions(context, media.getMimeType(), media.getUri());
      width  = dimens.first;
      height = dimens.second;
    }

    return new Media(media.getUri(), media.getMimeType(), media.getDate(), width, height, size, media.getBucketId(), media.getCaption());
  }

  private static class FolderResult {
    private final String                  cameraBucketId;
    private final Uri                     thumbnail;
    private final long                    thumbnailTimestamp;
    private final Map<String, FolderData> folderData;

    private FolderResult(@Nullable String cameraBucketId,
                         @Nullable Uri thumbnail,
                         long thumbnailTimestamp,
                         @NonNull Map<String, FolderData> folderData)
    {
      this.cameraBucketId     = cameraBucketId;
      this.thumbnail          = thumbnail;
      this.thumbnailTimestamp = thumbnailTimestamp;
      this.folderData         = folderData;
    }

    @Nullable String getCameraBucketId() {
      return cameraBucketId;
    }

    @Nullable Uri getThumbnail() {
      return thumbnail;
    }

    long getThumbnailTimestamp() {
      return thumbnailTimestamp;
    }

    @NonNull Map<String, FolderData> getFolderData() {
      return folderData;
    }
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
