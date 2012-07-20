package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;

public class ImageDivet extends ImageView {
  private static final float CORNER_OFFSET = 12F;
  private static final String[] POSITIONS  = new String[] {"left", "right"};

  private Drawable drawable;

  private int drawableIntrinsicWidth;
  private int drawableIntrinsicHeight;
  private int position;
  private float density;

  public ImageDivet(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize(attrs);
  }

  public ImageDivet(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(attrs);
  }

  public ImageDivet(Context context) {
    super(context);
    initialize(null);
  }

  private void initialize(AttributeSet attrs) {
    if (attrs != null) {
      position = attrs.getAttributeListValue(null, "position", POSITIONS, -1);
    }

    density = getContext().getResources().getDisplayMetrics().density;
    setDrawable();
  }

  private void setDrawable() {
    Resources r = getContext().getResources();

    switch (position) {
    case 0:
      drawable = r.getDrawable(R.drawable.divet_right);
      break;
    case 1:
      drawable = r.getDrawable(R.drawable.divet_left);
      break;
    }

    drawableIntrinsicWidth  = drawable.getIntrinsicWidth();
    drawableIntrinsicHeight = drawable.getIntrinsicHeight();
  }

  @Override
  public void onDraw(Canvas c) {
    super.onDraw(c);
    c.save();
    computeBounds(c);
    drawable.draw(c);
    c.restore();
  }

  public void setPosition(int position) {
    this.position = position;
    setDrawable();
    invalidate();
  }

  public int getPosition() {
    return position;
  }

  public float getCloseOffset() {
    return CORNER_OFFSET * density;
  }

  public ImageView asImageView() {
    return this;
  }

  public float getFarOffset() {
    return getCloseOffset() + drawableIntrinsicHeight;
  }

  private void computeBounds(Canvas c) {
    final int left = 0;
    final int top = 0;
    final int right = getWidth();

    final int cornerOffset = (int) getCloseOffset();

    switch (position) {
    case 1:
      drawable.setBounds(
          right - drawableIntrinsicWidth,
          top + cornerOffset,
          right,
          top + cornerOffset + drawableIntrinsicHeight);
          break;
    case 0:
     drawable.setBounds(
         left,
         top + cornerOffset,
         left + drawableIntrinsicWidth,
         top + cornerOffset + drawableIntrinsicHeight);
     break;
    }
  }
}
