package org.thoughtcrime.securesms.util;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;

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
    if (Build.VERSION.SDK_INT < 26) {
      return false;
    }

    MemoryInfo memoryInfo = getMemoryInfo(context);
    int        memoryMb   = (int) ByteUnit.BYTES.toMegabytes(memoryInfo.totalMem);

    return !isLowMemoryDevice(context) &&
           !memoryInfo.lowMemory       &&
           (memoryMb                >= FeatureFlags.animatedStickerMinimumTotalMemoryMb() ||
            getMemoryClass(context) >= FeatureFlags.animatedStickerMinimumMemoryClass());
  }

  public static boolean isLowMemoryDevice(@NonNull Context context) {
    ActivityManager activityManager = ServiceUtil.getActivityManager(context);
    return activityManager.isLowRamDevice();
  }

  public static int getMemoryClass(@NonNull Context context) {
    ActivityManager activityManager = ServiceUtil.getActivityManager(context);
    return activityManager.getMemoryClass();
  }

  public static @NonNull MemoryInfo getMemoryInfo(@NonNull Context context) {
    MemoryInfo      info            = new MemoryInfo();
    ActivityManager activityManager = ServiceUtil.getActivityManager(context);

    activityManager.getMemoryInfo(info);

    return info;
  }

  @RequiresApi(28)
  public static boolean isBackgroundRestricted(@NonNull Context context) {
    ActivityManager activityManager = ServiceUtil.getActivityManager(context);
    return activityManager.isBackgroundRestricted();
  }

  public static DataSaverState getDataSaverState(@NonNull Context context) {
    if (Build.VERSION.SDK_INT >= 24) {
      switch (ServiceUtil.getConnectivityManager(context).getRestrictBackgroundStatus()) {
        case ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED:
          return DataSaverState.ENABLED;
        case ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED:
          return DataSaverState.ENABLED_BUT_EXEMPTED;
        case ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED:
          return DataSaverState.DISABLED;
      }
    }

    return DataSaverState.DISABLED;
  }

  public enum DataSaverState {
    /** Data saver is enabled system-wide, and we are subject to the restrictions. */
    ENABLED(true, true),

    /** Data saver is enabled system-wide, but the user has exempted us by giving us 'unrestricted access' to data in the system settings */
    ENABLED_BUT_EXEMPTED(true, false),

    /** Data saver is disabled. */
    DISABLED(false, false);

    private final boolean enabled;
    private final boolean restricted;

    DataSaverState(boolean enabled, boolean restricted) {
      this.enabled    = enabled;
      this.restricted = restricted;
    }

    /** True if the device has data saver enabled, otherwise false. */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * True if we're subject to data saver restrictions, otherwise false.
     * Even if data saver is enabled device-wide, this could still be false if the user has given us 'unrestricted access' to data in the system settings.
     */
    public boolean isRestricted() {
      return restricted;
    }
  }
}
