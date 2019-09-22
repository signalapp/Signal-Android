package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;

import org.thoughtcrime.securesms.R;

public class DynamicTheme {

  public static final String DARK  = "dark";
  public static final String LIGHT = "light";
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

  protected int getSelectedTheme(Activity activity) {
    String theme = TextSecurePreferences.getTheme(activity);

    if (theme.equals(DARK) || (theme.equals(SYSTEM) && doesSystemWantDarkTheme(activity)))
      return R.style.TextSecure_DarkTheme;

    return R.style.TextSecure_LightTheme;
  }

  boolean doesSystemWantDarkTheme(Activity activity) {
    int systemFlags = activity.getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK;
    return systemFlags == Configuration.UI_MODE_NIGHT_YES;
  }

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }
}
