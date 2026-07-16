package org.thoughtcrime.securesms.logsubmit

import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.TrafficStats
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import org.signal.core.util.bytes
import org.thoughtcrime.securesms.util.BucketInfo
import org.thoughtcrime.securesms.util.DeviceProperties
import org.thoughtcrime.securesms.util.WakeLockUtil
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * A point-in-time summary of process resource usage relevant to battery consumption. The detailed
 * time series lives in the regular logs (emitted by [org.thoughtcrime.securesms.util.BatterySnapshotTracker]);
 * this section gives an at-a-glance summary accumulated since process start.
 */
class LogSectionBattery : LogSection {
  override fun getTitle(): String {
    return "BATTERY"
  }

  override fun getContent(context: Context): CharSequence {
    val cpuMs = Process.getElapsedCpuTime()
    val wakeLockMs = WakeLockUtil.getCumulativeHeldMs()
    val rxBytes = TrafficStats.getUidRxBytes(Process.myUid())
    val txBytes = TrafficStats.getUidTxBytes(Process.myUid())

    val builder = StringBuilder()

    if (Build.VERSION.SDK_INT >= 24) {
      val uptimeMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
      builder.append("Process uptime  : ").append(uptimeMs.milliseconds.toString()).append("\n")
      builder.append("CPU time        : ").append(cpuMs).append("ms (").append(percent(cpuMs, uptimeMs)).append(" of uptime)\n")
      builder.append("Wakelock time   : ").append(wakeLockMs).append("ms (").append(percent(wakeLockMs, uptimeMs)).append(" of uptime)\n")
    } else {
      builder.append("CPU time        : ").append(cpuMs).append("ms\n")
      builder.append("Wakelock time   : ").append(wakeLockMs).append("ms\n")
    }

    builder.append("Active wakelocks: ").append(WakeLockUtil.getActiveLockCount()).append("\n")
    builder.append("Network rx/tx   : ").append(rxBytes.bytes.toUnitString()).append(" / ").append(txBytes.bytes.toUnitString()).append("\n")
    builder.append("Battery level   : ").append(DeviceProperties.getBatteryLevel(context)).append("%\n")
    builder.append("Charge counter  : ").append(DeviceProperties.getBatteryChargeCounter(context)).append("µAh\n")
    builder.append("Charging        : ").append(DeviceProperties.isCharging(context)).append("\n")

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    if (powerManager != null) {
      builder.append("Power save mode : ").append(powerManager.isPowerSaveMode).append("\n")
      builder.append("Device idle     : ").append(powerManager.isDeviceIdleMode).append("\n")
      builder.append("Interactive     : ").append(powerManager.isInteractive).append("\n")
    }

    if (Build.VERSION.SDK_INT >= 28) {
      val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
      if (usageStatsManager != null) {
        builder.append("Standby bucket  : ").append(BucketInfo.bucketToString(usageStatsManager.appStandbyBucket)).append("\n")
      }
    }

    return builder
  }

  private fun percent(part: Long, whole: Long): String {
    if (whole <= 0) {
      return "n/a"
    }
    return String.format(Locale.US, "%.1f%%", 100.0 * part / whole)
  }
}
