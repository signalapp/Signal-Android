/**
 * Copyright (C) 2012 Whisper Systems
 * Copyright (C) 2013-2014 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;

public class ImageDivet extends ImageView {
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

  public ImageView asImageView() {
    return this;
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
