/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ShakeDetectorTest {

  companion object {
    private const val GRAVITY = 9.81f
    private val SAMPLE_RATES_HZ = listOf(15, 25, 50, 100, 200)
  }

  @Test
  fun `a vigorous shake is detected at the same point regardless of sample rate`() {
    for (rate in SAMPLE_RATES_HZ) {
      val detectedAt = firstShake(rate, durationS = 2f) { t -> floatArrayOf(20f * sin(2 * PI * 4 * t).toFloat(), 0f, 0f) }

      assertThat(detectedAt, "rate=$rate").isNotNull().isBetween(0.85f, 1.0f)
    }
  }

  @Test
  fun `a shake along a diagonal is detected`() {
    for (rate in SAMPLE_RATES_HZ) {
      val detectedAt = firstShake(rate, durationS = 2f) { t ->
        val a = 14f * sin(2 * PI * 4 * t).toFloat()
        floatArrayOf(a, a, a)
      }

      assertThat(detectedAt, "rate=$rate").isNotNull()
    }
  }

  @Test
  fun `a gentle shake is not detected`() {
    for (rate in SAMPLE_RATES_HZ) {
      val detectedAt = firstShake(rate, durationS = 5f) { t -> floatArrayOf(6f * sin(2 * PI * 3 * t).toFloat(), 0f, 0f) }

      assertThat(detectedAt, "rate=$rate").isNull()
    }
  }

  @Test
  fun `a slow swing is not detected`() {
    for (rate in SAMPLE_RATES_HZ) {
      val detectedAt = firstShake(rate, durationS = 5f) { t -> floatArrayOf(15f * sin(2 * PI * 1 * t).toFloat(), 0f, 0f) }

      assertThat(detectedAt, "rate=$rate").isNull()
    }
  }

  @Test
  fun `a single jolt is not detected`() {
    for (rate in SAMPLE_RATES_HZ) {
      val detectedAt = firstShake(rate, durationS = 2f) { t ->
        val a = if (t in 0.5..0.7) 25f * sin(2 * PI * (t - 0.5) / 0.2).toFloat() else 0f
        floatArrayOf(0f, a, 0f)
      }

      assertThat(detectedAt, "rate=$rate").isNull()
    }
  }

  @Test
  fun `a noisy device at rest is not detected`() {
    val random = Random(0)
    for (rate in SAMPLE_RATES_HZ) {
      val detectedAt = firstShake(rate, durationS = 10f) { floatArrayOf(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1) }

      assertThat(detectedAt, "rate=$rate").isNull()
    }
  }

  @Test
  fun `flipping the device over is not detected`() {
    for (rate in SAMPLE_RATES_HZ) {
      val processor = ShakeDetector.Processor()
      var detected = false

      forEachSample(rate, durationS = 2f) { t, timestampNs ->
        val angle = (PI * ((t - 0.5) / 0.3).coerceIn(0.0, 1.0)).toFloat()
        detected = detected || processor.onSample(timestampNs, 0f, GRAVITY * sin(angle), GRAVITY * cos(angle))
      }

      assertThat(detected, "rate=$rate").isFalse()
    }
  }

  private fun firstShake(rateHz: Int, durationS: Float, linear: (Double) -> FloatArray): Float? {
    val processor = ShakeDetector.Processor()
    var detectedAt: Float? = null

    forEachSample(rateHz, durationS) { t, timestampNs ->
      val a = linear(t)
      if (processor.onSample(timestampNs, a[0], a[1], a[2] + GRAVITY) && detectedAt == null) {
        detectedAt = t.toFloat()
      }
    }

    return detectedAt
  }

  private fun forEachSample(rateHz: Int, durationS: Float, block: (Double, Long) -> Unit) {
    val count = (durationS * rateHz).toInt()
    for (i in 0..count) {
      val t = i.toDouble() / rateHz
      block(t, (t * 1_000_000_000).toLong())
    }
  }
}
