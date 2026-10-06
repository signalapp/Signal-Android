package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.util.InternetConnectionObserver

class DonationReceiptListViewModel : ViewModel() {

  private val internalState = MutableStateFlow<List<DonationReceiptBadge>>(emptyList())
  private var networkDisposable: Disposable
  private var getBadgesJob: Job? = null

  val state: StateFlow<List<DonationReceiptBadge>> = internalState

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
    if (internalState.value.isEmpty()) {
      refresh()
    }
  }

  private fun refresh() {
    getBadgesJob?.cancel()
    getBadgesJob = viewModelScope.launch {
      internalState.value = DonationReceiptListRepository.getBadges()
    }
  }

  override fun onCleared() {
    networkDisposable.dispose()
  }
}
