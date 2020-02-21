package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;

import org.thoughtcrime.securesms.R;

public class DynamicTheme {

  public static final String DARK   = "dark";
  public static final String LIGHT  = "light";
  public static final String SYSTEM = "system";

  private int currentTheme;

  public void onCreate(Activity activity) {
    currentTheme = getSelectedTheme(activity);
    activity.setTheme(currentTheme);
  }

  public void onResume(Activity activity) {
    if (currentTheme != getSelectedTheme(activity)) {
      Intent intent = activity.getIntent();
      activity.finish();
      OverridePendingTransition.invoke(activity);
      activity.startActivity(intent);
      OverridePendingTransition.invoke(activity);
    }
  }

  private @StyleRes int getSelectedTheme(Activity activity) {
    if (isDarkTheme(activity)) {
      return getDarkThemeStyle();
    } else {
      return getLightThemeStyle();
    }
  }

  protected @StyleRes int getLightThemeStyle() {
    return R.style.TextSecure_LightTheme;
  }

  protected @StyleRes int getDarkThemeStyle() {
    return R.style.TextSecure_DarkTheme;
  }

  public static boolean systemThemeAvailable() {
    return Build.VERSION.SDK_INT >= 29;
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

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }
}
