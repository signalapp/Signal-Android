package org.thoughtcrime.securesms.components;

import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.ImageView;

import com.bumptech.glide.request.target.DrawableImageViewTarget;

import org.thoughtcrime.securesms.util.concurrent.SettableFuture;

public class GlideDrawableListeningTarget extends DrawableImageViewTarget {

  private final SettableFuture<Boolean> loaded;

  public GlideDrawableListeningTarget(@NonNull ImageView view, @NonNull SettableFuture<Boolean> loaded) {
    super(view);
    this.loaded = loaded;
  }

  @Override
  protected void setResource(@Nullable Drawable resource) {
    super.setResource(resource);
    loaded.set(true);
  }

  @Override
  public void onLoadFailed(@Nullable Drawable errorDrawable) {
    super.onLoadFailed(errorDrawable);
    loaded.set(true);
  }
}
