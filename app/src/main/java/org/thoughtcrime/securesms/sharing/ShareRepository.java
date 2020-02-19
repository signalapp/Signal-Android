package org.thoughtcrime.securesms.sharing;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.MediaSendConstants;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ShareRepository {

  private static final String TAG = Log.tag(ShareRepository.class);

  /**
   * Handles a single URI that may be local or external.
   */
  void getResolved(@Nullable Uri uri, @Nullable String mimeType, @NonNull Callback<Optional<ShareData>> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        callback.onResult(Optional.of(getResolvedInternal(uri, mimeType)));
      } catch (IOException e) {
        Log.w(TAG, "Failed to resolve!", e);
        callback.onResult(Optional.absent());
      }
    });
  }

  /**
   * Handles multiple URIs that are all assumed to be external images/videos.
   */
  void getResolved(@NonNull List<Uri> uris, @NonNull Callback<Optional<ShareData>> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        callback.onResult(Optional.fromNullable(getResolvedInternal(uris)));
      } catch (IOException e) {
        Log.w(TAG, "Failed to resolve!", e);
        callback.onResult(Optional.absent());
      }
    });
  }

  @WorkerThread
  private @NonNull ShareData getResolvedInternal(@Nullable Uri uri, @Nullable String mimeType) throws IOException  {
    Context context = ApplicationDependencies.getApplication();

    if (uri == null) {
      return ShareData.forPrimitiveTypes();
    }

    mimeType = getMimeType(context, uri, mimeType);

    if (PartAuthority.isLocalUri(uri)) {
      return ShareData.forIntentData(uri, mimeType, false);
    } else {
      InputStream stream = context.getContentResolver().openInputStream(uri);

      if (stream == null) {
        throw new IOException("Failed to open stream!");
      }

      long   size     = getSize(context, uri);
      String fileName = getFileName(context, uri);

      Uri blobUri;

      if (MediaUtil.isImageType(mimeType) || MediaUtil.isVideoType(mimeType)) {
        blobUri = BlobProvider.getInstance()
                              .forData(stream, size)
                              .withMimeType(mimeType)
                              .withFileName(fileName)
                              .createForSingleSessionOnDisk(context);
      } else {
        blobUri = BlobProvider.getInstance()
                              .forData(stream, size)
                              .withMimeType(mimeType)
                              .withFileName(fileName)
                              .createForMultipleSessionsOnDisk(context);
      }

      return ShareData.forIntentData(blobUri, mimeType, true);
    }
  }

  @WorkerThread
  private @Nullable ShareData getResolvedInternal(@NonNull List<Uri> uris) throws IOException  {
    Context context = ApplicationDependencies.getApplication();

    Map<Uri, String> mimeTypes = Stream.of(uris)
                                       .map(uri -> new Pair<>(uri, getMimeType(context, uri, null)))
                                       .filter(p -> MediaUtil.isImageType(p.second) || MediaUtil.isVideoType(p.second))
                                       .collect(Collectors.toMap(p -> p.first, p -> p.second));

    if (mimeTypes.isEmpty()) {
      return null;
    }

    List<Media> media = new ArrayList<>(mimeTypes.size());

    for (Map.Entry<Uri, String> entry : mimeTypes.entrySet()) {
      Uri    uri      = entry.getKey();
      String mimeType = entry.getValue();

      InputStream stream;
      try {
        stream = context.getContentResolver().openInputStream(uri);
        if (stream == null) {
          throw new IOException("Failed to open stream!");
        }
      } catch (IOException e) {
        Log.w(TAG, "Failed to open: " + uri);
        continue;
      }

      long                   size     = getSize(context, uri);
      Pair<Integer, Integer> dimens   = MediaUtil.getDimensions(context, mimeType, uri);
      long                   duration = getDuration(context, uri);
      Uri                    blobUri  = BlobProvider.getInstance()
                                                    .forData(stream, size)
                                                    .withMimeType(mimeType)
                                                    .createForSingleSessionOnDisk(context);

      media.add(new Media(blobUri,
                          mimeType,
                          System.currentTimeMillis(),
                          dimens.first,
                          dimens.second,
                          size,
                          duration,
                          Optional.of(Media.ALL_MEDIA_BUCKET_ID),
                          Optional.absent(),
                          Optional.absent()));

      if (media.size() >= MediaSendConstants.MAX_PUSH) {
        Log.w(TAG, "Exceeded the attachment limit! Skipping the rest.");
        break;
      }
    }

    if (media.size() > 0) {
      return ShareData.forMedia(media);
    } else {
      return null;
    }
  }

  private static @NonNull String getMimeType(@NonNull Context context, @NonNull Uri uri, @Nullable String mimeType) {
    String updatedMimeType = MediaUtil.getMimeType(context, uri);

    if (updatedMimeType == null) {
      updatedMimeType = MediaUtil.getCorrectedMimeType(mimeType);
    }

    return updatedMimeType != null ? updatedMimeType : MediaUtil.UNKNOWN;
  }

  private static long getSize(@NonNull Context context, @NonNull Uri uri) throws IOException {
    long size = 0;

    try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst() && cursor.getColumnIndex(OpenableColumns.SIZE) >= 0) {
        size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
      }
    }

    if (size <= 0) {
      size = MediaUtil.getMediaSize(context, uri);
    }

    return size;
  }

  private static @Nullable String getFileName(@NonNull Context context, @NonNull Uri uri) {
    try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst() && cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) >= 0) {
        return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
      }
    }

    return null;
  }

  private static long getDuration(@NonNull Context context, @NonNull Uri uri) {
    return 0;
  }

  interface Callback<E> {
    void onResult(@NonNull E result);
  }
}
