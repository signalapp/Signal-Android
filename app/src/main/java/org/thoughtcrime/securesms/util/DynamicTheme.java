package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;

import org.thoughtcrime.securesms.R;

public class DynamicTheme {

  public static final String DARK   = "dark";
  public static final String LIGHT  = "light";
  public static final String SYSTEM = "system";

  private static int globalNightModeConfiguration;

  private int onCreateNightModeConfiguration;

  public void onCreate(@NonNull Activity activity) {
    int previousGlobalConfiguration = globalNightModeConfiguration;

    onCreateNightModeConfiguration = ContextUtil.getNightModeConfiguration(activity);
    globalNightModeConfiguration   = onCreateNightModeConfiguration;

    activity.setTheme(getTheme());

    if (previousGlobalConfiguration != globalNightModeConfiguration) {
      CachedInflater.from(activity).clear();
    }
  }

  public void onResume(@NonNull Activity activity) {
    if (onCreateNightModeConfiguration != ContextUtil.getNightModeConfiguration(activity)) {
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
    String theme = TextSecurePreferences.getTheme(context);

    if (theme.equals(SYSTEM)) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    } else if (DynamicTheme.isDarkTheme(context)) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    } else {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    CachedInflater.from(context).clear();
  }

  /**
   * Takes the system theme into account.
   */
  public static boolean isDarkTheme(@NonNull Context context) {
    String theme = TextSecurePreferences.getTheme(context);

    if (theme.equals(SYSTEM) && systemThemeAvailable()) {
      return isSystemInDarkTheme(context);
    } else {
      return theme.equals(DARK);
    }
  }

  private static boolean isSystemInDarkTheme(@NonNull Context context) {
    return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
  }
}
