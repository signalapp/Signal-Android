package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

public class PowerManagerCompat {

  public static boolean isDeviceIdleMode(@NonNull PowerManager powerManager) {
    if (Build.VERSION.SDK_INT >= 23) {
      return powerManager.isDeviceIdleMode();
    }
    return false;
  }

  public static boolean isIgnoringBatteryOptimizations(@NonNull Context context) {
    if (Build.VERSION.SDK_INT < 23) {
      return true;
    }
    return ServiceUtil.getPowerManager(context).isIgnoringBatteryOptimizations(context.getPackageName());
  }

  @RequiresApi(api = 23)
  public static void requestIgnoreBatteryOptimizations(@NonNull Context context) {
    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                               Uri.parse("package:" + context.getPackageName()));
    context.startActivity(intent);
  }
}
