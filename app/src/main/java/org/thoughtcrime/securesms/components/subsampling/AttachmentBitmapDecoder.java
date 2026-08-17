package org.thoughtcrime.securesms.components.subsampling;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageDecoder;

import org.thoughtcrime.securesms.mms.PartAuthority;

import java.io.InputStream;

public class AttachmentBitmapDecoder implements ImageDecoder{

  private final @Nullable GainmapReporter reporter;

  public AttachmentBitmapDecoder(@Nullable GainmapReporter reporter) {
    this.reporter = reporter;
  }

  @Override
  public Bitmap decode(Context context, Uri uri) throws Exception {
    if (!PartAuthority.isLocalUri(uri)) {
      return new SkiaImageDecoder().decode(context, uri);
    }

    InputStream inputStream = PartAuthority.getAttachmentStream(context, uri);

    try {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inPreferredConfig = Bitmap.Config.ARGB_8888;

      Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);

      if (bitmap == null) {
        throw new RuntimeException("Skia image region decoder returned null bitmap - image format may not be supported");
      }

      // SubsamplingScaleImageView reaches this decoder both for a preview source and, from
      // initialiseBaseLayer(), for any image that fits at native resolution inside the canvas max bitmap
      // size -- the common case for Signal attachments. Both decoders therefore have to report.
      if (reporter != null && UltraHdrSupport.hasGainmap(bitmap)) {
        reporter.onGainmapPresent();
      }

      return bitmap;
    } finally {
      if (inputStream != null) inputStream.close();
    }
  }


}
