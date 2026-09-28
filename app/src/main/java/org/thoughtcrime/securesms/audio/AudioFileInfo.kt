package org.thoughtcrime.securesms.audio

import okio.ByteString.Companion.toByteString
import org.thoughtcrime.securesms.database.model.databaseprotos.AudioWaveFormData
import java.util.concurrent.TimeUnit

class AudioFileInfo @JvmOverloads internal constructor(
  private val durationUs: Long,
  private val waveFormBytes: ByteArray,
  val isSenderProvided: Boolean = false
) {

  companion object {
    @JvmStatic
    fun fromDatabaseProtobuf(audioWaveForm: AudioWaveFormData): AudioFileInfo {
      return AudioFileInfo(audioWaveForm.durationUs, audioWaveForm.waveForm.toByteArray(), audioWaveForm.senderProvided)
    }

    /**
     * Buckets the source bars into [targetCount] bars so that wave forms of any length can be rendered by views that expect a fixed bar count.
     */
    private fun resample(source: ByteArray, targetCount: Int): FloatArray {
      return FloatArray(targetCount) { i ->
        val start = (i.toLong() * source.size / targetCount).toInt()
        val end = maxOf(start + 1, ((i + 1).toLong() * source.size / targetCount).toInt()).coerceAtMost(source.size)

        var sum = 0
        for (j in start until end) {
          sum += source[j].toInt() and 0xff
        }

        sum / (end - start).toFloat() / 255f
      }
    }
  }

  val waveForm: FloatArray = if (waveFormBytes.isEmpty()) FloatArray(0) else resample(waveFormBytes, AudioWaveFormGenerator.BAR_COUNT)

  val barCount: Int = waveFormBytes.size

  fun getDuration(timeUnit: TimeUnit): Long {
    return timeUnit.convert(durationUs, TimeUnit.MICROSECONDS)
  }

  fun toDatabaseProtobuf(): AudioWaveFormData {
    return AudioWaveFormData.Builder()
      .durationUs(durationUs)
      .waveForm(waveFormBytes.toByteString())
      .senderProvided(isSenderProvided)
      .build()
  }
}
