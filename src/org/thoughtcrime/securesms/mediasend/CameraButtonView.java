package org.thoughtcrime.securesms.mediasend;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.appcompat.widget.AppCompatButton;

import org.thoughtcrime.securesms.R;

public final class CameraButtonView extends AppCompatButton {

  private Animation shrinkAnimation;
  private Animation growAnimation;

  public CameraButtonView(Context context) {
    super(context);
    init(context);
  }

  public CameraButtonView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  public CameraButtonView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context);
  }

  public void init(Context context) {
    shrinkAnimation = AnimationUtils.loadAnimation(context, R.anim.camera_capture_button_shrink);
    growAnimation   = AnimationUtils.loadAnimation(context, R.anim.camera_capture_button_grow);

    shrinkAnimation.setFillAfter(true);
    shrinkAnimation.setFillEnabled(true);

    growAnimation.setFillAfter(true);
    growAnimation.setFillEnabled(true);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        if (isEnabled()) {
          startAnimation(shrinkAnimation);
          performClick();
        }
        return true;
      case MotionEvent.ACTION_UP:
        startAnimation(growAnimation);
        return true;
    }
    return false;
  }

  @Override
  public boolean performClick() {
    return super.performClick();
  }
}
