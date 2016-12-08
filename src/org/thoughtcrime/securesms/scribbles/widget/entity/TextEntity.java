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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.thoughtcrime.securesms.scribbles.viewmodel.TextLayer;


public class TextEntity extends MotionEntity {

  private final TextPaint textPaint;

  @Nullable
  private Bitmap bitmap;

  public TextEntity(@NonNull TextLayer textLayer,
                    @IntRange(from = 1) int canvasWidth,
                    @IntRange(from = 1) int canvasHeight)
  {
    super(textLayer, canvasWidth, canvasHeight);
    this.textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    updateEntity(false);
  }

  private void updateEntity(boolean moveToPreviousCenter) {
    // save previous center
    PointF oldCenter = absoluteCenter();

    Bitmap newBmp = createBitmap(getLayer(), bitmap);

    // recycle previous bitmap (if not reused) as soon as possible
    if (bitmap != null && bitmap != newBmp && !bitmap.isRecycled()) {
      bitmap.recycle();
    }

    this.bitmap = newBmp;

    float width = bitmap.getWidth();
    float height = bitmap.getHeight();

    @SuppressWarnings("UnnecessaryLocalVariable")
    float widthAspect = 1.0F * canvasWidth / width;

    // for text we always match text width with parent width
    this.holyScale = widthAspect;

    // initial position of the entity
    srcPoints[0] = 0;
    srcPoints[1] = 0;
    srcPoints[2] = width;
    srcPoints[3] = 0;
    srcPoints[4] = width;
    srcPoints[5] = height;
    srcPoints[6] = 0;
    srcPoints[7] = height;
    srcPoints[8] = 0;
    srcPoints[8] = 0;

    if (moveToPreviousCenter) {
      // move to previous center
      moveCenterTo(oldCenter);
    }
  }

  /**
   * If reuseBmp is not null, and size of the new bitmap matches the size of the reuseBmp,
   * new bitmap won't be created, reuseBmp it will be reused instead
   *
   * @param textLayer text to draw
   * @param reuseBmp  the bitmap that will be reused
   * @return bitmap with the text
   */
  @NonNull
  private Bitmap createBitmap(@NonNull TextLayer textLayer, @Nullable Bitmap reuseBmp) {

    int boundsWidth = canvasWidth;

    // init params - size, color, typeface
    textPaint.setStyle(Paint.Style.FILL);
    textPaint.setTextSize(textLayer.getFont().getSize() * canvasWidth);
    textPaint.setColor(textLayer.getFont().getColor());
//        textPaint.setTypeface(fontProvider.getTypeface(textLayer.getFont().getTypeface()));

    // drawing text guide : http://ivankocijan.xyz/android-drawing-multiline-text-on-canvas/
    // Static layout which will be drawn on canvas
    StaticLayout sl = new StaticLayout(
        textLayer.getText(), // - text which will be drawn
        textPaint,
        boundsWidth, // - width of the layout
        Layout.Alignment.ALIGN_CENTER, // - layout alignment
        1, // 1 - text spacing multiply
        1, // 1 - text spacing add
        true); // true - include padding

    // calculate height for the entity, min - Limits.MIN_BITMAP_HEIGHT
    int boundsHeight = sl.getHeight();

    // create bitmap not smaller than TextLayer.Limits.MIN_BITMAP_HEIGHT
    int bmpHeight = (int) (canvasHeight * Math.max(TextLayer.Limits.MIN_BITMAP_HEIGHT,
                                                   1.0F * boundsHeight / canvasHeight));

    // create bitmap where text will be drawn
    Bitmap bmp;
    if (reuseBmp != null && reuseBmp.getWidth() == boundsWidth
        && reuseBmp.getHeight() == bmpHeight) {
      // if previous bitmap exists, and it's width/height is the same - reuse it
      bmp = reuseBmp;
      bmp.eraseColor(Color.TRANSPARENT); // erase color when reusing
    } else {
      bmp = Bitmap.createBitmap(boundsWidth, bmpHeight, Bitmap.Config.ARGB_8888);
    }

    Canvas canvas = new Canvas(bmp);
    canvas.save();

    // move text to center if bitmap is bigger that text
    if (boundsHeight < bmpHeight) {
      //calculate Y coordinate - In this case we want to draw the text in the
      //center of the canvas so we move Y coordinate to center.
      float textYCoordinate = (bmpHeight - boundsHeight) / 2;
      canvas.translate(0, textYCoordinate);
    }

    //draws static layout on canvas
    sl.draw(canvas);
    canvas.restore();

    return bmp;
  }

  @Override
    @NonNull
  public TextLayer getLayer() {
    return (TextLayer) layer;
  }

  @Override
  protected void drawContent(@NonNull Canvas canvas, @Nullable Paint drawingPaint) {
    if (bitmap != null) {
      canvas.drawBitmap(bitmap, matrix, drawingPaint);
    }
  }

  @Override
  public int getWidth() {
    return bitmap != null ? bitmap.getWidth() : 0;
  }

  @Override
  public int getHeight() {
    return bitmap != null ? bitmap.getHeight() : 0;
  }

  @Override
  public void updateEntity() {
    updateEntity(true);
  }
}