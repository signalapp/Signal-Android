package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.database.model.DonationReceiptRecord

class DonationReceiptListPageViewModel(type: DonationReceiptRecord.Type?, repository: DonationReceiptListPageRepository) : ViewModel() {

  private val disposables = CompositeDisposable()
  private val internalState = MutableLiveData<List<DonationReceiptRecord>>()

  val state: LiveData<List<DonationReceiptRecord>> = internalState

  init {
    disposables += repository.getRecords(type)
      .subscribe { records ->
        internalState.postValue(records)
      }
  }

  override fun onCleared() {
    disposables.clear()
  }

  class Factory(private val type: DonationReceiptRecord.Type?, private val repository: DonationReceiptListPageRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(DonationReceiptListPageViewModel(type, repository)) as T
    }
  }
}
