/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.util

import java.util.Locale
import kotlin.time.Duration

/**
 * Renders a media duration as a clock, e.g. "0:04" or "1:02:03". Hours are dropped below an hour so the
 * short clips that dominate the send flow read as minutes and seconds.
 */
internal fun Duration.formatAsClock(): String {
  return toComponents { hours, minutes, seconds, _ ->
    if (hours > 0) {
      String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
      String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
  }
}
