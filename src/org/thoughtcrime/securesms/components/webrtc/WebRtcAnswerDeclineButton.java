package org.thoughtcrime.securesms.components.webrtc;


import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

public class WebRtcAnswerDeclineButton extends LinearLayout implements View.OnTouchListener {

  @SuppressWarnings("unused")
  private static final String TAG = WebRtcAnswerDeclineButton.class.getSimpleName();

  private static final int TOTAL_TIME = 1000;
  private static final int SHAKE_TIME = 200;

  private static final int UP_TIME       = (TOTAL_TIME - SHAKE_TIME) / 2;
  private static final int DOWN_TIME     = (TOTAL_TIME - SHAKE_TIME) / 2;
  private static final int FADE_OUT_TIME = 300;
  private static final int FADE_IN_TIME  = 100;
  private static final int SHIMMER_TOTAL = UP_TIME + SHAKE_TIME;

  private static final int ANSWER_THRESHOLD  = 112;
  private static final int DECLINE_THRESHOLD = 56;

  private TextView  swipeUpText;
  private ImageView fab;
  private TextView  swipeDownText;

  private ImageView arrowOne;
  private ImageView arrowTwo;
  private ImageView arrowThree;
  private ImageView arrowFour;

  private float lastY;

  private boolean animating = false;
  private boolean complete  = false;

  private AnimatorSet animatorSet;
  private AnswerDeclineListener listener;

  public WebRtcAnswerDeclineButton(Context context) {
    super(context);
    initialize();
  }

