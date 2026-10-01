/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Uri
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.media.DecryptableUriMediaInput
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Generates voice note wave forms by decoding the audio and converting the mean square of each bar's samples into absolute decibels.
 *
 * This is a simplified LUFS computation, loosely based on ITU-R BS.1770 without sample filtering or overlap between bars.
 */
@RequiresApi(23)
object AudioWaveFormGenerator {

  private val TAG = Log.tag(AudioWaveFormGenerator::class)

  const val BAR_COUNT = 100

  private const val NOISE_BED_DB = -60.0
  private const val SHORT_FULL_SCALE_SQUARED = 32768.0 * 32768.0
  private const val MICROS_PER_SECOND = 1_000_000L
  private const val TIMEOUT_US = 5000L

  /**
   * Generate a waveform for the provide URI.
   */
  @WorkerThread
  @Throws(IOException::class)
  fun generateWaveForm(context: Context, uri: Uri): AudioFileInfo {
    DecryptableUriMediaInput.createForUri(context, uri).use { dataSource ->
      val extractor = dataSource.createExtractor()

      if (extractor.trackCount == 0) {
        throw IOException("No audio track")
      }

      val format = extractor.getTrackFormat(0)

      if (!format.containsKey(MediaFormat.KEY_DURATION)) {
        throw IOException("Unknown duration")
      }

      val totalDurationUs = format.getLong(MediaFormat.KEY_DURATION)
      val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("No mime type")

      if (!mime.startsWith("audio/")) {
        throw IOException("Mime not audio")
      }

      if (totalDurationUs == 0L) {
        throw IOException("Zero duration")
      }

      val codec = MediaCodec.createDecoderByType(mime)
      codec.configure(format, null, null, 0)
      codec.start()

      extractor.selectTrack(0)

      val accumulator = BarAccumulator(totalDurationUs, readPcmFormat(format))
      val info = MediaCodec.BufferInfo()
      var extractorDone = false
      var inputEOSQueued = false
      var sawOutputEOS = false
      var noOutputCounter = 0

      while (!sawOutputEOS && noOutputCounter < 50) {
        noOutputCounter++

        if (!inputEOSQueued) {
          val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
          if (inputBufferIndex >= 0) {
            if (extractorDone) {
              codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputEOSQueued = true
            } else {
              val destinationBuffer = codec.getInputBuffer(inputBufferIndex)
              val sampleSize = extractor.readSampleData(destinationBuffer!!, 0)

              if (sampleSize < 0) {
                codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputEOSQueued = true
              } else {
                codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                extractorDone = !extractor.advance()
              }
            }
          }
        }

        var outputBufferIndex: Int
        do {
          outputBufferIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)

          if (outputBufferIndex >= 0) {
            if (info.size > 0) {
              noOutputCounter = 0

              val buffer = codec.getOutputBuffer(outputBufferIndex)
              if (buffer != null) {
                accumulator.accumulate(buffer, info)
              }
            }

            codec.releaseOutputBuffer(outputBufferIndex, false)

            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
              sawOutputEOS = true
            }
          } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            Log.d(TAG, "Output format has changed to ${codec.outputFormat}")
            accumulator.pcmFormat = readPcmFormat(codec.outputFormat, format)
          }
        } while (outputBufferIndex >= 0)
      }

      codec.stop()
      codec.release()
      extractor.release()

      return AudioFileInfo(totalDurationUs, accumulator.toWaveForm())
    }
  }

  /**
   * Converts the mean square of a bar's samples, where each sample is normalized to [-1, 1], into a bar height in the [0, 255] range.
   */
  @VisibleForTesting
  fun meanSquareToBar(meanSquare: Double): Byte {
    val db = 10 * log10(meanSquare)
    val normalized = max(0.0, db - NOISE_BED_DB) / -NOISE_BED_DB

    return (255 * normalized).roundToInt().coerceIn(0, 255).toByte()
  }

  private fun readPcmFormat(vararg formats: MediaFormat): PcmFormat {
    return PcmFormat(
      sampleRate = formats.firstNotNullOfOrNull { it.getPositiveIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) } ?: 44100,
      channelCount = formats.firstNotNullOfOrNull { it.getPositiveIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) } ?: 1
    )
  }

  private fun MediaFormat.getPositiveIntegerOrNull(key: String): Int? {
    return if (containsKey(key)) {
      getInteger(key).takeIf { it > 0 }
    } else {
      null
    }
  }

  internal data class PcmFormat(val sampleRate: Int, val channelCount: Int)

  /**
   * Sums the squares of every decoded sample into the bar that the sample's timestamp falls into.
   *
   * Samples are accumulated as raw shorts and only scaled down once per bar, keeping the hot loop to integer math.
   */
  @VisibleForTesting
  internal class BarAccumulator(private val totalDurationUs: Long, var pcmFormat: PcmFormat, private val barCount: Int = BAR_COUNT) {

    private val sumOfSquares = LongArray(barCount)
    private val sampleCounts = LongArray(barCount)
    private var scratch = ShortArray(0)

    fun accumulate(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
      val sampleCount = info.size / 2 / pcmFormat.channelCount * pcmFormat.channelCount

      if (sampleCount == 0) {
        return
      }

      if (scratch.size < sampleCount) {
        scratch = ShortArray(sampleCount)
      }

      val pcm = buffer.duplicate()
      pcm.order(ByteOrder.nativeOrder())
      pcm.limit(info.offset + info.size)
      pcm.position(info.offset)
      pcm.asShortBuffer().get(scratch, 0, sampleCount)

      accumulate(scratch, sampleCount, info.presentationTimeUs)
    }

    fun accumulate(samples: ShortArray, sampleCount: Int, presentationTimeUs: Long) {
      val sampleRate = pcmFormat.sampleRate
      val channelCount = pcmFormat.channelCount
      val frameCount = sampleCount / channelCount

      var frame = 0
      while (frame < frameCount) {
        val frameTimeUs = presentationTimeUs + frame * MICROS_PER_SECOND / sampleRate
        val barIndex = (frameTimeUs * barCount / totalDurationUs).toInt()

        if (barIndex >= barCount) {
          return
        }

        val nextBarStartUs = (barIndex + 1).toLong() * totalDurationUs / barCount
        val framesBeforeNextBar = ceilDiv((nextBarStartUs - presentationTimeUs) * sampleRate, MICROS_PER_SECOND)
        val runEnd = framesBeforeNextBar.coerceIn((frame + 1).toLong(), frameCount.toLong()).toInt()

        if (barIndex >= 0) {
          var sum = 0L
          for (i in frame * channelCount until runEnd * channelCount) {
            val sample = samples[i].toInt()
            sum += (sample * sample).toLong()
          }

          sumOfSquares[barIndex] += sum
          sampleCounts[barIndex] += (runEnd - frame).toLong() * channelCount
        }

        frame = runEnd
      }
    }

    fun toWaveForm(): ByteArray {
      return ByteArray(barCount) { i ->
        if (sampleCounts[i] == 0L) {
          0
        } else {
          meanSquareToBar(sumOfSquares[i].toDouble() / sampleCounts[i] / SHORT_FULL_SCALE_SQUARED)
        }
      }
    }

    private fun ceilDiv(numerator: Long, denominator: Long): Long = (numerator + denominator - 1) / denominator
  }
}
