package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.io.IOException;

/**
 * SlideFactory encapsulates logic related to constructing slides from a set of paramaeters as defined
 * by {@link SlideFactory#getSlide}.
 */
public final class SlideFactory {

  private static final String TAG = Log.tag(SlideFactory.class);

  private SlideFactory() {
  }

  /**
   * Generates a slide from the given parameters.
   *
   * @param context             Application context
   * @param contentType         The contentType of the given Uri
   * @param uri                 The Uri pointing to the resource to create a slide out of
   * @param width               (Optional) width, can be 0.
   * @param height              (Optional) height, can be 0.
   * @param transformProperties (Optional) transformProperties, can be 0.
   *
   * @return A Slide with all the information we can gather about it.
   */
  public static @Nullable Slide getSlide(@NonNull Context context, @Nullable String contentType, @NonNull Uri uri, int width, int height, @Nullable AttachmentTable.TransformProperties transformProperties) {
    MediaType mediaType = MediaType.from(contentType);

    try {
      if (PartAuthority.isLocalUri(uri)) {
        return getManuallyCalculatedSlideInfo(context, mediaType, uri, width, height, transformProperties);
      } else {
        Slide result = getContentResolverSlideInfo(context, mediaType, uri, width, height, transformProperties);

        if (result == null) return getManuallyCalculatedSlideInfo(context, mediaType, uri, width, height, transformProperties);
        else                return result;
      }
    } catch (IOException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  private static @Nullable Slide getContentResolverSlideInfo(
      @NonNull Context context,
      @Nullable MediaType mediaType,
      @NonNull Uri uri,
      int width,
      int height,
      @Nullable AttachmentTable.TransformProperties transformProperties
  ) {
    long start = System.currentTimeMillis();

    try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        String fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
        long   fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
        String mimeType = context.getContentResolver().getType(uri);

        if (width == 0 || height == 0) {
          Pair<Integer, Integer> dimens = MediaUtil.getDimensions(context, mimeType, uri);
          width  = dimens.first;
          height = dimens.second;
        }

        if (mediaType == null) {
          mediaType = MediaType.DOCUMENT;
        }

        Log.d(TAG, "remote slide with size " + fileSize + " took " + (System.currentTimeMillis() - start) + "ms");
        return mediaType.createSlide(context, uri, fileName, mimeType, null, fileSize, width, height, false, transformProperties);
      }
    }

    return null;
  }

  private static @NonNull Slide getManuallyCalculatedSlideInfo(
      @NonNull Context context,
      @Nullable MediaType mediaType,
      @NonNull Uri uri,
      int width,
      int height,
      @Nullable AttachmentTable.TransformProperties transformProperties
  ) throws IOException
  {
    long     start     = System.currentTimeMillis();
    Long     mediaSize = null;
    String   fileName  = null;
    String   mimeType  = null;
    boolean  gif       = false;

    if (PartAuthority.isLocalUri(uri)) {
      mediaSize = PartAuthority.getAttachmentSize(context, uri);
      fileName  = PartAuthority.getAttachmentFileName(context, uri);
      mimeType  = PartAuthority.getAttachmentContentType(context, uri);
      gif       = PartAuthority.getAttachmentIsVideoGif(context, uri);
    }

    if (mediaSize == null) {
      mediaSize = MediaUtil.getMediaSize(context, uri);
    }

    if (mimeType == null) {
      mimeType = MediaUtil.getMimeType(context, uri);
    }

    if (width == 0 || height == 0) {
      Pair<Integer, Integer> dimens = MediaUtil.getDimensions(context, mimeType, uri);
      width  = dimens.first;
      height = dimens.second;
    }

    if (mediaType == null) {
      mediaType = MediaType.DOCUMENT;
    }

    Log.d(TAG, "local slide with size " + mediaSize + " took " + (System.currentTimeMillis() - start) + "ms");
    return mediaType.createSlide(context, uri, fileName, mimeType, null, mediaSize, width, height, gif, transformProperties);
  }

  public enum MediaType {

    IMAGE(MediaUtil.IMAGE_JPEG),
    GIF(MediaUtil.IMAGE_GIF),
    AUDIO(MediaUtil.AUDIO_AAC),
    VIDEO(MediaUtil.VIDEO_MP4),
    DOCUMENT(MediaUtil.UNKNOWN),
    VCARD(MediaUtil.VCARD);

    private final String fallbackMimeType;

    MediaType(String fallbackMimeType) {
      this.fallbackMimeType = fallbackMimeType;
    }


    public @NonNull Slide createSlide(@NonNull  Context  context,
                                      @NonNull  Uri      uri,
                                      @Nullable String   fileName,
                                      @Nullable String   mimeType,
                                      @Nullable BlurHash blurHash,
                                      long      dataSize,
                                      int       width,
                                      int       height,
                                      boolean   gif,
                                      @Nullable AttachmentTable.TransformProperties transformProperties)
    {
      if (mimeType == null) {
        mimeType = "application/octet-stream";
      }

      switch (this) {
      case IMAGE:    return new ImageSlide(context, uri, mimeType, dataSize, width, height, false, null, blurHash, transformProperties);
      case GIF:      return new GifSlide(context, uri, dataSize, width, height);
      case AUDIO:    return new AudioSlide(context, uri, dataSize, false);
      case VIDEO:    return new VideoSlide(context, uri, dataSize, gif);
      case VCARD:
      case DOCUMENT: return new DocumentSlide(context, uri, mimeType, dataSize, fileName);
      default:       throw  new AssertionError("unrecognized enum");
      }
    }

    public static @Nullable MediaType from(final @Nullable String mimeType) {
      if (TextUtils.isEmpty(mimeType))     return null;
      if (MediaUtil.isGif(mimeType))       return GIF;
      if (MediaUtil.isImageType(mimeType)) return IMAGE;
      if (MediaUtil.isAudioType(mimeType)) return AUDIO;
      if (MediaUtil.isVideoType(mimeType)) return VIDEO;
      if (MediaUtil.isVcard(mimeType))     return VCARD;

      return DOCUMENT;
    }

    public String toFallbackMimeType() {
      return fallbackMimeType;
    }
  }
}
