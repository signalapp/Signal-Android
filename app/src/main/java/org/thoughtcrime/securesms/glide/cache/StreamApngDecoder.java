package org.thoughtcrime.securesms.glide.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;

import org.signal.core.util.StreamUtil;
import org.signal.glide.common.io.StreamReader;
import org.signal.glide.load.resource.apng.decode.APNGDecoder;
import org.signal.glide.load.resource.apng.decode.APNGParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class StreamApngDecoder implements ResourceDecoder<InputStream, APNGDecoder> {

  /** Set to match {@link com.bumptech.glide.load.data.InputStreamRewinder}'s read limit */
  private static final int READ_LIMIT = 5 * 1024 * 1024;

  private final ResourceDecoder<ByteBuffer, APNGDecoder> byteBufferDecoder;

  public StreamApngDecoder(ResourceDecoder<ByteBuffer, APNGDecoder> byteBufferDecoder) {
    this.byteBufferDecoder = byteBufferDecoder;
  }

  @Override
  public boolean handles(@NonNull InputStream source, @NonNull Options options) {
    if (options.get(ApngOptions.ANIMATE)) {
      return APNGParser.isAPNG(new LimitedReader(new StreamReader(source), READ_LIMIT));
    } else {
      return false;
    }
  }

  @Override
  public @Nullable Resource<APNGDecoder> decode(@NonNull final InputStream source, int width, int height, @NonNull Options options) throws IOException {
    byte[] data = StreamUtil.readFully(source);

    if (data == null) {
      return null;
    }

    ByteBuffer byteBuffer = ByteBuffer.wrap(data);
    return byteBufferDecoder.decode(byteBuffer, width, height, options);
  }
}

