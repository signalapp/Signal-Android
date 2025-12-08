package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;

public class PowerManagerCompat {

  public static boolean isDeviceIdleMode(@NonNull PowerManager powerManager) {
    return powerManager.isDeviceIdleMode();
  }

  public static boolean isIgnoringBatteryOptimizations(@NonNull Context context) {
    return ServiceUtil.getPowerManager(context).isIgnoringBatteryOptimizations(context.getPackageName());
  }

  public static void requestIgnoreBatteryOptimizations(@NonNull Context context) {
    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                               Uri.parse("package:" + context.getPackageName()));
    context.startActivity(intent);
  }
}
