package org.thoughtcrime.securesms.keyvalue

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.payments.currency.CurrencyUtil
import org.thoughtcrime.securesms.subscription.LevelUpdateOperation
import org.thoughtcrime.securesms.subscription.Subscriber
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.subscriptions.IdempotencyKey
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.util.Currency
import java.util.Locale

internal class DonationsValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val KEY_SUBSCRIPTION_CURRENCY_CODE = "donation.currency.code"
    private const val KEY_CURRENCY_CODE_BOOST = "donation.currency.code.boost"
    private const val KEY_SUBSCRIBER_ID_PREFIX = "donation.subscriber.id."
    private const val KEY_IDEMPOTENCY = "donation.idempotency.key"
    private const val KEY_LEVEL = "donation.level"
    private const val KEY_LAST_KEEP_ALIVE_LAUNCH = "donation.last.successful.ping"
    private const val KEY_LAST_END_OF_PERIOD = "donation.last.end.of.period"
  }

  override fun onFirstEverAppLaunch() = Unit

  override fun getKeysToIncludeInBackup(): MutableList<String> = mutableListOf(KEY_SUBSCRIPTION_CURRENCY_CODE, KEY_LAST_KEEP_ALIVE_LAUNCH)

  private val subscriptionCurrencyPublisher: Subject<Currency> by lazy { BehaviorSubject.createDefault(getSubscriptionCurrency()) }
  val observableSubscriptionCurrency: Observable<Currency> by lazy { subscriptionCurrencyPublisher }

  private val boostCurrencyPublisher: Subject<Currency> by lazy { BehaviorSubject.createDefault(getBoostCurrency()) }
  val observableBoostCurrency: Observable<Currency> by lazy { boostCurrencyPublisher }

  private val levelUpdateOperationPublisher: Subject<Optional<LevelUpdateOperation>> by lazy { BehaviorSubject.createDefault(Optional.fromNullable(getLevelOperation())) }
  val levelUpdateOperationObservable: Observable<Optional<LevelUpdateOperation>> by lazy { levelUpdateOperationPublisher }

  fun getSubscriptionCurrency(): Currency {
    val currencyCode = getString(KEY_SUBSCRIPTION_CURRENCY_CODE, null)
    val currency: Currency? = if (currencyCode == null) {
      val localeCurrency = CurrencyUtil.getCurrencyByLocale(Locale.getDefault())
      if (localeCurrency == null) {
        val e164 = TextSecurePreferences.getLocalNumber(ApplicationDependencies.getApplication())
        if (e164 == null) {
          null
        } else {
          CurrencyUtil.getCurrencyByE164(e164)
        }
      } else {
        localeCurrency
      }
    } else {
      CurrencyUtil.getCurrencyByCurrencyCode(currencyCode)
    }

    return if (currency != null && StripeApi.Validation.supportedCurrencyCodes.contains(currency.currencyCode)) {
      currency
    } else {
      Currency.getInstance("USD")
    }
  }

  fun getBoostCurrency(): Currency {
    val boostCurrencyCode = getString(KEY_CURRENCY_CODE_BOOST, null)
    return if (boostCurrencyCode == null) {
      val currency = getSubscriptionCurrency()
      setBoostCurrency(currency)
      currency
    } else {
      Currency.getInstance(boostCurrencyCode)
    }
  }

  fun setSubscriptionCurrency(currency: Currency) {
    putString(KEY_SUBSCRIPTION_CURRENCY_CODE, currency.currencyCode)
    subscriptionCurrencyPublisher.onNext(currency)
  }

  fun setBoostCurrency(currency: Currency) {
    putString(KEY_CURRENCY_CODE_BOOST, currency.currencyCode)
    boostCurrencyPublisher.onNext(currency)
  }

  fun getSubscriber(): Subscriber? {
    val currencyCode = getSubscriptionCurrency().currencyCode
    val subscriberIdBytes = getBlob("$KEY_SUBSCRIBER_ID_PREFIX$currencyCode", null)

    return if (subscriberIdBytes == null) {
      null
    } else {
      Subscriber(SubscriberId.fromBytes(subscriberIdBytes), currencyCode)
    }
  }

  fun requireSubscriber(): Subscriber {
    return getSubscriber() ?: throw Exception("Subscriber ID is not set.")
  }

  fun setSubscriber(subscriber: Subscriber) {
    val currencyCode = subscriber.currencyCode
    putBlob("$KEY_SUBSCRIBER_ID_PREFIX$currencyCode", subscriber.subscriberId.bytes)
  }

  fun getLevelOperation(): LevelUpdateOperation? {
    val level = getString(KEY_LEVEL, null)
    val idempotencyKey = getIdempotencyKey()

    return if (level == null || idempotencyKey == null) {
      null
    } else {
      LevelUpdateOperation(idempotencyKey, level)
    }
  }

  fun setLevelOperation(levelUpdateOperation: LevelUpdateOperation) {
    putString(KEY_LEVEL, levelUpdateOperation.level)
    setIdempotencyKey(levelUpdateOperation.idempotencyKey)
    dispatchLevelOperation()
  }

  fun clearLevelOperation(levelUpdateOperation: LevelUpdateOperation): Boolean {
    val currentKey = getIdempotencyKey()
    return if (currentKey == levelUpdateOperation.idempotencyKey) {
      clearLevelOperation()
      true
    } else {
      false
    }
  }

  private fun clearLevelOperation() {
    remove(KEY_IDEMPOTENCY)
    remove(KEY_LEVEL)
    dispatchLevelOperation()
  }

  private fun getIdempotencyKey(): IdempotencyKey? {
    return getBlob(KEY_IDEMPOTENCY, null)?.let { IdempotencyKey.fromBytes(it) }
  }

  private fun setIdempotencyKey(key: IdempotencyKey) {
    putBlob(KEY_IDEMPOTENCY, key.bytes)
  }

  fun getLastKeepAliveLaunchTime(): Long {
    return getLong(KEY_LAST_KEEP_ALIVE_LAUNCH, 0L)
  }

  fun setLastKeepAliveLaunchTime(timestamp: Long) {
    putLong(KEY_LAST_KEEP_ALIVE_LAUNCH, timestamp)
  }

  fun getLastEndOfPeriod(): Long {
    return getLong(KEY_LAST_END_OF_PERIOD, 0L)
  }

  fun setLastEndOfPeriod(timestamp: Long) {
    putLong(KEY_LAST_END_OF_PERIOD, timestamp)
  }

  private fun dispatchLevelOperation() {
    levelUpdateOperationPublisher.onNext(Optional.fromNullable(getLevelOperation()))
  }
}
