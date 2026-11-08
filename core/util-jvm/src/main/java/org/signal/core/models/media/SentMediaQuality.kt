/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.media

/**
 * Quality levels to send media at.
 */
enum class SentMediaQuality(@JvmField val code: Int) {
  STANDARD(0),
  HIGH(1);

  companion object {
    @JvmStatic
    fun fromCode(code: Int): SentMediaQuality {
      return if (HIGH.code == code) {
        HIGH
      } else {
        STANDARD
      }
    }
  }
}
