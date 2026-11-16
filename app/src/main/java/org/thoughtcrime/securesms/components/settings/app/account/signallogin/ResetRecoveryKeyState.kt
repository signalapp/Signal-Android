/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin

/**
 * Tracks where the user is in the confirmation flow that precedes a recovery key reset.
 *
 * [hasResetPermitsRemaining] is null until the server tells us how many resets the user has left.
 */
data class ResetRecoveryKeyState(
  val dialog: Dialog = Dialog.NONE,
  val hasResetPermitsRemaining: Boolean? = null,
  val areBackupsEnabled: Boolean = false
) {
  enum class Dialog {
    NONE,

    /** Sheet explaining what a reset entails. */
    CONFIRMATION,

    /** Storage optimization has to be turned off before the key can be reset. */
    DOWNLOAD_MEDIA,

    /** The user has used up their resets for now. */
    KEY_LIMIT_REACHED
  }
}
