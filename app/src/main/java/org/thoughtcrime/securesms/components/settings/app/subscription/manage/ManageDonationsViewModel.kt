package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.subscription.SubscriptionsRepository
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.subscription.LevelUpdate
import org.thoughtcrime.securesms.util.livedata.Store
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription

class ManageDonationsViewModel(
  private val subscriptionsRepository: SubscriptionsRepository
) : ViewModel() {

  private val store = Store(ManageDonationsState())
  private val eventPublisher = PublishSubject.create<ManageDonationsEvent>()
  private val disposables = CompositeDisposable()

  val state: LiveData<ManageDonationsState> = store.stateLiveData
  val events: Observable<ManageDonationsEvent> = eventPublisher.observeOn(AndroidSchedulers.mainThread())

  init {
    store.update(Recipient.self().live().liveDataResolved) { self, state ->
      state.copy(featuredBadge = self.featuredBadge)
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun refresh() {
    disposables.clear()

    val levelUpdateOperationEdges: Observable<Boolean> = LevelUpdate.isProcessing.distinctUntilChanged()
    val activeSubscription: Single<ActiveSubscription> = subscriptionsRepository.getActiveSubscription()

    disposables += SubscriptionRedemptionJobWatcher.watch().subscribeBy { jobStateOptional ->
      store.update { manageDonationsState ->
        manageDonationsState.copy(
          subscriptionRedemptionState = jobStateOptional.transform { jobState ->
            when (jobState) {
              JobTracker.JobState.PENDING -> ManageDonationsState.SubscriptionRedemptionState.IN_PROGRESS
              JobTracker.JobState.RUNNING -> ManageDonationsState.SubscriptionRedemptionState.IN_PROGRESS
              JobTracker.JobState.SUCCESS -> ManageDonationsState.SubscriptionRedemptionState.NONE
              JobTracker.JobState.FAILURE -> ManageDonationsState.SubscriptionRedemptionState.FAILED
              JobTracker.JobState.IGNORED -> ManageDonationsState.SubscriptionRedemptionState.NONE
            }
          }.or(ManageDonationsState.SubscriptionRedemptionState.NONE)
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

        if (transactionState is ManageDonationsState.TransactionState.NotInTransaction && transactionState.activeSubscription.activeSubscription == null) {
          eventPublisher.onNext(ManageDonationsEvent.NOT_SUBSCRIBED)
        }
      },
      onError = {
        eventPublisher.onNext(ManageDonationsEvent.ERROR_GETTING_SUBSCRIPTION)
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
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(ManageDonationsViewModel(subscriptionsRepository))!!
    }
  }

  companion object {
    private val TAG = Log.tag(ManageDonationsViewModel::class.java)
  }
}
