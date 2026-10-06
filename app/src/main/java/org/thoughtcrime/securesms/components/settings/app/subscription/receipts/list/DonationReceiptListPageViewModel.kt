package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord

class DonationReceiptListPageViewModel(type: InAppPaymentReceiptRecord.Type?) : ViewModel() {
  val state: StateFlow<DonationReceiptListPageState> = flow { emit(DonationReceiptListPageRepository.getRecords(type)) }
    .map { DonationReceiptListPageState(records = it, isLoaded = true) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, DonationReceiptListPageState())
}
