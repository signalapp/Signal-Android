package org.thoughtcrime.securesms.imageeditor.model;

import android.animation.ValueAnimator;
import androidx.annotation.Nullable;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

final class AlphaAnimation {

  private final static Interpolator interpolator = new LinearInterpolator();

  final static AlphaAnimation NULL_1 = new AlphaAnimation(1);

  private final float    from;
  private final float    to;
  private final Runnable invalidate;
  private final boolean  canAnimate;
  private       float    animatedFraction;

  private AlphaAnimation(float from, float to, @Nullable Runnable invalidate) {
    this.from       = from;
    this.to         = to;
    this.invalidate = invalidate;
    this.canAnimate = invalidate != null;
  }

  private AlphaAnimation(float fixed) {
    this(fixed, fixed, null);
  }

  static AlphaAnimation animate(float from, float to, @Nullable Runnable invalidate) {
    if (invalidate == null) {
      return new AlphaAnimation(to);
    }

    if (from != to) {
      AlphaAnimation animationMatrix = new AlphaAnimation(from, to, invalidate);
      animationMatrix.start();
      return animationMatrix;
    } else {
      return new AlphaAnimation(to);
    }
  }

  private void start() {
    if (canAnimate && invalidate != null) {
      ValueAnimator animator = ValueAnimator.ofFloat(from, to);
      animator.setDuration(200);
      animator.setInterpolator(interpolator);
      animator.addUpdateListener(animation -> {
        animatedFraction = (float) animation.getAnimatedValue();
        invalidate.run();
      });
      animator.start();
    }
  }

  float getValue() {
    if (!canAnimate) return to;

    return animatedFraction;
  }
}
