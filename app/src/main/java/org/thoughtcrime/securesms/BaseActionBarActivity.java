package org.thoughtcrime.securesms;

import static org.session.libsession.utilities.TextSecurePreferences.SELECTED_ACCENT_COLOR;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.dynamiclanguage.DynamicLanguageActivityHelper;
import org.session.libsession.utilities.dynamiclanguage.DynamicLanguageContextWrapper;
import org.thoughtcrime.securesms.util.ActivityUtilitiesKt;
import org.thoughtcrime.securesms.util.ThemeState;
import org.thoughtcrime.securesms.util.UiModeUtilities;

import network.loki.messenger.R;

public abstract class BaseActionBarActivity extends AppCompatActivity {
  private static final String TAG = BaseActionBarActivity.class.getSimpleName();
  public ThemeState currentThemeState;

  private TextSecurePreferences getPreferences() {
    ApplicationContext appContext = (ApplicationContext) getApplicationContext();
    return appContext.textSecurePreferences;
  }

  @StyleRes
  public int getDesiredTheme() {
    ThemeState themeState = ActivityUtilitiesKt.themeState(getPreferences());
    int userSelectedTheme = themeState.getTheme();
    if (themeState.getFollowSystem()) {
      // do light or dark based on the selected theme
      boolean isDayUi = UiModeUtilities.isDayUiMode(this);
      if (userSelectedTheme == R.style.Ocean_Dark || userSelectedTheme == R.style.Ocean_Light) {
        return isDayUi ? R.style.Ocean_Light : R.style.Ocean_Dark;
      } else {
        return isDayUi ? R.style.Classic_Light : R.style.Classic_Dark;
      }
    } else {
      return userSelectedTheme;
    }
  }

  @StyleRes @Nullable
  public Integer getAccentTheme() {
    if (!getPreferences().hasPreference(SELECTED_ACCENT_COLOR)) return null;
    ThemeState themeState = ActivityUtilitiesKt.themeState(getPreferences());
    return themeState.getAccentStyle();
  }

  @Override
  public Resources.Theme getTheme() {
    // New themes
    Resources.Theme modifiedTheme = super.getTheme();
    modifiedTheme.applyStyle(getDesiredTheme(), true);
    Integer accentTheme = getAccentTheme();
    if (accentTheme != null) {
      modifiedTheme.applyStyle(accentTheme, true);
    }
    currentThemeState = ActivityUtilitiesKt.themeState(getPreferences());
    return modifiedTheme;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setHomeButtonEnabled(true);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    initializeScreenshotSecurity(true);
    DynamicLanguageActivityHelper.recreateIfNotInCorrectLanguage(this, TextSecurePreferences.getLanguage(this));
    String name = getResources().getString(R.string.app_name);
    Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_foreground);
    int color = getResources().getColor(R.color.app_icon_background);
    setTaskDescription(new ActivityManager.TaskDescription(name, icon, color));
    if (!currentThemeState.equals(ActivityUtilitiesKt.themeState(getPreferences()))) {
      recreate();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    initializeScreenshotSecurity(false);
  }

  @Override
  public boolean onSupportNavigateUp() {
    if (super.onSupportNavigateUp()) return true;

    onBackPressed();
    return true;
  }

  private void initializeScreenshotSecurity(boolean isResume) {
    if (!isResume) {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    } else {
      if (TextSecurePreferences.isScreenSecurityEnabled(this)) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
      } else {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
      }
    }
  }

  @Override
  protected void attachBaseContext(Context newBase) {
    super.attachBaseContext(DynamicLanguageContextWrapper.updateContext(newBase, TextSecurePreferences.getLanguage(newBase)));
  }
}
