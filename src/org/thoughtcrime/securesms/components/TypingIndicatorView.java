package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import org.thoughtcrime.securesms.R;

public class TypingIndicatorView extends LinearLayout {

  private static final long DURATION   = 300;
  private static final long PRE_DELAY  = 500;
  private static final long POST_DELAY = 500;
  private static final long  CYCLE_DURATION = 1500;
  private static final long  DOT_DURATION   = 600;
  private static final float MIN_ALPHA      = 0.4f;
  private static final float MIN_SCALE      = 0.75f;

  private boolean isActive;
  private long    startTime;

  private View dot1;
  private View dot2;
  private View dot3;

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

    setWillNotDraw(false);

    dot1 = findViewById(R.id.typing_dot1);
    dot2 = findViewById(R.id.typing_dot2);
    dot3 = findViewById(R.id.typing_dot3);

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.TypingIndicatorView, 0, 0);
      int        tint       = typedArray.getColor(R.styleable.TypingIndicatorView_typingIndicator_tint, Color.WHITE);
      typedArray.recycle();

      dot1.getBackground().setColorFilter(tint, PorterDuff.Mode.MULTIPLY);
      dot2.getBackground().setColorFilter(tint, PorterDuff.Mode.MULTIPLY);
      dot3.getBackground().setColorFilter(tint, PorterDuff.Mode.MULTIPLY);
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (!isActive) {
      super.onDraw(canvas);
      return;
    }

    long timeInCycle = (System.currentTimeMillis() - startTime) % CYCLE_DURATION;

    render(dot1, timeInCycle, 0);
    render(dot2, timeInCycle, 150);
    render(dot3, timeInCycle, 300);

    super.onDraw(canvas);
    postInvalidate();
  }

  private void render(View dot, long timeInCycle, long start) {
    long end  = start + DOT_DURATION;
    long peak = start + (DOT_DURATION / 2);

    if (timeInCycle < start || timeInCycle > end) {
      renderDefault(dot);
    } else if (timeInCycle < peak) {
      renderFadeIn(dot, timeInCycle, start);
    } else {
      renderFadeOut(dot, timeInCycle, peak);
    }
  }

  private void renderDefault(View dot) {
    dot.setAlpha(MIN_ALPHA);
    dot.setScaleX(MIN_SCALE);
    dot.setScaleY(MIN_SCALE);
  }

  private void renderFadeIn(View dot, long timeInCycle, long fadeInStart) {
    float percent = (float) (timeInCycle - fadeInStart) / 300;
    dot.setAlpha(MIN_ALPHA + (1 - MIN_ALPHA) * percent);
    dot.setScaleX(MIN_SCALE + (1 - MIN_SCALE) * percent);
    dot.setScaleY(MIN_SCALE + (1 - MIN_SCALE) * percent);
  }

  private void renderFadeOut(View dot, long timeInCycle, long fadeOutStart) {
    float percent = (float) (timeInCycle - fadeOutStart) / 300;
    dot.setAlpha(1 - (1 - MIN_ALPHA) * percent);
    dot.setScaleX(1 - (1 - MIN_SCALE) * percent);
    dot.setScaleY(1 - (1 - MIN_SCALE) * percent);
  }

  public void startAnimation() {
    isActive  = true;
    startTime = System.currentTimeMillis();

    postInvalidate();
  }

  public void stopAnimation() {
    isActive = false;
  }
}
