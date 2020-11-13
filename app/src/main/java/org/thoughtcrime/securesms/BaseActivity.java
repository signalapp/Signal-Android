package org.thoughtcrime.securesms;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.WindowUtil;
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

  @Override
  protected void attachBaseContext(Context newBase) {
    super.attachBaseContext(newBase);

    Configuration configuration = new Configuration();
    configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | getDelegate().getLocalNightMode();

    applyOverrideConfiguration(configuration);
  }

  @Override
  public void applyOverrideConfiguration(Configuration overrideConfiguration) {
    DynamicLanguageContextWrapper.prepareOverrideConfiguration(this, overrideConfiguration);
    super.applyOverrideConfiguration(overrideConfiguration);
  }

  private void logEvent(@NonNull String event) {
    Log.d(TAG, "[" + Log.tag(getClass()) + "] " + event);
  }

  public final @NonNull ActionBar requireSupportActionBar() {
    return Objects.requireNonNull(getSupportActionBar());
  }
}
