package org.thoughtcrime.securesms.conversation;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Drawable which lets you punch a hole through another drawable.
 *
 * TODO: Remove in favor of ClipProjectionDrawable
 */
public final class MaskDrawable extends Drawable {

  private final RectF bounds   = new RectF();
  private final Path  clipPath = new Path();

  private Rect    clipRect;
  private float[] clipPathRadii;

  private final Drawable wrapped;

  public MaskDrawable(@NonNull Drawable wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public void draw(@NonNull Canvas canvas) {
    if (clipRect == null) {
      wrapped.draw(canvas);
      return;
    }

    canvas.save();

    if (clipPathRadii != null) {
      clipPath.reset();
      bounds.set(clipRect);
      clipPath.addRoundRect(bounds, clipPathRadii, Path.Direction.CW);
      canvas.clipPath(clipPath, Region.Op.DIFFERENCE);
    } else {
      canvas.clipRect(clipRect, Region.Op.DIFFERENCE);
    }

    wrapped.draw(canvas);
    canvas.restore();
  }

  @Override
  public void setAlpha(int alpha) {
    wrapped.setAlpha(alpha);
  }

  @Override
  public void setColorFilter(@Nullable ColorFilter colorFilter) {
    wrapped.setColorFilter(colorFilter);
  }

  @Override
  public int getOpacity() {
    return wrapped.getOpacity();
  }

  @Override
  public void setBounds(int left, int top, int right, int bottom) {
    super.setBounds(left, top, right, bottom);
    wrapped.setBounds(left, top, right, bottom);
  }

  @Override
  public boolean getPadding(@NonNull Rect padding) {
    return wrapped.getPadding(padding);
  }

  public void setMask(@Nullable Rect mask) {
    this.clipRect = new Rect(mask);

    invalidateSelf();
  }

  public void setCorners(@Nullable float[] clipPathRadii) {
    this.clipPathRadii = clipPathRadii;

    invalidateSelf();
  }
}
