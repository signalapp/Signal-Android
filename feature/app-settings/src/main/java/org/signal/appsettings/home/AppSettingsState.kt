/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.home

import androidx.compose.runtime.Immutable
import org.signal.core.util.censor

/**
 * Everything [AppSettingsScreen] needs to render.
 */
@Immutable
data class AppSettingsState(
  val profile: Profile? = null,
  val isPrimaryDevice: Boolean = true,
  val unreadPaymentsCount: Int = 0,
  val hasExpiredGiftBadge: Boolean = false,
  val allowUserToGoToDonationManagementScreen: Boolean = false,
  val userUnregistered: Boolean = false,
  val clientDeprecated: Boolean = false,
  val showInternalPreferences: Boolean = false,
  val showPayments: Boolean = false,
  val showAppUpdates: Boolean = false,
  val backupFailureState: BackupFailureState = BackupFailureState.NONE,
  val snackbar: Snackbar = Snackbar.None
) {

  /** Most rows need a working registration, so they're disabled when unregistered or deprecated. */
  val isRegisteredAndUpToDate: Boolean
    get() = !userUnregistered && !clientDeprecated

  /** What's shown about the user at the top of the screen. The avatar is drawn by the host. */
  data class Profile(
    val name: String,
    val formattedPhoneNumber: String = "",
    val username: String = "",
    val about: String? = null
  ) {
    override fun toString(): String = "Profile(name=${name.censor()}, formattedPhoneNumber=${formattedPhoneNumber.censor()}, username=${username.censor()}, about=${about?.censor()})"
  }

  /** Whichever snackbar the screen is showing, if any. */
  sealed interface Snackbar {
    data object None : Snackbar

    /** The donor subscriber id was copied to the clipboard. */
    data object CopiedDonorSubscriberId : Snackbar

    /** The backups subscriber id was copied to the clipboard. */
    data object CopiedBackupsSubscriberId : Snackbar
  }
}
