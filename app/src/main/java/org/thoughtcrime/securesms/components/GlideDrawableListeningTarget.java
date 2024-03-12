package org.thoughtcrime.securesms.components;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.request.target.DrawableImageViewTarget;

import org.signal.core.util.concurrent.SettableFuture;
import org.signal.core.util.logging.Log;

public class GlideDrawableListeningTarget extends DrawableImageViewTarget {

  private static final String TAG = Log.tag(GlideDrawableListeningTarget.class);

  private final SettableFuture<Boolean> loaded;

  public GlideDrawableListeningTarget(@NonNull ImageView view, @NonNull SettableFuture<Boolean> loaded) {
    super(view);
    this.loaded = loaded;
  }

  @Override
  protected void setResource(@Nullable Drawable resource) {
    if (resource == null) {
      Log.d(TAG, "Loaded null resource");
    } else {
      Log.d(TAG, "Loaded resource of w " + resource.getIntrinsicWidth() + " by h " + resource.getIntrinsicHeight());
    }

    super.setResource(resource);
    loaded.set(true);
  }

  @Override
  public void onLoadFailed(@Nullable Drawable errorDrawable) {
    super.onLoadFailed(errorDrawable);
    loaded.set(true);
  }
}
