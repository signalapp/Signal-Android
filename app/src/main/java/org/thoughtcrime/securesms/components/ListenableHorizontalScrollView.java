package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;

import androidx.annotation.Nullable;

/**
 * Unfortunately {@link HorizontalScrollView#setOnScrollChangeListener(OnScrollChangeListener)}
 * wasn't added until API 23, so now we have to do this ourselves.
 */
public class ListenableHorizontalScrollView extends HorizontalScrollView {

  private OnScrollListener listener;

  public ListenableHorizontalScrollView(Context context) {
    super(context);
  }

  public ListenableHorizontalScrollView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setOnScrollListener(@Nullable OnScrollListener listener) {
    this.listener = listener;
  }

  @Override
  protected void onScrollChanged(int newLeft, int newTop, int oldLeft, int oldTop) {
    if (listener != null) {
      listener.onScroll(newLeft, oldLeft);
    }
    super.onScrollChanged(newLeft, newTop, oldLeft, oldTop);
  }

  public interface OnScrollListener {
    void onScroll(int newLeft, int oldLeft);
  }
}
