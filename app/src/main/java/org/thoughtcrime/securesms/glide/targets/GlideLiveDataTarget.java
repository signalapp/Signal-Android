package org.thoughtcrime.securesms.glide.targets;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

/**
 * A Glide target that exposes a LiveData<Bitmap> that can be observed.
 *
 * If the load is canceled or otherwise fails, it will post a null value.
 */
public class GlideLiveDataTarget extends CustomTarget<Bitmap> {

  private final MutableLiveData<Bitmap> liveData = new MutableLiveData<>();

  public GlideLiveDataTarget(int width, int height) {
    super(width, height);
  }

  @Override
  public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
    liveData.postValue(resource);
  }

  @Override
  public void onLoadCleared(@Nullable Drawable placeholder) {
    liveData.postValue(null);
  }

  @Override
  public void onLoadFailed(@Nullable Drawable errorDrawable) {
    liveData.postValue(null);
  }

  public @NonNull LiveData<Bitmap> getLiveData() {
    return liveData;
  }
}
