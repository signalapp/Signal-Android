package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import androidx.appcompat.widget.AppCompatImageView;
import android.util.AttributeSet;

import org.thoughtcrime.securesms.R;

public class ImageDivet extends AppCompatImageView {
  private static final float CORNER_OFFSET = 12F;
  private static final String[] POSITIONS  = new String[] {"bottom_right"};

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
    int attributes[]     = new int[] {R.attr.lower_right_divet};

    TypedArray drawables = getContext().obtainStyledAttributes(attributes);

    switch (position) {
    case 0:
      drawable = drawables.getDrawable(0);
      break;
    }

    drawableIntrinsicWidth  = drawable.getIntrinsicWidth();
    drawableIntrinsicHeight = drawable.getIntrinsicHeight();

    drawables.recycle();
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

  public float getFarOffset() {
    return getCloseOffset() + drawableIntrinsicHeight;
  }

  private void computeBounds(Canvas c) {
    final int right = getWidth();
    final int bottom = getHeight();

    switch (position) {
    case 0:
     drawable.setBounds(
         right - drawableIntrinsicWidth,
         bottom - drawableIntrinsicHeight,
         right,
         bottom);
     break;
    }
  }
}
