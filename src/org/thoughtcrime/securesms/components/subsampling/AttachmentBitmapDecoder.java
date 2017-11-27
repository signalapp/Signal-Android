package org.thoughtcrime.securesms.components.subsampling;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageDecoder;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.service.KeyCachingService;

import java.io.InputStream;

public class AttachmentBitmapDecoder implements ImageDecoder{

  private final MasterSecret masterSecret;

  public AttachmentBitmapDecoder(@NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
  }

  @Override
  public Bitmap decode(Context context, Uri uri) throws Exception {
    if (!PartAuthority.isLocalUri(uri)) {
      return new SkiaImageDecoder().decode(context, uri);
    }

    InputStream inputStream = PartAuthority.getAttachmentStream(context, masterSecret, uri);

    try {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inPreferredConfig = Bitmap.Config.ARGB_8888;

      Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);

      if (bitmap == null) {
        throw new RuntimeException("Skia image region decoder returned null bitmap - image format may not be supported");
      }

      return bitmap;
    } finally {
      if (inputStream != null) inputStream.close();
    }
  }


}
