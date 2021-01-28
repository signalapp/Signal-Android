package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.components.emoji.EmojiEditText;

/**
 * Selection aware {@link EmojiEditText}. This view allows the developer to provide an
 * {@link OnSelectionChangedListener} that will be notified when the selection is changed.
 */
public class SelectionAwareEmojiEditText extends EmojiEditText {

  private OnSelectionChangedListener onSelectionChangedListener;

  public SelectionAwareEmojiEditText(Context context) {
    super(context);
  }

  public SelectionAwareEmojiEditText(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SelectionAwareEmojiEditText(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setOnSelectionChangedListener(@Nullable OnSelectionChangedListener onSelectionChangedListener) {
    this.onSelectionChangedListener = onSelectionChangedListener;
  }

  @Override
  protected void onSelectionChanged(int selStart, int selEnd) {
    if (onSelectionChangedListener != null) {
      onSelectionChangedListener.onSelectionChanged(selStart, selEnd);
    }
    super.onSelectionChanged(selStart, selEnd);
  }

  public interface OnSelectionChangedListener {
    void onSelectionChanged(int selStart, int selEnd);
  }
}
