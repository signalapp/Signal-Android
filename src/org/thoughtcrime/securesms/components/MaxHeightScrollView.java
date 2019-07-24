package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.ScrollView;

import org.thoughtcrime.securesms.R;

public class MaxHeightScrollView extends ScrollView {

  private int maxHeight = -1;

  public MaxHeightScrollView(Context context) {
    super(context);
    initialize(null);
  }

  public MaxHeightScrollView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(attrs);
  }

  private void initialize(@Nullable AttributeSet attrs) {
    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.MaxHeightScrollView, 0, 0);

      maxHeight = typedArray.getDimensionPixelOffset(R.styleable.MaxHeightScrollView_scrollView_maxHeight, -1);

      typedArray.recycle();
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    if (maxHeight >= 0) {
      heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }
}
