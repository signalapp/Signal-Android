/**
 * Copyright (c) 2016 UPTech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.thoughtcrime.securesms.scribbles.widget.entity;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.scribbles.viewmodel.Layer;


public class ImageEntity extends MotionEntity {

  @NonNull
  private final Bitmap bitmap;

  public ImageEntity(@NonNull Layer layer,
                     @NonNull Bitmap bitmap,
                     @IntRange(from = 1) int canvasWidth,
                     @IntRange(from = 1) int canvasHeight) {
    super(layer, canvasWidth, canvasHeight);

    this.bitmap = bitmap;
    float width = bitmap.getWidth();
    float height = bitmap.getHeight();

    float widthAspect = 1.0F * canvasWidth / width;
    float heightAspect = 1.0F * canvasHeight / height;
    // fit the smallest size
    holyScale = Math.min(widthAspect, heightAspect);

    // initial position of the entity
    srcPoints[0] = 0; srcPoints[1] = 0;
    srcPoints[2] = width; srcPoints[3] = 0;
    srcPoints[4] = width; srcPoints[5] = height;
    srcPoints[6] = 0; srcPoints[7] = height;
    srcPoints[8] = 0; srcPoints[8] = 0;
  }

  @Override
  public void drawContent(@NonNull Canvas canvas, @Nullable Paint drawingPaint) {
    canvas.drawBitmap(bitmap, matrix, drawingPaint);
  }

  @Override
  public int getWidth() {
    return bitmap.getWidth();
  }

  @Override
  public int getHeight() {
    return bitmap.getHeight();
  }

  @Override
  public void release() {
    if (!bitmap.isRecycled()) {
      bitmap.recycle();
    }
  }
}