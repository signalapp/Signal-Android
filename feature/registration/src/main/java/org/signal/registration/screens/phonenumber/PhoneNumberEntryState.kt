/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

import org.signal.core.util.censor
import org.signal.network.api.RegistrationApiV2.SessionMetadata
import org.signal.network.api.RegistrationApiV2.SvrCredentials
import org.signal.registration.PendingRestoreOption
import org.signal.registration.PreExistingRegistrationData
import org.signal.registration.VerificationCodeRequest
import org.signal.registration.screens.shared.AccountIdError
import org.signal.registration.screens.shared.AccountIdFormat
import kotlin.time.Duration

data class PhoneNumberEntryState(
  val regionCode: String = "",
  val countryCode: String = "",
  val countryName: String = "",
  val countryEmoji: String = "",
  val nationalNumber: String = "",
  val formattedNumber: String = "",
  /** Read [enteredAccountId] for now, as it guards stuff behind the feature flag. Can remove this indirection when we launch. */
  private val accountId: String? = null,
  /** Why the entered [accountId] can't be submitted, if it can't be. Null while the user is still partway through one. */
  val accountIdError: AccountIdError? = null,
  val sessionE164: String? = null,
  val sessionMetadata: SessionMetadata? = null,
  val smsVerificationCodeRequest: VerificationCodeRequest? = null,
  val showSpinner: Boolean = false,
  val dialogs: Dialogs = Dialogs(),
  val preExistingRegistrationData: PreExistingRegistrationData? = null,
  val restoredSvrCredentials: List<SvrCredentials> = emptyList(),
  val pendingRestoreOption: PendingRestoreOption? = null,
  /** Whether the user saw the archive restore selection screen. */
  val sawArchiveRestoreSelectionScreen: Boolean = false,
  val initialized: Boolean = false,
  /** Whether the entered number has a plausible length for the selected country code. */
  val isNumberPossible: Boolean = false,
  /** Whether the entered number is definitively invalid. A still-too-short number is not considered invalid, since the user may simply be mid-entry. */
  val isNumberInvalid: Boolean = false,
  /** Gates whether the link device option is shown in the overflow menu. */
  val isLinkAndSyncAvailable: Boolean = false,
  val isPhoneNumberlessRegistrationAvailable: Boolean = false
) {

  /** The account ID being entered, or null when the field holds a phone number.*/
  val enteredAccountId: String?
    get() = if (isPhoneNumberlessRegistrationAvailable) {
      accountId
    } else {
      null
    }

  /** Whether what has been entered is complete enough to submit, be it a phone number or an account ID. */
  val isNextEnabled: Boolean
    get() {
      val accountId = enteredAccountId

      return when {
        showSpinner -> false
        accountId != null -> AccountIdFormat.toAciOrNull(accountId) != null
        else -> isNumberPossible
      }
    }

  override fun toString(): String = "PhoneNumberEntryState(regionCode=$regionCode, countryCode=$countryCode, countryName=$countryName, countryEmoji=$countryEmoji, nationalNumber=${nationalNumber.censor()}, formattedNumber=${formattedNumber.censor()}, accountId=${accountId?.censor()}, accountIdError=$accountIdError, sessionE164=$sessionE164, sessionMetadata=$sessionMetadata, smsVerificationCodeRequest=$smsVerificationCodeRequest, showSpinner=$showSpinner, dialogs=$dialogs, preExistingRegistrationData=${preExistingRegistrationData?.let { "present" }}, restoredSvrCredentials=${restoredSvrCredentials.size} items, pendingRestoreOption=$pendingRestoreOption, sawArchiveRestoreSelectionScreen=$sawArchiveRestoreSelectionScreen, initialized=$initialized, isNumberPossible=$isNumberPossible, isNumberInvalid=$isNumberInvalid,  isLinkAndSyncAvailable=$isLinkAndSyncAvailable, isPhoneNumberlessRegistrationAvailable=$isPhoneNumberlessRegistrationAvailable)"

  data class Dialogs(
    /** Asks the user to confirm the number they entered before submitting it. */
    val confirmNumber: Boolean = false,
    val networkError: Boolean = false,
    val unknownError: Boolean = false,
    /** When non-null, shows a rate limit error dialog. A non-positive duration indicates the server didn't say how long to wait. */
    val rateLimitedRetryAfter: Duration? = null,
    val unableToSendSms: Boolean = false,
    val couldNotRequestCodeWithSelectedTransport: Boolean = false,
    val invalidPhoneNumber: Boolean = false
  )
}
