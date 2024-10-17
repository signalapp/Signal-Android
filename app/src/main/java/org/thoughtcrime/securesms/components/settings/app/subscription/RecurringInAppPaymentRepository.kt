package org.thoughtcrime.securesms.components.settings.app.subscription

import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.requireSubscriberType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError.BadgeRedemptionError
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.InAppPaymentKeepAliveJob
import org.thoughtcrime.securesms.jobs.InAppPaymentRecurringContextJob
import org.thoughtcrime.securesms.jobs.MultiDeviceSubscriptionSyncRequestJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.subscription.LevelUpdate
import org.thoughtcrime.securesms.subscription.LevelUpdateOperation
import org.thoughtcrime.securesms.subscription.Subscription
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.IdempotencyKey
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.internal.EmptyResponse
import org.whispersystems.signalservice.internal.ServiceResponse
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * Repository which can query for the user's active subscription as well as a list of available subscriptions,
 * in the currency indicated.
 */
object RecurringInAppPaymentRepository {

  private val TAG = Log.tag(RecurringInAppPaymentRepository::class.java)

  private val donationsService = AppDependencies.donationsService

  fun getActiveSubscription(type: InAppPaymentSubscriberRecord.Type): Single<ActiveSubscription> {
    return Single.fromCallable {
      getActiveSubscriptionSync(type).getOrThrow()
    }.subscribeOn(Schedulers.io())
  }

