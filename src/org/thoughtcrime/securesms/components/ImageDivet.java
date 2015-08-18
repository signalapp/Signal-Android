package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;

public class ImageDivet extends ImageView {
  private static final String[] POSITIONS  = new String[] {"bottom_right"};

  private Drawable drawable;

  private int drawableIntrinsicWidth;
  private int drawableIntrinsicHeight;
  private int position;

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
  public void onDraw(@NonNull Canvas c) {
    super.onDraw(c);
    c.save();
    computeBounds();
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

  private void computeBounds() {
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
