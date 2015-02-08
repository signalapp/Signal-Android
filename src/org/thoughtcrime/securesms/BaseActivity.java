package org.thoughtcrime.securesms;

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;

public abstract class BaseActivity extends FragmentActivity {
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    return isKeyCodeWorkaroundRequired(keyCode) || super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
    if (isKeyCodeWorkaroundRequired(keyCode)) {
      openOptionsMenu();
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  public static boolean isKeyCodeWorkaroundRequired(int keyCode) {
    return (keyCode == KeyEvent.KEYCODE_MENU) &&
           (Build.VERSION.SDK_INT == 16)      &&
           ("LGE".equalsIgnoreCase(Build.MANUFACTURER));
  }
}
