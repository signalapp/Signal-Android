package org.thoughtcrime.securesms.audio

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64
import org.signal.core.util.ParcelUtil
import org.thoughtcrime.securesms.database.model.databaseprotos.AudioWaveFormData
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.messages.SignalServiceMessageLimits
import java.io.IOException
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * An AudioHash is a compact string representation of the wave form and duration for an audio file.
 */
@Parcelize
class AudioHash private constructor(
  val hash: String,
  @TypeParceler<AudioWaveFormData, AudioWaveFormDataParceler>() internal val audioWaveForm: AudioWaveFormData
) : Parcelable {

  companion object {
    @JvmStatic
    fun parseOrNull(hash: String?): AudioHash? {
      if (hash == null) {
        return null
      }

      return try {
        AudioHash(hash, AudioWaveFormData.ADAPTER.decode(Base64.decode(hash)))
      } catch (e: IOException) {
        null
      }
    }

    /**
     * Builds a hash out of the wave form data provided by the sender of an attachment, or null if the attachment isn't audio or the data is absent
     * or malformed. Durations longer than [SignalServiceMessageLimits.MAX_AUDIO_DURATION_SECONDS] are clamped.
     */
    @JvmStatic
    fun fromSenderProvided(contentType: String?, waveForm: ByteArray?, durationSeconds: Float?): AudioHash? {
      if (!MediaUtil.isAudioType(contentType)) {
        return null
      }

      if (waveForm == null || waveForm.isEmpty() || waveForm.size > SignalServiceMessageLimits.MAX_AUDIO_WAVEFORM_BAR_COUNT) {
        return null
      }

      val durationUs = if (durationSeconds != null && durationSeconds > 0) {
        durationSeconds
          .coerceAtMost(SignalServiceMessageLimits.MAX_AUDIO_DURATION_SECONDS)
          .toDouble()
          .seconds
          .inWholeMicroseconds
      } else {
        0
      }

      return AudioHash(
        AudioWaveFormData.Builder()
          .durationUs(durationUs)
          .waveForm(waveForm.toByteString())
          .senderProvided(true)
          .build()
      )
    }
  }

  constructor(audioWaveForm: AudioWaveFormData) : this(Base64.encodeWithPadding(audioWaveForm.encode()), audioWaveForm)

  val waveFormBytes: ByteArray
    get() = audioWaveForm.waveForm.toByteArray()

  val durationSeconds: Float?
    get() = if (audioWaveForm.durationUs > 0) audioWaveForm.durationUs.microseconds.toDouble(DurationUnit.SECONDS).toFloat() else null

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other == null || javaClass != other.javaClass) {
      return false
    }
    return hash == (other as AudioHash).hash
  }

  override fun hashCode(): Int = hash.hashCode()
}

private object AudioWaveFormDataParceler : Parceler<AudioWaveFormData> {
  override fun create(parcel: Parcel): AudioWaveFormData {
    return AudioWaveFormData.ADAPTER.decode(requireNotNull(ParcelUtil.readByteArray(parcel)))
  }

  override fun AudioWaveFormData.write(parcel: Parcel, flags: Int) {
    ParcelUtil.writeByteArray(parcel, encode())
  }
}
