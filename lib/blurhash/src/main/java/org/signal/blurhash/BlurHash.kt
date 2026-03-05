package org.signal.blurhash

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A BlurHash is a compact string representation of a blurred image that we can use to show fast
 * image previews.
 */
@Parcelize
data class BlurHash(val hash: String) : Parcelable {
  companion object {
    fun parseOrNull(hash: String?): BlurHash? {
      if (Base83.isValid(hash)) {
        return BlurHash(hash!!)
      }
      return null
    }
  }
}
