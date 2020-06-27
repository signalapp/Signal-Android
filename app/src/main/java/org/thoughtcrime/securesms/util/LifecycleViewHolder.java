package org.thoughtcrime.securesms.util;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.recyclerview.widget.RecyclerView;

public abstract class LifecycleViewHolder extends RecyclerView.ViewHolder implements LifecycleOwner {

  private final LifecycleRegistry lifecycleRegistry;

  public LifecycleViewHolder(@NonNull View itemView) {
    super(itemView);

    lifecycleRegistry = new LifecycleRegistry(this);
  }

  void onAttachedToWindow() {
    lifecycleRegistry.setCurrentState(Lifecycle.State.RESUMED);
  }

  void onDetachedFromWindow() {
    lifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
  }

  @Override
  public @NonNull Lifecycle getLifecycle() {
    return lifecycleRegistry;
  }
}
