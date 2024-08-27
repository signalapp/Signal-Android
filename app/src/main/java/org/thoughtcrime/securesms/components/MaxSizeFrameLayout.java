package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;

/**
 * FrameLayout which allows user to specify maximum dimensions of itself and therefore its children.
 */
public class MaxSizeFrameLayout extends FrameLayout {

  private final int maxHeight;
  private final int maxWidth;

  public MaxSizeFrameLayout(@NonNull Context context) {
    this(context, null);
  }

  public MaxSizeFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public MaxSizeFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    if (attrs != null) {
      TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MaxSizeFrameLayout);

      maxHeight = a.getDimensionPixelSize(R.styleable.MaxSizeFrameLayout_msfl_maxHeight, 0);
      maxWidth  = a.getDimensionPixelSize(R.styleable.MaxSizeFrameLayout_msfl_maxWidth, 0);
      a.recycle();
    } else {
      maxHeight = 0;
      maxWidth  = 0;
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int newWidthSpec = updateMeasureSpecWithMaxSize(widthMeasureSpec, maxWidth);
    int newHeightSpec = updateMeasureSpecWithMaxSize(heightMeasureSpec, maxHeight);

    super.onMeasure(newWidthSpec, newHeightSpec);
  }

  private int updateMeasureSpecWithMaxSize(int measureSpec, int maxSize) {
    if (maxSize <= 0) {
      return measureSpec;
    }

    int mode = MeasureSpec.getMode(measureSpec);
    int size = MeasureSpec.getSize(measureSpec);

    if (mode == MeasureSpec.UNSPECIFIED) {
      return MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.AT_MOST);
    } else if (mode == MeasureSpec.EXACTLY && size > maxSize) {
      return MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.EXACTLY);
    } else if (mode == MeasureSpec.AT_MOST && size > maxSize) {
      return MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.AT_MOST);
    } else {
      return measureSpec;
    }
  }
}
