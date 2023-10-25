/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.jobs

import io.reactivex.rxjava3.core.Single
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.PaymentSourceType
import org.signal.donations.StripeApi
import org.signal.donations.StripeIntentAccessor
import org.signal.donations.json.StripeIntentStatus
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper
import org.thoughtcrime.securesms.components.settings.app.subscription.MonthlyDonationRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe.Stripe3DSData
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DonationReceiptRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.DonationErrorValue
import org.thoughtcrime.securesms.database.model.databaseprotos.TerminalDonationQueue
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.subscription.LevelUpdate
import org.thoughtcrime.securesms.util.Environment
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.push.DonationProcessor
import java.util.concurrent.TimeUnit

/**
 * Proceeds with an externally approved (say, in a bank app) donation
 * and continues to process it.
 */
class ExternalLaunchDonationJob private constructor(
  private val stripe3DSData: Stripe3DSData,
  parameters: Parameters
) : BaseJob(parameters), StripeApi.PaymentIntentFetcher, StripeApi.SetupIntentHelper {

  companion object {
    const val KEY = "ExternalLaunchDonationJob"

    private val TAG = Log.tag(ExternalLaunchDonationJob::class.java)

    @JvmStatic
    fun enqueueIfNecessary() {
      val stripe3DSData = SignalStore.donationsValues().consumePending3DSData(-1L) ?: return

      val jobChain = when (stripe3DSData.gatewayRequest.donateToSignalType) {
        DonateToSignalType.ONE_TIME -> BoostReceiptRequestResponseJob.createJobChainForBoost(
          stripe3DSData.stripeIntentAccessor.intentId,
          DonationProcessor.STRIPE,
          -1L,
          TerminalDonationQueue.TerminalDonation(
            level = stripe3DSData.gatewayRequest.level,
            isLongRunningPaymentMethod = stripe3DSData.paymentSourceType == PaymentSourceType.Stripe.SEPADebit
          )
        )

        DonateToSignalType.MONTHLY -> SubscriptionReceiptRequestResponseJob.createSubscriptionContinuationJobChain(
          -1L,
          TerminalDonationQueue.TerminalDonation(
            level = stripe3DSData.gatewayRequest.level,
            isLongRunningPaymentMethod = stripe3DSData.paymentSourceType.isBankTransfer
          )
        )

        DonateToSignalType.GIFT -> BoostReceiptRequestResponseJob.createJobChainForGift(
          stripe3DSData.stripeIntentAccessor.intentId,
          stripe3DSData.gatewayRequest.recipientId,
          stripe3DSData.gatewayRequest.additionalMessage,
          stripe3DSData.gatewayRequest.level,
          DonationProcessor.STRIPE,
          -1L,
          TerminalDonationQueue.TerminalDonation(
            level = stripe3DSData.gatewayRequest.level,
            isLongRunningPaymentMethod = stripe3DSData.paymentSourceType == PaymentSourceType.Stripe.SEPADebit
          )
        )
      }

      val checkJob = ExternalLaunchDonationJob(
        stripe3DSData,
        Parameters.Builder()
          .setQueue(if (stripe3DSData.gatewayRequest.donateToSignalType == DonateToSignalType.MONTHLY) DonationReceiptRedemptionJob.SUBSCRIPTION_QUEUE else DonationReceiptRedemptionJob.ONE_TIME_QUEUE)
          .addConstraint(NetworkConstraint.KEY)
          .setMaxAttempts(Parameters.UNLIMITED)
          .setLifespan(TimeUnit.DAYS.toDays(1))
          .build()
      )

      jobChain.after(checkJob).enqueue()
    }
  }

  private val stripeApi = StripeApi(Environment.Donations.STRIPE_CONFIGURATION, this, this, ApplicationDependencies.getOkHttpClient())

  override fun serialize(): ByteArray {
    return stripe3DSData.toProtoBytes()
  }

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

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
    val donationReceiptRecord = if (stripe3DSData.gatewayRequest.donateToSignalType == DonateToSignalType.ONE_TIME) {
      DonationReceiptRecord.createForBoost(stripe3DSData.gatewayRequest.fiat)
    } else {
      DonationReceiptRecord.createForGift(stripe3DSData.gatewayRequest.fiat)
    }

    SignalDatabase.donationReceipts.addReceipt(donationReceiptRecord)

    Log.i(TAG, "Creating and inserting one-time pending donation.", true)
    SignalStore.donationsValues().setPendingOneTimeDonation(
      DonationSerializationHelper.createPendingOneTimeDonationProto(
        stripe3DSData.gatewayRequest.badge,
        stripe3DSData.paymentSourceType,
        stripe3DSData.gatewayRequest.fiat
      )
    )

    Log.i(TAG, "Continuing job chain...", true)
  }

  private fun runForSetupIntent() {
    Log.d(TAG, "Downloading setup intent...")
    val stripeSetupIntent = stripeApi.getSetupIntent(stripe3DSData.stripeIntentAccessor)
    checkIntentStatus(stripeSetupIntent.status)

    val subscriber = SignalStore.donationsValues().requireSubscriber()

    Log.i(TAG, "Setting default payment method...", true)
    val setPaymentMethodResponse = if (stripe3DSData.paymentSourceType == PaymentSourceType.Stripe.IDEAL) {
      ApplicationDependencies.getDonationsService()
        .setDefaultIdealPaymentMethod(subscriber.subscriberId, stripeSetupIntent.id)
    } else {
      ApplicationDependencies.getDonationsService()
        .setDefaultStripePaymentMethod(subscriber.subscriberId, stripeSetupIntent.paymentMethod!!)
    }

    getResultOrThrow(setPaymentMethodResponse)

    Log.i(TAG, "Set default payment method via Signal service!", true)
    Log.i(TAG, "Storing the subscription payment source type locally.", true)
    SignalStore.donationsValues().setSubscriptionPaymentSourceType(stripe3DSData.paymentSourceType)

    val subscriptionLevel = stripe3DSData.gatewayRequest.level.toString()

    try {
      val levelUpdateOperation = MonthlyDonationRepository.getOrCreateLevelUpdateOperation(TAG, subscriptionLevel)
      Log.d(TAG, "Attempting to set user subscription level to $subscriptionLevel", true)

      val updateSubscriptionLevelResponse = ApplicationDependencies.getDonationsService().updateSubscriptionLevel(
        subscriber.subscriberId,
        subscriptionLevel,
        subscriber.currencyCode,
        levelUpdateOperation.idempotencyKey.serialize(),
        SubscriptionReceiptRequestResponseJob.MUTEX
      )

      getResultOrThrow(updateSubscriptionLevelResponse, doOnApplicationError = {
        SignalStore.donationsValues().clearLevelOperations()
      })

      if (updateSubscriptionLevelResponse.status in listOf(200, 204)) {
        Log.d(TAG, "Successfully set user subscription to level $subscriptionLevel with response code ${updateSubscriptionLevelResponse.status}", true)
        SignalStore.donationsValues().updateLocalStateForLocalSubscribe()
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
        throw Exception("User cancelled payment.")
      }

      else -> {
        Log.i(TAG, "Stripe Intent is still processing, retry later.", true)
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

      SignalStore.donationsValues().appendToTerminalDonationQueue(
        TerminalDonationQueue.TerminalDonation(
          level = stripe3DSData.gatewayRequest.level,
          isLongRunningPaymentMethod = stripe3DSData.gatewayRequest.donateToSignalType == DonateToSignalType.MONTHLY && stripe3DSData.paymentSourceType.isBankTransfer ||
            stripe3DSData.paymentSourceType == PaymentSourceType.Stripe.SEPADebit,
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

      val stripe3DSData = Stripe3DSData.fromProtoBytes(serializedData, -1L)

      return ExternalLaunchDonationJob(stripe3DSData, parameters)
    }
  }

  override fun fetchPaymentIntent(price: FiatMoney, level: Long, sourceType: PaymentSourceType.Stripe): Single<StripeIntentAccessor> {
    error("Not needed, this job should not be creating intents.")
  }

  override fun fetchSetupIntent(sourceType: PaymentSourceType.Stripe): Single<StripeIntentAccessor> {
    error("Not needed, this job should not be creating intents.")
  }
}
