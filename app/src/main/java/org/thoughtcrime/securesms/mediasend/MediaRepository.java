package org.thoughtcrime.securesms.mediasend;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.OpenableColumns;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.signal.core.util.SqlUtil;
import org.signal.core.util.Stopwatch;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Handles the retrieval of media present on the user's device.
 */
public class MediaRepository {

  private static final String TAG    = Log.tag(MediaRepository.class);
  private static final String CAMERA = "Camera";

  /**
   * Retrieves a list of folders that contain media.
   */
  public void getFolders(@NonNull Context context, @NonNull Callback<List<MediaFolder>> callback) {
    if (!StorageUtil.canReadFromMediaStore()) {
      Log.w(TAG, "No storage permissions!", new Throwable());
      callback.onComplete(Collections.emptyList());
      return;
    }

    SignalExecutors.BOUNDED.execute(() -> callback.onComplete(getFolders(context)));
  }

  /**
   * Retrieves a list of recent media items (images and videos).
   */
  public Single<List<Media>> getRecentMedia() {
    return Single.<List<Media>>fromCallable(() -> {
                   if (!StorageUtil.canReadFromMediaStore()) {
                     Log.w(TAG, "No storage permissions!", new Throwable());
                     return Collections.emptyList();
                   }

                   return getMediaInBucket(AppDependencies.getApplication(), Media.ALL_MEDIA_BUCKET_ID);
                 })
                 .onErrorReturn(t -> {
                   Log.w(TAG, "Unable to get recent media", t);
                   return Collections.emptyList();
                 })
                 .subscribeOn(Schedulers.io());
  }

  /**
   * Retrieves a list of media items (images and videos) that are present int he specified bucket.
   */
  public void getMediaInBucket(@NonNull Context context, @NonNull String bucketId, @NonNull Callback<List<Media>> callback) {
    if (!StorageUtil.canReadFromMediaStore()) {
      Log.w(TAG, "No storage permissions!", new Throwable());
      callback.onComplete(Collections.emptyList());
      return;
    }

    SignalExecutors.BOUNDED.execute(() -> callback.onComplete(getMediaInBucket(context, bucketId)));
  }

  /**
   * Given an existing list of {@link Media}, this will ensure that the media is populate with as
   * much data as we have, like width/height.
   */
  public void getPopulatedMedia(@NonNull Context context, @NonNull List<Media> media, @NonNull Callback<List<Media>> callback) {
    if (Stream.of(media).allMatch(this::isPopulated)) {
      callback.onComplete(media);
      return;
    }

    if (!StorageUtil.canReadFromMediaStore()) {
      Log.w(TAG, "No storage permissions!", new Throwable());
      callback.onComplete(media);
      return;
    }


    SignalExecutors.BOUNDED.execute(() -> callback.onComplete(getPopulatedMedia(context, media)));
  }

  void getMostRecentItem(@NonNull Context context, @NonNull Callback<Optional<Media>> callback) {
    if (!StorageUtil.canReadFromMediaStore()) {
      Log.w(TAG, "No storage permissions!", new Throwable());
      callback.onComplete(Optional.empty());
      return;
    }

    SignalExecutors.BOUNDED.execute(() -> callback.onComplete(getMostRecentItem(context)));
  }

