/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.clockskew

/**
 * Events emitted by [ClockSkewScreen] and handled by [ClockSkewViewModel].
 */
sealed interface ClockSkewScreenEvent {
  /** The screen became visible; the displayed device time should be recomputed. */
  data object ScreenResumed : ClockSkewScreenEvent

  /** The user tapped the button to adjust their device date. */
  data object AdjustDateSelected : ClockSkewScreenEvent

  /** Internal: the [ClockSkewDetector]'s detection state changed. */
  data class SkewStateChanged(val detected: Boolean) : ClockSkewScreenEvent
}
