/*
 * Copyright 2024 Signal Messenger, LLC
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
import org.signal.donations.json.StripePaymentIntent
import org.signal.donations.json.StripeSetupIntent
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.requireSubscriberType
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.subscription.LevelUpdate
import org.thoughtcrime.securesms.subscription.LevelUpdateOperation
import org.thoughtcrime.securesms.util.Environment
import org.whispersystems.signalservice.internal.ServiceResponse
import kotlin.time.Duration.Companion.days

/**
 * Responsible for checking payment state after an external launch, such as for iDEAL
 */
class InAppPaymentAuthCheckJob private constructor(parameters: Parameters) : BaseJob(parameters), StripeApi.PaymentIntentFetcher, StripeApi.SetupIntentHelper {

  private constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxInstancesForFactory(1)
      .build()
  )

  companion object {
    private val TAG = Log.tag(InAppPaymentAuthCheckJob::class.java)
    const val KEY = "InAppPaymentAuthCheckJob"

    @JvmStatic
    fun enqueueIfNeeded() {
      if (SignalDatabase.inAppPayments.hasWaitingForAuth()) {
        AppDependencies.jobManager.add(InAppPaymentAuthCheckJob())
      }
    }
  }

  private val stripeApi = StripeApi(Environment.Donations.STRIPE_CONFIGURATION, this, this, AppDependencies.okHttpClient)

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onRun() {
    migrateLegacyData()

    val unauthorizedInAppPayments = SignalDatabase.inAppPayments.getAllWaitingForAuth()
    Log.i(TAG, "Found ${unauthorizedInAppPayments.size} payments awaiting authorization.", true)

    var hasRetry = false
    for (payment in unauthorizedInAppPayments) {
      val verificationStatus: CheckResult<Unit> = if (payment.type.recurring) {
        synchronized(payment.type.requireSubscriberType().inAppPaymentType) {
          checkRecurringPayment(payment)
        }
      } else {
        checkOneTimePayment(payment)
      }

      when (verificationStatus) {
        is CheckResult.Failure -> {
          markFailed(payment, verificationStatus.errorData)
        }

        CheckResult.Retry -> {
          markChecked(payment)
          hasRetry = true
        }

        is CheckResult.Success -> Unit
      }
    }

    if (hasRetry) {
      throw InAppPaymentRetryException()
    }
  }

  private fun migrateLegacyData() {
    val pending3DSData = SignalStore.inAppPayments.consumePending3DSData()
    if (pending3DSData != null) {
      Log.i(TAG, "Found legacy data. Performing migration.", true)

      SignalDatabase.inAppPayments.insert(
        type = pending3DSData.inAppPayment.type,
        state = InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION,
        subscriberId = if (pending3DSData.inAppPayment.type == InAppPaymentType.RECURRING_DONATION) {
          InAppPaymentsRepository.requireSubscriber(InAppPaymentSubscriberRecord.Type.DONATION).subscriberId
        } else {
          null
        },
        endOfPeriod = null,
        inAppPaymentData = pending3DSData.inAppPayment.data
      )
    }
  }

  private fun checkOneTimePayment(inAppPayment: InAppPaymentTable.InAppPayment): CheckResult<Unit> {
    if (inAppPayment.data.waitForAuth == null) {
      Log.d(TAG, "Could not check one-time payment without data.waitForAuth", true)
      return CheckResult.Failure()
    }

    Log.d(TAG, "Downloading payment intent.")
    val stripeIntentData: StripePaymentIntent = stripeApi.getPaymentIntent(
      StripeIntentAccessor(
        objectType = StripeIntentAccessor.ObjectType.PAYMENT_INTENT,
        intentId = inAppPayment.data.waitForAuth.stripeIntentId,
        intentClientSecret = inAppPayment.data.waitForAuth.stripeClientSecret
      )
    )

    checkIntentStatus(stripeIntentData.status)

    Log.i(TAG, "Creating and inserting receipt.", true)
    val receipt = when (inAppPayment.type) {
      InAppPaymentType.ONE_TIME_DONATION -> InAppPaymentReceiptRecord.createForBoost(inAppPayment.data.amount!!.toFiatMoney())
      InAppPaymentType.ONE_TIME_GIFT -> InAppPaymentReceiptRecord.createForGift(inAppPayment.data.amount!!.toFiatMoney())
      else -> {
        Log.e(TAG, "Unexpected type ${inAppPayment.type}", true)
        return CheckResult.Failure()
      }
    }

    SignalDatabase.donationReceipts.addReceipt(receipt)

    Log.i(TAG, "Verified payment. Updating InAppPayment::${inAppPayment.id.serialize()}")
    SignalDatabase.inAppPayments.update(
      inAppPayment = inAppPayment.copy(
        state = InAppPaymentTable.State.PENDING,
        data = inAppPayment.data.copy(
          waitForAuth = null,
          redemption = InAppPaymentData.RedemptionState(
            stage = InAppPaymentData.RedemptionState.Stage.INIT,
            paymentIntentId = inAppPayment.data.waitForAuth.stripeIntentId
          )
        )
      )
    )

    Log.i(TAG, "Enqueuing job chain.")
    val updatedPayment = SignalDatabase.inAppPayments.getById(inAppPayment.id)
    InAppPaymentOneTimeContextJob.createJobChain(updatedPayment!!).enqueue()
    return CheckResult.Success(Unit)
  }

  private fun checkRecurringPayment(inAppPayment: InAppPaymentTable.InAppPayment): CheckResult<Unit> {
    if (inAppPayment.data.waitForAuth == null) {
      Log.d(TAG, "Could not check recurring payment without data.waitForAuth", true)
      return CheckResult.Failure()
    }

    Log.d(TAG, "Downloading setup intent.")
    val stripeSetupIntent: StripeSetupIntent = stripeApi.getSetupIntent(
      StripeIntentAccessor(
        objectType = StripeIntentAccessor.ObjectType.SETUP_INTENT,
        intentId = inAppPayment.data.waitForAuth.stripeIntentId,
        intentClientSecret = inAppPayment.data.waitForAuth.stripeClientSecret
      )
    )

    val checkIntentStatusResult = checkIntentStatus(stripeSetupIntent.status)
    if (checkIntentStatusResult !is CheckResult.Success) {
      return checkIntentStatusResult
    }

    val subscriber = InAppPaymentsRepository.requireSubscriber(
      when (inAppPayment.type) {
        InAppPaymentType.RECURRING_DONATION -> InAppPaymentSubscriberRecord.Type.DONATION
        InAppPaymentType.RECURRING_BACKUP -> InAppPaymentSubscriberRecord.Type.BACKUP
        else -> {
          Log.e(TAG, "Expected recurring type but found ${inAppPayment.type}", true)
          return CheckResult.Failure()
        }
      }
    )

    if (subscriber.subscriberId != inAppPayment.subscriberId) {
      Log.w(TAG, "Found an old subscription with a subscriber id mismatch. Dropping.", true)

      SignalDatabase.inAppPayments.update(
        inAppPayment = inAppPayment.copy(
          state = InAppPaymentTable.State.END,
          notified = true,
          data = InAppPaymentData(
            waitForAuth = InAppPaymentData.WaitingForAuthorizationState("", "")
          )
        )
      )

      return CheckResult.Failure()
    }

    Log.i(TAG, "Setting default payment method...", true)
    val setPaymentMethodResponse = if (inAppPayment.data.paymentMethodType == InAppPaymentData.PaymentMethodType.IDEAL) {
      AppDependencies.donationsService.setDefaultIdealPaymentMethod(subscriber.subscriberId, stripeSetupIntent.id)
    } else {
      AppDependencies.donationsService.setDefaultStripePaymentMethod(subscriber.subscriberId, stripeSetupIntent.paymentMethod)
    }

    when (val result = checkResult(setPaymentMethodResponse)) {
      is CheckResult.Failure -> return CheckResult.Failure(result.errorData)
      is CheckResult.Retry -> return CheckResult.Retry
      else -> Unit
    }

    Log.d(TAG, "Set default payment method via Signal service.", true)

    val level = inAppPayment.data.level.toString()

    try {
      val updateOperation: LevelUpdateOperation = RecurringInAppPaymentRepository.getOrCreateLevelUpdateOperation(TAG, level)
      Log.d(TAG, "Attempting to set user subscription level to $level", true)

      val updateLevelResponse = AppDependencies.donationsService.updateSubscriptionLevel(
        subscriber.subscriberId,
        level,
        subscriber.currency.currencyCode,
        updateOperation.idempotencyKey.serialize(),
        subscriber.type
      )

      val updateLevelResult = checkResult(updateLevelResponse)
      if (updateLevelResult is CheckResult.Failure) {
        SignalStore.inAppPayments.clearLevelOperations()
        return CheckResult.Failure(updateLevelResult.errorData)
      }

      if (updateLevelResult == CheckResult.Retry) {
        return CheckResult.Retry
      }

      if (updateLevelResponse.status in listOf(200, 204)) {
        Log.d(TAG, "Successfully set user subscription to level $level with response code ${updateLevelResponse.status}", true)
        SignalDatabase.inAppPayments.update(
          inAppPayment = inAppPayment.copy(
            state = InAppPaymentTable.State.PENDING,
            data = inAppPayment.data.copy(
              waitForAuth = null,
              redemption = InAppPaymentData.RedemptionState(
                stage = InAppPaymentData.RedemptionState.Stage.INIT
              )
            )
          )
        )

        Log.d(TAG, "Reading fresh InAppPayment and enqueueing redemption chain.")
        with(SignalDatabase.inAppPayments.getById(inAppPayment.id)!!) {
          InAppPaymentRecurringContextJob.createJobChain(this).enqueue()
        }

        return CheckResult.Success(Unit)
      } else {
        Log.e(TAG, "Unexpected status code ${updateLevelResponse.status} without an application error or execution error.")
        return CheckResult.Failure(errorData = updateLevelResponse.status.toString())
      }
    } finally {
      LevelUpdate.updateProcessingState(false)
    }
  }

  private fun <Result> checkResult(
    serviceResponse: ServiceResponse<Result>
  ): CheckResult<Result> {
    if (serviceResponse.result.isPresent) {
      return CheckResult.Success(serviceResponse.result.get())
    } else if (serviceResponse.applicationError.isPresent) {
      Log.w(TAG, "An application error was present. ${serviceResponse.status}", serviceResponse.applicationError.get(), true)
      return CheckResult.Failure(errorData = serviceResponse.status.toString())
    } else if (serviceResponse.executionError.isPresent) {
      Log.w(TAG, "An execution error was present. ${serviceResponse.status}", serviceResponse.executionError.get(), true)
      return CheckResult.Retry
    }

    error("Should never get here.")
  }

  private fun checkIntentStatus(stripeIntentStatus: StripeIntentStatus?): CheckResult<Unit> {
    when (stripeIntentStatus) {
      null, StripeIntentStatus.SUCCEEDED -> {
        Log.i(TAG, "Stripe intent is in the SUCCEEDED state, we can proceed.", true)
        return CheckResult.Success(Unit)
      }

      StripeIntentStatus.CANCELED -> {
        Log.i(TAG, "Stripe intent is in the cancelled state, we cannot proceed.", true)
        return CheckResult.Failure()
      }

      StripeIntentStatus.REQUIRES_PAYMENT_METHOD -> {
        Log.i(TAG, "Stripe intent payment failed, we cannot proceed.", true)
        return CheckResult.Failure()
      }

      else -> {
        Log.i(TAG, "Stripe intent is still processing, retry later", true)
        return CheckResult.Retry
      }
    }
  }

  private fun markFailed(inAppPayment: InAppPaymentTable.InAppPayment, errorData: String?) {
    SignalDatabase.inAppPayments.update(
      inAppPayment = inAppPayment.copy(
        state = InAppPaymentTable.State.END,
        notified = true,
        data = inAppPayment.data.copy(
          error = InAppPaymentData.Error(
            type = InAppPaymentData.Error.Type.PAYMENT_SETUP,
            data_ = errorData
          ),
          waitForAuth = InAppPaymentData.WaitingForAuthorizationState("", ""),
          redemption = null
        )
      )
    )
  }

  private fun markChecked(inAppPayment: InAppPaymentTable.InAppPayment) {
    val fresh = SignalDatabase.inAppPayments.getById(inAppPayment.id)
    SignalDatabase.inAppPayments.update(
      inAppPayment = fresh!!.copy(
        data = fresh.data.copy(
          waitForAuth = fresh.data.waitForAuth!!.copy(
            checkedVerification = true
          )
        )
      )
    )
  }

  override fun onShouldRetry(e: Exception): Boolean = e is InAppPaymentRetryException
  override fun fetchPaymentIntent(price: FiatMoney, level: Long, sourceType: PaymentSourceType.Stripe): Single<StripeIntentAccessor> {
    error("Not needed, this job should not be creating intents.")
  }

  override fun fetchSetupIntent(inAppPaymentType: InAppPaymentType, sourceType: PaymentSourceType.Stripe): Single<StripeIntentAccessor> {
    error("Not needed, this job should not be creating intents.")
  }

  private sealed interface CheckResult<out T> {
    data class Success<T>(val data: T) : CheckResult<T>
    data class Failure(val errorData: String? = null) : CheckResult<Nothing>
    object Retry : CheckResult<Nothing>
  }

  class Factory : Job.Factory<InAppPaymentAuthCheckJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): InAppPaymentAuthCheckJob {
      return InAppPaymentAuthCheckJob(parameters)
    }
  }
}
