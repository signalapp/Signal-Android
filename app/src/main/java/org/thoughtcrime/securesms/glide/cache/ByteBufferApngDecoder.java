package org.thoughtcrime.securesms.glide.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;

import org.signal.glide.common.io.ByteBufferReader;
import org.signal.glide.common.loader.ByteBufferLoader;
import org.signal.glide.common.loader.Loader;
import org.signal.glide.load.resource.apng.decode.APNGDecoder;
import org.signal.glide.load.resource.apng.decode.APNGParser;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ByteBufferApngDecoder implements ResourceDecoder<ByteBuffer, APNGDecoder> {

  @Override
  public boolean handles(@NonNull ByteBuffer source, @NonNull Options options) {
    if (options.get(ApngOptions.ANIMATE)) {
      return APNGParser.isAPNG(new ByteBufferReader(source));
    } else {
      return false;
    }
  }

  @Override
  public @Nullable Resource<APNGDecoder> decode(@NonNull final ByteBuffer source, int width, int height, @NonNull Options options) throws IOException {
    if (!APNGParser.isAPNG(new ByteBufferReader(source))) {
      return null;
    }

    Loader loader = new ByteBufferLoader() {
      @Override
      public ByteBuffer getByteBuffer() {
        source.position(0);
        return source;
      }
    };

    return new FrameSeqDecoderResource(new APNGDecoder(loader, null), source.limit());
  }

  private static class FrameSeqDecoderResource implements Resource<APNGDecoder> {
    private final APNGDecoder decoder;
    private final int         size;

    FrameSeqDecoderResource(@NonNull APNGDecoder decoder, int size) {
      this.decoder = decoder;
      this.size    = size;
    }

    @Override
    public @NonNull Class<APNGDecoder> getResourceClass() {
      return APNGDecoder.class;
    }

    @Override
    public @NonNull APNGDecoder get() {
      return this.decoder;
    }

    @Override
    public int getSize() {
      return this.size;
    }

    @Override
    public void recycle() {
      this.decoder.stop();
    }
  }
}