  static void transformMedia(@NonNull Context context,
                             @NonNull List<Media> currentMedia,
                             @NonNull Map<Media, MediaTransform> modelsToTransform,
                             @NonNull Callback<LinkedHashMap<Media, Media>> callback)
  {
    SignalExecutors.BOUNDED.execute(() -> callback.onComplete(transformMediaSync(context, currentMedia, modelsToTransform)));
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
                                                                  .filter(folder -> folder.getTitle() != null)
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
    String                  cameraBucketId     = null;
    Uri                     globalThumbnail    = null;
    long                    thumbnailTimestamp = 0;
    Map<String, FolderData> folders            = new HashMap<>();

    String[] projection = new String[] { Images.Media._ID, Images.Media.BUCKET_ID, Images.Media.BUCKET_DISPLAY_NAME, Images.Media.DATE_MODIFIED };
    String   selection  = isNotPending() + " AND " + Images.Media.MIME_TYPE + " NOT LIKE ?";
    String[] args       = SqlUtil.buildArgs("%image/svg%");
    String   sortBy     = Images.Media.BUCKET_DISPLAY_NAME + " COLLATE NOCASE ASC, " + Images.Media.DATE_MODIFIED + " DESC";

    try (Cursor cursor = context.getContentResolver().query(contentUri, projection, selection, args, sortBy)) {
      while (cursor != null && cursor.moveToNext()) {
        long       rowId     = cursor.getLong(cursor.getColumnIndexOrThrow(projection[0]));
        Uri        thumbnail = ContentUris.withAppendedId(contentUri, rowId);
        String     bucketId  = cursor.getString(cursor.getColumnIndexOrThrow(projection[1]));
        String     title     = cursor.getString(cursor.getColumnIndexOrThrow(projection[2]));
        long       timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(projection[3]));
        FolderData folder    = Util.getOrDefault(folders, bucketId, new FolderData(thumbnail, localizeTitle(context, title), bucketId));

        folder.incrementCount();
        folders.put(bucketId, folder);

        if (cameraBucketId == null && CAMERA.equals(title)) {
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

  private @NonNull String localizeTitle(@NonNull Context context, @NonNull String title) {
    if (CAMERA.equals(title)) {
      return context.getString(R.string.MediaRepository__camera);
    } else {
      return title;
    }
  }

  @WorkerThread
  private @NonNull List<Media> getMediaInBucket(@NonNull Context context, @NonNull String bucketId) {
    Stopwatch stopwatch = new Stopwatch("getMediaInBucket");

    List<Media> images = getMediaInBucket(context, bucketId, Images.Media.EXTERNAL_CONTENT_URI, true);
    List<Media> videos = getMediaInBucket(context, bucketId, Video.Media.EXTERNAL_CONTENT_URI, false);
    List<Media> media  = new ArrayList<>(images.size() + videos.size());

    stopwatch.split("post fetch");

    media.addAll(images);
    media.addAll(videos);
    Collections.sort(media, (o1, o2) -> Long.compare(o2.getDate(), o1.getDate()));
    stopwatch.split("post sort");

    stopwatch.stop(TAG);
    return media;
  }

  @WorkerThread
  private @NonNull List<Media> getMediaInBucket(@NonNull Context context, @NonNull String bucketId, @NonNull Uri contentUri, boolean isImage) {
    List<Media> media         = new LinkedList<>();
    String      selection     = Images.Media.BUCKET_ID + " = ? AND " + isNotPending() + " AND " + Images.Media.MIME_TYPE + " NOT LIKE ?";
    String[]    selectionArgs = new String[] { bucketId, "%image/svg%" };
    String      sortBy        = Images.Media.DATE_MODIFIED + " DESC";

    String[] projection;

    if (isImage) {
      projection = new String[]{Images.Media._ID, Images.Media.MIME_TYPE, Images.Media.DATE_MODIFIED, Images.Media.ORIENTATION, Images.Media.WIDTH, Images.Media.HEIGHT, Images.Media.SIZE};
    } else {
      projection = new String[]{Images.Media._ID, Images.Media.MIME_TYPE, Images.Media.DATE_MODIFIED, Images.Media.WIDTH, Images.Media.HEIGHT, Images.Media.SIZE, Video.Media.DURATION};
    }

    if (Media.ALL_MEDIA_BUCKET_ID.equals(bucketId)) {
      selection     = isNotPending() + " AND " + Images.Media.MIME_TYPE + " NOT LIKE ?";
      selectionArgs = SqlUtil.buildArgs("%image/svg%");
    }

    try (Cursor cursor = context.getContentResolver().query(contentUri, projection, selection, selectionArgs, sortBy)) {
      while (cursor != null && cursor.moveToNext()) {
        long   rowId       = cursor.getLong(cursor.getColumnIndexOrThrow(projection[0]));
        Uri    uri         = ContentUris.withAppendedId(contentUri, rowId);
        String mimetype    = cursor.getString(cursor.getColumnIndexOrThrow(Images.Media.MIME_TYPE));
        long   date        = cursor.getLong(cursor.getColumnIndexOrThrow(Images.Media.DATE_MODIFIED));
        int    orientation = isImage ? cursor.getInt(cursor.getColumnIndexOrThrow(Images.Media.ORIENTATION)) : 0;
        int    width       = cursor.getInt(cursor.getColumnIndexOrThrow(getWidthColumn(orientation)));
        int    height      = cursor.getInt(cursor.getColumnIndexOrThrow(getHeightColumn(orientation)));
        long   size        = cursor.getLong(cursor.getColumnIndexOrThrow(Images.Media.SIZE));
        long   duration    = !isImage ? cursor.getInt(cursor.getColumnIndexOrThrow(Video.Media.DURATION)) : 0;

        media.add(fixMimeType(context, new Media(uri, mimetype, date, width, height, size, duration, false, false, Optional.of(bucketId), Optional.empty(), Optional.empty(), Optional.empty())));
      }
    }

    return media;
  }

  private @NonNull String isNotPending() {
    return Build.VERSION.SDK_INT <= 28 ? Images.Media.DATA + " NOT NULL" : MediaStore.MediaColumns.IS_PENDING + " != 1";
  }

  @WorkerThread
  public List<Media> getPopulatedMedia(@NonNull Context context, @NonNull List<Media> media) {
    return media.stream()
                .map(m -> {
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
                })
                .map(m -> fixMimeType(context, m))
                .collect(Collectors.toList());
  }

  @WorkerThread
  public static LinkedHashMap<Media, Media> transformMediaSync(@NonNull Context context,
                                                               @NonNull List<Media> currentMedia,
                                                               @NonNull Map<Media, MediaTransform> modelsToTransform)
  {
    LinkedHashMap<Media, Media> updatedMedia = new LinkedHashMap<>(currentMedia.size());

    for (Media media : currentMedia) {
      MediaTransform transformer = modelsToTransform.get(media);
      if (transformer != null) {
        updatedMedia.put(media, transformer.transform(context, media));
      } else {
        updatedMedia.put(media, media);
      }
    }
    return updatedMedia;
  }

  @WorkerThread
  private Optional<Media> getMostRecentItem(@NonNull Context context) {
    List<Media> media = getMediaInBucket(context, Media.ALL_MEDIA_BUCKET_ID, Images.Media.EXTERNAL_CONTENT_URI, true);
    return media.size() > 0 ? Optional.of(media.get(0)) : Optional.empty();
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private String getWidthColumn(int orientation) {
    if (orientation == 0 || orientation == 180) return Images.Media.WIDTH;
    else                                        return Images.Media.HEIGHT;
  }

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
      Optional<Long> optionalSize = Optional.ofNullable(PartAuthority.getAttachmentSize(context, media.getUri()));
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

    return new Media(media.getUri(), media.getMimeType(), media.getDate(), width, height, size, 0, media.isBorderless(), media.isVideoGif(), media.getBucketId(), media.getCaption(), Optional.empty(), Optional.empty());
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

    return new Media(media.getUri(), media.getMimeType(), media.getDate(), width, height, size, 0, media.isBorderless(), media.isVideoGif(), media.getBucketId(), media.getCaption(), Optional.empty(), Optional.empty());
  }

  @VisibleForTesting
  public static @NonNull Media fixMimeType(@NonNull Context context, @NonNull Media media) {
    if (MediaUtil.isOctetStream(media.getMimeType())) {
      Log.w(TAG, "Media has mimetype octet stream");
      String newMimeType = MediaUtil.getMimeType(context, media.getUri());
      if (newMimeType != null && !newMimeType.equals(media.getMimeType())) {
        Log.d(TAG, "Changing mime type to '" + newMimeType + "'");
        return Media.withMimeType(media, newMimeType);
      } else if (media.getSize() > 0 && media.getWidth() > 0 && media.getHeight() > 0) {
        boolean likelyVideo = media.getDuration() > 0;
        Log.d(TAG, "Assuming content is " + (likelyVideo ? "a video" : "an image") + ", setting mimetype");
        return Media.withMimeType(media, likelyVideo ? MediaUtil.VIDEO_UNSPECIFIED : MediaUtil.IMAGE_JPEG);
      } else {
        Log.d(TAG, "Unable to fix mimetype");
      }
    }
    return media;
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

  public interface Callback<E> {
    void onComplete(@NonNull E result);
  }
}
