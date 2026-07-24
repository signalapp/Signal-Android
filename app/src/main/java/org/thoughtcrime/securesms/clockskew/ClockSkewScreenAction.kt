/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.clockskew

/**
 * One-shot side effects emitted by [ClockSkewViewModel] for the host [ClockSkewActivity] to perform, since they require
 * an Activity context.
 */
sealed interface ClockSkewScreenAction {
  /** Open the system date and time settings. */
  data object OpenDateSettings : ClockSkewScreenAction

  /** Skew has been resolved; the blocking screen should close. */
  data object Finish : ClockSkewScreenAction
}
