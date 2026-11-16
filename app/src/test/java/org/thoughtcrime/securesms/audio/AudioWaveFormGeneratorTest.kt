/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.audio

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import org.thoughtcrime.securesms.audio.AudioWaveFormGenerator.BAR_COUNT
import org.thoughtcrime.securesms.audio.AudioWaveFormGenerator.BarAccumulator
import org.thoughtcrime.securesms.audio.AudioWaveFormGenerator.PcmFormat
import kotlin.math.roundToInt

class AudioWaveFormGeneratorTest {

  companion object {
    private const val SAMPLE_RATE = 1000
    private const val DURATION_US = 1_000_000L
    private const val FULL_SCALE: Short = 32767
  }

  @Test
  fun `full scale maps to the max bar height`() {
    assertThat(AudioWaveFormGenerator.meanSquareToBar(1.0).unsigned()).isEqualTo(255)
  }

  @Test
  fun `silence maps to an empty bar`() {
    assertThat(AudioWaveFormGenerator.meanSquareToBar(0.0).unsigned()).isEqualTo(0)
  }

  @Test
  fun `anything at or below the noise bed maps to an empty bar`() {
    assertThat(AudioWaveFormGenerator.meanSquareToBar(1e-6).unsigned()).isEqualTo(0)
    assertThat(AudioWaveFormGenerator.meanSquareToBar(1e-9).unsigned()).isEqualTo(0)
  }

  @Test
  fun `decibels are mapped linearly across the noise bed`() {
    assertThat(AudioWaveFormGenerator.meanSquareToBar(1e-3).unsigned()).isEqualTo(128)
    assertThat(AudioWaveFormGenerator.meanSquareToBar(1e-4).unsigned()).isEqualTo(85)
  }

  /**
   * Shared cross-client vector, confirming our bucketing and decibel mapping agree with iOS and Desktop.
   */
  @Test
  fun `cross client test vector`() {
    val samples = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f)
    val accumulator = BarAccumulator(totalDurationUs = 3_000_000, pcmFormat = PcmFormat(sampleRate = 2, channelCount = 1), barCount = 3)

    accumulator.accumulate(ShortArray(samples.size) { (samples[it] * 32768).roundToInt().toShort() }, samples.size, 0)

    assertThat(accumulator.toWaveForm().unsigned()).isEqualTo(listOf(187, 217, 233))
  }

  @Test
  fun `constant tone fills every bar`() {
    val accumulator = accumulator()

    accumulator.accumulate(ShortArray(SAMPLE_RATE) { FULL_SCALE }, SAMPLE_RATE, 0)

    assertThat(accumulator.toWaveForm().unsigned()).isEqualTo(List(BAR_COUNT) { 255 })
  }

  @Test
  fun `each sample lands in the bar its own timestamp falls into`() {
    val accumulator = accumulator()
    val samples = ShortArray(SAMPLE_RATE) { if (it < SAMPLE_RATE / 2) FULL_SCALE else 0 }

    accumulator.accumulate(samples, samples.size, 0)

    assertThat(accumulator.toWaveForm().unsigned()).isEqualTo(List(BAR_COUNT) { if (it < BAR_COUNT / 2) 255 else 0 })
  }

  @Test
  fun `buffers are placed by their presentation time`() {
    val accumulator = accumulator()
    val framesPerBuffer = SAMPLE_RATE / 10

    for (buffer in 0 until 10) {
      val samples = ShortArray(framesPerBuffer) { if (buffer == 3) FULL_SCALE else 0 }
      accumulator.accumulate(samples, samples.size, buffer * (DURATION_US / 10))
    }

    assertThat(accumulator.toWaveForm().unsigned()).isEqualTo(List(BAR_COUNT) { if (it in 30..39) 255 else 0 })
  }

  @Test
  fun `bars with no samples are empty`() {
    val accumulator = accumulator()
    val samples = ShortArray(SAMPLE_RATE / 2) { FULL_SCALE }

    accumulator.accumulate(samples, samples.size, 0)

    assertThat(accumulator.toWaveForm().unsigned()).isEqualTo(List(BAR_COUNT) { if (it < BAR_COUNT / 2) 255 else 0 })
  }

  @Test
  fun `both channels of a stereo frame are counted`() {
    val stereo = accumulator(channelCount = 2)
    val mono = accumulator(channelCount = 1)

    stereo.accumulate(ShortArray(SAMPLE_RATE * 2) { if (it % 2 == 0) FULL_SCALE else 0 }, SAMPLE_RATE * 2, 0)
    mono.accumulate(ShortArray(SAMPLE_RATE) { if (it % 2 == 0) FULL_SCALE else 0 }, SAMPLE_RATE, 0)

    assertThat(stereo.toWaveForm().unsigned()).isEqualTo(mono.toWaveForm().unsigned())
  }

  private fun accumulator(channelCount: Int = 1): BarAccumulator {
    return BarAccumulator(DURATION_US, PcmFormat(SAMPLE_RATE, channelCount))
  }

  private fun Byte.unsigned(): Int = this.toInt() and 0xff

  private fun ByteArray.unsigned(): List<Int> = this.map { it.unsigned() }
}
