package org.thoughtcrime.securesms.util;

import android.annotation.TargetApi;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.widget.EditText;

public class KeyRepeater {
  public static final KeyEvent DELETE_KEY_EVENT = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);

  public static void attach(final View trigger, final EditText target, final KeyEvent keyEvent) {
    trigger.setOnClickListener(new RepeaterClickListener(target, keyEvent));
    trigger.setOnTouchListener(new RepeaterTouchListener(trigger, target, keyEvent));
  }

  private static void injectKeyEvent(EditText editText, KeyEvent event) {
    if (editText.getText().length() > 0) {
      editText.dispatchKeyEvent(event);
    }
  }

  private static class RepeaterClickListener implements OnClickListener {
    private EditText target;
    private KeyEvent keyEvent;

    public RepeaterClickListener(EditText target, KeyEvent keyEvent) {
      this.target   = target;
      this.keyEvent = keyEvent;
    }

    @Override public void onClick(View v) {
      injectKeyEvent(target, keyEvent);
    }
  }

  private static class Repeater implements Runnable {
    private View     trigger;
    private EditText target;
    private KeyEvent keyEvent;

    public Repeater(View trigger, EditText target, KeyEvent keyEvent) {
      this.trigger  = trigger;
      this.target   = target;
      this.keyEvent = keyEvent;
    }

    @TargetApi(VERSION_CODES.HONEYCOMB_MR1)
    @Override
    public void run() {
      injectKeyEvent(target, keyEvent);
      trigger.postDelayed(this, VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB_MR1
                                ? ViewConfiguration.getKeyRepeatDelay()
                                : 50);
    }
  }

  private static class RepeaterTouchListener implements OnTouchListener {
    private EditText target;
    private Repeater repeater;

    public RepeaterTouchListener(View trigger, EditText target, KeyEvent keyEvent) {
      this.target   = target;
      this.repeater = new Repeater(trigger, target, keyEvent);
    }

    @TargetApi(VERSION_CODES.HONEYCOMB_MR1)
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
      switch (motionEvent.getAction()) {
      case MotionEvent.ACTION_DOWN:
        view.postDelayed(repeater, VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB_MR1
                                   ? ViewConfiguration.getKeyRepeatTimeout()
                                   : ViewConfiguration.getLongPressTimeout());
        target.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
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
}
