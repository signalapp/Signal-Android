package org.thoughtcrime.securesms;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.dynamiclanguage.DynamicLanguageActivityHelper;
import org.thoughtcrime.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;

import java.util.Objects;

/**
 * Base class for all activities. The vast majority of activities shouldn't extend this directly.
 * Instead, they should extend {@link PassphraseRequiredActivity} so they're protected by
 * screen lock.
 */
public abstract class BaseActivity extends AppCompatActivity {
  private static final String TAG = Log.tag(BaseActivity.class);

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    logEvent("onCreate()");
    super.onCreate(savedInstanceState);
  }

  @Override
  protected void onResume() {
    super.onResume();
    initializeScreenshotSecurity();
    DynamicLanguageActivityHelper.recreateIfNotInCorrectLanguage(this, TextSecurePreferences.getLanguage(this));
  }

  @Override
  protected void onStart() {
    logEvent("onStart()");
    super.onStart();
  }

  @Override
  protected void onStop() {
    logEvent("onStop()");
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    logEvent("onDestroy()");
    super.onDestroy();
  }

  private void initializeScreenshotSecurity() {
    if (TextSecurePreferences.isScreenSecurityEnabled(this)) {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    } else {
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
  }

  protected void startActivitySceneTransition(Intent intent, View sharedView, String transitionName) {
    Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(this, sharedView, transitionName)
                                         .toBundle();
    ActivityCompat.startActivity(this, intent, bundle);
  }

  @TargetApi(21)
  protected void setStatusBarColor(int color) {
    if (Build.VERSION.SDK_INT >= 21) {
      getWindow().setStatusBarColor(color);
    }
  }

  @Override
  protected void attachBaseContext(Context newBase) {
    super.attachBaseContext(DynamicLanguageContextWrapper.updateContext(newBase, TextSecurePreferences.getLanguage(newBase)));
  }

  private void logEvent(@NonNull String event) {
    Log.d(TAG, "[" + Log.tag(getClass()) + "] " + event);
  }

  public final @NonNull ActionBar requireSupportActionBar() {
    return Objects.requireNonNull(getSupportActionBar());
  }
}
