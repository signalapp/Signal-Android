package org.thoughtcrime.securesms.logsubmit

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.signal.core.util.BidiUtil
import org.signal.core.util.DiskUtil
import org.signal.core.util.FontUtil.canRenderEmojiAtFontSize
import org.signal.core.util.bytes
import org.signal.core.util.roundedString
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.emoji.EmojiFiles.Version.Companion.readVersion
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.StandardUserAgentInterceptor
import org.thoughtcrime.securesms.notifications.SlowNotificationHeuristics.isHavingDelayedNotifications
import org.thoughtcrime.securesms.recipients.Recipient.Companion.self
import org.thoughtcrime.securesms.service.webrtc.AndroidTelecomUtil.telecomSupported
import org.thoughtcrime.securesms.util.AppSignatureUtil
import org.thoughtcrime.securesms.util.ContextUtil
import org.thoughtcrime.securesms.util.DeviceProperties
import org.thoughtcrime.securesms.util.NetworkUtil
import org.thoughtcrime.securesms.util.PowerManagerCompat
import org.thoughtcrime.securesms.util.ScreenDensity
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.VersionTracker.getDaysSinceFirstInstalled
import org.thoughtcrime.securesms.window.getWindowSizeClass
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class LogSectionSystemInfo : LogSection {

  override fun getTitle(): String {
    return "SYSINFO"
  }

  override fun getContent(context: Context): CharSequence {
    return """
      Time              : ${System.currentTimeMillis()}
      Manufacturer      : ${Build.MANUFACTURER}
      Model             : ${Build.MODEL}
      Product           : ${Build.PRODUCT}
      SoC Manufacturer  : ${if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else "N/A"}
      SoC Model         : ${if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "N/A"}
      Screen            : ${getScreenResolution(context)}, ${ScreenDensity.get(context)}, ${getScreenRefreshRate(context)}
      WindowSizeClass   : ${context.resources.getWindowSizeClass()}
      Font Scale        : ${context.resources.configuration.fontScale}
      Animation Scale   : ${ContextUtil.getAnimationScale(context)}
      Android           : ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT} (${Build.VERSION.INCREMENTAL}, ${Build.DISPLAY})
      ABIs              : ${Build.SUPPORTED_ABIS.joinToString(separator = ", ")}
      Memory            : ${getMemoryUsage()}
      Memclass          : ${getMemoryClass(context)}
      MemInfo           : ${getMemoryInfo(context)}
      Disk Space        : ${getDiskSpaceInfo(context)}
      OS Host           : ${Build.HOST}
      RecipientId       : ${if (SignalStore.registration.isRegistrationComplete) self().id else "N/A"}
      ACI               : ${getCensoredAci()}
      Device ID         : ${SignalStore.account.deviceId}
      Censored          : ${AppDependencies.signalServiceNetworkAccess.isCensored()}
      Network Status    : ${NetworkUtil.getNetworkStatus(context)}
      Play Services     : ${getPlayServicesString(context)}
      FCM               : ${SignalStore.account.fcmEnabled}
      Locale            : ${Locale.getDefault()}
      Linked Devices    : ${SignalStore.account.isMultiDevice}
      First Version     : ${TextSecurePreferences.getFirstInstallVersion(context)}
      Days Installed    : ${getDaysSinceFirstInstalled(context)}
      Last Registration : ${getTimeRegistered()}
      Build Variant     : ${BuildConfig.BUILD_DISTRIBUTION_TYPE}${BuildConfig.BUILD_ENVIRONMENT_TYPE}${BuildConfig.BUILD_VARIANT_TYPE}
      Emoji Version     : ${getEmojiVersionString(context)}
      RenderBigEmoji    : ${canRenderEmojiAtFontSize(1024f)}
      DontKeepActivities: ${getDontKeepActivities(context)}
      Server Time Offset: ${SignalStore.misc.lastKnownServerTimeOffset} ms (last updated: ${SignalStore.misc.lastKnownServerTimeOffsetUpdateTime})
      Telecom           : $telecomSupported
      User-Agent        : ${StandardUserAgentInterceptor.USER_AGENT}
      SlowNotifications : ${isHavingDelayedNotifications()}
      IgnoringBatteryOpt: ${PowerManagerCompat.isIgnoringBatteryOptimizations(context)}
      BkgRestricted     : ${if (Build.VERSION.SDK_INT >= 28) DeviceProperties.isBackgroundRestricted(context) else "N/A"}
      Data Saver        : ${DeviceProperties.getDataSaverState(context)}
      APNG Animation    : ${DeviceProperties.shouldAllowApngStickerAnimation(context)}
      ApkManifestUrl    : ${BuildConfig.APK_UPDATE_MANIFEST_URL?.takeIf { BuildConfig.MANAGES_APP_UPDATES } ?: "N/A"}
      App               : ${getAppInfo(context)}
      Package           : ${BuildConfig.APPLICATION_ID} (${getSigningString(context)})
    """.trimIndent()
  }

  private fun getAppInfo(context: Context): String {
    return try {
      val packageManager = context.packageManager
      val appLabel = packageManager.getApplicationLabel(packageManager.getApplicationInfo(context.packageName, 0))
      val versionName = packageManager.getPackageInfo(context.packageName, 0).versionName
      val manifestApkVersion = Util.getManifestApkVersion(context)

      "$appLabel $versionName (${BuildConfig.CANONICAL_VERSION_CODE}, $manifestApkVersion) (${BuildConfig.GIT_HASH})"
    } catch (_: PackageManager.NameNotFoundException) {
      "Unknown"
    }
  }

  private fun getMemoryUsage(): String {
    val info = Runtime.getRuntime()
    val totalMemory = info.totalMemory()

    return String.format(
      Locale.ENGLISH,
      "%.0fM (%.2f%% free, %.0fM max)",
      totalMemory.bytes.inMebiBytes,
      info.freeMemory().toFloat() / totalMemory * 100f,
      info.maxMemory().bytes.inMebiBytes
    )
  }

  private fun getMemoryClass(context: Context): String {
    val activityManager = ServiceUtil.getActivityManager(context)
    var lowMem = ""

    if (activityManager.isLowRamDevice) {
      lowMem = ", low-mem device"
    }

    return activityManager.memoryClass.toString() + lowMem
  }

  private fun getMemoryInfo(context: Context): String {
    val info = DeviceProperties.getMemoryInfo(context)
    return "availMem: ${info.availMem.bytes.inMebiBytes.roundedString(2)} MiB, totalMem: ${info.totalMem.bytes.inMebiBytes.roundedString(2)} MiB, threshold: ${info.threshold.bytes.inMebiBytes.roundedString(2)} MiB, lowMemory: ${info.lowMemory}"
  }

  private fun getDiskSpaceInfo(context: Context): String {
    val totalSpace = DiskUtil.getTotalDiskSize(context)
    val freeSpace = DiskUtil.getAvailableSpace(context)
    val usedSpace = totalSpace - freeSpace

    return BidiUtil.stripAllDirectionalCharacters("${usedSpace.toUnitString()} / ${totalSpace.toUnitString()} (${freeSpace.toUnitString()} free)")
  }

  private fun getScreenResolution(context: Context): String {
    val displayMetrics = DisplayMetrics()
    val windowManager = ServiceUtil.getWindowManager(context)

    windowManager.defaultDisplay.getMetrics(displayMetrics)
    return displayMetrics.widthPixels.toString() + "x" + displayMetrics.heightPixels
  }

  private fun getScreenRefreshRate(context: Context): String {
    return String.format(Locale.ENGLISH, "%.2f hz", ServiceUtil.getWindowManager(context).defaultDisplay.refreshRate)
  }

  private fun getSigningString(context: Context): String {
    return AppSignatureUtil.getAppSignature(context)
  }

  private fun getPlayServicesString(context: Context): String {
    val result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    return if (result == ConnectionResult.SUCCESS) {
      "true"
    } else {
      "false ($result)"
    }
  }

  private fun getEmojiVersionString(context: Context): String {
    val version = readVersion(context)

    return if (version == null) {
      "None"
    } else {
      "${version.version} (${version.density})"
    }
  }

  private fun getCensoredAci(): String {
    val aci = SignalStore.account.aci

    if (aci != null) {
      val aciString = aci.toString()
      val lastThree = aciString.substring(aciString.length - 3)

      return "********-****-****-****-*********$lastThree"
    } else {
      return "N/A"
    }
  }

  private fun getDontKeepActivities(context: Context): String {
    val setting = Settings.Global.getInt(context.contentResolver, Settings.Global.ALWAYS_FINISH_ACTIVITIES, 0)
    return if (setting == 0) {
      "false"
    } else {
      "true"
    }
  }

  private fun getTimeRegistered(): String {
    if (SignalStore.account.registeredAtTimestamp <= 0) {
      return "Unknown"
    }

    val timeSince = (System.currentTimeMillis() - SignalStore.account.registeredAtTimestamp).milliseconds.toString()
    return "${SignalStore.account.registeredAtTimestamp} ($timeSince ago)"
  }
}
