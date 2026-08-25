package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import org.signal.core.util.ServiceUtil;

public class PowerManagerCompat {

  public static boolean isDeviceIdleMode(@NonNull PowerManager powerManager) {
    return powerManager.isDeviceIdleMode();
  }

  public static boolean isIgnoringBatteryOptimizations(@NonNull Context context) {
    return ServiceUtil.getPowerManager(context).isIgnoringBatteryOptimizations(context.getPackageName());
  }

  public static void requestIgnoreBatteryOptimizations(@NonNull Context context) {
    context.startActivity(buildRequestIgnoreBatteryOptimizationsIntent(context));
  }

  @NonNull
  public static Intent buildRequestIgnoreBatteryOptimizationsIntent(@NonNull Context context) {
    return new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                      Uri.parse("package:" + context.getPackageName()));
  }
}