  public WebRtcAnswerDeclineButton(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public WebRtcAnswerDeclineButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public WebRtcAnswerDeclineButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  private void initialize() {
    setOrientation(LinearLayout.VERTICAL);
    setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    inflate(getContext(), R.layout.webrtc_answer_decline_button, this);

    this.swipeUpText   = findViewById(R.id.swipe_up_text);
    this.fab           = findViewById(R.id.answer);
    this.swipeDownText = findViewById(R.id.swipe_down_text);

    this.arrowOne   = findViewById(R.id.arrow_one);
    this.arrowTwo   = findViewById(R.id.arrow_two);
    this.arrowThree = findViewById(R.id.arrow_three);
    this.arrowFour  = findViewById(R.id.arrow_four);

    this.fab.setOnTouchListener(this);
  }

  public void startRingingAnimation() {
    if (!animating) {
      animating = true;
      animateElements(0);
    }
  }

  public void stopRingingAnimation() {
    if (animating) {
      animating = false;
      resetElements();
    }
  }

  public void setAnswerDeclineListener(AnswerDeclineListener listener) {
    this.listener = listener;
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {

    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        resetElements();
        swipeUpText.animate().alpha(0).setDuration(200).start();
        swipeDownText.animate().alpha(0).setDuration(200).start();
        lastY = event.getRawY();
        break;
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP:
        swipeUpText.clearAnimation();
        swipeDownText.clearAnimation();
        swipeUpText.setAlpha(1);
        swipeDownText.setAlpha(1);
        fab.setRotation(0);

        if (Build.VERSION.SDK_INT >= 21) {
          fab.getDrawable().setTint(getResources().getColor(R.color.green_600));
          fab.getBackground().setTint(Color.WHITE);
        }

        animating = true;
        animateElements(0);
        break;
      case MotionEvent.ACTION_MOVE:
        float difference = event.getRawY() - lastY;

        float differenceThreshold;
        float percentageToThreshold;
        int   backgroundColor;
        int   foregroundColor;

        if (difference <= 0) {
          differenceThreshold   = ViewUtil.dpToPx(getContext(), ANSWER_THRESHOLD);
          percentageToThreshold = Math.min(1, (difference * -1) / differenceThreshold);
          backgroundColor       = (int) new ArgbEvaluator().evaluate(percentageToThreshold, getResources().getColor(R.color.green_100), getResources().getColor(R.color.green_600));

          if (percentageToThreshold > 0.5) {
            foregroundColor = Color.WHITE;
          } else {
            foregroundColor = getResources().getColor(R.color.green_600);
          }

          fab.setTranslationY(difference);

          if (percentageToThreshold == 1 && listener != null) {
            fab.setVisibility(View.INVISIBLE);
            lastY = event.getRawY();
            if (!complete) {
              complete = true;
              listener.onAnswered();
            }
          }
        } else {
          differenceThreshold = ViewUtil.dpToPx(getContext(), DECLINE_THRESHOLD);
          percentageToThreshold = Math.min(1, difference / differenceThreshold);
          backgroundColor = (int) new ArgbEvaluator().evaluate(percentageToThreshold, getResources().getColor(R.color.red_100), getResources().getColor(R.color.red_600));

          if (percentageToThreshold > 0.5) {
            foregroundColor = Color.WHITE;
          } else {
            foregroundColor = getResources().getColor(R.color.green_600);
          }

          fab.setRotation(135 * percentageToThreshold);

          if (percentageToThreshold == 1 && listener != null) {
            fab.setVisibility(View.INVISIBLE);
            lastY = event.getRawY();

            if (!complete) {
              complete = true;
              listener.onDeclined();
            }
          }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          fab.getBackground().setTint(backgroundColor);
          fab.getDrawable().setTint(foregroundColor);
        }

        break;
    }

    return true;
  }

  private void animateElements(int delay) {
    ObjectAnimator fabUp    = getUpAnimation(fab);
    ObjectAnimator fabDown  = getDownAnimation(fab);
    ObjectAnimator fabShake = getShakeAnimation(fab);

    animatorSet = new AnimatorSet();
    animatorSet.play(fabUp).with(getUpAnimation(swipeUpText));
    animatorSet.play(fabShake).after(fabUp);
    animatorSet.play(fabDown).with(getDownAnimation(swipeUpText)).after(fabShake);

    animatorSet.play(getFadeOut(swipeDownText)).with(fabUp);
    animatorSet.play(getFadeIn(swipeDownText)).after(fabDown);

    animatorSet.play(getShimmer(arrowFour, arrowThree, arrowTwo, arrowOne));

    animatorSet.addListener(new Animator.AnimatorListener() {
      @Override
      public void onAnimationStart(Animator animation) {
      }

      @Override
      public void onAnimationEnd(Animator animation) {
        if (animating) animateElements(1000);
      }

      @Override
      public void onAnimationCancel(Animator animation) {}
      @Override
      public void onAnimationRepeat(Animator animation) {}
    });

    animatorSet.setStartDelay(delay);
    animatorSet.start();
  }

  private Animator getShimmer(View... targets) {
    AnimatorSet animatorSet  = new AnimatorSet();
    int         evenDuration = SHIMMER_TOTAL / targets.length;
    int         interval     = 75;

    for (int i=0;i<targets.length;i++) {
      animatorSet.play(getShimmer(targets[i], evenDuration + (evenDuration - interval)))
                 .after(interval * i);
    }

    return animatorSet;
  }

  private ObjectAnimator getShimmer(View target, int duration) {
    ObjectAnimator shimmer = ObjectAnimator.ofFloat(target, "alpha", 0, 1, 0);
    shimmer.setDuration(duration);

    return shimmer;
  }

  private ObjectAnimator getShakeAnimation(View target) {
    ObjectAnimator animator = ObjectAnimator.ofFloat(target, "translationX", 0, 25, -25, 25, -25,15, -15, 6, -6, 0);
    animator.setDuration(SHAKE_TIME);

    return animator;
  }

  private ObjectAnimator getUpAnimation(View target) {
    ObjectAnimator animator = ObjectAnimator.ofFloat(target, "translationY", 0, -1 * ViewUtil.dpToPx(getContext(), 32));
    animator.setInterpolator(new AccelerateInterpolator());
    animator.setDuration(UP_TIME);

    return animator;
  }

  private ObjectAnimator getDownAnimation(View target) {
    ObjectAnimator animator = ObjectAnimator.ofFloat(target, "translationY", 0);
    animator.setInterpolator(new DecelerateInterpolator());
    animator.setDuration(DOWN_TIME);

    return animator;
  }

  private ObjectAnimator getFadeOut(View target) {
    ObjectAnimator animator = ObjectAnimator.ofFloat(target, "alpha", 1, 0);
    animator.setDuration(FADE_OUT_TIME);
    return animator;
  }

  private ObjectAnimator getFadeIn(View target) {
    ObjectAnimator animator = ObjectAnimator.ofFloat(target, "alpha", 0, 1);
    animator.setDuration(FADE_IN_TIME);
    return animator;
  }

  private void resetElements() {
    animating = false;
    complete  = false;
    animatorSet.cancel();

    swipeUpText.setTranslationY(0);
    fab.setTranslationY(0);
    swipeDownText.setAlpha(1);
  }

  public interface AnswerDeclineListener {
    void onAnswered();
    void onDeclined();
  }
}
