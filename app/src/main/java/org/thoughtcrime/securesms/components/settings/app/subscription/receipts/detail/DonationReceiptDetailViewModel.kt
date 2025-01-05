package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.util.livedata.Store

class DonationReceiptDetailViewModel(id: Long, private val repository: DonationReceiptDetailRepository) : ViewModel() {

  private val store = Store(DonationReceiptDetailState())
  private val disposables = CompositeDisposable()
  private val cachedRecord: Single<InAppPaymentReceiptRecord> = repository.getDonationReceiptRecord(id).cache()

  val state: LiveData<DonationReceiptDetailState> = store.stateLiveData

  init {
    refresh()
  }

  private fun refresh() {
    disposables.clear()

    disposables += cachedRecord.subscribe { record ->
      store.update { it.copy(inAppPaymentReceiptRecord = record) }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  class Factory(private val id: Long, private val repository: DonationReceiptDetailRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(DonationReceiptDetailViewModel(id, repository)) as T
    }
  }
}
