/*
 * Copyright (C) 2015 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.thoughtcrime.securesms.giph.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;


/**
 * AspectRatioImageView maintains an aspect ratio by adjusting the width or height dimension. The
 * aspect ratio (width to height ratio) and adjustment dimension can be configured.
 */
public class AspectRatioImageView extends ImageView {

  private static final float DEFAULT_ASPECT_RATIO = 1.0f;
  private static final int DEFAULT_ADJUST_DIMENSION = 0;
  // defined by attrs.xml enum
  static final int ADJUST_DIMENSION_HEIGHT = 0;
  static final int ADJUST_DIMENSION_WIDTH = 1;

  private double aspectRatio;         // width to height ratio
  private int dimensionToAdjust;      // ADJUST_DIMENSION_HEIGHT or ADJUST_DIMENSION_WIDTH

  public AspectRatioImageView(Context context) {
    this(context, null);
  }

  public AspectRatioImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
//    final TypedArray a = context.obtainStyledAttributes(attrs,
//                                                        R.styleable.tw__AspectRatioImageView);
//    try {
//      aspectRatio = a.getFloat(R.styleable.tw__AspectRatioImageView_tw__image_aspect_ratio,
//                               DEFAULT_ASPECT_RATIO);
//      dimensionToAdjust
//          = a.getInt(R.styleable.tw__AspectRatioImageView_tw__image_dimension_to_adjust,
//                     DEFAULT_ADJUST_DIMENSION);
//    } finally {
//      a.recycle();
//    }
  }

  public double getAspectRatio() {
    return aspectRatio;
  }

  public int getDimensionToAdjust() {
    return dimensionToAdjust;
  }

  /**
   * Sets the aspect ratio that should be respected during measurement.
   *
   * @param aspectRatio desired width to height ratio
   */
  public void setAspectRatio(final double aspectRatio) {
    this.aspectRatio = aspectRatio;
  }

  /**
   * Resets the size to 0.
   */
  public void resetSize() {
    if (getMeasuredWidth() == 0 && getMeasuredHeight() == 0) {
      return;
    }
    measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
    layout(0, 0, 0, 0);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    if (dimensionToAdjust == ADJUST_DIMENSION_HEIGHT) {
      height = calculateHeight(width, aspectRatio);
    } else {
      width = calculateWidth(height, aspectRatio);
    }
    setMeasuredDimension(width, height);
  }

  /**
   * Returns the height that will satisfy the width to height aspect ratio, keeping the given
   * width fixed.
   */
  int calculateHeight(int width, double ratio) {
    if (ratio == 0) {
      return 0;
    }
    return (int) Math.round(width / ratio);
  }

  /**
   * Returns the width that will satisfy the width to height aspect ratio, keeping the given
   * height fixed.
   */
  int calculateWidth(int height, double ratio) {
    return (int) Math.round(height * ratio);
  }
}