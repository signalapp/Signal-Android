/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.deleteaccount

import org.signal.core.util.censor

/**
 * Everything [DeleteAccountWithNumberScreen] and [DeleteAccountWithoutNumberScreen] need to render. Which of the two
 * is shown comes down to [hasPhoneNumber], and the fields each one leans on are called out below.
 */
data class DeleteAccountState(
  /** Whether this account has a phone number, which is what decides which of the two screens the user sees. */
  val hasPhoneNumber: Boolean = true,
  /** The region the phone number is being entered for, or "ZZ" while we don't know it. */
  val regionCode: String = UNKNOWN_REGION,
  /** The display name of [regionCode], which is empty until the user picks a country we recognize. */
  val countryDisplayName: String = "",
  /** The calling code, as digits, without the leading plus. */
  val countryCode: String = "",
  /** The national number as digits only, which is what we submit. */
  val nationalNumber: String = "",
  /** The national number as the user sees it, formatted for [regionCode]. */
  val formattedNumber: String = "",
  /** The user's username, or null when they don't have one. Only shown on [DeleteAccountWithoutNumberScreen]. */
  val username: String? = null,
  /** The user's payments balance, formatted for display, or null when they have nothing in there. */
  val walletBalance: String? = null,
  val dialog: Dialog = Dialog.None
) {

  override fun toString(): String = "DeleteAccountState(hasPhoneNumber=$hasPhoneNumber, regionCode=$regionCode, countryDisplayName=$countryDisplayName, countryCode=$countryCode, nationalNumber=${nationalNumber.censor()}, formattedNumber=${formattedNumber.censor()}, username=${username?.censor()}, walletBalance=${walletBalance?.censor()}, dialog=$dialog)"

  /** Whichever dialog the screen is showing, if any. Only one is ever up at a time. */
  sealed interface Dialog {
    data object None : Dialog

    /** The number the user entered isn't the one on this account, so there's nothing to confirm. */
    data object NumberDoesNotMatch : Dialog

    /** Asks the user to confirm that they really do want their account deleted. */
    data object ConfirmDeletion : Dialog

    /**
     * Asks a numberless user to confirm that they really do want their account deleted. They have no number to key in,
     * so instead they have to tick a box before we'll let them go ahead.
     */
    data class ConfirmNumberlessDeletion(val confirmationChecked: Boolean = false) : Dialog

    /** Deletion is underway and we're canceling the user's donation subscription. */
    data object CancelingSubscription : Dialog

    /** Deletion is underway and we're leaving the user's groups, [leaveCount] of [totalCount] done. */
    data class LeavingGroups(val totalCount: Int, val leaveCount: Int) : Dialog

    /** Deletion is underway and we're removing the account itself along with everything on this device. */
    data object DeletingAccount : Dialog

    /** Something the network had to do didn't work, so the account is still there and the user can try again. */
    data object DeletionFailed : Dialog

    /** The account is gone but we couldn't wipe this device, which the user has to finish in system settings. */
    data object LocalDataDeletionFailed : Dialog
  }

  companion object {
    /** Matches PhoneNumberUtil's own private UNKNOWN_REGION. */
    const val UNKNOWN_REGION = "ZZ"
  }
}
