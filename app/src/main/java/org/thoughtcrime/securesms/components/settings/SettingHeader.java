package org.thoughtcrime.securesms.components.settings;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder;

import java.util.Objects;

/**
 * Provide a default header {@link MappingModel} and {@link MappingViewHolder} for settings screens.
 */
public final class SettingHeader {

  public static final class ViewHolder extends MappingViewHolder<Item> {

    private final TextView headerText;

    public ViewHolder(@NonNull View itemView) {
      super(itemView);
      this.headerText = findViewById(R.id.base_settings_header_item_text);
    }

    @Override
    public void bind(@NonNull Item model) {
      if (model.text != null) {
        headerText.setText(model.text);
      } else {
        headerText.setText(model.textRes);
      }
    }
  }

  public static final class Item implements MappingModel<Item> {
    private final int    textRes;
    private final String text;

    public Item(String text) {
      this.text    = text;
      this.textRes = 0;
    }

    public Item(@StringRes int textRes) {
      this.text    = null;
      this.textRes = textRes;
    }

    @Override
    public boolean areItemsTheSame(@NonNull Item newItem) {
      return textRes == newItem.textRes && Objects.equals(text, newItem.text);
    }

    @Override
    public boolean areContentsTheSame(@NonNull Item newItem) {
      return areItemsTheSame(newItem);
    }
  }
}
