package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageButton;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;

public class ImageButtonDivet extends ImageButton {
  private static final float CORNER_OFFSET = 12F;
  private static final String[] POSITIONS  = new String[] {"none", "bottom_right"};

  private Drawable drawable;

  private int drawableIntrinsicWidth;
  private int drawableIntrinsicHeight;
  private int position;
  private int divetMarginH;
  private int divetMarginV;
  private float density;

  public ImageButtonDivet(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize(attrs);
  }

  public ImageButtonDivet(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(attrs);
  }

  public ImageButtonDivet(Context context) {
    super(context);
    initialize(null);
  }

  private void initialize(AttributeSet attrs) {
    if (attrs != null) {
      position = attrs.getAttributeListValue(null, "divet_position", POSITIONS, -1);
      int[] attrsArr = new int[] {R.attr.divet_margin_h, R.attr.divet_margin_v};
      TypedArray dimensions = getContext().obtainStyledAttributes(attrs, attrsArr);
      divetMarginH = attrs.getAttributeIntValue(null, "divet_margin_h", 0);
      divetMarginV = attrs.getAttributeIntValue(null, "divet_margin_v", 0);
    }

    density = getContext().getResources().getDisplayMetrics().density;
    setDrawable();
  }

  private void setDrawable() {
    int attributes[]     = new int[] {R.attr.lower_right_divet};

    TypedArray drawables = getContext().obtainStyledAttributes(attributes);

    switch (position) {
    case 0:
      drawable = null;
      break;
    case 1:
      drawable = drawables.getDrawable(0);
      break;
    }

    if(drawable != null) {
      drawableIntrinsicWidth = drawable.getIntrinsicWidth();
      drawableIntrinsicHeight = drawable.getIntrinsicHeight();
    }

    drawables.recycle();
  }

  @Override
  public void onDraw(Canvas c) {
    super.onDraw(c);
    if(drawable != null) {
      c.save();
      computeBounds(c);
      drawable.draw(c);
      c.restore();
    }
  }

  public void setDivetPosition(int position) {
    this.position = position;
    setDrawable();
    invalidate();
  }

  public int getPosition() {
    return position;
  }

  private void computeBounds(Canvas c) {
    final int right = getWidth();
    final int bottom = getHeight();

    int marginH = (int) (divetMarginH * density);
    int marginV = (int) (divetMarginV * density);

    switch (position) {
    case 1:
     drawable.setBounds(
         right - marginH - drawableIntrinsicWidth,
         bottom - marginV - drawableIntrinsicHeight,
         right - marginH,
         bottom - marginV);
     break;
    }
  }
}
