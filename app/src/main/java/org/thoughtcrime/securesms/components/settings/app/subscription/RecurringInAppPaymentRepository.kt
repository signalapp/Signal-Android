package org.thoughtcrime.securesms.components.settings.app.subscription

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError.BadgeRedemptionError
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.InAppPaymentKeepAliveJob
import org.thoughtcrime.securesms.jobs.InAppPaymentRecurringContextJob
import org.thoughtcrime.securesms.jobs.MultiDeviceSubscriptionSyncRequestJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.subscription.LevelUpdate
import org.thoughtcrime.securesms.subscription.LevelUpdateOperation
import org.thoughtcrime.securesms.subscription.Subscription
import org.whispersystems.signalservice.api.services.DonationsService
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
class RecurringInAppPaymentRepository(private val donationsService: DonationsService) {

  fun getActiveSubscription(type: InAppPaymentSubscriberRecord.Type): Single<ActiveSubscription> {
    val localSubscription = InAppPaymentsRepository.getSubscriber(type)
    return if (localSubscription != null) {
      Single.fromCallable { donationsService.getSubscription(localSubscription.subscriberId) }
        .subscribeOn(Schedulers.io())
        .flatMap(ServiceResponse<ActiveSubscription>::flattenResult)
        .doOnSuccess { activeSubscription ->
          if (activeSubscription.isActive && activeSubscription.activeSubscription.endOfCurrentPeriod > SignalStore.donationsValues().getLastEndOfPeriod()) {
            InAppPaymentKeepAliveJob.enqueueAndTrackTime(System.currentTimeMillis().milliseconds)
          }
        }
    } else {
      Single.just(ActiveSubscription.EMPTY)
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
            name = levelConfig.name,
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
    Log.d(TAG, "Ensuring SubscriberId exists on Signal service {isRotation?$isRotation}...", true)
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
            currencyCode = SignalStore.donationsValues().getSubscriptionCurrency(subscriberType).currencyCode,
            type = subscriberType,
            requiresCancel = false,
            paymentMethodType = InAppPaymentData.PaymentMethodType.UNKNOWN
          )
        )

        SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
        StorageSyncHelper.scheduleSyncForDataChange()
      }
  }

  fun cancelActiveSubscription(subscriberType: InAppPaymentSubscriberRecord.Type): Completable {
    Log.d(TAG, "Canceling active subscription...", true)
    return Single
      .fromCallable {
        val localSubscriber = InAppPaymentsRepository.requireSubscriber(subscriberType)

        donationsService.cancelSubscription(localSubscriber.subscriberId)
      }
      .subscribeOn(Schedulers.io())
      .flatMap(ServiceResponse<EmptyResponse>::flattenResult)
      .ignoreElement()
      .doOnComplete { Log.d(TAG, "Cancelled active subscription.", true) }
  }

  fun cancelActiveSubscriptionIfNecessary(subscriberType: InAppPaymentSubscriberRecord.Type): Completable {
    return Single.fromCallable { InAppPaymentsRepository.getShouldCancelSubscriptionBeforeNextSubscribeAttempt(subscriberType) }.flatMapCompletable {
      if (it) {
        Log.d(TAG, "Cancelling active subscription...", true)
        cancelActiveSubscription(subscriberType).doOnComplete {
          SignalStore.donationsValues().updateLocalStateForManualCancellation(subscriberType)
          MultiDeviceSubscriptionSyncRequestJob.enqueue()
        }
      } else {
        Completable.complete()
      }
    }
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
            ApplicationDependencies.getDonationsService().updateSubscriptionLevel(
              subscriber.subscriberId,
              subscriptionLevel,
              subscriber.currencyCode,
              levelUpdateOperation.idempotencyKey.serialize(),
              subscriberType
            )
          }
          .flatMapCompletable {
            if (it.status == 200 || it.status == 204) {
              Log.d(TAG, "Successfully set user subscription to level $subscriptionLevel with response code ${it.status}", true)
              SignalStore.donationsValues().updateLocalStateForLocalSubscribe(subscriberType)
              syncAccountRecord().subscribe()
              LevelUpdate.updateProcessingState(false)
              Completable.complete()
            } else {
              if (it.applicationError.isPresent) {
                Log.w(TAG, "Failed to set user subscription to level $subscriptionLevel with response code ${it.status}", it.applicationError.get(), true)
                SignalStore.donationsValues().clearLevelOperations()
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
                  Log.d(TAG, "Failure during redemption chain.", true)
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

  companion object {
    private val TAG = Log.tag(RecurringInAppPaymentRepository::class.java)

    fun getOrCreateLevelUpdateOperation(tag: String, subscriptionLevel: String): LevelUpdateOperation {
      Log.d(tag, "Retrieving level update operation for $subscriptionLevel")
      val levelUpdateOperation = SignalStore.donationsValues().getLevelOperation(subscriptionLevel)
      return if (levelUpdateOperation == null) {
        val newOperation = LevelUpdateOperation(
          idempotencyKey = IdempotencyKey.generate(),
          level = subscriptionLevel
        )

        SignalStore.donationsValues().setLevelOperation(newOperation)
        LevelUpdate.updateProcessingState(true)
        Log.d(tag, "Created a new operation for $subscriptionLevel")
        newOperation
      } else {
        LevelUpdate.updateProcessingState(true)
        Log.d(tag, "Reusing operation for $subscriptionLevel")
        levelUpdateOperation
      }
    }
  }

  /**
   * Update local state information and schedule a storage sync for the change. This method
   * assumes you've already properly called the DELETE method for the stored ID on the server.
   */
  private fun updateLocalSubscriptionStateAndScheduleDataSync(subscriberType: InAppPaymentSubscriberRecord.Type): Completable {
    return Completable.fromAction {
      Log.d(TAG, "Marking subscription cancelled...", true)
      SignalStore.donationsValues().updateLocalStateForManualCancellation(subscriberType)
      MultiDeviceSubscriptionSyncRequestJob.enqueue()
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }
}
