package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.thoughtcrime.securesms.components.settings.app.subscription.StripeRepository
import org.thoughtcrime.securesms.util.rx.RxStore

class GatewaySelectorViewModel(
  args: GatewaySelectorBottomSheetArgs,
  private val repository: StripeRepository
) : ViewModel() {

  private val store = RxStore(GatewaySelectorState(args.request.badge))
  private val disposables = CompositeDisposable()

  val state = store.stateFlowable

  init {
    checkIfGooglePayIsAvailable()
  }

  override fun onCleared() {
    store.dispose()
    disposables.clear()
  }

  private fun checkIfGooglePayIsAvailable() {
    disposables += repository.isGooglePayAvailable().subscribeBy(
      onComplete = {
        store.update { it.copy(isGooglePayAvailable = true) }
      },
      onError = {
        store.update { it.copy(isGooglePayAvailable = false) }
      }
    )
  }

  class Factory(
    private val args: GatewaySelectorBottomSheetArgs,
    private val repository: StripeRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(GatewaySelectorViewModel(args, repository)) as T
    }
  }
}