  @WorkerThread
  fun getActiveSubscriptionSync(type: InAppPaymentSubscriberRecord.Type): Result<ActiveSubscription> {
    val response = InAppPaymentsRepository.getSubscriber(type)?.let {
      donationsService.getSubscription(it.subscriberId)
    } ?: return Result.success(ActiveSubscription.EMPTY)

    return try {
      val result = response.resultOrThrow
      if (result.isActive && result.activeSubscription.endOfCurrentPeriod > SignalStore.inAppPayments.getLastEndOfPeriod()) {
        InAppPaymentKeepAliveJob.enqueueAndTrackTime(System.currentTimeMillis().milliseconds)
      }

      Result.success(result)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun getSubscriptions(): Single<List<Subscription>> {
    return Single
      .fromCallable { donationsService.getDonationsConfiguration(Locale.getDefault()) }
      .subscribeOn(Schedulers.io())
      .flatMap { it.flattenResult() }
      .map { config ->
        config.getSubscriptionLevels().map { (level, levelConfig) ->
          Subscription(
            id = level.toString(),
            level = level,
            badge = Badges.fromServiceBadge(levelConfig.badge),
            prices = config.getSubscriptionAmounts(level)
          )
        }
      }
  }

  fun syncAccountRecord(): Completable {
    return Completable.fromAction {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Since PayPal and Stripe can't interoperate, we need to be able to rotate the subscriber ID
   * in case of failures.
   */
  fun rotateSubscriberId(subscriberType: InAppPaymentSubscriberRecord.Type): Completable {
    Log.d(TAG, "Rotating SubscriberId due to alternate payment processor...", true)
    val cancelCompletable: Completable = if (InAppPaymentsRepository.getSubscriber(subscriberType) != null) {
      cancelActiveSubscription(subscriberType).andThen(updateLocalSubscriptionStateAndScheduleDataSync(subscriberType))
    } else {
      Completable.complete()
    }

    return cancelCompletable.andThen(ensureSubscriberId(subscriberType, isRotation = true))
  }

  fun ensureSubscriberId(subscriberType: InAppPaymentSubscriberRecord.Type, isRotation: Boolean = false): Completable {
    Log.d(TAG, "Ensuring SubscriberId for type $subscriberType exists on Signal service {isRotation?$isRotation}...", true)
    val subscriberId: SubscriberId = if (isRotation) {
      SubscriberId.generate()
    } else {
      InAppPaymentsRepository.getSubscriber(subscriberType)?.subscriberId ?: SubscriberId.generate()
    }

    return Single
      .fromCallable {
        donationsService.putSubscription(subscriberId)
      }
      .subscribeOn(Schedulers.io())
      .flatMap(ServiceResponse<EmptyResponse>::flattenResult).ignoreElement()
      .doOnComplete {
        Log.d(TAG, "Successfully set SubscriberId exists on Signal service.", true)

        InAppPaymentsRepository.setSubscriber(
          InAppPaymentSubscriberRecord(
            subscriberId = subscriberId,
            currency = SignalStore.inAppPayments.getSubscriptionCurrency(subscriberType),
            type = subscriberType,
            requiresCancel = false,
            paymentMethodType = InAppPaymentData.PaymentMethodType.UNKNOWN
          )
        )

        SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
        StorageSyncHelper.scheduleSyncForDataChange()
      }
  }

  fun cancelActiveSubscriptionSync(subscriberType: InAppPaymentSubscriberRecord.Type) {
    Log.d(TAG, "Canceling active subscription...", true)
    val localSubscriber = InAppPaymentsRepository.requireSubscriber(subscriberType)

    val serviceResponse: ServiceResponse<EmptyResponse> = donationsService.cancelSubscription(localSubscriber.subscriberId)
    serviceResponse.resultOrThrow

    Log.d(TAG, "Cancelled active subscription.", true)
    SignalStore.inAppPayments.updateLocalStateForManualCancellation(subscriberType)
    MultiDeviceSubscriptionSyncRequestJob.enqueue()
    InAppPaymentsRepository.scheduleSyncForAccountRecordChange()
  }

  @CheckResult
  fun cancelActiveSubscription(subscriberType: InAppPaymentSubscriberRecord.Type): Completable {
    return Completable
      .fromAction { cancelActiveSubscriptionSync(subscriberType) }
      .subscribeOn(Schedulers.io())
  }

  fun cancelActiveSubscriptionIfNecessary(subscriberType: InAppPaymentSubscriberRecord.Type): Completable {
    return Single.fromCallable { InAppPaymentsRepository.getShouldCancelSubscriptionBeforeNextSubscribeAttempt(subscriberType) }.flatMapCompletable {
      if (it) {
        cancelActiveSubscription(subscriberType).doOnComplete {
          SignalStore.inAppPayments.updateLocalStateForManualCancellation(subscriberType)
          MultiDeviceSubscriptionSyncRequestJob.enqueue()
        }
      } else {
        Completable.complete()
      }
    }.subscribeOn(Schedulers.io())
  }

  fun getPaymentSourceTypeOfLatestSubscription(subscriberType: InAppPaymentSubscriberRecord.Type): Single<PaymentSourceType> {
    return Single.fromCallable {
      InAppPaymentsRepository.getLatestPaymentMethodType(subscriberType).toPaymentSourceType()
    }
  }

  fun setSubscriptionLevel(inAppPayment: InAppPaymentTable.InAppPayment, paymentSourceType: PaymentSourceType): Completable {
    val subscriptionLevel = inAppPayment.data.level.toString()
    val isLongRunning = paymentSourceType.isBankTransfer
    val subscriberType = inAppPayment.type.requireSubscriberType()
    val errorSource = subscriberType.inAppPaymentType.toErrorSource()

    return getOrCreateLevelUpdateOperation(subscriptionLevel)
      .flatMapCompletable { levelUpdateOperation ->
        val subscriber = InAppPaymentsRepository.requireSubscriber(subscriberType)
        SignalDatabase.inAppPayments.update(
          inAppPayment = inAppPayment.copy(
            subscriberId = subscriber.subscriberId,
            data = inAppPayment.data.copy(
              redemption = InAppPaymentData.RedemptionState(
                stage = InAppPaymentData.RedemptionState.Stage.INIT
              )
            )
          )
        )

        val timeoutError = if (isLongRunning) {
          BadgeRedemptionError.DonationPending(errorSource, inAppPayment)
        } else {
          BadgeRedemptionError.TimeoutWaitingForTokenError(errorSource)
        }

        Log.d(TAG, "Attempting to set user subscription level to $subscriptionLevel", true)
        Single
          .fromCallable {
            AppDependencies.donationsService.updateSubscriptionLevel(
              subscriber.subscriberId,
              subscriptionLevel,
              subscriber.currency.currencyCode,
              levelUpdateOperation.idempotencyKey.serialize(),
              subscriberType
            )
          }
          .flatMapCompletable {
            if (it.status == 200 || it.status == 204) {
              Log.d(TAG, "Successfully set user subscription to level $subscriptionLevel with response code ${it.status}", true)
              SignalStore.inAppPayments.updateLocalStateForLocalSubscribe(subscriberType)
              syncAccountRecord().subscribe()
              LevelUpdate.updateProcessingState(false)
              Completable.complete()
            } else {
              if (it.applicationError.isPresent) {
                Log.w(TAG, "Failed to set user subscription to level $subscriptionLevel with response code ${it.status}", it.applicationError.get(), true)
                SignalStore.inAppPayments.clearLevelOperations()
              } else {
                Log.w(TAG, "Failed to set user subscription to level $subscriptionLevel", it.executionError.orElse(null), true)
              }

              LevelUpdate.updateProcessingState(false)
              it.flattenResult().ignoreElement()
            }
          }.andThen(
            Single.fromCallable {
              Log.d(TAG, "Enqueuing request response job chain.", true)
              val freshPayment = SignalDatabase.inAppPayments.getById(inAppPayment.id)!!
              InAppPaymentRecurringContextJob.createJobChain(freshPayment).enqueue()
            }.flatMap {
              Log.d(TAG, "Awaiting completion of redemption chain for up to 10 seconds.", true)
              InAppPaymentsRepository.observeUpdates(inAppPayment.id).filter {
                it.state == InAppPaymentTable.State.END
              }.take(1).map {
                if (it.data.error != null) {
                  Log.d(TAG, "Failure during redemption chain: ${it.data.error}", true)
                  throw DonationError.genericBadgeRedemptionFailure(errorSource)
                }
                it
              }.firstOrError()
            }.timeout(10, TimeUnit.SECONDS, Single.error(timeoutError)).ignoreElement()
          )
      }.doOnError {
        LevelUpdate.updateProcessingState(false)
      }.subscribeOn(Schedulers.io())
  }

  private fun getOrCreateLevelUpdateOperation(subscriptionLevel: String): Single<LevelUpdateOperation> = Single.fromCallable {
    getOrCreateLevelUpdateOperation(TAG, subscriptionLevel)
  }

  fun getOrCreateLevelUpdateOperation(tag: String, subscriptionLevel: String): LevelUpdateOperation {
    Log.d(tag, "Retrieving level update operation for $subscriptionLevel")
    val levelUpdateOperation = SignalStore.inAppPayments.getLevelOperation(subscriptionLevel)
    return if (levelUpdateOperation == null) {
      val newOperation = LevelUpdateOperation(
        idempotencyKey = IdempotencyKey.generate(),
        level = subscriptionLevel
      )

      SignalStore.inAppPayments.setLevelOperation(newOperation)
      LevelUpdate.updateProcessingState(true)
      Log.d(tag, "Created a new operation for $subscriptionLevel")
      newOperation
    } else {
      LevelUpdate.updateProcessingState(true)
      Log.d(tag, "Reusing operation for $subscriptionLevel")
      levelUpdateOperation
    }
  }

  /**
   * Update local state information and schedule a storage sync for the change. This method
   * assumes you've already properly called the DELETE method for the stored ID on the server.
   */
  private fun updateLocalSubscriptionStateAndScheduleDataSync(subscriberType: InAppPaymentSubscriberRecord.Type): Completable {
    return Completable.fromAction {
      Log.d(TAG, "Marking subscription cancelled...", true)
      SignalStore.inAppPayments.updateLocalStateForManualCancellation(subscriberType)
      MultiDeviceSubscriptionSyncRequestJob.enqueue()
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }
}
