package org.thoughtcrime.securesms;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.dynamiclanguage.DynamicLanguageActivityHelper;
import org.session.libsession.utilities.dynamiclanguage.DynamicLanguageContextWrapper;

import network.loki.messenger.R;

public abstract class BaseActionBarActivity extends AppCompatActivity {
  private static final String TAG = BaseActionBarActivity.class.getSimpleName();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setHomeButtonEnabled(true);
    }

    super.onCreate(savedInstanceState);
  }

  @Override
  protected void onResume() {
    super.onResume();
    initializeScreenshotSecurity();
    DynamicLanguageActivityHelper.recreateIfNotInCorrectLanguage(this, TextSecurePreferences.getLanguage(this));
    String name = getResources().getString(R.string.app_name);
    Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_foreground);
    int color = getResources().getColor(R.color.app_icon_background);
    setTaskDescription(new ActivityManager.TaskDescription(name, icon, color));
  }

  @Override
  public boolean onSupportNavigateUp() {
    if (super.onSupportNavigateUp()) return true;

    onBackPressed();
    return true;
  }

  private void initializeScreenshotSecurity() {
    if (TextSecurePreferences.isScreenSecurityEnabled(this)) {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    } else {
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
  }

  @Override
  protected void attachBaseContext(Context newBase) {
    super.attachBaseContext(DynamicLanguageContextWrapper.updateContext(newBase, TextSecurePreferences.getLanguage(newBase)));
  }
}
