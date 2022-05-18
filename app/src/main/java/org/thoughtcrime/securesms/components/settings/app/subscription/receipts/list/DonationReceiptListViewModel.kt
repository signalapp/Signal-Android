package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.util.InternetConnectionObserver

class DonationReceiptListViewModel(private val repository: DonationReceiptListRepository) : ViewModel() {

  private val disposables = CompositeDisposable()
  private val internalState = MutableLiveData<List<DonationReceiptBadge>>(emptyList())
  private var networkDisposable: Disposable

  val state: LiveData<List<DonationReceiptBadge>> = internalState

  init {
    networkDisposable = InternetConnectionObserver
      .observe()
      .distinctUntilChanged()
      .subscribe { isConnected ->
        if (isConnected) {
          retry()
        }
      }

    refresh()
  }

  private fun retry() {
    if (internalState.value?.isEmpty() == true) {
      refresh()
    }
  }

  private fun refresh() {
    disposables.clear()
    disposables += repository.getBadges().subscribe { badges ->
      internalState.postValue(badges)
    }
  }

  override fun onCleared() {
    disposables.clear()
    networkDisposable.dispose()
  }

  class Factory(private val repository: DonationReceiptListRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(DonationReceiptListViewModel(repository)) as T
    }
  }
}
