package org.thoughtcrime.securesms.animation.transitions;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.graphics.drawable.Drawable;
import android.transition.Transition;
import android.transition.TransitionValues;
import android.util.Property;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;

abstract class CircleSquareImageViewTransition extends Transition {

  private static final String CIRCLE_RATIO = "CIRCLE_RATIO";

  private final boolean toCircle;

  CircleSquareImageViewTransition(boolean toCircle) {
    this.toCircle = toCircle;
  }

  @Override
  public void captureStartValues(TransitionValues transitionValues) {
    View view = transitionValues.view;
    if (view instanceof ImageView) {
      transitionValues.values.put(CIRCLE_RATIO, toCircle ? 0f : 1f);
    }
  }

  @Override
  public void captureEndValues(TransitionValues transitionValues) {
    View view = transitionValues.view;
    if (view instanceof ImageView) {
      transitionValues.values.put(CIRCLE_RATIO, toCircle ? 1f : 0f);
    }
  }

  @Override
  public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
    if (startValues == null || endValues == null) {
      return null;
    }

    ImageView endImageView = (ImageView) endValues.view;
    float     start        = (float) startValues.values.get(CIRCLE_RATIO);
    float     end          = (float) endValues.values.get(CIRCLE_RATIO);

    return ObjectAnimator.ofFloat(endImageView, new RadiusRatioProperty(), start, end);
  }

  static final class RadiusRatioProperty extends Property<ImageView, Float> {

    private float ratio;

    RadiusRatioProperty() {
      super(Float.class, "circle_ratio");
    }

    @Override
    final public void set(ImageView imageView, Float ratio) {
      this.ratio = ratio;
      Drawable imageViewDrawable = imageView.getDrawable();
      if (imageViewDrawable instanceof RoundedBitmapDrawable) {
        RoundedBitmapDrawable drawable = (RoundedBitmapDrawable) imageViewDrawable;
        if (ratio > 0.95) {
          drawable.setCircular(true);
        } else {
          drawable.setCornerRadius(Math.min(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight()) * ratio * 0.5f);
        }
      }
    }

    @Override
    public Float get(ImageView object) {
      return ratio;
    }
  }
}
