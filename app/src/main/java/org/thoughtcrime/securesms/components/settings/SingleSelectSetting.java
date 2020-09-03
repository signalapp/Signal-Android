package org.thoughtcrime.securesms.components.settings;

import android.view.View;
import android.widget.CheckedTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.MappingModel;
import org.thoughtcrime.securesms.util.MappingViewHolder;

import java.util.Objects;

/**
 * Single select (radio) setting option
 */
public class SingleSelectSetting {

  public interface SingleSelectSelectionChangedListener {
    void onSelectionChanged(@NonNull Object selection);
  }

  public static class ViewHolder extends MappingViewHolder<Item> {

    protected final CheckedTextView                      text;
    protected final SingleSelectSelectionChangedListener selectionChangedListener;

    public ViewHolder(@NonNull View itemView, @NonNull SingleSelectSelectionChangedListener selectionChangedListener) {
      super(itemView);
      this.selectionChangedListener = selectionChangedListener;
      this.text                     = findViewById(R.id.single_select_item_text);
    }

    @Override
    public void bind(@NonNull Item model) {
      text.setText(model.text);
      setChecked(model.isSelected);
      itemView.setOnClickListener(v -> selectionChangedListener.onSelectionChanged(model.item));
    }

    protected void setChecked(boolean checked) {
      text.setChecked(checked);
    }
  }

  public static class Item implements MappingModel<Item> {
    private final String  text;
    private final Object  item;
    private final boolean isSelected;

    public <T> Item(@NonNull T item, @Nullable String text, boolean isSelected) {
      this.item       = item;
      this.text       = text != null ? text : item.toString();
      this.isSelected = isSelected;
    }

    public @NonNull String getText() {
      return text;
    }

    public @NonNull Object getItem() {
      return item;
    }

    @Override
    public boolean areItemsTheSame(@NonNull Item newItem) {
      return item.equals(newItem.item);
    }

    @Override
    public boolean areContentsTheSame(@NonNull Item newItem) {
      return Objects.equals(text, newItem.text) && isSelected == newItem.isSelected;
    }
  }
}
