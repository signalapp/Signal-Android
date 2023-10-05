package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SettingsValues.Theme;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

public class DynamicTheme {

  private static final String TAG = Log.tag(DynamicTheme.class);

  private static int globalNightModeConfiguration;

  private int onCreateNightModeConfiguration;

  public void onCreate(@NonNull Activity activity) {
    int previousGlobalConfiguration = globalNightModeConfiguration;

    onCreateNightModeConfiguration = ConfigurationUtil.getNightModeConfiguration(activity);
    globalNightModeConfiguration   = onCreateNightModeConfiguration;

    activity.setTheme(getTheme());

    if (previousGlobalConfiguration != globalNightModeConfiguration) {
      Log.d(TAG, "Previous night mode has changed previous: " + previousGlobalConfiguration + " now: " + globalNightModeConfiguration);
      CachedInflater.from(activity).clear();
    }
  }

  public void onResume(@NonNull Activity activity) {
    if (onCreateNightModeConfiguration != ConfigurationUtil.getNightModeConfiguration(activity)) {
      Log.d(TAG, "Create configuration different from current previous: " + onCreateNightModeConfiguration + " now: " +  ConfigurationUtil.getNightModeConfiguration(activity));
      CachedInflater.from(activity).clear();
    }
  }

  protected @StyleRes int getTheme() {
    return R.style.Signal_DayNight;
  }

  public static boolean systemThemeAvailable() {
    return Build.VERSION.SDK_INT >= 29;
  }

  public static void setDefaultDayNightMode(@NonNull Context context) {
    Theme theme = SignalStore.settings().getTheme();

    if (theme == Theme.SYSTEM) {
      Log.d(TAG, "Setting to follow system expecting: " + ConfigurationUtil.getNightModeConfiguration(context.getApplicationContext()));
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    } else if (DynamicTheme.isDarkTheme(context)) {
      Log.d(TAG, "Setting to always night");
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    } else {
      Log.d(TAG, "Setting to always day");
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    CachedInflater.from(context).clear();
  }

  /**
   * Takes the system theme into account.
   */
  public static boolean isDarkTheme(@NonNull Context context) {
    Theme theme = SignalStore.settings().getTheme();

    if (theme == Theme.SYSTEM && systemThemeAvailable()) {
      return isSystemInDarkTheme(context);
    } else {
      return theme == Theme.DARK;
    }
  }

  private static boolean isSystemInDarkTheme(@NonNull Context context) {
    return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
  }
}
