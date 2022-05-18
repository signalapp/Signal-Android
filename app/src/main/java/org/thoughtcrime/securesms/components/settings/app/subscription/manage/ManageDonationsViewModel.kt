package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.subscription.SubscriptionsRepository
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.subscription.LevelUpdate
import org.thoughtcrime.securesms.util.InternetConnectionObserver
import org.thoughtcrime.securesms.util.livedata.Store
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription

class ManageDonationsViewModel(
  private val subscriptionsRepository: SubscriptionsRepository
) : ViewModel() {

  private val store = Store(ManageDonationsState())
  private val disposables = CompositeDisposable()
  private val networkDisposable: Disposable

  val state: LiveData<ManageDonationsState> = store.stateLiveData

  init {
    store.update(Recipient.self().live().liveDataResolved) { self, state ->
      state.copy(featuredBadge = self.featuredBadge)
    }

    networkDisposable = InternetConnectionObserver
      .observe()
      .distinctUntilChanged()
      .subscribe { isConnected ->
        if (isConnected) {
          retry()
        }
      }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun retry() {
    if (!disposables.isDisposed && store.state.transactionState == ManageDonationsState.TransactionState.NetworkFailure) {
      store.update { it.copy(transactionState = ManageDonationsState.TransactionState.Init) }
      refresh()
    }
  }

  fun refresh() {
    disposables.clear()

    val levelUpdateOperationEdges: Observable<Boolean> = LevelUpdate.isProcessing.distinctUntilChanged()
    val activeSubscription: Single<ActiveSubscription> = subscriptionsRepository.getActiveSubscription()

    disposables += SubscriptionRedemptionJobWatcher.watch().subscribeBy { jobStateOptional ->
      store.update { manageDonationsState ->
        manageDonationsState.copy(
          subscriptionRedemptionState = jobStateOptional.map { jobState ->
            when (jobState) {
              JobTracker.JobState.PENDING -> ManageDonationsState.SubscriptionRedemptionState.IN_PROGRESS
              JobTracker.JobState.RUNNING -> ManageDonationsState.SubscriptionRedemptionState.IN_PROGRESS
              JobTracker.JobState.SUCCESS -> ManageDonationsState.SubscriptionRedemptionState.NONE
              JobTracker.JobState.FAILURE -> ManageDonationsState.SubscriptionRedemptionState.FAILED
              JobTracker.JobState.IGNORED -> ManageDonationsState.SubscriptionRedemptionState.NONE
            }
          }.orElse(ManageDonationsState.SubscriptionRedemptionState.NONE)
        )
      }
    }

    disposables += levelUpdateOperationEdges.flatMapSingle { isProcessing ->
      if (isProcessing) {
        Single.just(ManageDonationsState.TransactionState.InTransaction)
      } else {
        activeSubscription.map { ManageDonationsState.TransactionState.NotInTransaction(it) }
      }
    }.subscribeBy(
      onNext = { transactionState ->
        store.update {
          it.copy(transactionState = transactionState)
        }
      },
      onError = { throwable ->
        Log.w(TAG, "Error retrieving subscription transaction state", throwable)

        store.update {
          it.copy(transactionState = ManageDonationsState.TransactionState.NetworkFailure)
        }
      }
    )

    disposables += subscriptionsRepository.getSubscriptions().subscribeBy(
      onSuccess = { subs ->
        store.update { it.copy(availableSubscriptions = subs) }
      },
      onError = {
        Log.w(TAG, "Error retrieving subscriptions data", it)
      }
    )
  }

  class Factory(
    private val subscriptionsRepository: SubscriptionsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(ManageDonationsViewModel(subscriptionsRepository))!!
    }
  }

  companion object {
    private val TAG = Log.tag(ManageDonationsViewModel::class.java)
  }
}
