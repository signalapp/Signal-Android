package org.thoughtcrime.securesms.components;

import android.content.Context;
import androidx.appcompat.widget.AppCompatImageButton;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class RepeatableImageKey extends AppCompatImageButton {

  private KeyEventListener listener;

  public RepeatableImageKey(Context context) {
    super(context);
    init();
  }

  public RepeatableImageKey(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public RepeatableImageKey(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    setOnClickListener(new RepeaterClickListener());
    setOnTouchListener(new RepeaterTouchListener());
  }

  public void setOnKeyEventListener(KeyEventListener listener) {
    this.listener = listener;
  }

  private void notifyListener() {
    if (this.listener != null) this.listener.onKeyEvent();
  }

  private class RepeaterClickListener implements OnClickListener {
    @Override public void onClick(View v) {
      notifyListener();
    }
  }

  private class Repeater implements Runnable {
    @Override
    public void run() {
      notifyListener();
      postDelayed(this, ViewConfiguration.getKeyRepeatDelay());
    }
  }

  private class RepeaterTouchListener implements OnTouchListener {
    private final Repeater repeater;

    RepeaterTouchListener() {
      this.repeater = new Repeater();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
      switch (motionEvent.getAction()) {
      case MotionEvent.ACTION_DOWN:
        view.postDelayed(repeater, ViewConfiguration.getKeyRepeatTimeout());
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        return false;
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP:
        view.removeCallbacks(repeater);
        return false;
      default:
        return false;
      }
    }
  }

  public interface KeyEventListener {
    void onKeyEvent();
  }
}
