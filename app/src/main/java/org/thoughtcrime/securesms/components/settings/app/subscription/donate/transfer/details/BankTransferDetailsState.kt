/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.details

import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.BankDetailsValidator

data class BankTransferDetailsState(
  val name: String = "",
  val nameFocusState: FocusState = FocusState.NOT_FOCUSED,
  val iban: String = "",
  val email: String = "",
  val emailFocusState: FocusState = FocusState.NOT_FOCUSED,
  val ibanValidity: IBANValidator.Validity = IBANValidator.Validity.POTENTIALLY_VALID,
  val displayFindAccountInfoSheet: Boolean = false
) {
  val canProceed = BankDetailsValidator.validName(name) && BankDetailsValidator.validEmail(email) && ibanValidity == IBANValidator.Validity.COMPLETELY_VALID

  fun showNameError(): Boolean {
    return nameFocusState == FocusState.LOST_FOCUS && !BankDetailsValidator.validName(name)
  }

  fun showEmailError(): Boolean {
    return emailFocusState == FocusState.LOST_FOCUS && !BankDetailsValidator.validEmail(email)
  }

  fun asSEPADebitData(): StripeApi.SEPADebitData {
    return StripeApi.SEPADebitData(
      iban = iban.trim(),
      name = name.trim(),
      email = email.trim()
    )
  }

  enum class FocusState {
    NOT_FOCUSED,
    FOCUSED,
    LOST_FOCUS
  }
}
