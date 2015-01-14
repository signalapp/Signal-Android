package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.PartDatabase;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.PduPart;
import ws.com.google.android.mms.pdu.SendReq;

public class MediaUtil {
  private static final String TAG = MediaUtil.class.getSimpleName();

  public static ThumbnailData generateThumbnail(Context context, MasterSecret masterSecret, Uri uri, String type)
      throws IOException, BitmapDecodingException, OutOfMemoryError
  {
    long   startMillis = System.currentTimeMillis();
    ThumbnailData data;
    if      (ContentType.isImageType(type)) data = new ThumbnailData(generateImageThumbnail(context, masterSecret, uri));
    else                                    data = null;

    if (data != null) {
      Log.w(TAG, String.format("generated thumbnail for part, %dx%d (%.3f:1) in %dms",
                               data.getBitmap().getWidth(), data.getBitmap().getHeight(),
                               data.getAspectRatio(), System.currentTimeMillis() - startMillis));
    }

    return data;
  }

  public static byte[] getPartData(Context context, MasterSecret masterSecret, PduPart part)
      throws IOException
  {
    ByteArrayOutputStream os = part.getDataSize() > 0 && part.getDataSize() < Integer.MAX_VALUE
        ? new ByteArrayOutputStream((int) part.getDataSize())
        : new ByteArrayOutputStream();
    Util.copy(PartAuthority.getPartStream(context, masterSecret, part.getDataUri()), os);
    return os.toByteArray();
  }

  private static Bitmap generateImageThumbnail(Context context, MasterSecret masterSecret, Uri uri)
      throws IOException, BitmapDecodingException, OutOfMemoryError
  {
    int maxSize = context.getResources().getDimensionPixelSize(R.dimen.thumbnail_max_size);
    return BitmapUtil.createScaledBitmap(context, masterSecret, uri, maxSize, maxSize);
  }

  public static boolean isImage(PduPart part) {
    return ContentType.isImageType(Util.toIsoString(part.getContentType()));
  }

  public static boolean isAudio(PduPart part) {
    return ContentType.isAudioType(Util.toIsoString(part.getContentType()));
  }

  public static boolean isVideo(PduPart part) {
    return ContentType.isVideoType(Util.toIsoString(part.getContentType()));
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
