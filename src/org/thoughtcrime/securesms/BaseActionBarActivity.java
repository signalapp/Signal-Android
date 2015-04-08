package org.thoughtcrime.securesms;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

import org.thoughtcrime.securesms.util.DynamicLanguage;

import java.lang.reflect.Field;


public abstract class BaseActionBarActivity extends ActionBarActivity {
  private static final String TAG = BaseActionBarActivity.class.getSimpleName();

  protected final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    if (BaseActivity.isMenuWorkaroundRequired()) {
      forceOverflowMenu();
    }
    super.onCreate(savedInstanceState);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    dynamicLanguage.onActivityResult(this);
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicLanguage.onResume(this);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    dynamicLanguage.onConfigurationChanged(this, newConfig);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    return (keyCode == KeyEvent.KEYCODE_MENU && BaseActivity.isMenuWorkaroundRequired()) || super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_MENU && BaseActivity.isMenuWorkaroundRequired()) {
      openOptionsMenu();
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  /**
   * Modified from: http://stackoverflow.com/a/13098824
   */
  private void forceOverflowMenu() {
    try {
      ViewConfiguration config       = ViewConfiguration.get(this);
      Field             menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
      if(menuKeyField != null) {
        menuKeyField.setAccessible(true);
        menuKeyField.setBoolean(config, false);
      }
    } catch (IllegalAccessException | NoSuchFieldException e) {
      Log.w(TAG, "Failed to force overflow menu.");
    }
  }
}
