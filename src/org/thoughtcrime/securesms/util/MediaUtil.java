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

  private static Bitmap generateImageThumbnail(Context context, MasterSecret masterSecret, Uri uri)
      throws IOException, BitmapDecodingException, OutOfMemoryError
  {
    int maxSize = context.getResources().getDimensionPixelSize(R.dimen.thumbnail_max_size);
    return BitmapUtil.createScaledBitmap(context, masterSecret, uri, maxSize, maxSize);
  }

  public static void prepareMessageMedia(Context context, MasterSecret masterSecret, SendReq message,
                                         MediaConstraints constraints, boolean toMemory)
      throws IOException, UndeliverableMessageException
  {
    for (int i=0;i<message.getBody().getPartsNum();i++) {
      final PduPart part = message.getBody().getPart(i);
      Log.w(TAG, "Sending MMS part of content-type: " + Util.toIsoString(part.getContentType()));

      if (!constraints.isSatisfied(context, masterSecret, part)) {
        if (constraints.canResize(part)) resizePart(context, masterSecret, constraints, part, toMemory);
        else                                       throw new UndeliverableMessageException("Size constraints could not be satisfied.");
      } else if (toMemory) {
        ByteArrayOutputStream os = part.getDataSize() > 0 && part.getDataSize() < Integer.MAX_VALUE
            ? new ByteArrayOutputStream((int)part.getDataSize())
            : new ByteArrayOutputStream();
        Util.copy(PartAuthority.getPartStream(context, masterSecret, part.getDataUri()), os);
        byte[] data = os.toByteArray();
        part.setData(data);
        part.setDataSize(data.length);
      }
    }
  }

  public static void resizePart(Context context, MasterSecret masterSecret, MediaConstraints constraints,
                                PduPart part, boolean toMemory)
      throws IOException, UndeliverableMessageException
  {
    Log.w(TAG, "resizing part " + part.getId() + (toMemory ? " (storing in memory)" : ""));
    final long   oldSize = part.getDataSize();
    final byte[] data    = constraints.getResizedMedia(context, masterSecret, part);
    if (toMemory) {
      part.setData(data);
      part.setDataSize(data.length);
    }
    try {
      PartDatabase database = DatabaseFactory.getPartDatabase(context);
      database.updatePartData(masterSecret, part, new ByteArrayInputStream(data));
    } catch (MmsException me) {
      throw new UndeliverableMessageException(me);
    }
    Log.w(TAG, String.format("Resized part %.1fkb => %.1fkb", oldSize/1024.0, part.getDataSize()/1024.0));
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
      InputStream jpegStream  = BitmapUtil.toCompressedJpeg(bitmap);
      bitmap.recycle();
      return jpegStream;
    }
  }
}
