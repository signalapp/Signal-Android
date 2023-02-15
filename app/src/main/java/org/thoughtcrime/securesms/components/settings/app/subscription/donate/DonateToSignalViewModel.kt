package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.core.util.StringUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.core.util.money.PlatformCurrencyUtil
import org.thoughtcrime.securesms.components.settings.app.subscription.MonthlyDonationRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.OneTimeDonationRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.boost.Boost
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayRequest
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.SubscriptionRedemptionJobWatcher
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.subscription.LevelUpdate
import org.thoughtcrime.securesms.subscription.Subscriber
import org.thoughtcrime.securesms.subscription.Subscription
import org.thoughtcrime.securesms.util.InternetConnectionObserver
import org.thoughtcrime.securesms.util.rx.RxStore
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency

/**
 * Contains the logic to manage the UI of the unified donations screen.
 * Does not directly deal with performing payments, this ViewModel is
 * only in charge of rendering our "current view of the world."
 */
class DonateToSignalViewModel(
  startType: DonateToSignalType,
  private val subscriptionsRepository: MonthlyDonationRepository,
  private val oneTimeDonationRepository: OneTimeDonationRepository
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(DonateToSignalViewModel::class.java)
  }

  private val store = RxStore(DonateToSignalState(donateToSignalType = startType))
  private val oneTimeDonationDisposables = CompositeDisposable()
  private val monthlyDonationDisposables = CompositeDisposable()
  private val networkDisposable = CompositeDisposable()
  private val _actions = PublishSubject.create<DonateToSignalAction>()
  private val _activeSubscription = PublishSubject.create<ActiveSubscription>()

  val state = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())
  val actions: Observable<DonateToSignalAction> = _actions.observeOn(AndroidSchedulers.mainThread())

  init {
    initializeOneTimeDonationState(oneTimeDonationRepository)
    initializeMonthlyDonationState(subscriptionsRepository)

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
      initializeMonthlyDonationState(subscriptionsRepository)
    }
  }

  fun retryOneTimeDonationState() {
    if (!oneTimeDonationDisposables.isDisposed && store.state.oneTimeDonationState.donationStage == DonateToSignalState.DonationStage.FAILURE) {
      store.update { it.copy(oneTimeDonationState = it.oneTimeDonationState.copy(donationStage = DonateToSignalState.DonationStage.INIT)) }
      initializeOneTimeDonationState(oneTimeDonationRepository)
    }
  }

  fun requestChangeCurrency() {
    val snapshot = store.state
    if (snapshot.canSetCurrency) {
      _actions.onNext(DonateToSignalAction.DisplayCurrencySelectionDialog(snapshot.donateToSignalType, snapshot.selectableCurrencyCodes))
    }
  }

  fun requestSelectGateway() {
    val snapshot = store.state
    if (snapshot.areFieldsEnabled) {
      _actions.onNext(DonateToSignalAction.DisplayGatewaySelectorDialog(createGatewayRequest(snapshot)))
    }
  }

  fun updateSubscription() {
    val snapshot = store.state
    if (snapshot.areFieldsEnabled) {
      _actions.onNext(DonateToSignalAction.UpdateSubscription(createGatewayRequest(snapshot)))
    }
  }

  fun cancelSubscription() {
    val snapshot = store.state
    if (snapshot.areFieldsEnabled) {
      _actions.onNext(DonateToSignalAction.CancelSubscription(createGatewayRequest(snapshot)))
    }
  }

  fun toggleDonationType() {
    store.update {
      it.copy(
        donateToSignalType = when (it.donateToSignalType) {
          DonateToSignalType.ONE_TIME -> DonateToSignalType.MONTHLY
          DonateToSignalType.MONTHLY -> DonateToSignalType.ONE_TIME
          DonateToSignalType.GIFT -> error("We are in an illegal state")
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
    subscriptionsRepository
      .getActiveSubscription()
      .subscribeBy(
        onSuccess = {
          _activeSubscription.onNext(it)
        },
        onError = {
          _activeSubscription.onNext(ActiveSubscription.EMPTY)
        }
      )
  }

  private fun createGatewayRequest(snapshot: DonateToSignalState): GatewayRequest {
    val amount = getAmount(snapshot)
    return GatewayRequest(
      donateToSignalType = snapshot.donateToSignalType,
      badge = snapshot.badge!!,
      label = snapshot.badge!!.description,
      price = amount.amount,
      currencyCode = amount.currency.currencyCode,
      level = snapshot.level.toLong(),
      recipientId = Recipient.self().id
    )
  }

  private fun getAmount(snapshot: DonateToSignalState): FiatMoney {
    return when (snapshot.donateToSignalType) {
      DonateToSignalType.ONE_TIME -> getOneTimeAmount(snapshot.oneTimeDonationState)
      DonateToSignalType.MONTHLY -> getSelectedSubscriptionCost()
      DonateToSignalType.GIFT -> error("This ViewModel does not support gifts.")
    }
  }

  private fun getOneTimeAmount(snapshot: DonateToSignalState.OneTimeDonationState): FiatMoney {
    return if (snapshot.isCustomAmountFocused) {
      snapshot.customAmount
    } else {
      snapshot.selectedBoost!!.price
    }
  }

  private fun initializeOneTimeDonationState(oneTimeDonationRepository: OneTimeDonationRepository) {
    oneTimeDonationDisposables += oneTimeDonationRepository.getBoostBadge().subscribeBy(
      onSuccess = { badge ->
        store.update { it.copy(oneTimeDonationState = it.oneTimeDonationState.copy(badge = badge)) }
      },
      onError = {
        Log.w(TAG, "Could not load boost badge", it)
      }
    )

    oneTimeDonationDisposables += oneTimeDonationRepository.getMinimumDonationAmounts().subscribeBy(
      onSuccess = { amountMap ->
        store.update { it.copy(oneTimeDonationState = it.oneTimeDonationState.copy(minimumDonationAmounts = amountMap)) }
      },
      onError = {
        Log.w(TAG, "Could not load minimum custom donation amounts.", it)
      }
    )

    val boosts: Observable<Map<Currency, List<Boost>>> = oneTimeDonationRepository.getBoosts().toObservable()
    val oneTimeCurrency: Observable<Currency> = SignalStore.donationsValues().observableOneTimeCurrency

    oneTimeDonationDisposables += Observable.combineLatest(boosts, oneTimeCurrency) { boostMap, currency ->
      val boostList = if (currency in boostMap) {
        boostMap[currency]!!
      } else {
        SignalStore.donationsValues().setOneTimeCurrency(PlatformCurrencyUtil.USD)
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

  private fun initializeMonthlyDonationState(subscriptionsRepository: MonthlyDonationRepository) {
    monitorLevelUpdateProcessing()

    val allSubscriptions = subscriptionsRepository.getSubscriptions()
    ensureValidSubscriptionCurrency(allSubscriptions)
    monitorSubscriptionCurrency()
    monitorSubscriptionState(allSubscriptions)
    refreshActiveSubscription()
  }

  private fun monitorLevelUpdateProcessing() {
    val isTransactionJobInProgress: Observable<Boolean> = SubscriptionRedemptionJobWatcher.watch().map {
      it.map { jobState ->
        when (jobState) {
          JobTracker.JobState.PENDING -> true
          JobTracker.JobState.RUNNING -> true
          else -> false
        }
      }.orElse(false)
    }

    monthlyDonationDisposables += Observable
      .combineLatest(isTransactionJobInProgress, LevelUpdate.isProcessing, DonateToSignalState::TransactionState)
      .subscribeBy { transactionState ->
        store.update { state ->
          state.copy(
            monthlyDonationState = state.monthlyDonationState.copy(
              transactionState = transactionState
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
          val selectedCurrency = SignalStore.donationsValues().getSubscriptionCurrency()

          if (selectedCurrency !in priceCurrencies) {
            Log.w(TAG, "Unsupported currency selection. Defaulting to USD. $selectedCurrency isn't supported.")
            val usd = PlatformCurrencyUtil.USD
            val newSubscriber = SignalStore.donationsValues().getSubscriber(usd) ?: Subscriber(SubscriberId.generate(), usd.currencyCode)
            SignalStore.donationsValues().setSubscriber(newSubscriber)
            subscriptionsRepository.syncAccountRecord().subscribe()
          }
        }
      },
      onError = {}
    )
  }

  private fun monitorSubscriptionCurrency() {
    monthlyDonationDisposables += SignalStore.donationsValues().observableSubscriptionCurrency.subscribe {
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
    store.dispose()
  }

  class Factory(
    private val startType: DonateToSignalType,
    private val subscriptionsRepository: MonthlyDonationRepository = MonthlyDonationRepository(ApplicationDependencies.getDonationsService()),
    private val oneTimeDonationRepository: OneTimeDonationRepository = OneTimeDonationRepository(ApplicationDependencies.getDonationsService())
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(DonateToSignalViewModel(startType, subscriptionsRepository, oneTimeDonationRepository)) as T
    }
  }
}
