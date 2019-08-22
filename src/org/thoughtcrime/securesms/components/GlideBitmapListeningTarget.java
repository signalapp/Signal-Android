package org.thoughtcrime.securesms.components;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.widget.ImageView;

import com.bumptech.glide.request.target.BitmapImageViewTarget;

import org.thoughtcrime.securesms.util.concurrent.SettableFuture;

public class GlideBitmapListeningTarget extends BitmapImageViewTarget {

  private final SettableFuture<Boolean> loaded;

  public GlideBitmapListeningTarget(@NonNull ImageView view, @NonNull SettableFuture<Boolean> loaded) {
    super(view);
    this.loaded = loaded;
  }

  @Override
  protected void setResource(@Nullable Bitmap resource) {
    super.setResource(resource);
    loaded.set(true);
  }

  @Override
  public void onLoadFailed(@Nullable Drawable errorDrawable) {
    super.onLoadFailed(errorDrawable);
    loaded.set(true);
  }
}
