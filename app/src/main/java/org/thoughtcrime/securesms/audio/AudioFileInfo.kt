package org.thoughtcrime.securesms.audio

import okio.ByteString.Companion.toByteString
import org.thoughtcrime.securesms.database.model.databaseprotos.AudioWaveFormData
import java.util.concurrent.TimeUnit

class AudioFileInfo internal constructor(
  private val durationUs: Long,
  private val waveFormBytes: ByteArray
) {

  companion object {
    @JvmStatic
    fun fromDatabaseProtobuf(audioWaveForm: AudioWaveFormData): AudioFileInfo {
      return AudioFileInfo(audioWaveForm.durationUs, audioWaveForm.waveForm.toByteArray())
    }
  }

  val waveForm: FloatArray = FloatArray(waveFormBytes.size) { i -> (waveFormBytes[i].toInt() and 0xff) / 255f }

  fun getDuration(timeUnit: TimeUnit): Long {
    return timeUnit.convert(durationUs, TimeUnit.MICROSECONDS)
  }

  fun toDatabaseProtobuf(): AudioWaveFormData {
    return AudioWaveFormData.Builder()
      .durationUs(durationUs)
      .waveForm(waveFormBytes.toByteString())
      .build()
  }
}
