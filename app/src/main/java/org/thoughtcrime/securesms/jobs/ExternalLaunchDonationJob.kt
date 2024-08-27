/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.jobs

import io.reactivex.rxjava3.core.Single
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import org.signal.donations.StripeApi
import org.signal.donations.StripeIntentAccessor
import org.signal.donations.json.StripeIntentStatus
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe.Stripe3DSData
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError.Companion.toDonationErrorValue
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.DonationErrorValue
import org.thoughtcrime.securesms.database.model.databaseprotos.TerminalDonationQueue
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.subscription.LevelUpdate
import org.thoughtcrime.securesms.util.Environment
import org.whispersystems.signalservice.internal.ServiceResponse

/**
 * Proceeds with an externally approved (say, in a bank app) donation
 * and continues to process it.
 */
@Deprecated("Replaced with InAppPaymentAuthCheckJob")
class ExternalLaunchDonationJob private constructor(
  private val stripe3DSData: Stripe3DSData,
  parameters: Parameters
) : BaseJob(parameters), StripeApi.PaymentIntentFetcher, StripeApi.SetupIntentHelper {

  private var donationError: DonationError? = null

  companion object {
    const val KEY = "ExternalLaunchDonationJob"

    private val TAG = Log.tag(ExternalLaunchDonationJob::class.java)

    private fun createDonationError(stripe3DSData: Stripe3DSData, throwable: Throwable): DonationError {
      val source = stripe3DSData.inAppPayment.type.toErrorSource()
      return DonationError.PaymentSetupError.GenericError(source, throwable)
    }
  }

  private val stripeApi = StripeApi(Environment.Donations.STRIPE_CONFIGURATION, this, this, AppDependencies.okHttpClient)

  override fun serialize(): ByteArray {
    return stripe3DSData.toProtoBytes()
  }

  override fun getFactoryKey(): String = KEY

  override fun onFailure() {
    if (donationError != null) {
      when (stripe3DSData.inAppPayment.type) {
        InAppPaymentType.ONE_TIME_DONATION -> {
          SignalStore.inAppPayments.setPendingOneTimeDonation(
            DonationSerializationHelper.createPendingOneTimeDonationProto(
              Badges.fromDatabaseBadge(stripe3DSData.inAppPayment.data.badge!!),
              stripe3DSData.paymentSourceType,
              stripe3DSData.inAppPayment.data.amount!!.toFiatMoney()
            ).copy(
              error = donationError?.toDonationErrorValue()
            )
          )
        }

        InAppPaymentType.RECURRING_DONATION -> {
          SignalStore.inAppPayments.appendToTerminalDonationQueue(
            TerminalDonationQueue.TerminalDonation(
              level = stripe3DSData.inAppPayment.data.level,
              isLongRunningPaymentMethod = stripe3DSData.isLongRunning,
              error = donationError?.toDonationErrorValue()
            )
          )
        }

        else -> Log.w(TAG, "Job failed with donation error for type: ${stripe3DSData.inAppPayment.type}")
      }
    }
  }

  override fun onRun() {
    when (stripe3DSData.stripeIntentAccessor.objectType) {
      StripeIntentAccessor.ObjectType.NONE -> {
        Log.w(TAG, "NONE type does not require confirmation. Failing Permanently.")
        throw Exception()
      }

      StripeIntentAccessor.ObjectType.PAYMENT_INTENT -> runForPaymentIntent()
      StripeIntentAccessor.ObjectType.SETUP_INTENT -> runForSetupIntent()
    }
  }

  private fun runForPaymentIntent() {
    Log.d(TAG, "Downloading payment intent...")
    val stripePaymentIntent = stripeApi.getPaymentIntent(stripe3DSData.stripeIntentAccessor)
    checkIntentStatus(stripePaymentIntent.status)

    Log.i(TAG, "Creating and inserting donation receipt record.", true)
    val inAppPaymentReceiptRecord = if (stripe3DSData.inAppPayment.type == InAppPaymentType.ONE_TIME_DONATION) {
      InAppPaymentReceiptRecord.createForBoost(stripe3DSData.inAppPayment.data.amount!!.toFiatMoney())
    } else {
      InAppPaymentReceiptRecord.createForGift(stripe3DSData.inAppPayment.data.amount!!.toFiatMoney())
    }

    SignalDatabase.donationReceipts.addReceipt(inAppPaymentReceiptRecord)

    Log.i(TAG, "Creating and inserting one-time pending donation.", true)
    SignalStore.inAppPayments.setPendingOneTimeDonation(
      DonationSerializationHelper.createPendingOneTimeDonationProto(
        Badges.fromDatabaseBadge(stripe3DSData.inAppPayment.data.badge!!),
        stripe3DSData.paymentSourceType,
        stripe3DSData.inAppPayment.data.amount.toFiatMoney()
      )
    )

    Log.i(TAG, "Continuing job chain...", true)
  }

  private fun runForSetupIntent() {
    Log.d(TAG, "Downloading setup intent...")
    val stripeSetupIntent = stripeApi.getSetupIntent(stripe3DSData.stripeIntentAccessor)
    checkIntentStatus(stripeSetupIntent.status)

    val subscriber = InAppPaymentsRepository.requireSubscriber(InAppPaymentSubscriberRecord.Type.DONATION)

    Log.i(TAG, "Setting default payment method...", true)
    val setPaymentMethodResponse = if (stripe3DSData.paymentSourceType == PaymentSourceType.Stripe.IDEAL) {
      AppDependencies.donationsService
        .setDefaultIdealPaymentMethod(subscriber.subscriberId, stripeSetupIntent.id)
    } else {
      AppDependencies.donationsService
        .setDefaultStripePaymentMethod(subscriber.subscriberId, stripeSetupIntent.paymentMethod!!)
    }

    getResultOrThrow(setPaymentMethodResponse)

    Log.i(TAG, "Set default payment method via Signal service!", true)
    Log.i(TAG, "Storing the subscription payment source type locally.", true)
    SignalStore.inAppPayments.setSubscriptionPaymentSourceType(stripe3DSData.paymentSourceType)

    val subscriptionLevel = stripe3DSData.inAppPayment.data.level.toString()

    try {
      val levelUpdateOperation = RecurringInAppPaymentRepository.getOrCreateLevelUpdateOperation(TAG, subscriptionLevel)
      Log.d(TAG, "Attempting to set user subscription level to $subscriptionLevel", true)

      val updateSubscriptionLevelResponse = AppDependencies.donationsService.updateSubscriptionLevel(
        subscriber.subscriberId,
        subscriptionLevel,
        subscriber.currency.currencyCode,
        levelUpdateOperation.idempotencyKey.serialize(),
        subscriber.type
      )

      getResultOrThrow(updateSubscriptionLevelResponse, doOnApplicationError = {
        SignalStore.inAppPayments.clearLevelOperations()
      })

      if (updateSubscriptionLevelResponse.status in listOf(200, 204)) {
        Log.d(TAG, "Successfully set user subscription to level $subscriptionLevel with response code ${updateSubscriptionLevelResponse.status}", true)
        SignalStore.inAppPayments.updateLocalStateForLocalSubscribe(subscriber.type)
        SignalStore.inAppPayments.setVerifiedSubscription3DSData(stripe3DSData)
        SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
        StorageSyncHelper.scheduleSyncForDataChange()
      } else {
        error("Unexpected status code ${updateSubscriptionLevelResponse.status} without an application error or execution error.")
      }
    } finally {
      LevelUpdate.updateProcessingState(false)
    }
  }

  private fun checkIntentStatus(stripeIntentStatus: StripeIntentStatus?) {
    when (stripeIntentStatus) {
      null, StripeIntentStatus.SUCCEEDED -> {
        Log.i(TAG, "Stripe Intent is in the SUCCEEDED state, we can proceed.", true)
      }

      StripeIntentStatus.CANCELED -> {
        Log.i(TAG, "Stripe Intent is cancelled, we cannot proceed.", true)
        donationError = createDonationError(stripe3DSData, Exception("User cancelled payment."))
        throw donationError!!
      }

      StripeIntentStatus.REQUIRES_PAYMENT_METHOD -> {
        Log.i(TAG, "Stripe Intent payment failed, we cannot proceed.", true)
        donationError = createDonationError(stripe3DSData, Exception("payment failed"))
        throw donationError!!
      }

      else -> {
        Log.i(TAG, "Stripe Intent is still processing, retry later. $stripeIntentStatus", true)
        throw RetryException()
      }
    }
  }

  private fun <Result> getResultOrThrow(
    serviceResponse: ServiceResponse<Result>,
    doOnApplicationError: () -> Unit = {}
  ): Result {
    if (serviceResponse.result.isPresent) {
      return serviceResponse.result.get()
    } else if (serviceResponse.applicationError.isPresent) {
      Log.w(TAG, "An application error was present. ${serviceResponse.status}", serviceResponse.applicationError.get(), true)
      doOnApplicationError()

      SignalStore.inAppPayments.appendToTerminalDonationQueue(
        TerminalDonationQueue.TerminalDonation(
          level = stripe3DSData.inAppPayment.data.level,
          isLongRunningPaymentMethod = stripe3DSData.isLongRunning,
          error = DonationErrorValue(
            DonationErrorValue.Type.PAYMENT,
            code = serviceResponse.status.toString()
          )
        )
      )

      throw serviceResponse.applicationError.get()
    } else if (serviceResponse.executionError.isPresent) {
      Log.w(TAG, "An execution error was present. ${serviceResponse.status}", serviceResponse.executionError.get(), true)
      throw RetryException(serviceResponse.executionError.get())
    }

    error("Should never get here.")
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is RetryException
  }

  class RetryException(cause: Throwable? = null) : Exception(cause)

  class Factory : Job.Factory<ExternalLaunchDonationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ExternalLaunchDonationJob {
      if (serializedData == null) {
        error("Unexpected null value for serialized data")
      }

      val stripe3DSData = parseSerializedData(serializedData)

      return ExternalLaunchDonationJob(stripe3DSData, parameters)
    }

    companion object {
      fun parseSerializedData(serializedData: ByteArray): Stripe3DSData {
        return Stripe3DSData.fromProtoBytes(serializedData)
      }
    }
  }

  override fun fetchPaymentIntent(price: FiatMoney, level: Long, sourceType: PaymentSourceType.Stripe): Single<StripeIntentAccessor> {
    error("Not needed, this job should not be creating intents.")
  }

  override fun fetchSetupIntent(inAppPaymentType: InAppPaymentType, sourceType: PaymentSourceType.Stripe): Single<StripeIntentAccessor> {
    error("Not needed, this job should not be creating intents.")
  }
}
