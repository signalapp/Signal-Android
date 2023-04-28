package org.thoughtcrime.securesms.blurhash;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.SimpleResource;

import java.io.IOException;

public class BlurHashResourceDecoder implements ResourceDecoder<BlurHash, Bitmap> {

  private static final int MAX_DIMEN = 20;

  @Override
  public boolean handles(@NonNull BlurHash source, @NonNull Options options) throws IOException {
    return true;
  }

  @Override
  public @Nullable Resource<Bitmap> decode(@NonNull BlurHash source, int width, int height, @NonNull Options options) throws IOException {
    final int finalWidth;
    final int finalHeight;

    if (width > height) {
      finalWidth  = Math.min(width, MAX_DIMEN);
      finalHeight = (int) (finalWidth * height / (float) width);
    } else {
      finalHeight = Math.min(height, MAX_DIMEN);
      finalWidth  = (int) (finalHeight * width / (float) height);
    }

    return new SimpleResource<>(BlurHashDecoder.decode(source.getHash(), finalWidth, finalHeight));
  }
}
