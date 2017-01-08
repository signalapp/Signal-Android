package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.bumptech.glide.Glide;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.GifSlide;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

import ws.com.google.android.mms.ContentType;

public class MediaUtil {

  private static final String TAG = MediaUtil.class.getSimpleName();

  public static @Nullable ThumbnailData generateThumbnail(Context context, MasterSecret masterSecret, String contentType, Uri uri)
      throws BitmapDecodingException
  {
    long   startMillis = System.currentTimeMillis();
    ThumbnailData data = null;

    if (ContentType.isImageType(contentType)) {
      data = new ThumbnailData(generateImageThumbnail(context, masterSecret, uri));
    }

    if (data != null) {
      Log.w(TAG, String.format("generated thumbnail for part, %dx%d (%.3f:1) in %dms",
                               data.getBitmap().getWidth(), data.getBitmap().getHeight(),
                               data.getAspectRatio(), System.currentTimeMillis() - startMillis));
    }

    return data;
  }

  private static Bitmap generateImageThumbnail(Context context, MasterSecret masterSecret, Uri uri)
      throws BitmapDecodingException
  {
    try {
      int maxSize = context.getResources().getDimensionPixelSize(R.dimen.media_bubble_height);
      return Glide.with(context)
                  .load(new DecryptableUri(masterSecret, uri))
                  .asBitmap()
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
    } else if (ContentType.isImageType(attachment.getContentType())) {
      slide = new ImageSlide(context, attachment);
    } else if (ContentType.isVideoType(attachment.getContentType())) {
      slide = new VideoSlide(context, attachment);
    } else if (ContentType.isAudioType(attachment.getContentType())) {
      slide = new AudioSlide(context, attachment);
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
      return MimeTypeMap.getSingleton().hasMimeType(ContentType.IMAGE_JPEG)
             ? ContentType.IMAGE_JPEG
             : mimeType;
    default:
      return mimeType;
    }
  }

  public static long getMediaSize(Context context, MasterSecret masterSecret, Uri uri) throws IOException {
    InputStream in = PartAuthority.getAttachmentStream(context, masterSecret, uri);
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

  public static boolean isGif(String contentType) {
    return !TextUtils.isEmpty(contentType) && contentType.trim().equals("image/gif");
  }

  public static boolean isGif(Attachment attachment) {
    return isGif(attachment.getContentType());
  }

  public static boolean isImage(Attachment attachment) {
    return ContentType.isImageType(attachment.getContentType());
  }

  public static boolean isAudio(Attachment attachment) {
    return ContentType.isAudioType(attachment.getContentType());
  }

  public static boolean isVideo(Attachment attachment) {
    return ContentType.isVideoType(attachment.getContentType());
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
