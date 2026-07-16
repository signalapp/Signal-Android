/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.content.Context
import androidx.annotation.StringRes

/**
 * Quality levels to send media at.
 */
enum class SentMediaQuality(@JvmField val code: Int, @param:StringRes private val label: Int) {
  STANDARD(0, R.string.SentMediaQuality__standard),
  HIGH(1, R.string.SentMediaQuality__high);

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
