package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import org.signal.core.util.logging.Log;

import androidx.appcompat.widget.AppCompatEditText;

/**
 * This is supposed to be a *very* thin veneer over TextView.
 * Do not make any changes here that do anything that a TextView
 * with a key listener and a movement method wouldn't do!
 **/
public class Mp02EditText extends AppCompatEditText {
  private static final String TAG = "Mp02EditText";
  private boolean isNeedBack = false;

  public Mp02EditText(Context context) {
    super(context);
  }

  public Mp02EditText(Context context, AttributeSet attrs) {
    super(context,attrs);
  }

  public Mp02EditText(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  private boolean doBack;
  public boolean isDoBack(){
    return doBack;
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    Log.d(TAG, "onKeyDown keyCode=" + keyCode + " length()=" + length());
    int tKeyCode = keyCode;
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (length() == 0) {
        doBack = true;
      }

      if (length() > 0) {
        tKeyCode = KeyEvent.KEYCODE_DEL;
        doBack = false;
      }
    }
    if(keyCode==KeyEvent.KEYCODE_DPAD_CENTER){
      Log.d(TAG,"keyCode==KeyEvent.KEYCODE_DPAD_CENTER");
      if(!this.isEnabled()) {
        this.setEnabled(true);
        this.setCursorVisible(true);
        this.setSelection(this.getText().toString().length());
      }
    }

    return super.onKeyDown(tKeyCode, event);
  }
  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    Log.d(TAG, "onKeyUp keyCode=" + keyCode + " length()=" + length());
    int tKeyCode = keyCode;
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (length() > 0) {
        tKeyCode = KeyEvent.KEYCODE_DEL;
      }
    }

    return super.onKeyUp(tKeyCode, event);
  }
}