package org.thoughtcrime.securesms.glide.cache;


import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import org.thoughtcrime.securesms.logging.Log;

import com.bumptech.glide.load.EncodeStrategy;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceEncoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.bitmap.BitmapEncoder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public class EncryptedBitmapResourceEncoder extends EncryptedCoder implements ResourceEncoder<Bitmap> {

  private static final String TAG = EncryptedBitmapResourceEncoder.class.getSimpleName();

  private final byte[] secret;

  public EncryptedBitmapResourceEncoder(@NonNull byte[] secret) {
    this.secret = secret;
  }

  @Override
  public EncodeStrategy getEncodeStrategy(@NonNull Options options) {
    return EncodeStrategy.TRANSFORMED;
  }

  @SuppressWarnings("EmptyCatchBlock")
  @Override
  public boolean encode(@NonNull Resource<Bitmap> data, @NonNull File file, @NonNull Options options) {
    Log.i(TAG, "Encrypted resource encoder running: " + file.toString());

    Bitmap                bitmap  = data.get();
    Bitmap.CompressFormat format  = getFormat(bitmap, options);
    int                   quality = options.get(BitmapEncoder.COMPRESSION_QUALITY);

    try (OutputStream os = createEncryptedOutputStream(secret, file)) {
      bitmap.compress(format, quality, os);
      os.close();
      return true;
    } catch (IOException e) {
      Log.w(TAG, e);
      return false;
    }
  }

  private Bitmap.CompressFormat getFormat(Bitmap bitmap, Options options) {
    Bitmap.CompressFormat format = options.get(BitmapEncoder.COMPRESSION_FORMAT);

    if (format != null) {
      return format;
    } else if (bitmap.hasAlpha()) {
      return Bitmap.CompressFormat.PNG;
    } else {
      return Bitmap.CompressFormat.JPEG;
    }
  }


}
