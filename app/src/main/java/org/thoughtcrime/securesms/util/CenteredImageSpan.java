package org.thoughtcrime.securesms.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Centers the given drawable in the bounds of a single line of text, regardless of the size of the given drawable.
 */
public class CenteredImageSpan extends ReplacementSpan {

  private final Drawable drawable;

  public CenteredImageSpan(@NonNull Drawable drawable) {
    this.drawable = drawable;
  }

  @Override
  public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
    return drawable.getBounds().right;
  }

  @Override
  public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
    canvas.save();
    int transY = top + (bottom - top) / 2 - drawable.getBounds().height() / 2;
    canvas.translate(x, transY);
    drawable.draw(canvas);
    canvas.restore();
  }
}
