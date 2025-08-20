/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase

class DonationPendingBottomSheetViewModel(
  inAppPaymentId: InAppPaymentTable.InAppPaymentId
) : ViewModel() {

  private val internalInAppPayment = MutableStateFlow<InAppPaymentTable.InAppPayment?>(null)
  val inAppPayment: StateFlow<InAppPaymentTable.InAppPayment?> = internalInAppPayment

  init {
    viewModelScope.launch {
      val inAppPayment = withContext(SignalDispatchers.IO) {
        SignalDatabase.inAppPayments.getById(inAppPaymentId)!!
      }

      internalInAppPayment.update { inAppPayment }
    }
  }
}
