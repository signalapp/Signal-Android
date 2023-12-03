/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.ideal

import org.signal.donations.StripeApi

data class IdealTransferDetailsState(
  val idealBank: IdealBank? = null,
  val name: String = "",
  val email: String = ""
) {
  fun asIDEALData(): StripeApi.IDEALData {
    return StripeApi.IDEALData(
      bank = idealBank!!.code,
      name = name.trim(),
      email = email.trim()
    )
  }

  fun canProceed(): Boolean {
    return idealBank != null && name.isNotBlank() && email.isNotBlank()
  }
}
