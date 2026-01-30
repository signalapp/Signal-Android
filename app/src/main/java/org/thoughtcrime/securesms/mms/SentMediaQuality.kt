package org.thoughtcrime.securesms.mms

import android.content.Context
import androidx.annotation.StringRes
import org.thoughtcrime.securesms.R

/**
 * Quality levels to send media at.
 */
enum class SentMediaQuality(@JvmField val code: Int, @param:StringRes private val label: Int) {
  STANDARD(0, R.string.DataAndStorageSettingsFragment__standard),
  HIGH(1, R.string.DataAndStorageSettingsFragment__high);

  companion object {
    @JvmStatic
    fun fromCode(code: Int): SentMediaQuality {
      return if (HIGH.code == code) {
        HIGH
      } else {
        STANDARD
      }
    }

    fun getLabels(context: Context): Array<String> {
      return entries.map { context.getString(it.label) }.toTypedArray()
    }
  }
}
