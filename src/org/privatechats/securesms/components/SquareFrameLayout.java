package org.thoughtcrime.securesms.components;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class SquareFrameLayout extends FrameLayout {
  @SuppressWarnings("unused")
  public SquareFrameLayout(Context context) {
    super(context);
  }

  @SuppressWarnings("unused")
  public SquareFrameLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @TargetApi(VERSION_CODES.HONEYCOMB) @SuppressWarnings("unused")
  public SquareFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @TargetApi(VERSION_CODES.LOLLIPOP) @SuppressWarnings("unused")
  public SquareFrameLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    //noinspection SuspiciousNameCombination
    super.onMeasure(widthMeasureSpec, widthMeasureSpec);
  }
}
