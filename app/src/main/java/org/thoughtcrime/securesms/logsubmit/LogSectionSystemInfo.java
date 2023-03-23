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

import org.signal.core.util.FontUtil;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.emoji.EmojiFiles;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.net.StandardUserAgentInterceptor;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.webrtc.AndroidTelecomUtil;
import org.thoughtcrime.securesms.util.AppSignatureUtil;
import org.thoughtcrime.securesms.util.ByteUnit;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.DeviceProperties;
import org.thoughtcrime.securesms.util.NetworkUtil;
import org.thoughtcrime.securesms.util.ScreenDensity;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.VersionTracker;
import org.whispersystems.signalservice.api.push.ACI;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Locale;

public class LogSectionSystemInfo implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "SYSINFO";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    final PackageManager pm      = context.getPackageManager();
    final StringBuilder  builder = new StringBuilder();

    builder.append("Time           : ").append(System.currentTimeMillis()).append('\n');
    builder.append("Manufacturer   : ").append(Build.MANUFACTURER).append("\n");
    builder.append("Model          : ").append(Build.MODEL).append("\n");
    builder.append("Product        : ").append(Build.PRODUCT).append("\n");
    builder.append("Screen         : ").append(getScreenResolution(context)).append(", ")
                                      .append(ScreenDensity.get(context)).append(", ")
                                      .append(getScreenRefreshRate(context)).append("\n");
    builder.append("Font Scale     : ").append(context.getResources().getConfiguration().fontScale).append("\n");
    builder.append("Animation Scale: ").append(ContextUtil.getAnimationScale(context)).append("\n");
    builder.append("Android        : ").append(Build.VERSION.RELEASE).append(", API ")
                                      .append(Build.VERSION.SDK_INT).append(" (")
                                      .append(Build.VERSION.INCREMENTAL).append(", ")
                                      .append(Build.DISPLAY).append(")\n");
    builder.append("ABIs           : ").append(TextUtils.join(", ", getSupportedAbis())).append("\n");
    builder.append("Memory         : ").append(getMemoryUsage()).append("\n");
    builder.append("Memclass       : ").append(getMemoryClass(context)).append("\n");
    builder.append("MemInfo        : ").append(getMemoryInfo(context)).append("\n");
    builder.append("OS Host        : ").append(Build.HOST).append("\n");
    builder.append("RecipientId    : ").append(SignalStore.registrationValues().isRegistrationComplete() ? Recipient.self().getId() : "N/A").append("\n");
    builder.append("ACI            : ").append(getCensoredAci(context)).append("\n");
    builder.append("Device ID      : ").append(SignalStore.account().getDeviceId()).append("\n");
    builder.append("Censored       : ").append(ApplicationDependencies.getSignalServiceNetworkAccess().isCensored()).append("\n");
    builder.append("Network Status : ").append(NetworkUtil.getNetworkStatus(context)).append("\n");
    builder.append("Data Saver     : ").append(DeviceProperties.getDataSaverState(context)).append("\n");
    builder.append("Play Services  : ").append(getPlayServicesString(context)).append("\n");
    builder.append("FCM            : ").append(SignalStore.account().isFcmEnabled()).append("\n");
    builder.append("BkgRestricted  : ").append(Build.VERSION.SDK_INT >= 28 ? DeviceProperties.isBackgroundRestricted(context) : "N/A").append("\n");
    builder.append("Locale         : ").append(Locale.getDefault()).append("\n");
    builder.append("Linked Devices : ").append(TextSecurePreferences.isMultiDevice(context)).append("\n");
    builder.append("First Version  : ").append(TextSecurePreferences.getFirstInstallVersion(context)).append("\n");
    builder.append("Days Installed : ").append(VersionTracker.getDaysSinceFirstInstalled(context)).append("\n");
    builder.append("Build Variant  : ").append(BuildConfig.BUILD_DISTRIBUTION_TYPE).append(BuildConfig.BUILD_ENVIRONMENT_TYPE).append(BuildConfig.BUILD_VARIANT_TYPE).append("\n");
    builder.append("Emoji Version  : ").append(getEmojiVersionString(context)).append("\n");
    builder.append("RenderBigEmoji : ").append(FontUtil.canRenderEmojiAtFontSize(1024)).append("\n");
    builder.append("Telecom        : ").append(AndroidTelecomUtil.getTelecomSupported()).append("\n");
    builder.append("User-Agent     : ").append(StandardUserAgentInterceptor.USER_AGENT).append("\n");
    builder.append("App            : ");
    try {
      builder.append(pm.getApplicationLabel(pm.getApplicationInfo(context.getPackageName(), 0)))
             .append(" ")
             .append(pm.getPackageInfo(context.getPackageName(), 0).versionName)
             .append(" (")
             .append(BuildConfig.CANONICAL_VERSION_CODE)
             .append(", ")
             .append(Util.getManifestApkVersion(context))
             .append(") (")
             .append(BuildConfig.GIT_HASH).append(") \n");
    } catch (PackageManager.NameNotFoundException nnfe) {
      builder.append("Unknown\n");
    }
    builder.append("Package        : ").append(BuildConfig.APPLICATION_ID).append(" (").append(getSigningString(context)).append(")");

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

  private static @NonNull String getMemoryInfo(Context context) {
    ActivityManager.MemoryInfo info = DeviceProperties.getMemoryInfo(context);
    return String.format(Locale.US, "availMem: %d mb, totalMem: %d mb, threshold: %d mb, lowMemory: %b",
                         ByteUnit.BYTES.toMegabytes(info.availMem), ByteUnit.BYTES.toMegabytes(info.totalMem), ByteUnit.BYTES.toMegabytes(info.threshold), info.lowMemory);
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

  private static @NonNull String getScreenRefreshRate(@NonNull Context context) {
    return String.format(Locale.ENGLISH, "%.2f hz", ServiceUtil.getWindowManager(context).getDefaultDisplay().getRefreshRate());
  }

  private static String getSigningString(@NonNull Context context) {
    return AppSignatureUtil.getAppSignature(context);
  }

  private static String getPlayServicesString(@NonNull Context context) {
    int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
    return result == ConnectionResult.SUCCESS ? "true" : "false (" + result + ")";
  }

  private static String getEmojiVersionString(@NonNull Context context) {
    EmojiFiles.Version version = EmojiFiles.Version.readVersion(context);

    if (version == null) {
      return "None";
    } else {
      return version.getVersion() + " (" + version.getDensity() + ")";
    }
  }

  private static String getCensoredAci(@NonNull Context context) {
    ACI aci = SignalStore.account().getAci();

    if (aci != null) {
      String aciString = aci.toString();
      String lastThree = aciString.substring(aciString.length() - 3);

      return "********-****-****-****-*********" + lastThree;
    } else {
      return "N/A";
    }
  }
}
