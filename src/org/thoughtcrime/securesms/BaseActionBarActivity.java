package org.thoughtcrime.securesms;

import android.support.annotation.NonNull;
import android.support.v7.app.ActionBarActivity;
import android.view.KeyEvent;


public abstract class BaseActionBarActivity extends ActionBarActivity {
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    return BaseActivity.isKeyCodeWorkaroundRequired(keyCode) || super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
    if (BaseActivity.isKeyCodeWorkaroundRequired(keyCode)) {
      openOptionsMenu();
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }
}
