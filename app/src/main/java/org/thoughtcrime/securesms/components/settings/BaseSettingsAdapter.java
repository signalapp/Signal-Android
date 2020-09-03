package org.thoughtcrime.securesms.components.settings;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.MappingAdapter;

/**
 * Reusable adapter for generic settings list.
 */
public class BaseSettingsAdapter extends MappingAdapter {
  public void configureSingleSelect(@NonNull SingleSelectSetting.SingleSelectSelectionChangedListener selectionChangedListener) {
    registerFactory(SingleSelectSetting.Item.class,
                    new LayoutFactory<>(v -> new SingleSelectSetting.ViewHolder(v, selectionChangedListener), R.layout.single_select_item));
  }

  public void configureCustomizableSingleSelect(@NonNull CustomizableSingleSelectSetting.CustomizableSingleSelectionListener selectionListener) {
    registerFactory(CustomizableSingleSelectSetting.Item.class,
                    new LayoutFactory<>(v -> new CustomizableSingleSelectSetting.ViewHolder(v, selectionListener), R.layout.customizable_single_select_item));
  }
}
