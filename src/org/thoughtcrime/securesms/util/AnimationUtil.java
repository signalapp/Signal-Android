package org.thoughtcrime.securesms.util;

import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

public class AnimationUtil {

  public static void fadeOut(View view, int duration) {
    fadeOut(view, duration, View.INVISIBLE);
  }

  public static void fadeOut(final View view, int duration, final int visibility) {
    Animation animation = new AlphaAnimation(1, 0);
    animation.setDuration(duration);

    animation.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {}
      @Override
      public void onAnimationRepeat(Animation animation) {}
      @Override
      public void onAnimationEnd(Animation animation) {
        view.setVisibility(visibility);
      }
    });

    view.startAnimation(animation);
  }

  public static void fadeIn(View view, int duration) {
    Animation animation = new AlphaAnimation(0, 1);
    animation.setDuration(duration);

    view.setVisibility(View.VISIBLE);
    view.startAnimation(animation);
  }

}
