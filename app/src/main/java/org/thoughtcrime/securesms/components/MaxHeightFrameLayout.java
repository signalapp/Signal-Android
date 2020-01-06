package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.thoughtcrime.securesms.R;

public class MaxHeightFrameLayout extends FrameLayout {

  private final int maxHeight;

  public MaxHeightFrameLayout(@NonNull Context context) {
    this(context, null);
  }

  public MaxHeightFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public MaxHeightFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    if (attrs != null) {
      TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MaxHeightFrameLayout);

      maxHeight = a.getDimensionPixelSize(R.styleable.MaxHeightFrameLayout_mhfl_maxHeight, 0);

      a.recycle();
    } else {
      maxHeight = 0;
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, Math.min(bottom, top + maxHeight));
  }
}
