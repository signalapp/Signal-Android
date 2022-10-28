package org.thoughtcrime.securesms.util.adapter.mapping;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;

import java.util.function.Function;


public class LayoutFactory<T extends MappingModel<T>> implements Factory<T> {
  private       Function<View, MappingViewHolder<T>> creator;
  private final int                                  layout;

  public LayoutFactory(@NonNull Function<View, MappingViewHolder<T>> creator, @LayoutRes int layout) {
    this.creator = creator;
    this.layout  = layout;
  }

  @Override
  public @NonNull MappingViewHolder<T> createViewHolder(@NonNull ViewGroup parent) {
    return creator.apply(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
  }
}
