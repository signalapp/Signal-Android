package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.core.util.StringUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.core.util.money.PlatformCurrencyUtil
import org.signal.core.util.orNull
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatValue
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.OneTimeInAppPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.boost.Boost
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.DonationRedemptionJobStatus
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingOneTimeDonation
import org.thoughtcrime.securesms.database.model.isExpired
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.subscription.LevelUpdate
import org.thoughtcrime.securesms.subscription.Subscription
import org.thoughtcrime.securesms.util.InternetConnectionObserver
import org.thoughtcrime.securesms.util.rx.RxStore
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Optional

/**
 * Contains the logic to manage the UI of the unified donations screen.
 * Does not directly deal with performing payments, this ViewModel is
 * only in charge of rendering our "current view of the world."
 */
class DonateToSignalViewModel(
  startType: InAppPaymentType,
  private val oneTimeInAppPaymentRepository: OneTimeInAppPaymentRepository
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(DonateToSignalViewModel::class.java)
  }

  private val store = RxStore(DonateToSignalState(inAppPaymentType = startType))
  private val oneTimeDonationDisposables = CompositeDisposable()
  private val monthlyDonationDisposables = CompositeDisposable()
  private val networkDisposable = CompositeDisposable()
  private val actionDisposable = CompositeDisposable()
  private val _actions = PublishSubject.create<DonateToSignalAction>()
  private val _activeSubscription = PublishSubject.create<ActiveSubscription>()
  private val _inAppPaymentId = BehaviorProcessor.create<InAppPaymentTable.InAppPaymentId>()

  val state = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())
  val actions: Observable<DonateToSignalAction> = _actions.observeOn(AndroidSchedulers.mainThread())
  val inAppPaymentId: Flowable<InAppPaymentTable.InAppPaymentId> = _inAppPaymentId.onBackpressureLatest().distinctUntilChanged()

  init {
    initializeOneTimeDonationState(oneTimeInAppPaymentRepository)
    initializeMonthlyDonationState(RecurringInAppPaymentRepository)

    networkDisposable += InternetConnectionObserver
      .observe()
      .distinctUntilChanged()
      .subscribe { isConnected ->
        if (isConnected) {
          retryMonthlyDonationState()
          retryOneTimeDonationState()
        }
      }
  }

  fun retryMonthlyDonationState() {
    if (!monthlyDonationDisposables.isDisposed && store.state.monthlyDonationState.donationStage == DonateToSignalState.DonationStage.FAILURE) {
      store.update { it.copy(monthlyDonationState = it.monthlyDonationState.copy(donationStage = DonateToSignalState.DonationStage.INIT)) }
      initializeMonthlyDonationState(RecurringInAppPaymentRepository)
    }
  }

  fun retryOneTimeDonationState() {
    if (!oneTimeDonationDisposables.isDisposed && store.state.oneTimeDonationState.donationStage == DonateToSignalState.DonationStage.FAILURE) {
      store.update { it.copy(oneTimeDonationState = it.oneTimeDonationState.copy(donationStage = DonateToSignalState.DonationStage.INIT)) }
      initializeOneTimeDonationState(oneTimeInAppPaymentRepository)
    }
  }

  fun requestChangeCurrency() {
    val snapshot = store.state
    if (snapshot.canSetCurrency) {
      _actions.onNext(DonateToSignalAction.DisplayCurrencySelectionDialog(snapshot.inAppPaymentType, snapshot.selectableCurrencyCodes))
    }
  }

  fun requestSelectGateway() {
    val snapshot = store.state
    if (snapshot.areFieldsEnabled) {
      actionDisposable += createInAppPayment(snapshot).subscribeBy {
        _actions.onNext(DonateToSignalAction.DisplayGatewaySelectorDialog(it))
      }
    }
  }

  fun updateSubscription() {
    val snapshot = store.state

    check(snapshot.canUpdate)
    if (snapshot.areFieldsEnabled) {
      actionDisposable += createInAppPayment(snapshot).subscribeBy {
        _actions.onNext(DonateToSignalAction.UpdateSubscription(it, snapshot.isUpdateLongRunning))
      }
    }
  }

  fun cancelSubscription() {
    val snapshot = store.state
    if (snapshot.areFieldsEnabled) {
      _actions.onNext(DonateToSignalAction.CancelSubscription)
    }
  }

  fun toggleDonationType() {
    store.update {
      it.copy(
        inAppPaymentType = when (it.inAppPaymentType) {
          InAppPaymentType.ONE_TIME_DONATION -> InAppPaymentType.RECURRING_DONATION
          InAppPaymentType.RECURRING_DONATION -> InAppPaymentType.ONE_TIME_DONATION
          else -> error("Should never get here.")
        }
      )
    }
  }

  fun setSelectedSubscription(subscription: Subscription) {
    store.update { it.copy(monthlyDonationState = it.monthlyDonationState.copy(selectedSubscription = subscription)) }
  }

  fun setSelectedBoost(boost: Boost) {
    store.update { it.copy(oneTimeDonationState = it.oneTimeDonationState.copy(selectedBoost = boost, isCustomAmountFocused = false)) }
  }

  fun setCustomAmountFocused() {
    store.update { it.copy(oneTimeDonationState = it.oneTimeDonationState.copy(isCustomAmountFocused = true)) }
  }

  fun setCustomAmount(rawAmount: String) {
    val amount = StringUtil.stripBidiIndicator(rawAmount)
    val bigDecimalAmount: BigDecimal = if (amount.isEmpty() || amount == DecimalFormatSymbols.getInstance().decimalSeparator.toString()) {
      BigDecimal.ZERO
    } else {
      val decimalFormat = DecimalFormat.getInstance() as DecimalFormat
      decimalFormat.isParseBigDecimal = true

      try {
        decimalFormat.parse(amount) as BigDecimal
      } catch (e: NumberFormatException) {
        BigDecimal.ZERO
      }
    }

    store.update { it.copy(oneTimeDonationState = it.oneTimeDonationState.copy(customAmount = FiatMoney(bigDecimalAmount, it.oneTimeDonationState.customAmount.currency))) }
  }

  fun getSelectedSubscriptionCost(): FiatMoney {
    return store.state.monthlyDonationState.selectedSubscription!!.prices.first { it.currency == store.state.selectedCurrency }
  }

  fun refreshActiveSubscription() {
    RecurringInAppPaymentRepository
      .getActiveSubscription(InAppPaymentSubscriberRecord.Type.DONATION)
      .subscribeBy(
        onSuccess = {
          _activeSubscription.onNext(it)
        },
        onError = {
          _activeSubscription.onNext(ActiveSubscription.EMPTY)
        }
      )
  }

  private fun createInAppPayment(snapshot: DonateToSignalState): Single<InAppPaymentTable.InAppPayment> {
    val amount = getAmount(snapshot)

    return Single.fromCallable {
      SignalDatabase.inAppPayments.clearCreated()
      val id = SignalDatabase.inAppPayments.insert(
        type = snapshot.inAppPaymentType,
        state = InAppPaymentTable.State.CREATED,
        subscriberId = null,
        endOfPeriod = null,
        inAppPaymentData = InAppPaymentData(
          badge = snapshot.badge?.let { Badges.toDatabaseBadge(it) },
          amount = amount.toFiatValue(),
          level = snapshot.level.toLong(),
          recipientId = Recipient.self().id.serialize(),
          paymentMethodType = InAppPaymentData.PaymentMethodType.UNKNOWN,
          redemption = InAppPaymentData.RedemptionState(
            stage = InAppPaymentData.RedemptionState.Stage.INIT
          )
        )
      )

      _inAppPaymentId.onNext(id)
      SignalDatabase.inAppPayments.getById(id)!!
    }
  }

  private fun getAmount(snapshot: DonateToSignalState): FiatMoney {
    return when (snapshot.inAppPaymentType) {
      InAppPaymentType.ONE_TIME_DONATION -> getOneTimeAmount(snapshot.oneTimeDonationState)
      InAppPaymentType.RECURRING_DONATION -> getSelectedSubscriptionCost()
      else -> error("This ViewModel does not support ${snapshot.inAppPaymentType}.")
    }
  }

  private fun getOneTimeAmount(snapshot: DonateToSignalState.OneTimeDonationState): FiatMoney {
    return if (snapshot.isCustomAmountFocused) {
      snapshot.customAmount
    } else {
      snapshot.selectedBoost!!.price
    }
  }

  private fun initializeOneTimeDonationState(oneTimeInAppPaymentRepository: OneTimeInAppPaymentRepository) {
    val oneTimeDonationFromJob: Observable<Optional<PendingOneTimeDonation>> = InAppPaymentsRepository.observeInAppPaymentRedemption(InAppPaymentType.ONE_TIME_DONATION).map {
      when (it) {
        is DonationRedemptionJobStatus.PendingExternalVerification -> Optional.ofNullable(it.pendingOneTimeDonation)

        DonationRedemptionJobStatus.PendingReceiptRedemption,
        DonationRedemptionJobStatus.PendingReceiptRequest,
        DonationRedemptionJobStatus.FailedSubscription,
        DonationRedemptionJobStatus.None -> Optional.empty()
      }
    }.distinctUntilChanged()

    val oneTimeDonationFromStore: Observable<Optional<PendingOneTimeDonation>> = SignalStore.inAppPayments.observablePendingOneTimeDonation
      .map { pending -> pending.filter { !it.isExpired } }
      .distinctUntilChanged()

    oneTimeDonationDisposables += Observable
      .combineLatest(oneTimeDonationFromJob, oneTimeDonationFromStore) { job, store ->
        if (store.isPresent) {
          store
        } else {
          job
        }
      }
      .subscribe { pendingOneTimeDonation: Optional<PendingOneTimeDonation> ->
        store.update { it.copy(oneTimeDonationState = it.oneTimeDonationState.copy(pendingOneTimeDonation = pendingOneTimeDonation.orNull())) }
      }

    oneTimeDonationDisposables += oneTimeInAppPaymentRepository.getBoostBadge().subscribeBy(
      onSuccess = { badge ->
        store.update { it.copy(oneTimeDonationState = it.oneTimeDonationState.copy(badge = badge)) }
      },
      onError = {
        Log.w(TAG, "Could not load boost badge", it)
      }
    )

    oneTimeDonationDisposables += oneTimeInAppPaymentRepository.getMinimumDonationAmounts().subscribeBy(
      onSuccess = { amountMap ->
        store.update { it.copy(oneTimeDonationState = it.oneTimeDonationState.copy(minimumDonationAmounts = amountMap)) }
      },
      onError = {
        Log.w(TAG, "Could not load minimum custom donation amounts.", it)
      }
    )

    val boosts: Observable<Map<Currency, List<Boost>>> = oneTimeInAppPaymentRepository.getBoosts().toObservable()
    val oneTimeCurrency: Observable<Currency> = SignalStore.inAppPayments.observableOneTimeCurrency

    oneTimeDonationDisposables += Observable.combineLatest(boosts, oneTimeCurrency) { boostMap, currency ->
      val boostList = if (currency in boostMap) {
        boostMap[currency]!!
      } else {
        SignalStore.inAppPayments.setOneTimeCurrency(PlatformCurrencyUtil.USD)
        listOf()
      }

      Triple(boostList, currency, boostMap.keys)
    }.subscribeBy(
      onNext = { (boostList, currency, availableCurrencies) ->
        store.update { state ->
          state.copy(
            oneTimeDonationState = state.oneTimeDonationState.copy(
              boosts = boostList,
              selectedBoost = null,
              selectedCurrency = currency,
              donationStage = DonateToSignalState.DonationStage.READY,
              selectableCurrencyCodes = availableCurrencies.map(Currency::getCurrencyCode),
              isCustomAmountFocused = false,
              customAmount = FiatMoney(
                BigDecimal.ZERO,
                currency
              )
            )
          )
        }
      },
      onError = {
        Log.w(TAG, "Could not load boost information", it)
        store.update { it.copy(oneTimeDonationState = it.oneTimeDonationState.copy(donationStage = DonateToSignalState.DonationStage.FAILURE)) }
      }
    )
  }

  private fun initializeMonthlyDonationState(subscriptionsRepository: RecurringInAppPaymentRepository) {
    monitorLevelUpdateProcessing()

    val allSubscriptions = subscriptionsRepository.getSubscriptions()
    ensureValidSubscriptionCurrency(allSubscriptions)
    monitorSubscriptionCurrency()
    monitorSubscriptionState(allSubscriptions)
    refreshActiveSubscription()
  }

  private fun monitorLevelUpdateProcessing() {
    val redemptionJobStatus: Observable<DonationRedemptionJobStatus> = InAppPaymentsRepository.observeInAppPaymentRedemption(InAppPaymentType.RECURRING_DONATION)

    monthlyDonationDisposables += Observable
      .combineLatest(redemptionJobStatus, LevelUpdate.isProcessing, ::Pair)
      .subscribeBy { (jobStatus, levelUpdateProcessing) ->
        store.update { state ->
          state.copy(
            monthlyDonationState = state.monthlyDonationState.copy(
              nonVerifiedMonthlyDonation = if (jobStatus is DonationRedemptionJobStatus.PendingExternalVerification) jobStatus.nonVerifiedMonthlyDonation else null,
              transactionState = DonateToSignalState.TransactionState(jobStatus.isInProgress(), levelUpdateProcessing)
            )
          )
        }
      }
  }

  private fun monitorSubscriptionState(allSubscriptions: Single<List<Subscription>>) {
    monthlyDonationDisposables += Observable.combineLatest(allSubscriptions.toObservable(), _activeSubscription, ::Pair).subscribeBy(
      onNext = { (subs, active) ->
        store.update { state ->
          state.copy(
            monthlyDonationState = state.monthlyDonationState.copy(
              subscriptions = subs,
              selectedSubscription = state.monthlyDonationState.selectedSubscription ?: resolveSelectedSubscription(active, subs),
              _activeSubscription = active,
              donationStage = DonateToSignalState.DonationStage.READY,
              selectableCurrencyCodes = subs.firstOrNull()?.prices?.map { it.currency.currencyCode } ?: emptyList()
            )
          )
        }
      },
      onError = {
        store.update { state ->
          state.copy(
            monthlyDonationState = state.monthlyDonationState.copy(
              donationStage = DonateToSignalState.DonationStage.FAILURE
            )
          )
        }
      }
    )
  }

  private fun resolveSelectedSubscription(activeSubscription: ActiveSubscription, subscriptions: List<Subscription>): Subscription? {
    return if (activeSubscription.isActive) {
      subscriptions.firstOrNull { it.level == activeSubscription.activeSubscription.level }
    } else {
      subscriptions.firstOrNull()
    }
  }

  private fun ensureValidSubscriptionCurrency(allSubscriptions: Single<List<Subscription>>) {
    monthlyDonationDisposables += allSubscriptions.subscribeBy(
      onSuccess = { subscriptions ->
        if (subscriptions.isNotEmpty()) {
          val priceCurrencies = subscriptions[0].prices.map { it.currency }
          val selectedCurrency = SignalStore.inAppPayments.getSubscriptionCurrency(InAppPaymentSubscriberRecord.Type.DONATION)

          if (selectedCurrency !in priceCurrencies) {
            Log.w(TAG, "Unsupported currency selection. Defaulting to USD. $selectedCurrency isn't supported.")
            val usd = PlatformCurrencyUtil.USD
            val newSubscriber = InAppPaymentsRepository.getSubscriber(usd, InAppPaymentSubscriberRecord.Type.DONATION) ?: InAppPaymentSubscriberRecord(
              subscriberId = SubscriberId.generate(),
              currency = usd,
              type = InAppPaymentSubscriberRecord.Type.DONATION,
              requiresCancel = false,
              paymentMethodType = InAppPaymentData.PaymentMethodType.UNKNOWN
            )
            InAppPaymentsRepository.setSubscriber(newSubscriber)
            RecurringInAppPaymentRepository.syncAccountRecord().subscribe()
          }
        }
      },
      onError = {}
    )
  }

  private fun monitorSubscriptionCurrency() {
    monthlyDonationDisposables += SignalStore.inAppPayments.observableRecurringDonationCurrency.subscribe {
      store.update { state ->
        state.copy(monthlyDonationState = state.monthlyDonationState.copy(selectedCurrency = it))
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    oneTimeDonationDisposables.clear()
    monthlyDonationDisposables.clear()
    networkDisposable.clear()
    actionDisposable.clear()
    store.dispose()
  }

  class Factory(
    private val startType: InAppPaymentType,
    private val oneTimeInAppPaymentRepository: OneTimeInAppPaymentRepository = OneTimeInAppPaymentRepository(AppDependencies.donationsService)
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(DonateToSignalViewModel(startType, oneTimeInAppPaymentRepository)) as T
    }
  }
}
