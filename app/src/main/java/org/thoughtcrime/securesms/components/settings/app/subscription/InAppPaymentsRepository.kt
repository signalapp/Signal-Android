/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription

import android.annotation.SuppressLint
import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.donations.PaymentSourceType
import org.signal.donations.StripeDeclineCode
import org.signal.donations.StripeFailureCode
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequestContext
import org.signal.libsignal.zkgroup.receipts.ReceiptSerial
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.toDonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.DonationRedemptionJobStatus
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.DonationRedemptionJobWatcher
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.NonVerifiedMonthlyDonation
import org.thoughtcrime.securesms.database.DatabaseObserver.InAppPaymentObserver
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingOneTimeDonation
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.internal.push.DonationProcessor
import org.whispersystems.signalservice.internal.push.exceptions.DonationProcessorError
import java.security.SecureRandom
import java.util.Currency
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Unifies legacy access and new access to in app payment data.
 */
object InAppPaymentsRepository {

  private const val JOB_PREFIX = "InAppPayments__"
  private val TAG = Log.tag(InAppPaymentsRepository::class.java)

  private val temporaryErrorProcessor = PublishProcessor.create<Pair<InAppPaymentTable.InAppPaymentId, Throwable>>()

  /**
   * Wraps an in-app-payment update in a completable.
   */
  fun updateInAppPayment(inAppPayment: InAppPaymentTable.InAppPayment): Completable {
    return Completable.fromAction {
      SignalDatabase.inAppPayments.update(inAppPayment)
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Common logic for handling errors coming from the Rx chains that handle payments. These errors
   * are analyzed and then either written to the database or dispatched to the temporary error processor.
   */
  fun handlePipelineError(
    inAppPaymentId: InAppPaymentTable.InAppPaymentId,
    donationErrorSource: DonationErrorSource,
    paymentSourceType: PaymentSourceType,
    error: Throwable
  ) {
    if (error is InAppPaymentError) {
      setErrorIfNotPresent(inAppPaymentId, error.inAppPaymentDataError)
      return
    }

    val donationError: DonationError = when (error) {
      is DonationError -> error
      is DonationProcessorError -> error.toDonationError(donationErrorSource, paymentSourceType)
      else -> DonationError.genericBadgeRedemptionFailure(donationErrorSource)
    }

    val inAppPaymentError = InAppPaymentError.fromDonationError(donationError)?.inAppPaymentDataError
    if (inAppPaymentError != null) {
      Log.w(TAG, "Detected a terminal error.")
      setErrorIfNotPresent(inAppPaymentId, inAppPaymentError).subscribe()
    } else {
      Log.w(TAG, "Detected a temporary error.")
      temporaryErrorProcessor.onNext(inAppPaymentId to donationError)
    }
  }

  /**
   * Observe a stream of "temporary errors". These are situations in which either the user cancelled out, opened an external application,
   * or needs to wait a longer time period than 10s for the completion of their payment.
   */
  fun observeTemporaryErrors(inAppPaymentId: InAppPaymentTable.InAppPaymentId): Flowable<Pair<InAppPaymentTable.InAppPaymentId, Throwable>> {
    return temporaryErrorProcessor.filter { (id, _) -> id == inAppPaymentId }
  }

  /**
   * Writes the given error to the database, if and only if there is not already an error set.
   */
  private fun setErrorIfNotPresent(inAppPaymentId: InAppPaymentTable.InAppPaymentId, error: InAppPaymentData.Error?): Completable {
    return Completable.fromAction {
      val inAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)!!
      if (inAppPayment.data.error == null) {
        SignalDatabase.inAppPayments.update(
          inAppPayment.copy(
            notified = false,
            state = InAppPaymentTable.State.END,
            data = inAppPayment.data.copy(error = error)
          )
        )
      }
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Returns a Single that can give a snapshot of the given payment, and will throw if it is not found.
   */
  fun requireInAppPayment(inAppPaymentId: InAppPaymentTable.InAppPaymentId): Single<InAppPaymentTable.InAppPayment> {
    return Single.fromCallable {
      SignalDatabase.inAppPayments.getById(inAppPaymentId) ?: throw Exception("Not found.")
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Returns a Flowable source of InAppPayments that emits whenever the payment with the given id is updated. This
   * flowable is primed with the current state.
   */
  fun observeUpdates(inAppPaymentId: InAppPaymentTable.InAppPaymentId): Flowable<InAppPaymentTable.InAppPayment> {
    return Flowable.create({ emitter ->
      val observer = InAppPaymentObserver {
        if (it.id == inAppPaymentId) {
          emitter.onNext(it)
        }
      }

      AppDependencies.databaseObserver.registerInAppPaymentObserver(observer)
      emitter.setCancellable {
        AppDependencies.databaseObserver.unregisterObserver(observer)
      }

      SignalDatabase.inAppPayments.getById(inAppPaymentId)?.also {
        observer.onInAppPaymentChanged(it)
      }
    }, BackpressureStrategy.LATEST)
  }

  /**
   * For one-time:
   *  - Each job chain is serialized with respect to the in-app-payment ID
   *
   * For recurring:
   *  - Each job chain is serialized with respect to the in-app-payment type
   */
  fun resolveJobQueueKey(inAppPayment: InAppPaymentTable.InAppPayment): String {
    return when (inAppPayment.type) {
      InAppPaymentTable.Type.UNKNOWN -> error("Unsupported type UNKNOWN.")
      InAppPaymentTable.Type.ONE_TIME_GIFT, InAppPaymentTable.Type.ONE_TIME_DONATION -> "$JOB_PREFIX${inAppPayment.id.serialize()}"
      InAppPaymentTable.Type.RECURRING_DONATION, InAppPaymentTable.Type.RECURRING_BACKUP -> "$JOB_PREFIX${inAppPayment.type.code}"
    }
  }

  /**
   * Returns a duration to utilize for jobs tied to different payment methods. For long running bank transfers, we need to
   * allow extra time for completion.
   */
  fun resolveContextJobLifespan(inAppPayment: InAppPaymentTable.InAppPayment): Duration {
    return when (inAppPayment.data.paymentMethodType) {
      InAppPaymentData.PaymentMethodType.SEPA_DEBIT, InAppPaymentData.PaymentMethodType.IDEAL -> 30.days
      else -> 1.days
    }
  }

  /**
   * Returns the object to utilize as a mutex for recurring subscriptions.
   */
  fun resolveMutex(inAppPaymentId: InAppPaymentTable.InAppPaymentId): Any {
    val payment = SignalDatabase.inAppPayments.getById(inAppPaymentId) ?: error("Not found")

    return payment.type.requireSubscriberType()
  }

  /**
   * Maps a payment type into a request code for grabbing a Google Pay token.
   */
  fun getGooglePayRequestCode(inAppPaymentType: InAppPaymentTable.Type): Int {
    return when (inAppPaymentType) {
      InAppPaymentTable.Type.UNKNOWN -> error("Unsupported type UNKNOWN")
      InAppPaymentTable.Type.ONE_TIME_GIFT -> 16143
      InAppPaymentTable.Type.ONE_TIME_DONATION -> 16141
      InAppPaymentTable.Type.RECURRING_DONATION -> 16142
      InAppPaymentTable.Type.RECURRING_BACKUP -> 16144
    }
  }

  /**
   * Converts an error source to a persistable type. For types that don't map,
   * UNKNOWN is returned.
   */
  fun DonationErrorSource.toInAppPaymentType(): InAppPaymentTable.Type {
    return when (this) {
      DonationErrorSource.ONE_TIME -> InAppPaymentTable.Type.ONE_TIME_DONATION
      DonationErrorSource.MONTHLY -> InAppPaymentTable.Type.RECURRING_DONATION
      DonationErrorSource.GIFT -> InAppPaymentTable.Type.ONE_TIME_GIFT
      DonationErrorSource.GIFT_REDEMPTION -> InAppPaymentTable.Type.UNKNOWN
      DonationErrorSource.KEEP_ALIVE -> InAppPaymentTable.Type.UNKNOWN
      DonationErrorSource.UNKNOWN -> InAppPaymentTable.Type.UNKNOWN
    }
  }

  /**
   * Converts the structured payment source type into a type we can write to the database.
   */
  fun PaymentSourceType.toPaymentMethodType(): InAppPaymentData.PaymentMethodType {
    return when (this) {
      PaymentSourceType.PayPal -> InAppPaymentData.PaymentMethodType.PAYPAL
      PaymentSourceType.Stripe.CreditCard -> InAppPaymentData.PaymentMethodType.CARD
      PaymentSourceType.Stripe.GooglePay -> InAppPaymentData.PaymentMethodType.GOOGLE_PAY
      PaymentSourceType.Stripe.IDEAL -> InAppPaymentData.PaymentMethodType.IDEAL
      PaymentSourceType.Stripe.SEPADebit -> InAppPaymentData.PaymentMethodType.SEPA_DEBIT
      PaymentSourceType.Unknown -> InAppPaymentData.PaymentMethodType.UNKNOWN
    }
  }

  /**
   * Converts the database payment method type to the structured sealed type
   */
  fun InAppPaymentData.PaymentMethodType.toPaymentSourceType(): PaymentSourceType {
    return when (this) {
      InAppPaymentData.PaymentMethodType.PAYPAL -> PaymentSourceType.PayPal
      InAppPaymentData.PaymentMethodType.CARD -> PaymentSourceType.Stripe.CreditCard
      InAppPaymentData.PaymentMethodType.GOOGLE_PAY -> PaymentSourceType.Stripe.GooglePay
      InAppPaymentData.PaymentMethodType.IDEAL -> PaymentSourceType.Stripe.IDEAL
      InAppPaymentData.PaymentMethodType.SEPA_DEBIT -> PaymentSourceType.Stripe.SEPADebit
      InAppPaymentData.PaymentMethodType.UNKNOWN -> PaymentSourceType.Unknown
    }
  }

  /**
   * Converts network ChargeFailure objects into the form we can persist in the database.
   */
  fun ActiveSubscription.ChargeFailure.toInAppPaymentDataChargeFailure(): InAppPaymentData.ChargeFailure {
    return InAppPaymentData.ChargeFailure(
      code = this.code ?: "",
      message = this.message ?: "",
      outcomeNetworkStatus = outcomeNetworkStatus ?: "",
      outcomeNetworkReason = outcomeNetworkReason ?: "",
      outcomeType = outcomeType ?: ""
    )
  }

  /**
   * Converts our database persistable ChargeFailure objects into the form we expect from the network.
   */
  fun InAppPaymentData.ChargeFailure.toActiveSubscriptionChargeFailure(): ActiveSubscription.ChargeFailure {
    return ActiveSubscription.ChargeFailure(
      code,
      message,
      outcomeNetworkStatus,
      outcomeNetworkReason,
      outcomeType
    )
  }

  /**
   * Retrieves the latest payment method type, biasing the result towards what is available in the database and falling
   * back on information in SignalStore. This information is utilized in some error presentation as well as in subscription
   * updates.
   */
  @WorkerThread
  fun getLatestPaymentMethodType(subscriberType: InAppPaymentSubscriberRecord.Type): InAppPaymentData.PaymentMethodType {
    val paymentMethodType = getSubscriber(subscriberType)?.paymentMethodType ?: InAppPaymentData.PaymentMethodType.UNKNOWN
    return if (paymentMethodType != InAppPaymentData.PaymentMethodType.UNKNOWN) {
      paymentMethodType
    } else if (subscriberType == InAppPaymentSubscriberRecord.Type.DONATION) {
      SignalStore.donationsValues().getSubscriptionPaymentSourceType().toPaymentMethodType()
    } else {
      return InAppPaymentData.PaymentMethodType.UNKNOWN
    }
  }

  /**
   * Checks if the latest subscription was manually cancelled by the user. We bias towards what the database tells us and
   * fall back on the SignalStore value (which is deprecated and will be removed in a future release)
   */
  @JvmStatic
  @WorkerThread
  fun isUserManuallyCancelled(subscriberType: InAppPaymentSubscriberRecord.Type): Boolean {
    val latestSubscription = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(subscriberType.inAppPaymentType)

    return if (latestSubscription == null) {
      SignalStore.donationsValues().isUserManuallyCancelled()
    } else {
      latestSubscription.data.cancellation?.reason == InAppPaymentData.Cancellation.Reason.MANUAL
    }
  }

  @JvmStatic
  @WorkerThread
  fun setShouldCancelSubscriptionBeforeNextSubscribeAttempt(subscriber: InAppPaymentSubscriberRecord, shouldCancel: Boolean) {
    setShouldCancelSubscriptionBeforeNextSubscribeAttempt(subscriber.type, subscriber.subscriberId, shouldCancel)
  }

  /**
   * Sets whether we should force a cancellation before our next subscription attempt. This is to help clean up
   * bad state in some edge cases.
   */
  @JvmStatic
  @WorkerThread
  fun setShouldCancelSubscriptionBeforeNextSubscribeAttempt(subscriberType: InAppPaymentSubscriberRecord.Type, subscriberId: SubscriberId?, shouldCancel: Boolean) {
    if (subscriberType == InAppPaymentSubscriberRecord.Type.DONATION) {
      SignalStore.donationsValues().shouldCancelSubscriptionBeforeNextSubscribeAttempt = shouldCancel
    }

    if (subscriberId == null) {
      return
    }

    SignalDatabase.inAppPaymentSubscribers.setRequiresCancel(
      subscriberId = subscriberId,
      requiresCancel = shouldCancel
    )
  }

  /**
   * Retrieves whether or not we should force a cancel before next subscribe attempt for in app payments of the given
   * type. This method will first check the database, and then fall back on the deprecated SignalStore value.
   */
  @JvmStatic
  @WorkerThread
  fun getShouldCancelSubscriptionBeforeNextSubscribeAttempt(subscriberType: InAppPaymentSubscriberRecord.Type): Boolean {
    val latestSubscriber = getSubscriber(subscriberType)

    return latestSubscriber?.requiresCancel ?: if (subscriberType == InAppPaymentSubscriberRecord.Type.DONATION) {
      SignalStore.donationsValues().shouldCancelSubscriptionBeforeNextSubscribeAttempt
    } else {
      false
    }
  }

  /**
   * Grabs a subscriber based off the type and currency
   */
  @JvmStatic
  @Suppress("DEPRECATION")
  @SuppressLint("DiscouragedApi")
  @WorkerThread
  fun getSubscriber(currency: Currency, type: InAppPaymentSubscriberRecord.Type): InAppPaymentSubscriberRecord? {
    val subscriber = SignalDatabase.inAppPaymentSubscribers.getByCurrencyCode(currency.currencyCode, type)

    return if (subscriber == null && type == InAppPaymentSubscriberRecord.Type.DONATION) {
      SignalStore.donationsValues().getSubscriber(currency)
    } else {
      subscriber
    }
  }

  /**
   * Grabs the "active" subscriber according to the selected currency in the value store.
   */
  @JvmStatic
  @WorkerThread
  fun getSubscriber(type: InAppPaymentSubscriberRecord.Type): InAppPaymentSubscriberRecord? {
    val currency = SignalStore.donationsValues().getSubscriptionCurrency(type)

    return getSubscriber(currency, type)
  }

  /**
   * Gets a non-null subscriber for the given type, or throws.
   */
  @JvmStatic
  @WorkerThread
  fun requireSubscriber(type: InAppPaymentSubscriberRecord.Type): InAppPaymentSubscriberRecord {
    return requireNotNull(getSubscriber(type))
  }

  /**
   * Sets the subscriber, writing them to the database.
   */
  @JvmStatic
  @WorkerThread
  fun setSubscriber(subscriber: InAppPaymentSubscriberRecord) {
    SignalDatabase.inAppPaymentSubscribers.insertOrReplace(subscriber)
  }

  /**
   * Checks whether or not a pending donation exists either in the database or via the legacy job watcher.
   */
  @WorkerThread
  fun hasPendingDonation(): Boolean {
    return SignalDatabase.inAppPayments.hasPendingDonation() || DonationRedemptionJobWatcher.hasPendingRedemptionJob()
  }

  /**
   * Emits a stream of status updates for donations of the given type. Only One-time donations and recurring donations are currently supported.
   */
  fun observeInAppPaymentRedemption(type: InAppPaymentTable.Type): Observable<DonationRedemptionJobStatus> {
    val jobStatusObservable: Observable<DonationRedemptionJobStatus> = when (type) {
      InAppPaymentTable.Type.UNKNOWN -> Observable.empty()
      InAppPaymentTable.Type.ONE_TIME_GIFT -> Observable.empty()
      InAppPaymentTable.Type.ONE_TIME_DONATION -> DonationRedemptionJobWatcher.watchOneTimeRedemption()
      InAppPaymentTable.Type.RECURRING_DONATION -> DonationRedemptionJobWatcher.watchSubscriptionRedemption()
      InAppPaymentTable.Type.RECURRING_BACKUP -> Observable.empty()
    }

    val fromDatabase: Observable<DonationRedemptionJobStatus> = Observable.create { emitter ->
      val observer = InAppPaymentObserver {
        val latestInAppPayment = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(type)

        emitter.onNext(Optional.ofNullable(latestInAppPayment))
      }

      AppDependencies.databaseObserver.registerInAppPaymentObserver(observer)
      emitter.setCancellable { AppDependencies.databaseObserver.unregisterObserver(observer) }
    }.switchMap { inAppPaymentOptional ->
      val inAppPayment = inAppPaymentOptional.getOrNull() ?: return@switchMap jobStatusObservable

      val value = when (inAppPayment.state) {
        InAppPaymentTable.State.CREATED -> error("This should have been filtered out.")
        InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION -> {
          DonationRedemptionJobStatus.PendingExternalVerification(
            pendingOneTimeDonation = inAppPayment.toPendingOneTimeDonation(),
            nonVerifiedMonthlyDonation = inAppPayment.toNonVerifiedMonthlyDonation()
          )
        }
        InAppPaymentTable.State.PENDING -> {
          if (inAppPayment.data.redemption?.stage == InAppPaymentData.RedemptionState.Stage.REDEMPTION_STARTED) {
            DonationRedemptionJobStatus.PendingReceiptRedemption
          } else {
            DonationRedemptionJobStatus.PendingReceiptRequest
          }
        }
        InAppPaymentTable.State.END -> {
          if (type.recurring && inAppPayment.data.error != null) {
            DonationRedemptionJobStatus.FailedSubscription
          } else {
            DonationRedemptionJobStatus.None
          }
        }
      }

      Observable.just(value)
    }

    return fromDatabase
      .switchMap {
        if (it == DonationRedemptionJobStatus.None) {
          jobStatusObservable
        } else {
          Observable.just(it)
        }
      }
      .distinctUntilChanged()
  }

  private fun InAppPaymentTable.InAppPayment.toPendingOneTimeDonation(): PendingOneTimeDonation? {
    if (type.recurring) {
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
      },
      amount = data.amount!!,
      badge = data.badge!!,
      timestamp = insertedAt.inWholeMilliseconds,
      error = null,
      pendingVerification = true,
      checkedVerification = data.waitForAuth!!.checkedVerification
    )
  }

  private fun InAppPaymentTable.InAppPayment.toNonVerifiedMonthlyDonation(): NonVerifiedMonthlyDonation? {
    if (!type.recurring) {
      return null
    }

    return NonVerifiedMonthlyDonation(
      timestamp = insertedAt.inWholeMilliseconds,
      price = data.amount!!.toFiatMoney(),
      level = data.level.toInt(),
      checkedVerification = data.waitForAuth!!.checkedVerification
    )
  }

  /**
   * Generates a new request credential that can be used to retrieve a presentation that can be submitted to get a badge or backup.
   */
  fun generateRequestCredential(): ReceiptCredentialRequestContext {
    Log.d(TAG, "Generating request credentials context for token redemption...", true)
    val secureRandom = SecureRandom()
    val randomBytes = Util.getSecretBytes(ReceiptSerial.SIZE)

    return try {
      val receiptSerial = ReceiptSerial(randomBytes)
      val operations = AppDependencies.clientZkReceiptOperations
      operations.createReceiptCredentialRequestContext(secureRandom, receiptSerial)
    } catch (e: InvalidInputException) {
      Log.e(TAG, "Failed to create credential.", e)
      throw AssertionError(e)
    } catch (e: VerificationFailedException) {
      Log.e(TAG, "Failed to create credential.", e)
      throw AssertionError(e)
    }
  }

  /**
   * Common logic for building failures based off payment failure state.
   */
  fun buildPaymentFailure(inAppPayment: InAppPaymentTable.InAppPayment, chargeFailure: ActiveSubscription.ChargeFailure?): InAppPaymentData.Error {
    val builder = InAppPaymentData.Error.Builder()

    if (chargeFailure == null) {
      builder.type = InAppPaymentData.Error.Type.PAYMENT_PROCESSING

      return builder.build()
    }

    val donationProcessor = inAppPayment.data.paymentMethodType.toDonationProcessor()
    if (donationProcessor == DonationProcessor.PAYPAL) {
      builder.type = InAppPaymentData.Error.Type.PAYPAL_CODED_ERROR
      builder.data_ = chargeFailure.code
    } else {
      val declineCode = StripeDeclineCode.getFromCode(chargeFailure.outcomeNetworkReason)
      val failureCode = StripeFailureCode.getFromCode(chargeFailure.code)

      if (failureCode.isKnown) {
        builder.type = InAppPaymentData.Error.Type.STRIPE_FAILURE
        builder.data_ = failureCode.toString()
      } else if (declineCode.isKnown()) {
        builder.type = InAppPaymentData.Error.Type.STRIPE_DECLINED_ERROR
        builder.data_ = declineCode.toString()
      } else {
        builder.type = InAppPaymentData.Error.Type.STRIPE_CODED_ERROR
        builder.data_ = chargeFailure.code
      }
    }

    return builder.build()
  }

  /**
   * Converts a payment method type into the processor that manages it, either Stripe or PayPal.
   */
  fun InAppPaymentData.PaymentMethodType.toDonationProcessor(): DonationProcessor {
    return when (this) {
      InAppPaymentData.PaymentMethodType.UNKNOWN -> DonationProcessor.STRIPE
      InAppPaymentData.PaymentMethodType.GOOGLE_PAY -> DonationProcessor.STRIPE
      InAppPaymentData.PaymentMethodType.CARD -> DonationProcessor.STRIPE
      InAppPaymentData.PaymentMethodType.SEPA_DEBIT -> DonationProcessor.STRIPE
      InAppPaymentData.PaymentMethodType.IDEAL -> DonationProcessor.STRIPE
      InAppPaymentData.PaymentMethodType.PAYPAL -> DonationProcessor.PAYPAL
    }
  }
}
