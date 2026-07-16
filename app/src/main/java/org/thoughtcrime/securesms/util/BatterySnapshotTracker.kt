package org.thoughtcrime.securesms.util

import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.TrafficStats
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import java.util.Locale

/**
 * Records lightweight, delta-based snapshots of process resource usage that correlate with battery
 * drain (CPU time, held wakelocks, network bytes, and consumed charge), along with the power-related
 * device state at the time of sampling.
 *
 * Every value read here is a cheap, in-process syscall, so this is intended to be sampled only when
 * the app is already awake for another reason (e.g. a routine background fetch or a foreground
 * transition). It deliberately never schedules its own wakeups, which would themselves drain battery.
 */
object BatterySnapshotTracker {

  private val TAG = Log.tag(BatterySnapshotTracker::class.java)

  private var lastSample: Sample? = null

  @JvmStatic
  @Synchronized
  fun emit(context: Context, trigger: String) {
    val sample = Sample(
      elapsedRealtimeMs = SystemClock.elapsedRealtime(),
      cpuMs = Process.getElapsedCpuTime(),
      rxBytes = TrafficStats.getUidRxBytes(Process.myUid()),
      txBytes = TrafficStats.getUidTxBytes(Process.myUid()),
      wakeLockMs = WakeLockUtil.getCumulativeHeldMs(),
      chargeCounterUah = DeviceProperties.getBatteryChargeCounter(context)
    )

    val previous = lastSample
    lastSample = sample

    val state = describeState(context)

    if (previous == null) {
      Log.i(TAG, "[$trigger] Baseline established. $state")
      return
    }

    val wallMs = sample.elapsedRealtimeMs - previous.elapsedRealtimeMs
    if (wallMs <= 0) {
      Log.w(TAG, "[$trigger] Non-positive interval ($wallMs ms). Skipping.")
      return
    }

    val cpuDeltaMs = sample.cpuMs - previous.cpuMs
    val wakeDeltaMs = sample.wakeLockMs - previous.wakeLockMs

    Log.i(
      TAG,
      String.format(
        Locale.US,
        "[%s] Δ=%.1fm | cpu=+%dms(%.1f%%) | wakelock=+%dms(%.1f%%) | net=%s | chargeΔ=%s | %s",
        trigger,
        wallMs / 60000.0,
        cpuDeltaMs,
        100.0 * cpuDeltaMs / wallMs,
        wakeDeltaMs,
        100.0 * wakeDeltaMs / wallMs,
        formatNet(previous, sample),
        formatCharge(previous, sample),
        state
      )
    )
  }

  private fun formatNet(previous: Sample, sample: Sample): String {
    if (sample.rxBytes < 0 || sample.txBytes < 0 || previous.rxBytes < 0 || previous.txBytes < 0) {
      return "unsupported"
    }
    val rx = sample.rxBytes - previous.rxBytes
    val tx = sample.txBytes - previous.txBytes
    return "+${(rx + tx).bytes.toUnitString()} (rx ${rx.bytes.toUnitString()} / tx ${tx.bytes.toUnitString()})"
  }

  private fun formatCharge(previous: Sample, sample: Sample): String {
    if (sample.chargeCounterUah < 0 || previous.chargeCounterUah < 0) {
      return "unsupported"
    }
    return "${sample.chargeCounterUah - previous.chargeCounterUah}µAh"
  }

  private fun describeState(context: Context): String {
    val level = DeviceProperties.getBatteryLevel(context)
    val charging = DeviceProperties.isCharging(context)

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val powerSave = powerManager?.isPowerSaveMode ?: false
    val doze = powerManager?.isDeviceIdleMode ?: false
    val interactive = powerManager?.isInteractive ?: false

    val bucket = if (Build.VERSION.SDK_INT >= 28) {
      val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
      usageStatsManager?.let { BucketInfo.bucketToString(it.appStandbyBucket) } ?: "unknown"
    } else {
      "n/a"
    }

    return "battery=$level% charging=$charging powerSave=$powerSave doze=$doze interactive=$interactive bucket=$bucket"
  }

  private data class Sample(
    val elapsedRealtimeMs: Long,
    val cpuMs: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val wakeLockMs: Long,
    val chargeCounterUah: Int
  )
}
