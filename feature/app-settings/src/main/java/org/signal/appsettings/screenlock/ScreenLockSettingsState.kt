/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.screenlock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Everything [ScreenLockSettingsScreen] needs to render.
 */
data class ScreenLockSettingsState(
  val isScreenLockEnabled: Boolean = false,
  val timeout: Duration = Duration.ZERO,
  val canAuthenticate: Boolean = true,
  val dialog: Dialog = Dialog.None
) {

  companion object {
    /** The timeouts offered as one tap options. Anything else is a custom timeout. */
    val PRESET_TIMEOUTS: List<Duration> = listOf(1.minutes, 5.minutes, 30.minutes)
  }

  val isCustomTimeout: Boolean
    get() = timeout !in PRESET_TIMEOUTS

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    /** Lets the user pick a timeout that isn't one of the presets. */
    data object CustomTimeout : Dialog
  }
}
