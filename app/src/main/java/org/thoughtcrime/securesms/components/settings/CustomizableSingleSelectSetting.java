package org.thoughtcrime.securesms.components.settings;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder;

import java.util.Objects;

/**
 * Adds ability to customize a value for a single select (radio) setting.
 */
public class CustomizableSingleSelectSetting {

  public interface CustomizableSingleSelectionListener extends SingleSelectSetting.SingleSelectSelectionChangedListener {
    void onCustomizeClicked(@Nullable Item item);
  }

  public static class ViewHolder extends MappingViewHolder<Item> {
    private final View                                customize;
    private final SingleSelectSetting.ViewHolder      delegate;
    private final Group                               customizeGroup;
    private final CustomizableSingleSelectionListener selectionListener;

    public ViewHolder(@NonNull View itemView, @NonNull CustomizableSingleSelectionListener selectionListener) {
      super(itemView);
      this.selectionListener = selectionListener;

      customize      = findViewById(R.id.customizable_single_select_customize);
      customizeGroup = findViewById(R.id.customizable_single_select_customize_group);

      delegate = new SingleSelectSetting.ViewHolder(itemView, selectionListener);
    }

    @Override
    public void bind(@NonNull Item model) {
      delegate.bind(model.singleSelectItem);
      customizeGroup.setVisibility(model.singleSelectItem.isSelected() ? View.VISIBLE : View.GONE);
      customize.setOnClickListener(v -> selectionListener.onCustomizeClicked(model));
    }
  }

  public static class Item implements MappingModel<Item> {
    private final SingleSelectSetting.Item singleSelectItem;
    private final Object                   customValue;

    public <T> Item(@NonNull T item, @Nullable String text, boolean isSelected, @Nullable Object customValue, @Nullable String summaryText) {
      this.customValue = customValue;

      singleSelectItem = new SingleSelectSetting.Item(item, text, summaryText, isSelected);
    }

    public @Nullable Object getCustomValue() {
      return customValue;
    }

    @Override
    public boolean areItemsTheSame(@NonNull Item newItem) {
      return singleSelectItem.areItemsTheSame(newItem.singleSelectItem);
    }

    @Override
    public boolean areContentsTheSame(@NonNull Item newItem) {
      return singleSelectItem.areContentsTheSame(newItem.singleSelectItem) && Objects.equals(customValue, newItem.customValue);
    }
  }
}
