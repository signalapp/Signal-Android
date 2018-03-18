package org.thoughtcrime.securesms.util;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.DocumentSlide;
import org.thoughtcrime.securesms.mms.GifSlide;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.MmsSlide;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

public class MediaUtil {

  private static final String TAG = MediaUtil.class.getSimpleName();

  public static final String IMAGE_PNG         = "image/png";
  public static final String IMAGE_JPEG        = "image/jpeg";
  public static final String IMAGE_GIF         = "image/gif";
  public static final String AUDIO_AAC         = "audio/aac";
  public static final String AUDIO_UNSPECIFIED = "audio/*";
  public static final String VIDEO_UNSPECIFIED = "video/*";
  public static final String TEXT_VCARD        = "text/vcard";

  public static @Nullable ThumbnailData generateThumbnail(Context context, String contentType, Uri uri)
      throws BitmapDecodingException
  {
    long   startMillis = System.currentTimeMillis();
    ThumbnailData data = null;

    if (isImageType(contentType)) {
      data = new ThumbnailData(generateImageThumbnail(context, uri));
    }

    if (data != null) {
      Log.w(TAG, String.format("generated thumbnail for part, %dx%d (%.3f:1) in %dms",
                               data.getBitmap().getWidth(), data.getBitmap().getHeight(),
                               data.getAspectRatio(), System.currentTimeMillis() - startMillis));
    }

    return data;
  }

  private static Bitmap generateImageThumbnail(Context context, Uri uri)
      throws BitmapDecodingException
  {
    try {
      int maxSize = context.getResources().getDimensionPixelSize(R.dimen.media_bubble_height);
      return GlideApp.with(context.getApplicationContext())
                     .asBitmap()
                     .load(new DecryptableUri(uri))
                     .centerCrop()
                     .into(maxSize, maxSize)
                     .get();
    } catch (InterruptedException | ExecutionException e) {
      Log.w(TAG, e);
      throw new BitmapDecodingException(e);
    }
  }

  public static Slide getSlideForAttachment(Context context, Attachment attachment) {
    Slide slide = null;
    if (isGif(attachment.getContentType())) {
      slide = new GifSlide(context, attachment);
    } else if (isImageType(attachment.getContentType())) {
      slide = new ImageSlide(context, attachment);
    } else if (isVideoType(attachment.getContentType())) {
      slide = new VideoSlide(context, attachment);
    } else if (isAudioType(attachment.getContentType())) {
      slide = new AudioSlide(context, attachment);
    } else if (isMms(attachment.getContentType())) {
      slide = new MmsSlide(context, attachment);
    } else if (attachment.getContentType() != null) {
      slide = new DocumentSlide(context, attachment);
    }

    return slide;
  }

  public static @Nullable String getMimeType(Context context, Uri uri) {
    if (uri == null) return null;

    if (PersistentBlobProvider.isAuthority(context, uri)) {
      return PersistentBlobProvider.getMimeType(context, uri);
    }

    String type = context.getContentResolver().getType(uri);
    if (type == null) {
      final String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
      type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
    }
    return getCorrectedMimeType(type);
  }

  public static @Nullable String getCorrectedMimeType(@Nullable String mimeType) {
    if (mimeType == null) return null;

    switch(mimeType) {
    case "image/jpg":
      return MimeTypeMap.getSingleton().hasMimeType(IMAGE_JPEG)
             ? IMAGE_JPEG
             : mimeType;
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

  public static boolean isMms(String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals("application/mms");
  }

  public static boolean isGif(Attachment attachment) {
    return isGif(attachment.getContentType());
  }

  public static boolean isImage(Attachment attachment) {
    return isImageType(attachment.getContentType());
  }

  public static boolean isAudio(Attachment attachment) {
    return isAudioType(attachment.getContentType());
  }

  public static boolean isVideo(Attachment attachment) {
    return isVideoType(attachment.getContentType());
  }

  public static boolean isVideo(String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().startsWith("video/");
  }

  public static boolean isGif(String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals("image/gif");
  }

  public static boolean isFile(Attachment attachment) {
    return !isGif(attachment) && !isImage(attachment) && !isAudio(attachment) && !isVideo(attachment);
  }

  public static boolean isTextType(String contentType) {
    return (null != contentType) && contentType.startsWith("text/");
  }

  public static boolean isImageType(String contentType) {
    return (null != contentType) && contentType.startsWith("image/");
  }

  public static boolean isAudioType(String contentType) {
    return (null != contentType) && contentType.startsWith("audio/");
  }

  public static boolean isVideoType(String contentType) {
    return (null != contentType) && contentType.startsWith("video/");
  }

  public static boolean hasVideoThumbnail(Uri uri) {
    Log.w(TAG, "Checking: " + uri);

    if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
      return false;
    }

    if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
      return uri.getLastPathSegment().contains("video");
    }

    return false;
  }

  public static @Nullable Bitmap getVideoThumbnail(Context context, Uri uri) {
    if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
      long videoId = Long.parseLong(uri.getLastPathSegment().split(":")[1]);

      return MediaStore.Video.Thumbnails.getThumbnail(context.getContentResolver(),
                                                      videoId,
                                                      MediaStore.Images.Thumbnails.MINI_KIND,
                                                      null);
    }

    return null;
  }

  public static @Nullable String getDiscreteMimeType(@NonNull String mimeType) {
    final String[] sections = mimeType.split("/", 2);
    return sections.length > 1 ? sections[0] : null;
  }

  public static class ThumbnailData {
    Bitmap bitmap;
    float aspectRatio;

    public ThumbnailData(Bitmap bitmap) {
      this.bitmap      = bitmap;
      this.aspectRatio = (float) bitmap.getWidth() / (float) bitmap.getHeight();
    }

    public Bitmap getBitmap() {
      return bitmap;
    }

    public float getAspectRatio() {
      return aspectRatio;
    }

    public InputStream toDataStream() {
      return BitmapUtil.toCompressedJpeg(bitmap);
    }
  }
}
