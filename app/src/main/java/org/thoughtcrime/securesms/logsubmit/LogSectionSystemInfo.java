package org.thoughtcrime.securesms.logsubmit;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.util.AppSignatureUtil;
import org.thoughtcrime.securesms.util.ByteUnit;
import org.thoughtcrime.securesms.util.CensorshipUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.VersionTracker;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

public class LogSectionSystemInfo implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "SYSINFO";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    final PackageManager pm      = context.getPackageManager();
    final StringBuilder  builder = new StringBuilder();

    builder.append("Time          : ").append(System.currentTimeMillis()).append('\n');
    builder.append("Manufacturer  : ").append(Build.MANUFACTURER).append("\n");
    builder.append("Model         : ").append(Build.MODEL).append("\n");
    builder.append("Product       : ").append(Build.PRODUCT).append("\n");
    builder.append("Screen        : ").append(getScreenResolution(context)).append(", ")
                                     .append(getScreenDensityClass(context)).append(", ")
                                     .append(getScreenRefreshRate(context)).append("\n");
    builder.append("Font Scale    : ").append(context.getResources().getConfiguration().fontScale).append("\n");
    builder.append("Android       : ").append(Build.VERSION.RELEASE).append(" (")
                                     .append(Build.VERSION.INCREMENTAL).append(", ")
                                     .append(Build.DISPLAY).append(")\n");
    builder.append("ABIs          : ").append(TextUtils.join(", ", getSupportedAbis())).append("\n");
    builder.append("Memory        : ").append(getMemoryUsage()).append("\n");
    builder.append("Memclass      : ").append(getMemoryClass(context)).append("\n");
    builder.append("OS Host       : ").append(Build.HOST).append("\n");
    builder.append("Censored      : ").append(CensorshipUtil.isCensored(context)).append("\n");
    builder.append("Play Services : ").append(getPlayServicesString(context)).append("\n");
    builder.append("FCM           : ").append(!TextSecurePreferences.isFcmDisabled(context)).append("\n");
    builder.append("Locale        : ").append(Locale.getDefault().toString()).append("\n");
    builder.append("Linked Devices: ").append(TextSecurePreferences.isMultiDevice(context)).append("\n");
    builder.append("First Version : ").append(TextSecurePreferences.getFirstInstallVersion(context)).append("\n");
    builder.append("Days Installed: ").append(VersionTracker.getDaysSinceFirstInstalled(context)).append("\n");
    builder.append("App           : ");
    try {
      builder.append(pm.getApplicationLabel(pm.getApplicationInfo(context.getPackageName(), 0)))
             .append(" ")
             .append(pm.getPackageInfo(context.getPackageName(), 0).versionName)
             .append(" (")
             .append(BuildConfig.CANONICAL_VERSION_CODE)
             .append(", ")
             .append(Util.getManifestApkVersion(context))
             .append(")\n");
    } catch (PackageManager.NameNotFoundException nnfe) {
      builder.append("Unknown\n");
    }
    builder.append("Package       : ").append(BuildConfig.APPLICATION_ID).append(" (").append(getSigningString(context)).append(")");

    return builder;
  }

  private static @NonNull String getMemoryUsage() {
    Runtime info        = Runtime.getRuntime();
    long    totalMemory = info.totalMemory();

    return String.format(Locale.ENGLISH,
                         "%dM (%.2f%% free, %dM max)",
                         ByteUnit.BYTES.toMegabytes(totalMemory),
                         (float) info.freeMemory() / totalMemory * 100f,
                         ByteUnit.BYTES.toMegabytes(info.maxMemory()));
  }

  private static @NonNull String getMemoryClass(Context context) {
    ActivityManager activityManager = ServiceUtil.getActivityManager(context);
    String          lowMem          = "";

    if (activityManager.isLowRamDevice()) {
      lowMem = ", low-mem device";
    }

    return activityManager.getMemoryClass() + lowMem;
  }

  private static @NonNull Iterable<String> getSupportedAbis() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return Arrays.asList(Build.SUPPORTED_ABIS);
    } else {
      LinkedList<String> abis = new LinkedList<>();
      abis.add(Build.CPU_ABI);
      if (Build.CPU_ABI2 != null && !"unknown".equals(Build.CPU_ABI2)) {
        abis.add(Build.CPU_ABI2);
      }
      return abis;
    }
  }

  private static @NonNull String getScreenResolution(@NonNull Context context) {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    WindowManager  windowManager  = ServiceUtil.getWindowManager(context);

    windowManager.getDefaultDisplay().getMetrics(displayMetrics);
    return displayMetrics.widthPixels + "x" + displayMetrics.heightPixels;
  }

  private static @NonNull String getScreenDensityClass(@NonNull Context context) {
    int density = context.getResources().getDisplayMetrics().densityDpi;

    LinkedHashMap<Integer, String> levels = new LinkedHashMap<Integer, String>() {{
      put(DisplayMetrics.DENSITY_LOW,     "ldpi");
      put(DisplayMetrics.DENSITY_MEDIUM,  "mdpi");
      put(DisplayMetrics.DENSITY_HIGH,    "hdpi");
      put(DisplayMetrics.DENSITY_XHIGH,   "xhdpi");
      put(DisplayMetrics.DENSITY_XXHIGH,  "xxhdpi");
      put(DisplayMetrics.DENSITY_XXXHIGH, "xxxhdpi");
    }};

    String densityString = "unknown";

    for (Map.Entry<Integer, String> entry : levels.entrySet()) {
      densityString = entry.getValue();
      if (entry.getKey() > density) {
        break;
      }
    }

    return densityString + " (" + density + ")";
  }

  private static @NonNull String getScreenRefreshRate(@NonNull Context context) {
    return String.format(Locale.ENGLISH, "%.2f hz", ServiceUtil.getWindowManager(context).getDefaultDisplay().getRefreshRate());
  }

  private static String getSigningString(@NonNull Context context) {
    return AppSignatureUtil.getAppSignature(context).or("Unknown");
  }

  private static String getPlayServicesString(@NonNull Context context) {
    int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
    return result == ConnectionResult.SUCCESS ? "true" : "false (" + result + ")";
  }
}
