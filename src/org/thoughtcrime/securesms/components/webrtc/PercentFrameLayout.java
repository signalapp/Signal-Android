/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Xfermode;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.LinkedList;
import java.util.List;

/**
 * Simple container that confines the children to a subrectangle specified as percentage values of
 * the container size. The children are centered horizontally and vertically inside the confined
 * space.
 */
public class PercentFrameLayout extends ViewGroup {
  private int xPercent = 0;
  private int yPercent = 0;
  private int widthPercent = 100;
  private int heightPercent = 100;

  private boolean square = false;
  private boolean hidden = false;

  public PercentFrameLayout(Context context) {
    super(context);
  }

  public PercentFrameLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public PercentFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setSquare(boolean square) {
    this.square = square;
  }

  public void setHidden(boolean hidden) {
    this.hidden = hidden;
  }

  public boolean isHidden() {
    return this.hidden;
  }

  public void setPosition(int xPercent, int yPercent, int widthPercent, int heightPercent) {
    this.xPercent = xPercent;
    this.yPercent = yPercent;
    this.widthPercent = widthPercent;
    this.heightPercent = heightPercent;
  }

  @Override
  public boolean shouldDelayChildPressedState() {
    return false;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    final int width = getDefaultSize(Integer.MAX_VALUE, widthMeasureSpec);
    final int height = getDefaultSize(Integer.MAX_VALUE, heightMeasureSpec);

    setMeasuredDimension(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                         MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));

    int childWidth  = width * widthPercent / 100;
    int childHeight = height * heightPercent / 100;

    if (square) {
      if (width > height) childWidth  = childHeight;
      else                childHeight = childWidth;
    }

    if (hidden) {
      childWidth  = 1;
      childHeight = 1;
    }

    int childWidthMeasureSpec  = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.AT_MOST);
    int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.AT_MOST);

    for (int i = 0; i < getChildCount(); ++i) {
      final View child = getChildAt(i);
      if (child.getVisibility() != GONE) {
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
      }
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    final int width = right - left;
    final int height = bottom - top;
    // Sub-rectangle specified by percentage values.
    final int subWidth = width * widthPercent / 100;
    final int subHeight = height * heightPercent / 100;
    final int subLeft = left + width * xPercent / 100;
    final int subTop = top + height * yPercent / 100;


    for (int i = 0; i < getChildCount(); ++i) {
      final View child = getChildAt(i);
      if (child.getVisibility() != GONE) {
        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();
        // Center child both vertically and horizontally.
        int childLeft = subLeft + (subWidth - childWidth) / 2;
        int childTop = subTop + (subHeight - childHeight) / 2;

        if (hidden) {
          childLeft = 0;
          childTop  = 0;
        }

        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
      }
    }
  }
}
