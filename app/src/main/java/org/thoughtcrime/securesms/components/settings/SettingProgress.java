package org.thoughtcrime.securesms.components.settings;

import android.view.View;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder;

/**
 * Simple progress indicator that can be used multiple times (if provided with different {@link Item#id}s).
 */
public final class SettingProgress {

  public static final class ViewHolder extends MappingViewHolder<SettingProgress.Item> {

    public ViewHolder(@NonNull View itemView) {
      super(itemView);
    }

    @Override
    public void bind(@NonNull SettingProgress.Item model) { }
  }

  public static final class Item implements MappingModel<SettingProgress.Item> {
    private final int id;

    public Item() {
      this(0);
    }

    public Item(int id) {
      this.id = id;
    }

    @Override
    public boolean areItemsTheSame(@NonNull SettingProgress.Item newItem) {
      return id == newItem.id;
    }

    @Override
    public boolean areContentsTheSame(@NonNull SettingProgress.Item newItem) {
      return areItemsTheSame(newItem);
    }
  }
}
