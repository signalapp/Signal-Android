package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsControllerCompat;

import org.signal.core.ui.util.ThemeUtil;
import org.signal.core.util.ServiceUtil;
import org.signal.core.util.logging.Log;

public final class WindowUtil {

  private static final String TAG = Log.tag(WindowUtil.class);

  private WindowUtil() {
  }

  /**
   * Configures window refresh rate attributes to allow true 120Hz, 144Hz, and higher refresh rates
   * on devices whose displays support it, preventing OEMs from capping third-party app windows at 60Hz.
   */
  public static void initializeHighRefreshRate(@NonNull Activity activity, @NonNull Window window) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        Display display = null;
        try {
          display = activity.getDisplay();
        } catch (Throwable ignored) {
        }
        if (display == null) {
          DisplayManager dm = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
          if (dm != null) {
            display = dm.getDisplay(Display.DEFAULT_DISPLAY);
          }
        }
        if (display != null) {
          Display.Mode[] modes = display.getSupportedModes();
          Display.Mode bestMode = null;
          float maxRefreshRate = 0f;
          for (Display.Mode mode : modes) {
            if (mode.getRefreshRate() > maxRefreshRate) {
              maxRefreshRate = mode.getRefreshRate();
              bestMode = mode;
            }
          }

          if (maxRefreshRate >= 90f) {
            WindowManager.LayoutParams params = window.getAttributes();
            boolean changed = false;

            if (params.preferredMaxDisplayRefreshRate != maxRefreshRate) {
              params.preferredMaxDisplayRefreshRate = maxRefreshRate;
              changed = true;
            }

            if (bestMode != null && params.preferredDisplayModeId != bestMode.getModeId()) {
              params.preferredDisplayModeId = bestMode.getModeId();
              changed = true;
            }

            if (changed) {
              window.setAttributes(params);
            }
          }
        }
      } catch (Throwable t) {
        Log.w(TAG, "Failed to initialize high refresh rate", t);
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      try {
        Display display = ServiceUtil.getWindowManager(activity).getDefaultDisplay();
        if (display != null) {
          Display.Mode[] modes = display.getSupportedModes();
          Display.Mode bestMode = null;
          float maxRefreshRate = 0f;
          for (Display.Mode mode : modes) {
            if (mode.getRefreshRate() > maxRefreshRate) {
              maxRefreshRate = mode.getRefreshRate();
              bestMode = mode;
            }
          }
          if (bestMode != null && maxRefreshRate >= 90f) {
            WindowManager.LayoutParams params = window.getAttributes();
            if (params.preferredDisplayModeId != bestMode.getModeId()) {
              params.preferredDisplayModeId = bestMode.getModeId();
              window.setAttributes(params);
            }
          }
        }
      } catch (Throwable t) {
        Log.w(TAG, "Failed to set preferred display mode", t);
      }
    }
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
