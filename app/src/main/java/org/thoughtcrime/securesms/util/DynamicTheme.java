package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Intent;
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
    String theme = TextSecurePreferences.getTheme(activity);

    if (theme.equals(SYSTEM) && systemThemeAvailable()) {
      if (isSystemInDarkTheme(activity)) {
        return getDarkThemeStyle();
      } else {
        return getLightThemeStyle();
      }
    } else if (theme.equals(DARK)) {
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

  private static boolean isSystemInDarkTheme(@NonNull Activity activity) {
    return (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
  }

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }
}
