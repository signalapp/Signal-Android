package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.preference.PreferenceManager;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;

public class DynamicTheme {

  private int currentTheme;

  public void onCreate(Activity activity) {
    currentTheme = getSelectedTheme(activity);
    activity.setTheme(currentTheme);
  }

  public void onResume(Activity activity) {
    if (currentTheme != getSelectedTheme(activity)) {
      Intent intent = activity.getIntent();
      intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

      activity.startActivity(intent);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
        OverridePendingTransition.invoke(activity);
      }

      activity.finish();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
        OverridePendingTransition.invoke(activity);
      }

    }
  }

  public static int getSelectedTheme(Context context) {
    String theme = PreferenceManager.getDefaultSharedPreferences(context)
                                    .getString(ApplicationPreferencesActivity.THEME_PREF, "light");

    return getSelectedTheme(theme);
  }

  public static int getSelectedTheme(String theme) {
    if (theme.equals("light")) {
      return R.style.TextSecure_LightTheme;
    } else if (theme.equals("dark")) {
      return R.style.TextSecure_DarkTheme;
    }

    return R.style.TextSecure_LightTheme;
  }

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }
}
