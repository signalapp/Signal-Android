package org.thoughtcrime.securesms.components;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.LinearLayout;

public class HidingLinearLayout extends LinearLayout {

  public HidingLinearLayout(Context context) {
    super(context);
  }

  public HidingLinearLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public HidingLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void hide() {
    if (!isEnabled() || getVisibility() == GONE) return;

    AnimationSet animation = new AnimationSet(true);
    animation.addAnimation(new ScaleAnimation(1, 0.5f, 1, 1, Animation.RELATIVE_TO_SELF, 1f, Animation.RELATIVE_TO_SELF, 0.5f));
    animation.addAnimation(new AlphaAnimation(1, 0));
    animation.setDuration(100);

    animation.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {
      }

      @Override
      public void onAnimationRepeat(Animation animation) {
      }

      @Override
      public void onAnimationEnd(Animation animation) {
        setVisibility(GONE);
      }
    });

    animateWith(animation);
  }

  public void show() {
    if (!isEnabled() || getVisibility() == VISIBLE) return;

    setVisibility(VISIBLE);

    AnimationSet animation = new AnimationSet(true);
    animation.addAnimation(new ScaleAnimation(0.5f, 1, 1, 1, Animation.RELATIVE_TO_SELF, 1f, Animation.RELATIVE_TO_SELF, 0.5f));
    animation.addAnimation(new AlphaAnimation(0, 1));
    animation.setDuration(100);

    animateWith(animation);
  }

  private void animateWith(Animation animation) {
    animation.setDuration(150);
    animation.setInterpolator(new FastOutSlowInInterpolator());
    startAnimation(animation);
  }

  public void disable() {
    setVisibility(GONE);
    setEnabled(false);
  }
}
