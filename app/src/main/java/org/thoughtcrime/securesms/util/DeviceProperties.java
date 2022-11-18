package org.thoughtcrime.securesms.util;

import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Easy access to various properties of the device, typically to make performance-related decisions.
 */
public final class DeviceProperties {

  /**
   * Whether or not we believe the device has the performance capabilities to efficiently render
   * large numbers of APNGs simultaneously.
   */
  public static boolean shouldAllowApngStickerAnimation(@NonNull Context context) {
    return !isLowMemoryDevice(context) && getMemoryClass(context) >= FeatureFlags.animatedStickerMinimumMemory();
  }

  public static boolean isLowMemoryDevice(@NonNull Context context) {
    ActivityManager activityManager = ServiceUtil.getActivityManager(context);
    return activityManager.isLowRamDevice();
  }

  public static int getMemoryClass(@NonNull Context context) {
    ActivityManager activityManager = ServiceUtil.getActivityManager(context);
    return activityManager.getMemoryClass();
  }

  @RequiresApi(28)
  public static boolean isBackgroundRestricted(@NonNull Context context) {
    ActivityManager activityManager = ServiceUtil.getActivityManager(context);
    return activityManager.isBackgroundRestricted();
  }
}
