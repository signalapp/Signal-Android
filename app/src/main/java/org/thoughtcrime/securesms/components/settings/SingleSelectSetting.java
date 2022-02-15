package org.thoughtcrime.securesms.components.settings;

import android.text.TextUtils;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder;

import java.util.Objects;

/**
 * Single select (radio) setting option
 */
public class SingleSelectSetting {

  public interface SingleSelectSelectionChangedListener {
    void onSelectionChanged(@NonNull Object selection);
  }

  public static class ViewHolder extends MappingViewHolder<Item> {

    private final   RadioButton                          radio;
    protected final CheckedTextView                      text;
    private final   TextView                             summaryText;
    protected final SingleSelectSelectionChangedListener selectionChangedListener;

    public ViewHolder(@NonNull View itemView, @NonNull SingleSelectSelectionChangedListener selectionChangedListener) {
      super(itemView);
      this.selectionChangedListener = selectionChangedListener;
      this.radio                    = findViewById(R.id.single_select_item_radio);
      this.text                     = findViewById(R.id.single_select_item_text);
      this.summaryText              = findViewById(R.id.single_select_item_summary);
    }

    @Override
    public void bind(@NonNull Item model) {
      radio.setChecked(model.isSelected);
      text.setText(model.text);
      if (!TextUtils.isEmpty(model.summaryText)) {
        summaryText.setText(model.summaryText);
        summaryText.setVisibility(View.VISIBLE);
      } else {
        summaryText.setVisibility(View.GONE);
      }
      itemView.setOnClickListener(v -> selectionChangedListener.onSelectionChanged(model.item));
    }
  }

  public static class Item implements MappingModel<Item> {
    private final Object  item;
    private final String  text;
    private final String  summaryText;
    private final boolean isSelected;

    public <T> Item(@NonNull T item, @Nullable String text, @Nullable String summaryText, boolean isSelected) {
      this.item        = item;
      this.summaryText = summaryText;
      this.text        = text != null ? text : item.toString();
      this.isSelected  = isSelected;
    }

    public @NonNull Object getItem() {
      return item;
    }

    public @Nullable String getText() {
      return text;
    }

    public @Nullable String getSummaryText() {
      return summaryText;
    }

    public boolean isSelected() {
      return isSelected;
    }

    @Override
    public boolean areItemsTheSame(@NonNull Item newItem) {
      return item.equals(newItem.item);
    }

    @Override
    public boolean areContentsTheSame(@NonNull Item newItem) {
      return Objects.equals(text, newItem.text)               &&
             Objects.equals(summaryText, newItem.summaryText) &&
             isSelected == newItem.isSelected;
    }
  }
}
