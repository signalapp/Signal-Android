package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

public final class ConfigurationUtil {

  private ConfigurationUtil() {}

  public static int getNightModeConfiguration(@NonNull Context context) {
    return getNightModeConfiguration(context.getResources().getConfiguration());
  }

  public static int getNightModeConfiguration(@NonNull Configuration configuration) {
    return configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
  }

  public static float getFontScale(@NonNull Configuration configuration) {
    return configuration.fontScale;
  }

  public static boolean isUiModeChanged(@NonNull Configuration configuration, @NonNull Configuration newConfiguration) {
    int oldTheme = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
    int newTheme = newConfiguration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
    return oldTheme != newTheme;
  }
}
