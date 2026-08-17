package org.thoughtcrime.securesms.components.subsampling;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageRegionDecoder;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.mms.PartAuthority;

import java.io.InputStream;

public class AttachmentRegionDecoder implements ImageRegionDecoder {

  private static final String TAG = Log.tag(AttachmentRegionDecoder.class);

  private final @Nullable GainmapReporter reporter;

  private SkiaImageRegionDecoder passthrough;

  private BitmapRegionDecoder bitmapRegionDecoder;

  public AttachmentRegionDecoder(@Nullable GainmapReporter reporter) {
    this.reporter = reporter;
  }

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
      options.inPreferredConfig = Bitmap.Config.ARGB_8888;

      Bitmap bitmap = bitmapRegionDecoder.decodeRegion(rect, options);

      if (bitmap == null) {
        throw new RuntimeException("Skia image decoder returned null bitmap - image format may not be supported");
      }

      // The tiling half of the pair: SubsamplingScaleImageView only keeps this decoder when the image does not
      // fit at native resolution inside the canvas max bitmap size, otherwise initialiseBaseLayer() recycles it
      // and hands the load to AttachmentBitmapDecoder. Both decoders therefore have to report.
      if (reporter != null && UltraHdrSupport.hasGainmap(bitmap)) {
        reporter.onGainmapPresent();
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
