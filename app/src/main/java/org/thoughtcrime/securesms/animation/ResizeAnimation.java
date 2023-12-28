package org.thoughtcrime.securesms.animation;

import android.graphics.Point;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import androidx.annotation.NonNull;

public class ResizeAnimation extends Animation {

  private final View target;
  private final int  targetWidthPx;
  private final int  targetHeightPx;

  private int startWidth;
  private int startHeight;

  public ResizeAnimation(@NonNull View target, @NonNull Point dimension) {
    this(target, dimension.x, dimension.y);
  }

  public ResizeAnimation(@NonNull View target, int targetWidthPx, int targetHeightPx) {
    this.target         = target;
    this.targetWidthPx  = targetWidthPx;
    this.targetHeightPx = targetHeightPx;
  }

  @Override
  protected void applyTransformation(float interpolatedTime, Transformation t) {
    int newWidth  = (int) (startWidth + (targetWidthPx - startWidth) * interpolatedTime);
    int newHeight = (int) (startHeight + (targetHeightPx - startHeight) * interpolatedTime);

    ViewGroup.LayoutParams params = target.getLayoutParams();

    params.width  = newWidth;
    params.height = newHeight;

    target.setLayoutParams(params);
  }

  @Override
  public void initialize(int width, int height, int parentWidth, int parentHeight) {
    super.initialize(width, height, parentWidth, parentHeight);

    this.startWidth  = width;
    this.startHeight = height;
  }

  @Override
  public boolean willChangeBounds() {
    return true;
  }
}
