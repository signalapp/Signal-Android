package org.thoughtcrime.securesms.components.camera;

import android.content.Context;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;

import org.thoughtcrime.securesms.R;

public class HidingImageButton extends ImageButton {
  public HidingImageButton(Context context) {
    super(context);
  }

  public HidingImageButton(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public HidingImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void hide() {
    if (!isEnabled() || getVisibility() == GONE) return;
    final Animation animation = AnimationUtils.loadAnimation(getContext(), R.anim.slide_to_right);
    animation.setAnimationListener(new AnimationListener() {
      @Override public void onAnimationStart(Animation animation) {}
      @Override public void onAnimationRepeat(Animation animation) {}
      @Override public void onAnimationEnd(Animation animation) {
        setVisibility(GONE);
      }
    });
    animateWith(animation);
  }

  public void show() {
    if (!isEnabled() || getVisibility() == VISIBLE) return;
    setVisibility(VISIBLE);
    animateWith(AnimationUtils.loadAnimation(getContext(), R.anim.slide_from_right));
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
