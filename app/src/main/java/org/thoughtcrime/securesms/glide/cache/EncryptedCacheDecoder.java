package org.thoughtcrime.securesms.glide.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;

import org.signal.core.util.logging.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class EncryptedCacheDecoder<DecodeType> extends EncryptedCoder implements ResourceDecoder<File, DecodeType> {

  private static final String TAG = Log.tag(EncryptedCacheDecoder.class);

  private final byte[]                                   secret;
  private final ResourceDecoder<InputStream, DecodeType> decoder;

  public EncryptedCacheDecoder(byte[] secret, ResourceDecoder<InputStream, DecodeType> decoder) {
    this.secret  = secret;
    this.decoder = decoder;
  }

  @Override
  public boolean handles(@NonNull File source, @NonNull Options options) throws IOException {
    try (InputStream inputStream = createEncryptedInputStream(secret, source)) {
      return decoder.handles(inputStream, options);
    } catch (IOException e) {
      Log.w(TAG, e);
      return false;
    }
  }

  @Override
  public @Nullable Resource<DecodeType> decode(@NonNull File source, int width, int height, @NonNull Options options) throws IOException {
    try (InputStream inputStream = createEncryptedInputStream(secret, source)) {
      return decoder.decode(inputStream, width, height, options);
    }
  }
}
