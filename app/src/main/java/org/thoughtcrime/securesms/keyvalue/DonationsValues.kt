package org.thoughtcrime.securesms.keyvalue

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.core.util.logging.Log
import org.signal.donations.PaymentSourceType
import org.signal.donations.StripeApi
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequestContext
import org.signal.libsignal.zkgroup.receipts.ReceiptSerial
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.database.model.databaseprotos.BadgeList
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.SubscriptionReceiptRequestResponseJob
import org.thoughtcrime.securesms.payments.currency.CurrencyUtil
import org.thoughtcrime.securesms.subscription.LevelUpdateOperation
import org.thoughtcrime.securesms.subscription.Subscriber
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.IdempotencyKey
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.security.SecureRandom
import java.util.Currency
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class DonationsValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(DonationsValues::class.java)

    private const val KEY_SUBSCRIPTION_CURRENCY_CODE = "donation.currency.code"
    private const val KEY_CURRENCY_CODE_ONE_TIME = "donation.currency.code.boost"
    private const val KEY_SUBSCRIBER_ID_PREFIX = "donation.subscriber.id."
    private const val KEY_LAST_KEEP_ALIVE_LAUNCH = "donation.last.successful.ping"

    /**
     * Our last known "end of period" for a subscription. This value is used to determine
     * when a user should try to redeem a badge for their subscription, and as a hint that
     * a user has an active subscription.
     */
    private const val KEY_LAST_END_OF_PERIOD_SECONDS = "donation.last.end.of.period"

    private const val EXPIRED_BADGE = "donation.expired.badge"
    private const val EXPIRED_GIFT_BADGE = "donation.expired.gift.badge"
    private const val USER_MANUALLY_CANCELLED = "donation.user.manually.cancelled"
    private const val KEY_LEVEL_OPERATION_PREFIX = "donation.level.operation."
    private const val KEY_LEVEL_HISTORY = "donation.level.history"
    private const val DISPLAY_BADGES_ON_PROFILE = "donation.display.badges.on.profile"
    private const val SUBSCRIPTION_REDEMPTION_FAILED = "donation.subscription.redemption.failed"
    private const val SHOULD_CANCEL_SUBSCRIPTION_BEFORE_NEXT_SUBSCRIBE_ATTEMPT = "donation.should.cancel.subscription.before.next.subscribe.attempt"
    private const val SUBSCRIPTION_CANCELATION_CHARGE_FAILURE = "donation.subscription.cancelation.charge.failure"
    private const val SUBSCRIPTION_CANCELATION_REASON = "donation.subscription.cancelation.reason"
    private const val SUBSCRIPTION_CANCELATION_TIMESTAMP = "donation.subscription.cancelation.timestamp"
    private const val SUBSCRIPTION_CANCELATION_WATERMARK = "donation.subscription.cancelation.watermark"
    private const val SHOW_CANT_PROCESS_DIALOG = "show.cant.process.dialog"

    /**
     * The current request context for subscription. This should be stored until either
     * it is successfully converted into a response, the end of period changes, or the user
     * manually cancels the subscription.
     */
    private const val SUBSCRIPTION_CREDENTIAL_REQUEST = "subscription.credential.request"

    /**
     * The current response presentation that can be submitted for a badge. This should be
     * stored until it is successfully redeemed, the end of period changes, or the user
     * manually cancels their subscription.
     */
    private const val SUBSCRIPTION_CREDENTIAL_RECEIPT = "subscription.credential.receipt"

    /**
     * Notes the "end of period" time for the latest subscription that we have started
     * to get a response presentation for. When this is equal to the latest "end of period"
     * it can be assumed that we have a request context that can be safely reused.
     */
    private const val SUBSCRIPTION_EOP_STARTED_TO_CONVERT = "subscription.eop.convert"

    /**
     * Notes the "end of period" time for the latest subscription that we have started
     * to redeem a response presentation for. When this is equal to the latest "end of
     * period" it can be assumed that we have a response presentation that we can submit
     * to get an active token for.
     */
    private const val SUBSCRIPTION_EOP_STARTED_TO_REDEEM = "subscription.eop.redeem"

    /**
     * Notes the "end of period" time for the latest subscription that we have successfully
     * and fully redeemed a token for. If this is equal to the latest "end of period" it is
     * assumed that there is no work to be done.
     */
    private const val SUBSCRIPTION_EOP_REDEEMED = "subscription.eop.redeemed"

    /**
     * Notes the type of payment the user utilized for the latest subscription. This is useful
     * in determining which error messaging they should see if something goes wrong.
     */
    private const val SUBSCRIPTION_PAYMENT_SOURCE_TYPE = "subscription.payment.source.type"
  }

  override fun onFirstEverAppLaunch() = Unit

  override fun getKeysToIncludeInBackup(): MutableList<String> = mutableListOf(
    KEY_CURRENCY_CODE_ONE_TIME,
    KEY_LAST_KEEP_ALIVE_LAUNCH,
    KEY_LAST_END_OF_PERIOD_SECONDS,
    SHOULD_CANCEL_SUBSCRIPTION_BEFORE_NEXT_SUBSCRIBE_ATTEMPT,
    SUBSCRIPTION_CANCELATION_REASON,
    SUBSCRIPTION_CANCELATION_TIMESTAMP,
    SUBSCRIPTION_CANCELATION_WATERMARK,
    SHOW_CANT_PROCESS_DIALOG,
    SUBSCRIPTION_CREDENTIAL_REQUEST,
    SUBSCRIPTION_CREDENTIAL_RECEIPT,
    SUBSCRIPTION_EOP_STARTED_TO_CONVERT,
    SUBSCRIPTION_EOP_STARTED_TO_REDEEM,
    SUBSCRIPTION_EOP_REDEEMED,
    SUBSCRIPTION_PAYMENT_SOURCE_TYPE
  )

  private val subscriptionCurrencyPublisher: Subject<Currency> by lazy { BehaviorSubject.createDefault(getSubscriptionCurrency()) }
  val observableSubscriptionCurrency: Observable<Currency> by lazy { subscriptionCurrencyPublisher }

  private val oneTimeCurrencyPublisher: Subject<Currency> by lazy { BehaviorSubject.createDefault(getOneTimeCurrency()) }
  val observableOneTimeCurrency: Observable<Currency> by lazy { oneTimeCurrencyPublisher }

  fun getSubscriptionCurrency(): Currency {
    val currencyCode = getString(KEY_SUBSCRIPTION_CURRENCY_CODE, null)
    val currency: Currency? = if (currencyCode == null) {
      val localeCurrency = CurrencyUtil.getCurrencyByLocale(Locale.getDefault())
      if (localeCurrency == null) {
        val e164: String? = SignalStore.account().e164
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

    return if (currency != null && StripeApi.Validation.supportedCurrencyCodes.contains(currency.currencyCode.uppercase(Locale.ROOT))) {
      currency
    } else {
      Currency.getInstance("USD")
    }
  }

  fun getOneTimeCurrency(): Currency {
    val oneTimeCurrency = getString(KEY_CURRENCY_CODE_ONE_TIME, null)
    return if (oneTimeCurrency == null) {
      val currency = getSubscriptionCurrency()
      setOneTimeCurrency(currency)
      currency
    } else {
      Currency.getInstance(oneTimeCurrency)
    }
  }

  fun setOneTimeCurrency(currency: Currency) {
    putString(KEY_CURRENCY_CODE_ONE_TIME, currency.currencyCode)
    oneTimeCurrencyPublisher.onNext(currency)
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
    Log.i(TAG, "Setting subscriber for currency ${subscriber.currencyCode}", Exception(), true)
    val currencyCode = subscriber.currencyCode
    store.beginWrite()
      .putBlob("$KEY_SUBSCRIBER_ID_PREFIX$currencyCode", subscriber.subscriberId.bytes)
      .putString(KEY_SUBSCRIPTION_CURRENCY_CODE, currencyCode)
      .apply()

    subscriptionCurrencyPublisher.onNext(Currency.getInstance(currencyCode))
  }

  fun getLevelOperation(level: String): LevelUpdateOperation? {
    val idempotencyKey = getBlob("${KEY_LEVEL_OPERATION_PREFIX}$level", null)
    return if (idempotencyKey != null) {
      LevelUpdateOperation(IdempotencyKey.fromBytes(idempotencyKey), level)
    } else {
      null
    }
  }

  fun setLevelOperation(levelUpdateOperation: LevelUpdateOperation) {
    addLevelToHistory(levelUpdateOperation.level)
    putBlob("$KEY_LEVEL_OPERATION_PREFIX${levelUpdateOperation.level}", levelUpdateOperation.idempotencyKey.bytes)
  }

  private fun getLevelHistory(): Set<String> {
    return getString(KEY_LEVEL_HISTORY, "").split(",").toSet()
  }

  private fun addLevelToHistory(level: String) {
    val levels = getLevelHistory() + level
    putString(KEY_LEVEL_HISTORY, levels.joinToString(","))
  }

  fun clearLevelOperations() {
    val levelHistory = getLevelHistory()
    val write = store.beginWrite()
    for (level in levelHistory) {
      write.remove("${KEY_LEVEL_OPERATION_PREFIX}$level")
    }
    write.apply()
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

  fun setExpiredGiftBadge(badge: Badge?) {
    if (badge != null) {
      putBlob(EXPIRED_GIFT_BADGE, Badges.toDatabaseBadge(badge).toByteArray())
    } else {
      remove(EXPIRED_GIFT_BADGE)
    }
  }

  fun getExpiredGiftBadge(): Badge? {
    val badgeBytes = getBlob(EXPIRED_GIFT_BADGE, null) ?: return null

    return Badges.fromDatabaseBadge(BadgeList.Badge.parseFrom(badgeBytes))
  }

  fun getLastKeepAliveLaunchTime(): Long {
    return getLong(KEY_LAST_KEEP_ALIVE_LAUNCH, 0L)
  }

  fun setLastKeepAliveLaunchTime(timestamp: Long) {
    putLong(KEY_LAST_KEEP_ALIVE_LAUNCH, timestamp)
  }

  fun getLastEndOfPeriod(): Long {
    return getLong(KEY_LAST_END_OF_PERIOD_SECONDS, 0L)
  }

  fun setLastEndOfPeriod(timestamp: Long) {
    putLong(KEY_LAST_END_OF_PERIOD_SECONDS, timestamp)
  }

  /**
   * True if the local user is likely a sustainer, otherwise false. Note the term 'likely', because this is based on cached data. Any serious decisions that
   * rely on this should make a network request to determine subscription status.
   */
  fun isLikelyASustainer(): Boolean {
    return TimeUnit.SECONDS.toMillis(getLastEndOfPeriod()) > System.currentTimeMillis()
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

  fun setDisplayBadgesOnProfile(enabled: Boolean) {
    putBoolean(DISPLAY_BADGES_ON_PROFILE, enabled)
  }

  fun getDisplayBadgesOnProfile(): Boolean {
    return getBoolean(DISPLAY_BADGES_ON_PROFILE, false)
  }

  fun getSubscriptionRedemptionFailed(): Boolean {
    return getBoolean(SUBSCRIPTION_REDEMPTION_FAILED, false)
  }

  fun markSubscriptionRedemptionFailed() {
    Log.w(TAG, "markSubscriptionRedemptionFailed()", Throwable(), true)
    putBoolean(SUBSCRIPTION_REDEMPTION_FAILED, true)
  }

  fun clearSubscriptionRedemptionFailed() {
    putBoolean(SUBSCRIPTION_REDEMPTION_FAILED, false)
  }

  fun setUnexpectedSubscriptionCancelationChargeFailure(chargeFailure: ActiveSubscription.ChargeFailure?) {
    if (chargeFailure == null) {
      remove(SUBSCRIPTION_CANCELATION_CHARGE_FAILURE)
    } else {
      putString(SUBSCRIPTION_CANCELATION_CHARGE_FAILURE, JsonUtil.toJson(chargeFailure))
    }
  }

  fun getUnexpectedSubscriptionCancelationChargeFailure(): ActiveSubscription.ChargeFailure? {
    val json = getString(SUBSCRIPTION_CANCELATION_CHARGE_FAILURE, null)
    return if (json.isNullOrEmpty()) {
      null
    } else {
      JsonUtil.fromJson(json, ActiveSubscription.ChargeFailure::class.java)
    }
  }

  var unexpectedSubscriptionCancelationReason: String? by stringValue(SUBSCRIPTION_CANCELATION_REASON, null)
  var unexpectedSubscriptionCancelationTimestamp: Long by longValue(SUBSCRIPTION_CANCELATION_TIMESTAMP, 0L)
  var unexpectedSubscriptionCancelationWatermark: Long by longValue(SUBSCRIPTION_CANCELATION_WATERMARK, 0L)

  @get:JvmName("showCantProcessDialog")
  var showCantProcessDialog: Boolean by booleanValue(SHOW_CANT_PROCESS_DIALOG, true)

  /**
   * Denotes that the previous attempt to subscribe failed in some way. Either an
   * automatic renewal failed resulting in an unexpected expiration, or payment failed
   * on Stripe's end.
   *
   * Before trying to resubscribe, we should first ensure there are no subscriptions set
   * on the server. Otherwise, we could get into a situation where the user is unable to
   * resubscribe.
   */
  var shouldCancelSubscriptionBeforeNextSubscribeAttempt: Boolean
    get() = getBoolean(SHOULD_CANCEL_SUBSCRIPTION_BEFORE_NEXT_SUBSCRIBE_ATTEMPT, false)
    set(value) = putBoolean(SHOULD_CANCEL_SUBSCRIPTION_BEFORE_NEXT_SUBSCRIBE_ATTEMPT, value)

  /**
   * Consolidates a bunch of data clears that should occur whenever a user manually cancels their
   * subscription:
   *
   * 1. Clears keep-alive flag
   * 1. Clears level operation
   * 1. Marks the user as manually cancelled
   * 1. Clears out unexpected cancelation state
   * 1. Clears expired badge if it is for a subscription
   */
  @WorkerThread
  fun updateLocalStateForManualCancellation() {
    synchronized(SubscriptionReceiptRequestResponseJob.MUTEX) {
      Log.d(TAG, "[updateLocalStateForManualCancellation] Clearing donation values.")

      setLastEndOfPeriod(0L)
      clearLevelOperations()
      markUserManuallyCancelled()
      shouldCancelSubscriptionBeforeNextSubscribeAttempt = false
      setUnexpectedSubscriptionCancelationChargeFailure(null)
      unexpectedSubscriptionCancelationReason = null
      unexpectedSubscriptionCancelationTimestamp = 0L

      clearSubscriptionRequestCredential()
      clearSubscriptionReceiptCredential()

      val expiredBadge = getExpiredBadge()
      if (expiredBadge != null && expiredBadge.isSubscription()) {
        Log.d(TAG, "[updateLocalStateForManualCancellation] Clearing expired badge.")
        setExpiredBadge(null)
      }
    }
  }

  /**
   * Consolidates a bunch of data clears that should occur whenever a user begins a new subscription:
   *
   * 1. Manual cancellation marker
   * 1. Any set level operations
   * 1. Unexpected cancelation flags
   * 1. Expired badge, if it is of a subscription
   */
  @WorkerThread
  fun updateLocalStateForLocalSubscribe() {
    synchronized(SubscriptionReceiptRequestResponseJob.MUTEX) {
      Log.d(TAG, "[updateLocalStateForLocalSubscribe] Clearing donation values.")

      clearUserManuallyCancelled()
      clearLevelOperations()
      shouldCancelSubscriptionBeforeNextSubscribeAttempt = false
      setUnexpectedSubscriptionCancelationChargeFailure(null)
      unexpectedSubscriptionCancelationReason = null
      unexpectedSubscriptionCancelationTimestamp = 0L
      refreshSubscriptionRequestCredential()
      clearSubscriptionReceiptCredential()

      val expiredBadge = getExpiredBadge()
      if (expiredBadge != null && expiredBadge.isSubscription()) {
        Log.d(TAG, "[updateLocalStateForLocalSubscribe] Clearing expired badge.")
        setExpiredBadge(null)
      }
    }
  }

  fun refreshSubscriptionRequestCredential() {
    putBlob(SUBSCRIPTION_CREDENTIAL_REQUEST, generateRequestCredential().serialize())
  }

  fun setSubscriptionRequestCredential(requestContext: ReceiptCredentialRequestContext) {
    putBlob(SUBSCRIPTION_CREDENTIAL_REQUEST, requestContext.serialize())
  }

  fun getSubscriptionRequestCredential(): ReceiptCredentialRequestContext? {
    val bytes = getBlob(SUBSCRIPTION_CREDENTIAL_REQUEST, null) ?: return null

    return ReceiptCredentialRequestContext(bytes)
  }

  fun clearSubscriptionRequestCredential() {
    remove(SUBSCRIPTION_CREDENTIAL_REQUEST)
  }

  fun setSubscriptionReceiptCredential(receiptCredentialPresentation: ReceiptCredentialPresentation) {
    putBlob(SUBSCRIPTION_CREDENTIAL_RECEIPT, receiptCredentialPresentation.serialize())
  }

  fun getSubscriptionReceiptCredential(): ReceiptCredentialPresentation? {
    val bytes = getBlob(SUBSCRIPTION_CREDENTIAL_RECEIPT, null) ?: return null

    return ReceiptCredentialPresentation(bytes)
  }

  fun clearSubscriptionReceiptCredential() {
    remove(SUBSCRIPTION_CREDENTIAL_RECEIPT)
  }

  fun setSubscriptionPaymentSourceType(paymentSourceType: PaymentSourceType) {
    putString(SUBSCRIPTION_PAYMENT_SOURCE_TYPE, paymentSourceType.code)
  }

  fun getSubscriptionPaymentSourceType(): PaymentSourceType {
    return PaymentSourceType.fromCode(getString(SUBSCRIPTION_PAYMENT_SOURCE_TYPE, null))
  }

  var subscriptionEndOfPeriodConversionStarted by longValue(SUBSCRIPTION_EOP_STARTED_TO_CONVERT, 0L)
  var subscriptionEndOfPeriodRedemptionStarted by longValue(SUBSCRIPTION_EOP_STARTED_TO_REDEEM, 0L)
  var subscriptionEndOfPeriodRedeemed by longValue(SUBSCRIPTION_EOP_REDEEMED, 0L)

  private fun generateRequestCredential(): ReceiptCredentialRequestContext {
    Log.d(TAG, "Generating request credentials context for token redemption...", true)
    val secureRandom = SecureRandom()
    val randomBytes = Util.getSecretBytes(ReceiptSerial.SIZE)

    return try {
      val receiptSerial = ReceiptSerial(randomBytes)
      val operations = ApplicationDependencies.getClientZkReceiptOperations()
      operations.createReceiptCredentialRequestContext(secureRandom, receiptSerial)
    } catch (e: InvalidInputException) {
      Log.e(TAG, "Failed to create credential.", e)
      throw AssertionError(e)
    } catch (e: VerificationFailedException) {
      Log.e(TAG, "Failed to create credential.", e)
      throw AssertionError(e)
    }
  }
}
