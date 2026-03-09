package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.DonationErrorValue
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingOneTimeDonation
import org.thoughtcrime.securesms.jobs.InAppPaymentKeepAliveJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.InternetConnectionObserver
import org.thoughtcrime.securesms.util.livedata.Store
import kotlin.time.Duration.Companion.milliseconds

class ManageDonationsViewModel : ViewModel() {

  private val store = Store(ManageDonationsState())
  private val disposables = CompositeDisposable()
  private val networkDisposable: Disposable

  val state: LiveData<ManageDonationsState> = store.stateLiveData
  private val internalDisplayThanksBottomSheetPulse = MutableSharedFlow<Badge>()

  val displayThanksBottomSheetPulse: SharedFlow<Badge> = internalDisplayThanksBottomSheetPulse

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

    viewModelScope.launch {
      ManageDonationsRepository.consumeSuccessfulIdealPayments()
        .collectLatest {
          internalDisplayThanksBottomSheetPulse.emit(Badges.fromDatabaseBadge(it.data.badge!!))
        }
    }

    viewModelScope.launch(Dispatchers.IO) {
      updateRecurringDonationState()

      InAppPaymentsRepository.observeInAppPaymentRedemption(InAppPaymentType.RECURRING_DONATION)
        .asFlow()
        .collect { redemptionStatus ->
          val latestPayment = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_DONATION)

          val activeSubscription: InAppPaymentTable.InAppPayment? = latestPayment?.let {
            if (it.data.cancellation == null) it else null
          }

          store.update { manageDonationsState ->
            manageDonationsState.copy(
              nonVerifiedMonthlyDonation = if (redemptionStatus is DonationRedemptionJobStatus.PendingExternalVerification) redemptionStatus.nonVerifiedMonthlyDonation else null,
              subscriptionRedemptionState = deriveRedemptionState(redemptionStatus, latestPayment),
              activeSubscription = activeSubscription
            )
          }
        }
    }

    viewModelScope.launch(Dispatchers.IO) {
      InAppPaymentsRepository.observeInAppPaymentRedemption(InAppPaymentType.ONE_TIME_DONATION)
        .asFlow()
        .collect { redemptionStatus ->
          val pendingOneTimeDonation = when (redemptionStatus) {
            is DonationRedemptionJobStatus.PendingExternalVerification -> redemptionStatus.pendingOneTimeDonation
            DonationRedemptionJobStatus.PendingReceiptRedemption,
            DonationRedemptionJobStatus.PendingReceiptRequest -> {
              val latestPayment = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.ONE_TIME_DONATION)
              latestPayment?.toPendingOneTimeDonation()
            }
            else -> null
          }

          store.update { it.copy(pendingOneTimeDonation = pendingOneTimeDonation) }
        }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun retry() {
    if (!disposables.isDisposed && store.state.networkError) {
      store.update { it.copy(networkError = false) }
      refresh()
    }
  }

  fun refresh() {
    disposables.clear()

    InAppPaymentKeepAliveJob.enqueueAndTrackTime(System.currentTimeMillis().milliseconds)

    disposables += Single.fromCallable {
      InAppPaymentsRepository.getShouldCancelSubscriptionBeforeNextSubscribeAttempt(InAppPaymentSubscriberRecord.Type.DONATION)
    }.subscribeOn(Schedulers.io()).subscribeBy(
      onSuccess = { requiresCancel ->
        store.update {
          it.copy(subscriberRequiresCancel = requiresCancel)
        }
      },
      onError = { throwable ->
        Log.w(TAG, "Error retrieving cancel state", throwable)
        store.update {
          it.copy(networkError = true)
        }
      }
    )

    disposables += Recipient.observable(Recipient.self().id).map { it.badges }.subscribeBy { badges ->
      store.update { state ->
        state.copy(
          hasOneTimeBadge = badges.any { it.isBoost() }
        )
      }
    }

    disposables += Single.fromCallable { SignalDatabase.donationReceipts.hasReceipts() }.subscribeOn(Schedulers.io()).subscribe { hasReceipts ->
      store.update { it.copy(hasReceipts = hasReceipts) }
    }

    disposables += RecurringInAppPaymentRepository.getSubscriptions().subscribeBy(
      onSuccess = { subs ->
        store.update { it.copy(availableSubscriptions = subs) }
      },
      onError = {
        Log.w(TAG, "Error retrieving subscriptions data", it)
      }
    )
  }

  private fun updateRecurringDonationState() {
    val latestPayment = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_DONATION)

    val activeSubscription: InAppPaymentTable.InAppPayment? = latestPayment?.let {
      if (it.data.cancellation == null) it else null
    }

    store.update { manageDonationsState ->
      manageDonationsState.copy(
        isLoaded = true,
        activeSubscription = activeSubscription
      )
    }
  }

  private fun deriveRedemptionState(status: DonationRedemptionJobStatus, latestPayment: InAppPaymentTable.InAppPayment?): ManageDonationsState.RedemptionState {
    return when (status) {
      DonationRedemptionJobStatus.None -> ManageDonationsState.RedemptionState.NONE
      DonationRedemptionJobStatus.PendingKeepAlive -> ManageDonationsState.RedemptionState.SUBSCRIPTION_REFRESH
      DonationRedemptionJobStatus.FailedSubscription -> ManageDonationsState.RedemptionState.FAILED

      is DonationRedemptionJobStatus.PendingExternalVerification -> {
        if (latestPayment != null && (latestPayment.data.paymentMethodType == InAppPaymentData.PaymentMethodType.SEPA_DEBIT || latestPayment.data.paymentMethodType == InAppPaymentData.PaymentMethodType.IDEAL)) {
          ManageDonationsState.RedemptionState.IS_PENDING_BANK_TRANSFER
        } else {
          ManageDonationsState.RedemptionState.IN_PROGRESS
        }
      }

      DonationRedemptionJobStatus.PendingReceiptRedemption,
      DonationRedemptionJobStatus.PendingReceiptRequest -> ManageDonationsState.RedemptionState.IN_PROGRESS
    }
  }

  private fun InAppPaymentTable.InAppPayment.toPendingOneTimeDonation(): PendingOneTimeDonation? {
    if (type.recurring || data.amount == null || data.badge == null) {
      return null
    }

    return PendingOneTimeDonation(
      paymentMethodType = when (data.paymentMethodType) {
        InAppPaymentData.PaymentMethodType.UNKNOWN -> PendingOneTimeDonation.PaymentMethodType.CARD
        InAppPaymentData.PaymentMethodType.GOOGLE_PAY -> PendingOneTimeDonation.PaymentMethodType.CARD
        InAppPaymentData.PaymentMethodType.CARD -> PendingOneTimeDonation.PaymentMethodType.CARD
        InAppPaymentData.PaymentMethodType.SEPA_DEBIT -> PendingOneTimeDonation.PaymentMethodType.SEPA_DEBIT
        InAppPaymentData.PaymentMethodType.IDEAL -> PendingOneTimeDonation.PaymentMethodType.IDEAL
        InAppPaymentData.PaymentMethodType.PAYPAL -> PendingOneTimeDonation.PaymentMethodType.PAYPAL
        InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING -> error("Not supported.")
      },
      amount = data.amount,
      badge = data.badge,
      timestamp = insertedAt.inWholeMilliseconds,
      error = data.error?.takeIf { it.data_ != InAppPaymentKeepAliveJob.KEEP_ALIVE }?.let {
        DonationErrorValue(
          type = when (it.type) {
            InAppPaymentData.Error.Type.REDEMPTION -> DonationErrorValue.Type.REDEMPTION
            InAppPaymentData.Error.Type.PAYMENT_PROCESSING -> DonationErrorValue.Type.PAYMENT
            else -> DonationErrorValue.Type.PAYMENT
          }
        )
      }
    )
  }

  companion object {
    private val TAG = Log.tag(ManageDonationsViewModel::class.java)
  }
}
