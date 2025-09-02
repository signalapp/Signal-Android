package org.thoughtcrime.securesms.glide.cache;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.drawable.DrawableResource;
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder;

import org.signal.glide.load.resource.apng.APNGDrawable;
import org.signal.glide.load.resource.apng.decode.APNGDecoder;

public class ApngFrameDrawableTranscoder implements ResourceTranscoder<APNGDecoder, Drawable> {

  @Override
  public @Nullable Resource<Drawable> transcode(@NonNull Resource<APNGDecoder> toTranscode, @NonNull Options options) {
    APNGDecoder  decoder  = toTranscode.get();
    APNGDrawable drawable = new APNGDrawable(decoder);

    drawable.setAutoPlay(false);
    drawable.setLoopLimit(0);

    return new DrawableResource<Drawable>(drawable) {
      @Override
      public @NonNull Class<Drawable> getResourceClass() {
        return Drawable.class;
      }

      @Override
      public int getSize() {
        return 0;
      }

      @Override
      public void recycle() {
      }

      @Override
      public void initialize() {
        super.initialize();
      }
    };
  }
}

