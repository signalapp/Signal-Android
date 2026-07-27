package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsControllerCompat;

import org.signal.core.ui.util.ThemeUtil;

public final class WindowUtil {

  private WindowUtil() {
  }

  public static void initializeScreenshotSecurity(@NonNull Context context, @NonNull Window window) {
    org.signal.core.ui.WindowExtensionsKt.initializeScreenshotSecurity(window);
  }

  public static void setLightNavigationBarFromTheme(@NonNull Activity activity) {
    if (Build.VERSION.SDK_INT < 27) return;

    final boolean isLightNavigationBar = ThemeUtil.getThemedBoolean(activity, android.R.attr.windowLightNavigationBar);

    if (isLightNavigationBar) setLightNavigationBar(activity.getWindow());
    else                      clearLightNavigationBar(activity.getWindow());
  }

  public static void clearLightNavigationBar(@NonNull Window window) {
    if (Build.VERSION.SDK_INT < 27) return;

    controller(window).setAppearanceLightNavigationBars(false);
  }

  public static void setLightNavigationBar(@NonNull Window window) {
    if (Build.VERSION.SDK_INT < 27) return;

    controller(window).setAppearanceLightNavigationBars(true);
  }

  public static void setLightStatusBarFromTheme(@NonNull Activity activity) {
    final boolean isLightStatusBar = ThemeUtil.getThemedBoolean(activity, android.R.attr.windowLightStatusBar);

    if (isLightStatusBar) setLightStatusBar(activity.getWindow());
    else                  clearLightStatusBar(activity.getWindow());
  }

  public static void clearLightStatusBar(@NonNull Window window) {
    controller(window).setAppearanceLightStatusBars(false);
  }

  public static void setLightStatusBar(@NonNull Window window) {
    controller(window).setAppearanceLightStatusBars(true);
  }

  private static @NonNull WindowInsetsControllerCompat controller(@NonNull Window window) {
    return new WindowInsetsControllerCompat(window, window.getDecorView());
  }
}
