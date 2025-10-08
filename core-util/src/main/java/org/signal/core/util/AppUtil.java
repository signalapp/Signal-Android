package org.signal.core.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public final class AppUtil {

  private AppUtil() {}

  /**
   * Restarts the application. Should generally only be used for internal tools.
   */
  public static void restart(@NonNull Context context) {
    String packageName   = context.getPackageName();
    Intent defaultIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);

    defaultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    context.startActivity(defaultIntent);
    Runtime.getRuntime().exit(0);
  }

  /**
   * Clears all app data, will cause the app to stop running.
   */
  public static void clearData(@NonNull Context context) {
    ContextCompat.getSystemService(context, ActivityManager.class).clearApplicationUserData();
  }
}
