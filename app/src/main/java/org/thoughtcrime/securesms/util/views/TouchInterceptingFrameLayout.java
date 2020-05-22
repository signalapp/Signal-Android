package org.thoughtcrime.securesms.util.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TouchInterceptingFrameLayout extends FrameLayout {

  private OnInterceptTouchEventListener listener;

  public TouchInterceptingFrameLayout(@NonNull Context context) {
    super(context);
  }

  public TouchInterceptingFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public TouchInterceptingFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    if (listener != null) {
      return listener.onInterceptTouchEvent(ev);
    } else {
      return super.onInterceptTouchEvent(ev);
    }
  }

  public void setOnInterceptTouchEventListener(@Nullable OnInterceptTouchEventListener listener) {
    this.listener = listener;
  }

  public interface OnInterceptTouchEventListener {
    boolean onInterceptTouchEvent(MotionEvent ev);
  }
}
