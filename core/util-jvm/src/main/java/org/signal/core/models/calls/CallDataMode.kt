/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.calls

/**
 * Represents the user's desired data mode for calls.
 */
enum class CallDataMode(val code: Int) {
  LOW_ALWAYS(0),
  HIGH_ON_WIFI(1),
  HIGH_ALWAYS(2);

  companion object {
    @JvmStatic
    fun fromCode(code: Int): CallDataMode {
      return when (code) {
        1 -> HIGH_ON_WIFI
        2 -> HIGH_ALWAYS
        else -> LOW_ALWAYS
      }
    }
  }
}
