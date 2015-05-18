package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;

public class AnimatingToggle extends FrameLayout {

  private static final int SPEED_MILLIS = 200;

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

    if (getChildCount() == 1) child.setVisibility(View.VISIBLE);
    else                      child.setVisibility(View.GONE);
  }

  public void display(View view) {
    if (view.getVisibility() == View.VISIBLE) return;

    int oldViewIndex = getVisibleViewIndex();
    int newViewIndex = getViewIndex(view);

    int sign;

    if (oldViewIndex < newViewIndex) sign = -1;
    else                             sign = 1;

    TranslateAnimation oldViewAnimation = createTranslation(0.0f, sign * 1.0f);
    TranslateAnimation newViewAnimation = createTranslation(sign * -1.0f, 0.0f);

    animateOut(oldViewIndex, oldViewAnimation);
    animateIn(newViewIndex, newViewAnimation);
  }

  private void animateOut(int viewIndex, TranslateAnimation animation) {
    final View view = getChildAt(viewIndex);

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

  private void animateIn(int viewIndex, TranslateAnimation animation) {
    final View view = getChildAt(viewIndex);
    view.setVisibility(View.VISIBLE);
    view.startAnimation(animation);
  }

  private int getVisibleViewIndex() {
    for (int i=0;i<getChildCount();i++) {
      if (getChildAt(i).getVisibility() == View.VISIBLE) return i;
    }

    throw new AssertionError("No visible view?");
  }

  private int getViewIndex(View view) {
    for (int i=0;i<getChildCount();i++) {
      if (getChildAt(i) == view) return i;
    }

    throw new IllegalArgumentException("Not a parent of this view.");
  }

  private TranslateAnimation createTranslation(float startY, float endY) {
    TranslateAnimation translateAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f,
                                                                   Animation.RELATIVE_TO_SELF, 0.0f,
                                                                   Animation.RELATIVE_TO_SELF, startY,
                                                                   Animation.RELATIVE_TO_SELF, endY);

    translateAnimation.setDuration(SPEED_MILLIS);

    return translateAnimation;
  }

}
