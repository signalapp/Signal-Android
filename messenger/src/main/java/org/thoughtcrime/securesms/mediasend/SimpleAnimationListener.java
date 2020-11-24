package org.thoughtcrime.securesms.mediasend;

import android.view.animation.Animation;

/**
 * Basic implementation of {@link android.view.animation.Animation.AnimationListener} with empty
 * implementation so you don't have to override every method.
 */
public class SimpleAnimationListener implements Animation.AnimationListener {
  @Override
  public void onAnimationStart(Animation animation) {
  }

  @Override
  public void onAnimationEnd(Animation animation) {
  }

  @Override
  public void onAnimationRepeat(Animation animation) {
  }
}
