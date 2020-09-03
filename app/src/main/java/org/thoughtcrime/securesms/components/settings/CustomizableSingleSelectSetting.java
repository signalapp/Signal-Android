package org.thoughtcrime.securesms.components.settings;

import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.MappingModel;
import org.thoughtcrime.securesms.util.MappingViewHolder;

import java.util.Objects;

/**
 * Adds ability to customize a value for a single select (radio) setting.
 */
public class CustomizableSingleSelectSetting {

  public interface CustomizableSingleSelectionListener extends SingleSelectSetting.SingleSelectSelectionChangedListener {
    void onCustomizeClicked(@NonNull Item item);
  }

  public static class ViewHolder extends MappingViewHolder<Item> {
    private final TextView                            summaryText;
    private final View                                customize;
    private final RadioButton                         radio;
    private final SingleSelectSetting.ViewHolder      delegate;
    private final Group                               customizeGroup;
    private final CustomizableSingleSelectionListener selectionListener;

    public ViewHolder(@NonNull View itemView, @NonNull CustomizableSingleSelectionListener selectionListener) {
      super(itemView);
      this.selectionListener = selectionListener;

      radio          = findViewById(R.id.customizable_single_select_radio);
      summaryText    = findViewById(R.id.customizable_single_select_summary);
      customize      = findViewById(R.id.customizable_single_select_customize);
      customizeGroup = findViewById(R.id.customizable_single_select_customize_group);

      delegate = new SingleSelectSetting.ViewHolder(itemView, selectionListener) {
        @Override
        protected void setChecked(boolean checked) {
          radio.setChecked(checked);
        }
      };
    }

    @Override
    public void bind(@NonNull Item model) {
      delegate.bind(model.singleSelectItem);
      customizeGroup.setVisibility(radio.isChecked() ? View.VISIBLE : View.GONE);
      customize.setOnClickListener(v -> selectionListener.onCustomizeClicked(model));
      if (model.getCustomValue() != null) {
        summaryText.setText(model.getSummaryText());
      }
    }
  }

  public static class Item implements MappingModel<Item> {
    private SingleSelectSetting.Item singleSelectItem;
    private Object                   customValue;
    private String                   summaryText;

    public <T> Item(@NonNull T item, @Nullable String text, boolean isSelected, @Nullable Object customValue, @Nullable String summaryText) {
      this.customValue = customValue;
      this.summaryText = summaryText;

      singleSelectItem = new SingleSelectSetting.Item(item, text, isSelected);
    }

    public @Nullable Object getCustomValue() {
      return customValue;
    }

    public @Nullable String getSummaryText() {
      return summaryText;
    }

    @Override
    public boolean areItemsTheSame(@NonNull Item newItem) {
      return singleSelectItem.areItemsTheSame(newItem.singleSelectItem);
    }

    @Override
    public boolean areContentsTheSame(@NonNull Item newItem) {
      return singleSelectItem.areContentsTheSame(newItem.singleSelectItem) && Objects.equals(customValue, newItem.customValue) && Objects.equals(summaryText, newItem.summaryText);
    }
  }
}
