package org.thoughtcrime.securesms.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import org.thoughtcrime.securesms.R;

public class TypingIndicatorView extends LinearLayout {

  private static final long DURATION   = 300;
  private static final long PRE_DELAY  = 500;
  private static final long POST_DELAY = 500;

  private AnimatorSet animation1;
  private AnimatorSet animation2;
  private AnimatorSet animation3;

  public TypingIndicatorView(Context context) {
    super(context);
    initialize(null);
  }

  public TypingIndicatorView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize(attrs);
  }

  private void initialize(@Nullable AttributeSet attrs) {
    inflate(getContext(), R.layout.typing_indicator_view, this);

    View dot1 = findViewById(R.id.typing_dot1);
    View dot2 = findViewById(R.id.typing_dot2);
    View dot3 = findViewById(R.id.typing_dot3);

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.TypingIndicatorView, 0, 0);
      int        tint       = typedArray.getColor(R.styleable.TypingIndicatorView_typingIndicator_tint, Color.WHITE);
      typedArray.recycle();

      dot1.getBackground().setColorFilter(tint, PorterDuff.Mode.MULTIPLY);
      dot2.getBackground().setColorFilter(tint, PorterDuff.Mode.MULTIPLY);
      dot3.getBackground().setColorFilter(tint, PorterDuff.Mode.MULTIPLY);
    }

    animation1 = getAnimation(dot1, DURATION, 0           );
    animation2 = getAnimation(dot2, DURATION, DURATION / 2);
    animation3 = getAnimation(dot3, DURATION, DURATION    );

    animation3.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        postDelayed(TypingIndicatorView.this::startAnimation, POST_DELAY);
      }
    });
  }

  public void startAnimation() {
    postDelayed(() -> {
      animation1.start();
      animation2.start();
      animation3.start();
    }, PRE_DELAY);
  }

  public void stopAnimation() {
    animation1.cancel();
    animation2.cancel();
    animation3.cancel();
  }

  private AnimatorSet getAnimation(@NonNull View view, long duration, long startDelay) {
    AnimatorSet grow = new AnimatorSet();
    grow.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, 0.5f, 1).setDuration(duration),
                      ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.5f, 1).setDuration(duration),
                      ObjectAnimator.ofFloat(view, View.ALPHA,   0.5f, 1).setDuration(duration));

    AnimatorSet shrink = new AnimatorSet();
    shrink.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, 1, 0.5f).setDuration(duration),
                        ObjectAnimator.ofFloat(view, View.SCALE_Y, 1, 0.5f).setDuration(duration),
                        ObjectAnimator.ofFloat(view, View.ALPHA,   1, 0.5f).setDuration(duration));

    AnimatorSet all = new AnimatorSet();
    all.playSequentially(grow, shrink);
    all.setStartDelay(startDelay);

    return all;
  }
}
