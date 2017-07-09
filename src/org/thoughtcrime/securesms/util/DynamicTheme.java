package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.securesms.R;

public class DynamicTheme {
  public static final String AUTO_DARK = "auto_dark";
  public static final String DARK  = "dark";
  public static final String LIGHT = "light";

  private int currentTheme;
  private AutoDarkModeManager autoDarkModeManager;

  public void onCreate(Activity activity) {
    currentTheme = getSelectedTheme(activity);
    activity.setTheme(currentTheme);

    if (autoDarkModeEnabled(activity))
      autoDarkModeManager = new AutoDarkModeManager(activity);
  }

  public void onResume(Activity activity) {
    if (currentTheme != getSelectedTheme(activity)) {
      Intent intent = activity.getIntent();
      activity.finish();
      OverridePendingTransition.invoke(activity);
      activity.startActivity(intent);
      OverridePendingTransition.invoke(activity);
    }

    if (autoDarkModeEnabled(activity) && autoDarkModeManager != null)
      autoDarkModeManager.startListening();
  }

  public void onPause(Activity activity) {
    if (autoDarkModeEnabled(activity) && autoDarkModeManager != null)
      autoDarkModeManager.stopListening();
  }

  protected int getSelectedTheme(Activity activity) {
    String theme = TextSecurePreferences.getTheme(activity);

    if (theme.equals(DARK)) return R.style.TextSecure_DarkTheme;

    if (theme.equals(AUTO_DARK) && AutoDarkModeManager.shouldShowDarkTheme())
      return R.style.TextSecure_DarkTheme;

    return R.style.TextSecure_LightTheme;
  }

  private boolean autoDarkModeEnabled(Context context) {
    return TextSecurePreferences.getTheme(context).equals(AUTO_DARK);
  }

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }
}
