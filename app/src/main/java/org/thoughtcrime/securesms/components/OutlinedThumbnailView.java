package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;

public class OutlinedThumbnailView extends ThumbnailView {

  private CornerMask cornerMask;
  private Outliner   outliner;
  private boolean    isOutlineEnabled;

  public OutlinedThumbnailView(Context context) {
    super(context);
    init(null);
  }

  public OutlinedThumbnailView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attrs) {
    cornerMask = new CornerMask(this);
    outliner   = new Outliner();

    int defaultOutlinerColor = ContextCompat.getColor(getContext(), R.color.signal_inverse_transparent_20);
    outliner.setColor(defaultOutlinerColor);

    int radius = 0;

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.OutlinedThumbnailView, 0, 0);
      radius = typedArray.getDimensionPixelOffset(R.styleable.OutlinedThumbnailView_otv_cornerRadius, 0);

      outliner.setStrokeWidth(typedArray.getDimensionPixelSize(R.styleable.OutlinedThumbnailView_otv_strokeWidth, 1));
      outliner.setColor(typedArray.getColor(R.styleable.OutlinedThumbnailView_otv_strokeColor, defaultOutlinerColor));
    }

    setRadius(radius);
    setCorners(radius, radius, radius, radius);

    setWillNotDraw(false);
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);

    if (isOutlineEnabled) {
      cornerMask.mask(canvas);
      outliner.draw(canvas);
    }
  }

  public void setOutlineEnabled(boolean isOutlineEnabled) {
    this.isOutlineEnabled = isOutlineEnabled;
    invalidate();
  }

  public void setCorners(int topLeft, int topRight, int bottomRight, int bottomLeft) {
    cornerMask.setRadii(topLeft, topRight, bottomRight, bottomLeft);
    outliner.setRadii(topLeft, topRight, bottomRight, bottomLeft);
    postInvalidate();
  }
}
