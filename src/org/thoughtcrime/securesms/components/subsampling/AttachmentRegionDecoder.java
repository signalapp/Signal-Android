package org.thoughtcrime.securesms.components.subsampling;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageRegionDecoder;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.PartAuthority;

import java.io.InputStream;

public class AttachmentRegionDecoder implements ImageRegionDecoder {

  private static final String TAG = AttachmentRegionDecoder.class.getName();

  private SkiaImageRegionDecoder passthrough;

  private BitmapRegionDecoder bitmapRegionDecoder;

  @Override
  public Point init(Context context, Uri uri) throws Exception {
    Log.d(TAG, "Init!");
    if (!PartAuthority.isLocalUri(uri)) {
      passthrough = new SkiaImageRegionDecoder();
      return passthrough.init(context, uri);
    }

    InputStream inputStream = PartAuthority.getAttachmentStream(context, uri);

    this.bitmapRegionDecoder = BitmapRegionDecoder.newInstance(inputStream, false);
    inputStream.close();

    return new Point(bitmapRegionDecoder.getWidth(), bitmapRegionDecoder.getHeight());
  }

  @Override
  public Bitmap decodeRegion(Rect rect, int sampleSize) {
    Log.d(TAG, "Decode region: " + rect);

    if (passthrough != null) {
      return passthrough.decodeRegion(rect, sampleSize);
    }

    synchronized(this) {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize      = sampleSize;
      options.inPreferredConfig = Bitmap.Config.RGB_565;

      Bitmap bitmap = bitmapRegionDecoder.decodeRegion(rect, options);

      if (bitmap == null) {
        throw new RuntimeException("Skia image decoder returned null bitmap - image format may not be supported");
      }

      return bitmap;
    }
  }

  public boolean isReady() {
    Log.d(TAG, "isReady");
    return (passthrough != null && passthrough.isReady()) ||
           (bitmapRegionDecoder != null && !bitmapRegionDecoder.isRecycled());
  }

  public void recycle() {
    if (passthrough != null) {
      passthrough.recycle();
      passthrough = null;
    } else {
      bitmapRegionDecoder.recycle();
    }
  }
}
