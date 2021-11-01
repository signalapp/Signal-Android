package org.thoughtcrime.securesms.keyvalue

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.database.model.databaseprotos.BadgeList
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.payments.currency.CurrencyUtil
import org.thoughtcrime.securesms.subscription.LevelUpdateOperation
import org.thoughtcrime.securesms.subscription.Subscriber
import org.thoughtcrime.securesms.util.TextSecurePreferences
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
    private const val EXPIRED_BADGE = "donation.expired.badge"
    private const val USER_MANUALLY_CANCELLED = "donation.user.manually.cancelled"
  }

  override fun onFirstEverAppLaunch() = Unit

  override fun getKeysToIncludeInBackup(): MutableList<String> = mutableListOf(
    KEY_CURRENCY_CODE_BOOST,
    KEY_LAST_KEEP_ALIVE_LAUNCH
  )

  private val subscriptionCurrencyPublisher: Subject<Currency> by lazy { BehaviorSubject.createDefault(getSubscriptionCurrency()) }
  val observableSubscriptionCurrency: Observable<Currency> by lazy { subscriptionCurrencyPublisher }

  private val boostCurrencyPublisher: Subject<Currency> by lazy { BehaviorSubject.createDefault(getBoostCurrency()) }
  val observableBoostCurrency: Observable<Currency> by lazy { boostCurrencyPublisher }

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

  fun setBoostCurrency(currency: Currency) {
    putString(KEY_CURRENCY_CODE_BOOST, currency.currencyCode)
    boostCurrencyPublisher.onNext(currency)
  }

  fun getSubscriber(currency: Currency): Subscriber? {
    val currencyCode = currency.currencyCode
    val subscriberIdBytes = getBlob("$KEY_SUBSCRIBER_ID_PREFIX$currencyCode", null)

    return if (subscriberIdBytes == null) {
      null
    } else {
      Subscriber(SubscriberId.fromBytes(subscriberIdBytes), currencyCode)
    }
  }

  fun getSubscriber(): Subscriber? {
    return getSubscriber(getSubscriptionCurrency())
  }

  fun requireSubscriber(): Subscriber {
    return getSubscriber() ?: throw Exception("Subscriber ID is not set.")
  }

  fun setSubscriber(subscriber: Subscriber) {
    val currencyCode = subscriber.currencyCode
    store.beginWrite()
      .putBlob("$KEY_SUBSCRIBER_ID_PREFIX$currencyCode", subscriber.subscriberId.bytes)
      .putString(KEY_SUBSCRIPTION_CURRENCY_CODE, currencyCode)
      .apply()

    subscriptionCurrencyPublisher.onNext(Currency.getInstance(currencyCode))
  }

  fun getLevelOperation(): LevelUpdateOperation? {
    val level = getString(KEY_LEVEL, null)
    val idempotencyKey = getBlob(KEY_IDEMPOTENCY, null)?.let { IdempotencyKey.fromBytes(it) }

    return if (level == null || idempotencyKey == null) {
      null
    } else {
      LevelUpdateOperation(idempotencyKey, level)
    }
  }

  fun setLevelOperation(levelUpdateOperation: LevelUpdateOperation) {
    store.beginWrite()
      .putString(KEY_LEVEL, levelUpdateOperation.level)
      .putBlob(KEY_IDEMPOTENCY, levelUpdateOperation.idempotencyKey.bytes)
      .apply()
  }

  fun clearLevelOperation() {
    store.beginWrite()
      .remove(KEY_LEVEL)
      .remove(KEY_IDEMPOTENCY)
      .apply()
  }

  fun setExpiredBadge(badge: Badge?) {
    if (badge != null) {
      putBlob(EXPIRED_BADGE, Badges.toDatabaseBadge(badge).toByteArray())
    } else {
      remove(EXPIRED_BADGE)
    }
  }

  fun getExpiredBadge(): Badge? {
    val badgeBytes = getBlob(EXPIRED_BADGE, null) ?: return null

    return Badges.fromDatabaseBadge(BadgeList.Badge.parseFrom(badgeBytes))
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

  fun isUserManuallyCancelled(): Boolean {
    return getBoolean(USER_MANUALLY_CANCELLED, false)
  }

  fun markUserManuallyCancelled() {
    putBoolean(USER_MANUALLY_CANCELLED, true)
  }

  fun clearUserManuallyCancelled() {
    remove(USER_MANUALLY_CANCELLED)
  }
}
