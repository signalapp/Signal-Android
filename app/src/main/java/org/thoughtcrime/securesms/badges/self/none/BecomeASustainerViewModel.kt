package org.thoughtcrime.securesms.badges.self.none

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.util.livedata.Store

class BecomeASustainerViewModel : ViewModel() {

  private val store = Store(BecomeASustainerState())

  val state: LiveData<BecomeASustainerState> = store.stateLiveData

  private val disposables = CompositeDisposable()

  init {
    disposables += RecurringInAppPaymentRepository.getSubscriptions().subscribeBy(
      onError = { Log.w(TAG, "Could not load subscriptions.") },
      onSuccess = { subscriptions ->
        store.update {
          it.copy(badge = subscriptions.firstOrNull()?.badge)
        }
      }
    )
  }

  override fun onCleared() {
    disposables.clear()
  }

  companion object {
    private val TAG = Log.tag(BecomeASustainerViewModel::class.java)
  }
}
