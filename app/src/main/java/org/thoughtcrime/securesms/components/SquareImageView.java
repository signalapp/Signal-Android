package org.thoughtcrime.securesms.components;

import android.content.Context;
import androidx.appcompat.widget.AppCompatImageView;
import android.util.AttributeSet;

public class SquareImageView extends AppCompatImageView {
  public SquareImageView(Context context) {
    super(context);
  }

  public SquareImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SquareImageView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    //noinspection SuspiciousNameCombination
    super.onMeasure(widthMeasureSpec, widthMeasureSpec);
  }
}
