/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.media.DecryptableUriMediaInput
import org.thoughtcrime.securesms.video.videoconverter.utils.MediaCodecCompat
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

  const val BAR_COUNT = 100

  private const val NOISE_BED_DB = -60.0
  private const val SHORT_FULL_SCALE_SQUARED = 32768.0 * 32768.0
  private const val MICROS_PER_SECOND = 1_000_000L
  private const val TIMEOUT_US = 5000L
  private const val MAX_IDLE_ITERATIONS = 50
  private const val MAX_FRAMES_PER_INPUT = 32
  private const val BATCHED_INPUT_SIZE = 64 * 1024
  private const val SCRATCH_SAMPLE_SIZE = 16 * 1024

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

      val packer = AdtsPacker.fromFormat(format)
      val softwareDecoder = packer?.let { MediaCodecCompat.findSoftwareDecoder(mime) }

      // Feeding many ADTS frames per input buffer cuts the per-buffer round trips that dominate decode time, but hardware
      // decoders generally only accept one frame per buffer, so we only batch when we can pin a software decoder.
      val codec: MediaCodec
      val batchedReader: BatchedInputReader?
      if (packer != null && softwareDecoder != null) {
        format.setInteger(MediaFormat.KEY_IS_ADTS, 1)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, BATCHED_INPUT_SIZE)
        codec = MediaCodec.createByCodecName(softwareDecoder)
        batchedReader = BatchedInputReader(packer)
      } else {
        codec = MediaCodec.createDecoderByType(mime)
        batchedReader = null
      }

      codec.configure(format, null, null, 0)
      codec.start()

      extractor.selectTrack(0)

      val accumulator = BarAccumulator(totalDurationUs, readPcmFormat(format))
      val info = MediaCodec.BufferInfo()
      var extractorDone = false
      var inputEOSQueued = false
      var sawOutputEOS = false
      var idleCounter = 0

      while (!sawOutputEOS && idleCounter < MAX_IDLE_ITERATIONS) {
        var fedInput = false

        while (!inputEOSQueued) {
          val inputBufferIndex = codec.dequeueInputBuffer(0)
          if (inputBufferIndex < 0) {
            break
          }

          fedInput = true

          if (extractorDone) {
            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputEOSQueued = true
          } else {
            val destinationBuffer = codec.getInputBuffer(inputBufferIndex)!!
            val input = batchedReader?.read(extractor, destinationBuffer) ?: readSingleInput(extractor, destinationBuffer)

            if (input.frames == 0) {
              codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputEOSQueued = true
            } else {
              codec.queueInputBuffer(inputBufferIndex, 0, input.size, input.presentationTimeUs, 0)
            }
            extractorDone = input.extractorDone
          }
        }

        var gotOutput = false
        var outputBufferIndex: Int
        do {
          // Only block when the decoder has nothing new to chew on, otherwise poll so input keeps flowing
          outputBufferIndex = codec.dequeueOutputBuffer(info, if (gotOutput || fedInput) 0 else TIMEOUT_US)

          if (outputBufferIndex >= 0) {
            gotOutput = true

            if (info.size > 0) {
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
            accumulator.pcmFormat = readPcmFormat(codec.outputFormat, format)
          }
        } while (outputBufferIndex >= 0 && !sawOutputEOS)

        idleCounter = if (fedInput || gotOutput) 0 else idleCounter + 1
      }

      codec.stop()
      codec.release()
      extractor.release()

      return AudioFileInfo(totalDurationUs, accumulator.toWaveForm())
    }
  }

  private fun readSingleInput(extractor: MediaExtractor, destination: ByteBuffer): InputChunk {
    val sampleSize = extractor.readSampleData(destination, 0)
    if (sampleSize < 0) {
      return InputChunk(frames = 0, size = 0, presentationTimeUs = 0, extractorDone = true)
    }

    val presentationTimeUs = extractor.sampleTime
    val extractorDone = !extractor.advance()

    return InputChunk(frames = 1, size = sampleSize, presentationTimeUs = presentationTimeUs, extractorDone = extractorDone)
  }

  /**
   * Packs as many ADTS-wrapped frames as fit into [destination]. A frame that does not fit is left in the extractor for the next call.
   */
  private class BatchedInputReader(private val packer: AdtsPacker) {

    private val scratch: ByteBuffer = ByteBuffer.allocateDirect(SCRATCH_SAMPLE_SIZE)

    fun read(extractor: MediaExtractor, destination: ByteBuffer): InputChunk {
      destination.clear()

      var frames = 0
      var presentationTimeUs = 0L
      var extractorDone = false

      while (frames < MAX_FRAMES_PER_INPUT && !extractorDone) {
        scratch.clear()
        val sampleSize = extractor.readSampleData(scratch, 0)
        if (sampleSize < 0) {
          extractorDone = true
          break
        }

        if (!packer.pack(scratch, destination)) {
          if (frames == 0) {
            throw IOException("Sample of $sampleSize bytes does not fit in input buffer of ${destination.capacity()} bytes")
          }
          break
        }

        if (frames == 0) {
          presentationTimeUs = extractor.sampleTime
        }

        frames++
        extractorDone = !extractor.advance()
      }

      return InputChunk(frames = frames, size = destination.position(), presentationTimeUs = presentationTimeUs, extractorDone = extractorDone)
    }
  }

  private class InputChunk(val frames: Int, val size: Int, val presentationTimeUs: Long, val extractorDone: Boolean)

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
