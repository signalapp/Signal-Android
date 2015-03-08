package org.thoughtcrime.securesms.util;

import android.app.Activity;

import org.thoughtcrime.securesms.R;

public class DynamicIntoTheme extends DynamicTheme {
  @Override
  protected int getSelectedTheme(Activity activity) {
    String theme = TextSecurePreferences.getTheme(activity);

    if (theme.equals("dark")) return R.style.TextSecure_DarkIntroTheme;

    return R.style.TextSecure_LightIntroTheme;
  }
}
