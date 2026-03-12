/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.util

import org.signal.registration.BuildConfig

open class DebugLoggableModel : DebugLoggable {
  override fun toString(): String {
    return if (BuildConfig.DEBUG) {
      toDebugString()
    } else {
      toSafeString()
    }
  }
}