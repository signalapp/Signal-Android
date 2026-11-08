/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.annotation.VisibleForTesting
import kotlin.math.sqrt

/**
 * Detects the device being shaken back and forth.
 *
 * Gravity is filtered out of the accelerometer readings, and a shake is counted as several
 * strong accelerations in alternating directions within a short window. Counting direction
 * reversals rather than the fraction of samples above a threshold keeps the behavior the same
 * regardless of how often a given device delivers sensor events.
 */
class ShakeDetector(private val listener: Listener) : SensorEventListener {

  fun interface Listener {
    /** Called on the main thread when the device is shaken. */
    fun onShakeDetected()
  }

  private val processor = Processor()

  private var sensorManager: SensorManager? = null
  private var accelerometer: Sensor? = null

  /**
   * Starts listening for shakes on devices with appropriate hardware.
   *
   * @return true if the device supports shake detection.
   */
  fun start(sensorManager: SensorManager): Boolean {
    if (accelerometer != null) {
      return true
    }

    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false

    processor.reset()
    this.sensorManager = sensorManager
    this.accelerometer = sensor
    sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)

    return true
  }

  /**
   * Stops listening. Safe to call when already stopped.
   */
  fun stop() {
    val sensor = accelerometer ?: return

    sensorManager?.unregisterListener(this, sensor)
    sensorManager = null
    accelerometer = null
    processor.reset()
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (processor.onSample(event.timestamp, event.values[0], event.values[1], event.values[2])) {
      listener.onShakeDetected()
    }
  }

  override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

  /**
   * The sensor-independent detection logic. Feed it raw accelerometer samples and it reports when a shake completes.
   */
  @VisibleForTesting
  class Processor {

    companion object {
      /** Linear acceleration, in m/s^2, a sample must reach to count toward a shake. */
      private const val THRESHOLD = 10f

      /** Number of alternating-direction peaks that make up a shake. */
      private const val REQUIRED_PEAKS = 8

      /** All [REQUIRED_PEAKS] must land within this window. */
      private const val WINDOW_NS = 1_000_000_000L

      /** If no strong acceleration is seen for this long, the current peak is considered over. */
      private const val MAX_PEAK_GAP_NS = 500_000_000L

      /** Time constant of the gravity low-pass filter. */
      private const val GRAVITY_TIME_CONSTANT_S = 0.3f
    }

    private val gravity = FloatArray(3)
    private val lastPeakDirection = FloatArray(3)
    private val peakTimestamps = ArrayDeque<Long>()

    private var lastTimestamp = 0L
    private var lastPeakTimestamp = 0L
    private var hasGravity = false
    private var hasPeak = false

    fun reset() {
      hasGravity = false
      clearPeaks()
    }

    /**
     * @param timestamp sample time in nanoseconds
     * @return true if this sample completes a shake
     */
    fun onSample(timestamp: Long, x: Float, y: Float, z: Float): Boolean {
      if (!hasGravity) {
        gravity[0] = x
        gravity[1] = y
        gravity[2] = z
        lastTimestamp = timestamp
        hasGravity = true
        return false
      }

      val dt = (timestamp - lastTimestamp).coerceAtLeast(0L) / 1_000_000_000f
      lastTimestamp = timestamp

      val alpha = GRAVITY_TIME_CONSTANT_S / (GRAVITY_TIME_CONSTANT_S + dt)
      gravity[0] = alpha * gravity[0] + (1 - alpha) * x
      gravity[1] = alpha * gravity[1] + (1 - alpha) * y
      gravity[2] = alpha * gravity[2] + (1 - alpha) * z

      val lx = x - gravity[0]
      val ly = y - gravity[1]
      val lz = z - gravity[2]

      if (hasPeak && timestamp - lastPeakTimestamp > MAX_PEAK_GAP_NS) {
        clearPeaks()
      }

      val magnitude = sqrt(lx * lx + ly * ly + lz * lz)
      if (magnitude < THRESHOLD) {
        return false
      }

      val dx = lx / magnitude
      val dy = ly / magnitude
      val dz = lz / magnitude

      val isNewPeak = !hasPeak || (dx * lastPeakDirection[0] + dy * lastPeakDirection[1] + dz * lastPeakDirection[2]) < 0

      lastPeakDirection[0] = dx
      lastPeakDirection[1] = dy
      lastPeakDirection[2] = dz
      lastPeakTimestamp = timestamp
      hasPeak = true

      if (!isNewPeak) {
        return false
      }

      peakTimestamps.addLast(timestamp)
      while (timestamp - peakTimestamps.first() > WINDOW_NS) {
        peakTimestamps.removeFirst()
      }

      if (peakTimestamps.size >= REQUIRED_PEAKS) {
        clearPeaks()
        return true
      }

      return false
    }

    private fun clearPeaks() {
      peakTimestamps.clear()
      hasPeak = false
    }
  }
}
