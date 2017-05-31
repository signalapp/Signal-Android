package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Intent;

import org.thoughtcrime.securesms.R;

public class DynamicTheme {

  public static final String DARK  = "dark";
  public static final String LIGHT = "light";

  private int currentTheme;
  private int darkTheme;
  private int lightTheme;

  public DynamicTheme(int lightTheme, int darkTheme) {
    this.lightTheme = lightTheme;
    this.darkTheme  = darkTheme;
  }

  public DynamicTheme() {
    this(R.style.TextSecure_LightTheme,R.style.TextSecure_DarkTheme);
  }

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

    if (theme.equals(DARK)) return darkTheme;

    return lightTheme;
  }

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }
}
