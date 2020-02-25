package org.thoughtcrime.securesms.imageeditor.model;

import android.animation.ValueAnimator;
import android.graphics.Matrix;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.animation.CycleInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import org.thoughtcrime.securesms.imageeditor.CanvasMatrix;

/**
 * Animation Matrix provides a matrix that animates over time down to the identity matrix.
 */
final class AnimationMatrix {

  private final static float[]      iValues           = new float[9];
  private final static Interpolator interpolator      = new DecelerateInterpolator();
  private final static Interpolator pulseInterpolator = inverse(new CycleInterpolator(0.5f));

  static AnimationMatrix NULL = new AnimationMatrix();

  static {
    new Matrix().getValues(iValues);
  }

  private final Runnable invalidate;
  private final boolean  canAnimate;
  private final float[]  undoValues = new float[9];

  private final Matrix   temp       = new Matrix();
  private final float[]  tempValues = new float[9];

  private ValueAnimator animator;
  private float         animatedFraction;

  private AnimationMatrix(@NonNull Matrix undo, @NonNull Runnable invalidate) {
    this.invalidate = invalidate;
    this.canAnimate = true;
    undo.getValues(undoValues);
  }

  private AnimationMatrix() {
    canAnimate = false;
    invalidate = null;
  }

  static @NonNull AnimationMatrix animate(@NonNull Matrix from, @NonNull Matrix to, @Nullable Runnable invalidate) {
    if (invalidate == null) {
      return NULL;
    }

    Matrix undo = new Matrix();
    boolean inverted = to.invert(undo);
    if (inverted) {
      undo.preConcat(from);
    }
    if (inverted && !undo.isIdentity()) {
      AnimationMatrix animationMatrix = new AnimationMatrix(undo, invalidate);
      animationMatrix.start(interpolator);
      return animationMatrix;
    } else {
      return NULL;
    }
  }

  /**
   * Animate applying a matrix and then animate removing.
   */
  static @NonNull AnimationMatrix singlePulse(@NonNull Matrix pulse, @Nullable Runnable invalidate) {
    if (invalidate == null) {
      return NULL;
    }

    AnimationMatrix animationMatrix = new AnimationMatrix(pulse, invalidate);
    animationMatrix.start(pulseInterpolator);

    return animationMatrix;
  }

  private void start(@NonNull Interpolator interpolator) {
    if (canAnimate) {
      animator = ValueAnimator.ofFloat(1, 0);
      animator.setDuration(250);
      animator.setInterpolator(interpolator);
      animator.addUpdateListener(animation -> {
        animatedFraction = (float) animation.getAnimatedValue();
        invalidate.run();
      });
      animator.start();
    }
  }

  void stop() {
    ValueAnimator animator = this.animator;
    if (animator != null) animator.cancel();
  }

  /**
   * Append the current animation value.
   */
  void preConcatValueTo(@NonNull Matrix onTo) {
    if (!canAnimate) return;

    onTo.preConcat(buildTemp());
  }

  /**
   * Append the current animation value.
   */
  void preConcatValueTo(@NonNull CanvasMatrix canvasMatrix) {
    if (!canAnimate) return;

    canvasMatrix.concat(buildTemp());
  }

  private Matrix buildTemp() {
    if (!canAnimate) {
      temp.reset();
      return temp;
    }

    final float fractionCompliment = 1f - animatedFraction;
    for (int i = 0; i < 9; i++) {
      tempValues[i] = fractionCompliment * iValues[i] + animatedFraction * undoValues[i];
    }

    temp.setValues(tempValues);
    return temp;
  }

  private static Interpolator inverse(@NonNull Interpolator interpolator) {
    return input -> 1f - interpolator.getInterpolation(input);
  }
}
