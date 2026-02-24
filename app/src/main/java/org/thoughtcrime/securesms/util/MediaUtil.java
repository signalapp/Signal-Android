/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import kotlin.Pair;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.exifinterface.media.ExifInterface;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.gif.GifDrawable;

import org.signal.core.util.ContentTypeUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.signal.core.models.media.Media;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.signal.glide.decryptableuri.DecryptableUri;
import org.thoughtcrime.securesms.mms.DocumentSlide;
import org.thoughtcrime.securesms.mms.GifSlide;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.MmsSlide;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.mms.TextSlide;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.mms.ViewOnceSlide;
import org.thoughtcrime.securesms.providers.BlobProvider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class MediaUtil {

  private static final String TAG = Log.tag(MediaUtil.class);

  public static final String IMAGE_PNG         = ContentTypeUtil.IMAGE_PNG;
  public static final String IMAGE_JPEG        = ContentTypeUtil.IMAGE_JPEG;
  public static final String IMAGE_HEIC        = ContentTypeUtil.IMAGE_HEIC;
  public static final String IMAGE_HEIF        = ContentTypeUtil.IMAGE_HEIF;
  public static final String IMAGE_AVIF        = ContentTypeUtil.IMAGE_AVIF;
  public static final String IMAGE_WEBP        = ContentTypeUtil.IMAGE_WEBP;
  public static final String IMAGE_GIF         = ContentTypeUtil.IMAGE_GIF;
  public static final String AUDIO_AAC         = ContentTypeUtil.AUDIO_AAC;
  public static final String AUDIO_MP4         = ContentTypeUtil.AUDIO_MP4;
  public static final String AUDIO_UNSPECIFIED = ContentTypeUtil.AUDIO_UNSPECIFIED;
  public static final String VIDEO_MP4         = ContentTypeUtil.VIDEO_MP4;
  public static final String VIDEO_UNSPECIFIED = ContentTypeUtil.VIDEO_UNSPECIFIED;
  public static final String VCARD             = ContentTypeUtil.VCARD;
  public static final String LONG_TEXT         = ContentTypeUtil.LONG_TEXT;
  public static final String VIEW_ONCE         = ContentTypeUtil.VIEW_ONCE;
  public static final String UNKNOWN           = ContentTypeUtil.UNKNOWN;
  public static final String OCTET             = ContentTypeUtil.OCTET;

  public static @NonNull SlideType getSlideTypeFromContentType(@Nullable String contentType) {
    if (isGif(contentType)) {
      return SlideType.GIF;
    } else if (isImageType(contentType)) {
      return SlideType.IMAGE;
    } else if (isVideoType(contentType)) {
      return SlideType.VIDEO;
    } else if (isAudioType(contentType)) {
      return SlideType.AUDIO;
    } else if (isMms(contentType)) {
      return SlideType.MMS;
    } else if (isLongTextType(contentType)) {
      return SlideType.LONG_TEXT;
    } else if (isViewOnceType(contentType)) {
      return SlideType.VIEW_ONCE;
    } else {
      return SlideType.DOCUMENT;
    }
  }

  public static @NonNull Slide getSlideForAttachment(Attachment attachment) {
    if (attachment.isSticker()) {
      return new StickerSlide(attachment);
    }

    switch (getSlideTypeFromContentType(attachment.contentType)) {
      case GIF       : return new GifSlide(attachment);
      case IMAGE     : return new ImageSlide(attachment);
      case VIDEO     : return new VideoSlide(attachment);
      case AUDIO     : return new AudioSlide(attachment);
      case MMS       : return new MmsSlide(attachment);
      case LONG_TEXT : return new TextSlide(attachment);
      case VIEW_ONCE : return new ViewOnceSlide(attachment);
      case DOCUMENT  : return new DocumentSlide(attachment);
      default        : throw new AssertionError();
    }
  }

  public static @Nullable String getMimeType(@NonNull Context context, @Nullable Uri uri) {
    return getMimeType(context, uri, null);
  }

  public static @Nullable String getMimeType(@NonNull Context context, @Nullable Uri uri, @Nullable String fileExtension) {
    if (uri == null) return null;

    if (PartAuthority.isLocalUri(uri)) {
      return PartAuthority.getAttachmentContentType(context, uri);
    }

    String type = context.getContentResolver().getType(uri);
    if (type == null || isOctetStream(type)) {
      String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
      if (TextUtils.isEmpty(extension) && fileExtension != null) {
        extension = fileExtension;
      }
      type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
      return getCorrectedMimeType(type, fileExtension);
    }

    return getCorrectedMimeType(type);
  }

  public static @NonNull Optional<String> getFileType(@NonNull Context context, Optional<String> fileName, Uri uri) {
    if (fileName.isPresent()) {
      String fileType = getFileType(fileName);
      if (!fileType.isEmpty()) {
        return Optional.of(fileType);
      }
    }

    return Optional.ofNullable(MediaUtil.getExtension(context, uri));
  }

  private static @NonNull String getFileType(Optional<String> fileName) {
    if (!fileName.isPresent()) return "";

    String[] parts = fileName.get().split("\\.");

    if (parts.length < 2) {
      return "";
    }

    String suffix = parts[parts.length - 1];

    if (suffix.length() <= 3) {
      return suffix;
    }

    return "";
  }

  public static @Nullable String getExtension(@NonNull Context context, @Nullable Uri uri) {
    return MimeTypeMap.getSingleton()
                      .getExtensionFromMimeType(getMimeType(context, uri));
  }

  private static String safeMimeTypeOverride(String originalType, String overrideType) {
    if (MimeTypeMap.getSingleton().hasMimeType(overrideType)) {
      return overrideType;
    }
    return originalType;
  }

  public static @Nullable String overrideMimeTypeWithExtension(@Nullable String mimeType, @Nullable String fileExtension) {
    if (fileExtension == null) {
      return mimeType;
    }
    if (fileExtension.toLowerCase().equals("m4a")) {
      return safeMimeTypeOverride(mimeType, AUDIO_MP4);
    }
    return mimeType;
  }

  public static @Nullable String getCorrectedMimeType(@Nullable String mimeType) {
    return getCorrectedMimeType(mimeType, null);
  }

  public static @Nullable String getCorrectedMimeType(@Nullable String mimeType, @Nullable String fileExtension) {
    if (mimeType == null) return null;

    switch(mimeType) {
    case "image/jpg":
      return safeMimeTypeOverride(mimeType, IMAGE_JPEG);
    case "audio/mpeg":
      return overrideMimeTypeWithExtension(mimeType, fileExtension);
    default:
      return mimeType;
    }
  }

  public static long getMediaSize(Context context, Uri uri) throws IOException {
    InputStream in = PartAuthority.getAttachmentStream(context, uri);
    if (in == null) throw new IOException("Couldn't obtain input stream.");

    long   size   = 0;
    byte[] buffer = new byte[4096];
    int    read;

    while ((read = in.read(buffer)) != -1) {
      size += read;
    }
    in.close();

    return size;
  }

  @WorkerThread
  public static Pair<Integer, Integer> getDimensions(@NonNull Context context, @Nullable String contentType, @Nullable Uri uri) {
    if (uri == null || (!MediaUtil.isImageType(contentType) && !MediaUtil.isVideoType(contentType))) {
      return new Pair<>(0, 0);
    }

    Pair<Integer, Integer> dimens = null;

    if (MediaUtil.isGif(contentType)) {
      try {
        GifDrawable drawable = Glide.with(context)
                                       .asGif()
                                       .skipMemoryCache(true)
                                       .diskCacheStrategy(DiskCacheStrategy.NONE)
                                       .load(new DecryptableUri(uri))
                                       .submit()
                                       .get();
        dimens = new Pair<>(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
      } catch (InterruptedException e) {
        Log.w(TAG, "Was unable to complete work for GIF dimensions.", e);
      } catch (ExecutionException e) {
        Log.w(TAG, "Glide experienced an exception while trying to get GIF dimensions.", e);
      }
    } else if (MediaUtil.hasVideoThumbnail(context, uri)) {
      Bitmap thumbnail = MediaUtil.getVideoThumbnail(context, uri, 1000);

      if (thumbnail != null) {
        dimens = new Pair<>(thumbnail.getWidth(), thumbnail.getHeight());
      }
    } else {
      InputStream attachmentStream = null;
      try {
        if (MediaUtil.isJpegType(contentType)) {
          attachmentStream = PartAuthority.getAttachmentStream(context, uri);
          dimens = BitmapUtil.getExifDimensions(new ExifInterface(attachmentStream));
          attachmentStream.close();
          attachmentStream = null;
        }
        if (dimens == null) {
          attachmentStream = PartAuthority.getAttachmentStream(context, uri);
          dimens = BitmapUtil.getDimensions(attachmentStream);
        }
      } catch (FileNotFoundException e) {
        Log.w(TAG, "Failed to find file when retrieving media dimensions.", e);
      } catch (IOException e) {
        Log.w(TAG, "Experienced a read error when retrieving media dimensions.", e);
      } catch (BitmapDecodingException e) {
        Log.w(TAG, "Bitmap decoding error when retrieving dimensions.", e);
      } finally {
        if (attachmentStream != null) {
          try {
            attachmentStream.close();
          } catch (IOException e) {
            Log.w(TAG, "Failed to close stream after retrieving dimensions.", e);
          }
        }
      }
    }
    if (dimens == null) {
      dimens = new Pair(0, 0);
    }
    Log.d(TAG, "Dimensions for [" + uri + "] are " + dimens.getFirst() + " x " + dimens.getSecond());
    return dimens;
  }

  public static boolean isMms(String contentType) {
    return ContentTypeUtil.isMms(contentType);
  }

  public static boolean isGif(Attachment attachment) {
    return isGif(attachment.contentType);
  }

  public static boolean isJpeg(Attachment attachment) {
    return isJpegType(attachment.contentType);
  }

  public static boolean isHeic(Attachment attachment) {
    return isHeicType(attachment.contentType);
  }

  public static boolean isHeif(Attachment attachment) {
    return isHeifType(attachment.contentType);
  }

  public static boolean isImage(Attachment attachment) {
    return isImageType(attachment.contentType);
  }

  public static boolean isAudio(Attachment attachment) {
    return isAudioType(attachment.contentType);
  }

  public static boolean isVideo(Attachment attachment) {
    return isVideoType(attachment.contentType);
  }

  public static boolean isVideo(String contentType) {
    return ContentTypeUtil.isVideo(contentType);
  }

  public static boolean isVcard(String contentType) {
    return ContentTypeUtil.isVcard(contentType);
  }

  public static boolean isGif(String contentType) {
    return ContentTypeUtil.isGif(contentType);
  }

  public static boolean isJpegType(String contentType) {
    return ContentTypeUtil.isJpegType(contentType);
  }

  public static boolean isHeicType(String contentType) {
    return ContentTypeUtil.isHeicType(contentType);
  }

  public static boolean isHeifType(String contentType) {
    return ContentTypeUtil.isHeifType(contentType);
  }

  public static boolean isAvifType(String contentType) {
    return ContentTypeUtil.isAvifType(contentType);
  }

  public static boolean isWebpType(String contentType) {
    return ContentTypeUtil.isWebpType(contentType);
  }

  public static boolean isPngType(String contentType) {
    return ContentTypeUtil.isPngType(contentType);
  }

  public static boolean isFile(Attachment attachment) {
    return !isGif(attachment) && !isImage(attachment) && !isAudio(attachment) && !isVideo(attachment);
  }

  public static boolean isTextType(String contentType) {
    return ContentTypeUtil.isTextType(contentType);
  }

  public static boolean isNonGifVideo(Media media) {
    return isVideo(media.getContentType()) && !media.isVideoGif();
  }

  public static boolean isImageType(String contentType) {
    return ContentTypeUtil.isImageType(contentType);
  }

  public static boolean isAudioType(String contentType) {
    return ContentTypeUtil.isAudioType(contentType);
  }

  public static boolean isVideoType(String contentType) {
    return ContentTypeUtil.isVideoType(contentType);
  }

  public static boolean isImageOrVideoType(String contentType) {
    return ContentTypeUtil.isImageOrVideoType(contentType);
  }

  public static boolean isStorySupportedType(String contentType) {
    return ContentTypeUtil.isStorySupportedType(contentType);
  }

  public static boolean isImageVideoOrAudioType(String contentType) {
    return ContentTypeUtil.isImageVideoOrAudioType(contentType);
  }

  public static boolean isImageAndNotGif(@NonNull String contentType) {
    return ContentTypeUtil.isImageAndNotGif(contentType);
  }

  public static boolean isLongTextType(String contentType) {
    return ContentTypeUtil.isLongTextType(contentType);
  }

  public static boolean isViewOnceType(String contentType) {
    return ContentTypeUtil.isViewOnceType(contentType);
  }

  public static boolean isOctetStream(@Nullable String contentType) {
    return ContentTypeUtil.isOctetStream(contentType);
  }

  public static boolean isDocumentType(String contentType) {
    return ContentTypeUtil.isDocumentType(contentType);
  }

  public static boolean hasVideoThumbnail(@NonNull Context context, @Nullable Uri uri) {
    if (uri == null) {
      return false;
    }

    if (BlobProvider.isAuthority(uri) && MediaUtil.isVideo(BlobProvider.getMimeType(uri))) {
      return true;
    }

    if (!isSupportedVideoUriScheme(uri.getScheme())) {
      return false;
    }

    if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
      return uri.getLastPathSegment().contains("video");
    } else if (uri.toString().startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString())) {
      return true;
    } else if (uri.toString().startsWith("file://") &&
               MediaUtil.isVideo(URLConnection.guessContentTypeFromName(uri.toString()))) {
      return true;
    } else return PartAuthority.isAttachmentUri(uri) && MediaUtil.isVideoType(PartAuthority.getAttachmentContentType(context, uri));
  }

  @WorkerThread
  public static @Nullable Bitmap getVideoThumbnail(@NonNull Context context, @Nullable Uri uri) {
    return getVideoThumbnail(context, uri, 1000);
  }

  @WorkerThread
  public static @Nullable Bitmap getVideoThumbnail(@NonNull Context context, @Nullable Uri uri, long timeUs) {
    if (uri == null) {
      return null;
    } else if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
      long videoId = Long.parseLong(uri.getLastPathSegment().split(":")[1]);

      return MediaStore.Video.Thumbnails.getThumbnail(context.getContentResolver(),
                                                      videoId,
                                                      MediaStore.Images.Thumbnails.MINI_KIND,
                                                      null);
    } else if (uri.toString().startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString())) {
      long videoId = Long.parseLong(uri.getLastPathSegment());

      return MediaStore.Video.Thumbnails.getThumbnail(context.getContentResolver(),
                                                      videoId,
                                                      MediaStore.Images.Thumbnails.MINI_KIND,
                                                      null);
    } else if (uri.toString().startsWith("file://") &&
               MediaUtil.isVideo(URLConnection.guessContentTypeFromName(uri.toString()))) {
      return ThumbnailUtils.createVideoThumbnail(uri.toString().replace("file://", ""),
                                                 MediaStore.Video.Thumbnails.MINI_KIND);
    } else if (BlobProvider.isAuthority(uri) &&
               MediaUtil.isVideo(BlobProvider.getMimeType(uri)))
    {
      try {
        MediaDataSource source = BlobProvider.getInstance().getMediaDataSource(context, uri);
        return extractFrame(source, timeUs);
      } catch (IOException e) {
        Log.w(TAG, "Failed to extract frame for URI: " + uri, e);
      }
    } else if (PartAuthority.isAttachmentUri(uri) &&
               MediaUtil.isVideoType(PartAuthority.getAttachmentContentType(context, uri)))
    {
      try {
        AttachmentId    attachmentId = PartAuthority.requireAttachmentId(uri);
        MediaDataSource source       = SignalDatabase.attachments().mediaDataSourceFor(attachmentId, false);
        return extractFrame(source, timeUs);
      } catch (IOException e) {
        Log.w(TAG, "Failed to extract frame for URI: " + uri, e);
      }
    }

    return null;
  }

  private static @Nullable Bitmap extractFrame(@Nullable MediaDataSource dataSource, long timeUs) throws IOException {
    if (dataSource == null) {
      return null;
    }

    MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();

    MediaMetadataRetrieverUtil.setDataSource(mediaMetadataRetriever, dataSource);
    return mediaMetadataRetriever.getFrameAtTime(timeUs);
  }

  public static boolean isInstantVideoSupported(Slide slide) {
    final Attachment attachment                        = slide.asAttachment();
    final boolean    isIncremental                     = attachment.getIncrementalDigest() != null;
    final boolean    hasIncrementalMacChunkSizeDefined = attachment.incrementalMacChunkSize > 0;
    final boolean    contentTypeSupported              = isVideoType(slide.getContentType());
    return isIncremental && contentTypeSupported && hasIncrementalMacChunkSizeDefined;
  }

  public static @Nullable String getDiscreteMimeType(@NonNull String mimeType) {
    final String[] sections = mimeType.split("/", 2);
    return sections.length > 1 ? sections[0] : null;
  }

  public static class ThumbnailData implements AutoCloseable {

    @NonNull private final Bitmap bitmap;
    private final          float  aspectRatio;

    public ThumbnailData(@NonNull Bitmap bitmap) {
      this.bitmap      = bitmap;
      this.aspectRatio = (float) bitmap.getWidth() / (float) bitmap.getHeight();
    }

    public @NonNull Bitmap getBitmap() {
      return bitmap;
    }

    public float getAspectRatio() {
      return aspectRatio;
    }

    public InputStream toDataStream() {
      return BitmapUtil.toCompressedJpeg(bitmap);
    }

    @Override
    public void close() {
     bitmap.recycle();
    }
  }

  private static boolean isSupportedVideoUriScheme(@Nullable String scheme) {
    return ContentResolver.SCHEME_CONTENT.equals(scheme) ||
           ContentResolver.SCHEME_FILE.equals(scheme);
  }

  public enum SlideType {
    GIF,
    IMAGE,
    VIDEO,
    AUDIO,
    MMS,
    LONG_TEXT,
    VIEW_ONCE,
    DOCUMENT
  }
}
