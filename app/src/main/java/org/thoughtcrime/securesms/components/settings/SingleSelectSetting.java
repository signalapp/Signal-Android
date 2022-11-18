package org.thoughtcrime.securesms.components.settings;

import android.text.TextUtils;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.Color;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;

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

//    private final   RadioButton                          radio;
    protected final CheckedTextView                      text;
    protected final SingleSelectSelectionChangedListener selectionChangedListener;

    public ViewHolder(@NonNull View itemView, @NonNull SingleSelectSelectionChangedListener selectionChangedListener) {
      super(itemView);
      this.selectionChangedListener = selectionChangedListener;
      this.text                     = findViewById(R.id.single_select_item_text);
      text.setOnFocusChangeListener(new View.OnFocusChangeListener() {

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
          updateFocusView(text, hasFocus);
        }
      });
    }



    public void updateFocusView(CheckedTextView tv, boolean itemFocus) {
      Resources res =  getContext().getResources();
      int mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
      int mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);
      int mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
      int mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);
      int mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
      int mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);

      ValueAnimator va;
      if (itemFocus) {
        va = ValueAnimator.ofFloat(0, 1);
      } else {
        va = ValueAnimator.ofFloat(1, 0);
      }

      va.addUpdateListener(valueAnimator -> {
        float scale = (float) valueAnimator.getAnimatedValue();
        float height = ((float) (mFocusHeight - mNormalHeight)) * (scale) + (float) mNormalHeight;
        float textsize = ((float) (mFocusTextSize - mNormalTextSize)) * (scale) + (float) mNormalTextSize;
        float padding = (float) mNormalPaddingX - ((float) (mNormalPaddingX - mFocusPaddingX)) * (scale);
        int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
        int color = alpha * 0x1000000 + 0xffffff;
        tv.setBackgroundColor(Color.argb(0, 0, 0, 0));

        tv.setTextColor(color);
        tv.setPadding((int) padding, tv.getPaddingTop(), tv.getPaddingRight(), tv.getPaddingBottom());
        tv.setTextSize((int) textsize);
        tv.getLayoutParams().height = (int) height;
      });

      FastOutLinearInInterpolator mInterpolator = new FastOutLinearInInterpolator();
      va.setInterpolator(mInterpolator);
      if (itemFocus) {
        va.setDuration(300);
        va.start();
      } else {
        va.setDuration(300);
        va.start();
      }
    }

    @Override
    public void bind(@NonNull Item model) {
      text.setText(model.text);
      setChecked(model.isSelected);
      if (model.isSelected) text.postDelayed(new Runnable() {
        @Override
        public void run() {
          if (model.text.equals(getContext().getResources().getString(R.string.preferences_storage__custom))) {
            ((View) text.getParent()).requestFocus();
          } else {
            text.requestFocus();
          }
        }
      }, 50);
      itemView.setOnClickListener(v -> selectionChangedListener.onSelectionChanged(model.item));
    }

    protected void setChecked(boolean checked) {
      text.setChecked(checked);
    }
  }

  public static class Item implements MappingModel<Item> {
    private final Object  item;
    private final String  text;
    private final boolean isSelected;

    public <T> Item(@NonNull T item, @Nullable String text, boolean isSelected) {
      this.item        = item;
      this.text        = text != null ? text : item.toString();
      this.isSelected  = isSelected;
    }

    public @NonNull Object getItem() {
      return item;
    }

    public @NonNull String getText() {
      return text;
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
             isSelected == newItem.isSelected;
    }
  }
}
