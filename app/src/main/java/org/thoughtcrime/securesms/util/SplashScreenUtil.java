package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SettingsValues;

public final class SplashScreenUtil {
  private SplashScreenUtil() {}

  /**
   * Sets the splash screen for Android 12+ devices based on the passed-in theme.
   */
  public static void setSplashScreenThemeIfNecessary(@Nullable Activity activity, @NonNull SettingsValues.Theme theme) {
    if (Build.VERSION.SDK_INT < 31 || activity == null) {
      return;
    }

    switch (theme) {
      case LIGHT:
        activity.getSplashScreen().setSplashScreenTheme(R.style.Theme_Signal_DayNight_NoActionBar_LightSplash);
        break;
      case DARK:
        activity.getSplashScreen().setSplashScreenTheme(R.style.Theme_Signal_DayNight_NoActionBar_DarkSplash);
        break;
      case SYSTEM:
        activity.getSplashScreen().setSplashScreenTheme(Resources.ID_NULL);
        break;
    }
  }
}
