package org.thoughtcrime.securesms.glide.cache;


import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import org.thoughtcrime.securesms.logging.Log;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.bitmap.StreamBitmapDecoder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class EncryptedBitmapCacheDecoder extends EncryptedCoder implements ResourceDecoder<File, Bitmap> {

  private static final String TAG = EncryptedBitmapCacheDecoder.class.getSimpleName();

  private final StreamBitmapDecoder streamBitmapDecoder;
  private final byte[]              secret;

  public EncryptedBitmapCacheDecoder(@NonNull byte[] secret, @NonNull StreamBitmapDecoder streamBitmapDecoder) {
    this.secret              = secret;
    this.streamBitmapDecoder = streamBitmapDecoder;
  }

  @Override
  public boolean handles(@NonNull File source, @NonNull Options options)
      throws IOException
  {
    Log.i(TAG, "Checking item for encrypted Bitmap cache decoder: " + source.toString());

    try (InputStream inputStream = createEncryptedInputStream(secret, source)) {
      return streamBitmapDecoder.handles(inputStream, options);
    } catch (IOException e) {
      Log.w(TAG, e);
      return false;
    }
  }

  @Nullable
  @Override
  public Resource<Bitmap> decode(@NonNull File source, int width, int height, @NonNull Options options)
      throws IOException
  {
    Log.i(TAG, "Encrypted Bitmap cache decoder running: " + source.toString());
    try (InputStream inputStream = createEncryptedInputStream(secret, source)) {
      return streamBitmapDecoder.decode(inputStream, width, height, options);
    }
  }
}
