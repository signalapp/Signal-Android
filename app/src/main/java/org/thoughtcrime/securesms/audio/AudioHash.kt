package org.thoughtcrime.securesms.audio

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import org.signal.core.util.Base64
import org.signal.core.util.ParcelUtil
import org.thoughtcrime.securesms.database.model.databaseprotos.AudioWaveFormData
import java.io.IOException

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
  }

  constructor(audioWaveForm: AudioWaveFormData) : this(Base64.encodeWithPadding(audioWaveForm.encode()), audioWaveForm)

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
