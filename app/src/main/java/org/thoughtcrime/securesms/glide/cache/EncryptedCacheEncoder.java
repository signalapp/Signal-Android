package org.thoughtcrime.securesms.glide.cache;


import androidx.annotation.NonNull;

import com.bumptech.glide.load.Encoder;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool;

import org.signal.core.util.logging.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

public class EncryptedCacheEncoder extends EncryptedCoder implements Encoder<InputStream> {

  private static final String TAG = Log.tag(EncryptedCacheEncoder.class);

  private final byte[]    secret;
  private final ArrayPool byteArrayPool;

  public EncryptedCacheEncoder(@NonNull byte[] secret, @NonNull ArrayPool byteArrayPool) {
    this.secret        = secret;
    this.byteArrayPool = byteArrayPool;
  }

  @SuppressWarnings("EmptyCatchBlock")
  @Override
  public boolean encode(@NonNull InputStream data, @NonNull File file, @NonNull Options options) {
    byte[] buffer = byteArrayPool.get(ArrayPool.STANDARD_BUFFER_SIZE_BYTES, byte[].class);

    try (OutputStream outputStream = createEncryptedOutputStream(secret, file)) {
      int read;

      while ((read = data.read(buffer)) != -1) {
        outputStream.write(buffer, 0, read);
      }

      return true;
    } catch (IOException e) {
      if (e instanceof SocketException) {
        Log.d(TAG, "Socket exception. Likely a cancellation.");
      } else {
        Log.w(TAG, e);
      }
      return false;
    } finally {
      byteArrayPool.put(buffer);
    }
  }
}
