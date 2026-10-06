package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DonationReceiptDetailViewModel(id: Long) : ViewModel() {
  val state: StateFlow<DonationReceiptDetailState> = flow { emit(DonationReceiptDetailRepository.getDonationReceiptRecord(id)) }
    .map { record -> DonationReceiptDetailState(inAppPaymentReceiptRecord = record) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, DonationReceiptDetailState())
}
