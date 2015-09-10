package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import org.thoughtcrime.securesms.R;

public class AnimatingToggle extends FrameLayout {

  private View current;

  public AnimatingToggle(Context context) {
    super(context);
  }

  public AnimatingToggle(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public AnimatingToggle(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public void addView(@NonNull View child, int index, ViewGroup.LayoutParams params) {
    super.addView(child, index, params);

    if (getChildCount() == 1) {
      current = child;
      child.setVisibility(View.VISIBLE);
    } else {
      child.setVisibility(View.GONE);
    }
    child.setClickable(false);
  }

  public void display(@Nullable View view) {
    display(view, true);
  }

  protected void display(@Nullable View view, boolean animated) {
    if (view == current) return;

    if (animated) {
      if (current != null) animateOut(current, AnimationUtils.loadAnimation(getContext(), R.anim.animation_toggle_out));
      if (view    != null) animateIn(view, AnimationUtils.loadAnimation(getContext(), R.anim.animation_toggle_in));
    } else {
      if (current != null) current.setVisibility(GONE);
      if (view    != null) view.setVisibility(VISIBLE);
    }

    current = view;
  }

  protected @Nullable View getCurrent() {
    return current;
  }

  private void animateOut(final View view, Animation animation) {
    animation.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {
      }

      @Override
      public void onAnimationEnd(Animation animation) {
        view.setVisibility(View.GONE);
      }

      @Override
      public void onAnimationRepeat(Animation animation) {
      }
    });

    view.startAnimation(animation);
  }

  private void animateIn(View view, Animation animation) {
    animation.setInterpolator(new FastOutSlowInInterpolator());
    view.setVisibility(View.VISIBLE);
    view.startAnimation(animation);
  }
}
