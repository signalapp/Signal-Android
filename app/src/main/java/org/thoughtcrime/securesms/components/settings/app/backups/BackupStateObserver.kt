/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import androidx.annotation.WorkerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.core.util.throttleLatest
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.InternetConnectionObserver
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Manages BackupState information gathering for the UI.
 *
 * This class utilizes a stream of requests which are throttled to one per 100ms, such that we don't flood
 * ourselves with network and database activity.
 *
 * @param scope A coroutine scope, generally expected to be a viewModelScope
 * @param useDatabaseFallbackOnNetworkError Whether we will display network errors or fall back to database information. Defaults to false.
 */
class BackupStateObserver(
  scope: CoroutineScope,
  private val useDatabaseFallbackOnNetworkError: Boolean = false
) {
  companion object {
    private val TAG = Log.tag(BackupStateObserver::class)

    private val staticScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backupTierChangedNotifier = MutableSharedFlow<Unit>()

    /**
     * Called when the value returned by [SignalStore.backup.backupTier] changes.
     */
    fun notifyBackupTierChanged(scope: CoroutineScope = staticScope) {
      Log.d(TAG, "Notifier got a change")
      scope.launch {
        backupTierChangedNotifier.emit(Unit)
      }
    }

    /**
     * Builds a BackupState without touching the database or network. At most what this
     * can tell you is whether the tier is set or if backups are available at all.
     *
     * This method is meant to be lightweight and instantaneous, and is a good candidate for
     * setting initial ViewModel state values.
     */
    fun getNonIOBackupState(): BackupState {
      return if (RemoteConfig.messageBackups) {
        val tier = SignalStore.backup.backupTier

        if (tier != null) {
          BackupState.LocalStore(tier)
        } else {
          BackupState.None
        }
      } else {
        BackupState.NotAvailable
      }
    }
  }

  private val internalBackupState = MutableStateFlow(getNonIOBackupState())
  private val backupStateRefreshRequest = MutableSharedFlow<Unit>()

  val backupState: StateFlow<BackupState> = internalBackupState

  init {
    scope.launch(SignalDispatchers.IO) {
      performDatabaseBackupStateRefresh()
    }

    scope.launch(SignalDispatchers.IO) {
      backupStateRefreshRequest
        .throttleLatest(100.milliseconds)
        .collect {
          performFullBackupStateRefresh()
        }
    }

    scope.launch(SignalDispatchers.IO) {
      backupTierChangedNotifier.collect {
        requestBackupStateRefresh()
      }
    }

    scope.launch(SignalDispatchers.IO) {
      InternetConnectionObserver.observe().asFlow()
        .collect {
          if (backupState.value == BackupState.Error) {
            requestBackupStateRefresh()
          }
        }
    }

    scope.launch(SignalDispatchers.IO) {
      InAppPaymentsRepository.observeLatestBackupPayment().collect {
        requestBackupStateRefresh()
      }
    }

    scope.launch(SignalDispatchers.IO) {
      SignalStore.backup.subscriptionStateMismatchDetectedFlow.collect {
        requestBackupStateRefresh()
      }
    }

    scope.launch(SignalDispatchers.IO) {
      SignalStore.backup.deletionStateFlow.collect {
        requestBackupStateRefresh()
      }
    }
  }

  /**
   * Requests a refresh behind a throttler.
   */
  private suspend fun requestBackupStateRefresh() {
    Log.d(TAG, "Requesting refresh.")
    backupStateRefreshRequest.emit(Unit)
  }

  /**
   * Produces state based off what we have locally in the database. Does not hit the network.
   */
  @WorkerThread
  private fun getDatabaseBackupState(): BackupState {
    if (SignalStore.backup.backupTier != MessageBackupTier.PAID) {
      Log.d(TAG, "No additional information available without accessing the network.")
      return getNonIOBackupState()
    }

    val latestPayment = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_BACKUP)
    if (latestPayment == null) {
      Log.d(TAG, "No additional information is available in the local database.")
      return getNonIOBackupState()
    }

    val price = latestPayment.data.amount!!.toFiatMoney()
    val isPending = SignalDatabase.inAppPayments.hasPendingBackupRedemption()
    if (isPending) {
      return BackupState.Pending(price = price)
    }

    val paidBackupType = MessageBackupsType.Paid(
      pricePerMonth = price,
      storageAllowanceBytes = -1L,
      mediaTtl = 0.days
    )

    val isCanceled = latestPayment.data.cancellation != null
    if (isCanceled) {
      return BackupState.Canceled(
        messageBackupsType = paidBackupType,
        renewalTime = latestPayment.endOfPeriod
      )
    }

    if (SignalStore.backup.subscriptionStateMismatchDetected) {
      return BackupState.SubscriptionMismatchMissingGooglePlay(
        messageBackupsType = paidBackupType,
        renewalTime = latestPayment.endOfPeriod
      )
    }

    if (latestPayment.endOfPeriod < System.currentTimeMillis().milliseconds) {
      return BackupState.Inactive(
        messageBackupsType = paidBackupType,
        renewalTime = latestPayment.endOfPeriod
      )
    }

    return BackupState.ActivePaid(
      messageBackupsType = paidBackupType,
      price = price,
      renewalTime = latestPayment.endOfPeriod
    )
  }

  private suspend fun performDatabaseBackupStateRefresh() {
    if (!RemoteConfig.messageBackups) {
      return
    }

    if (!SignalStore.account.isRegistered) {
      Log.d(TAG, "Dropping refresh for unregistered user.")
      return
    }

    if (backupState.value !is BackupState.LocalStore) {
      Log.d(TAG, "Dropping database refresh for non-local store state.")
      return
    }

    internalBackupState.emit(getDatabaseBackupState())
  }

  private suspend fun performFullBackupStateRefresh() {
    if (!RemoteConfig.messageBackups) {
      return
    }

    if (!SignalStore.account.isRegistered) {
      Log.d(TAG, "Dropping refresh for unregistered user.")
      return
    }

    Log.d(TAG, "Performing refresh.")
    withContext(SignalDispatchers.IO) {
      val latestInAppPayment = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_BACKUP)
      internalBackupState.emit(getNetworkBackupState(latestInAppPayment))
    }
  }

  /**
   * Utilizes everything we can to resolve the most accurate backup state available, including database and network.
   */
  private suspend fun getNetworkBackupState(lastPurchase: InAppPaymentTable.InAppPayment?): BackupState {
    if (lastPurchase?.state == InAppPaymentTable.State.PENDING) {
      Log.d(TAG, "We have a pending subscription.")
      return BackupState.Pending(
        price = lastPurchase.data.amount!!.toFiatMoney()
      )
    }

    if (SignalStore.backup.subscriptionStateMismatchDetected) {
      Log.d(TAG, "[subscriptionStateMismatchDetected] A mismatch was detected.")

      val hasActiveGooglePlayBillingSubscription = when (val purchaseResult = AppDependencies.billingApi.queryPurchases()) {
        is BillingPurchaseResult.Success -> {
          Log.d(TAG, "[subscriptionStateMismatchDetected] Found a purchase: $purchaseResult")
          purchaseResult.isAcknowledged && purchaseResult.isAutoRenewing
        }

        else -> {
          Log.d(TAG, "[subscriptionStateMismatchDetected] No purchase found in Google Play Billing: $purchaseResult")
          false
        }
      } || SignalStore.backup.backupTierInternalOverride == MessageBackupTier.PAID

      Log.d(TAG, "[subscriptionStateMismatchDetected] hasActiveGooglePlayBillingSubscription: $hasActiveGooglePlayBillingSubscription")

      val activeSubscription = withContext(Dispatchers.IO) {
        RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP).getOrNull()
      }

      val hasActiveSignalSubscription = activeSubscription?.isActive == true

      Log.d(TAG, "[subscriptionStateMismatchDetected] hasActiveSignalSubscription: $hasActiveSignalSubscription")

      when {
        hasActiveSignalSubscription && !hasActiveGooglePlayBillingSubscription -> {
          val type = buildPaidTypeFromSubscription(activeSubscription.activeSubscription)

          if (type == null) {
            Log.d(TAG, "[subscriptionMismatchDetected] failed to load backup configuration. Likely a network error.")
            return getStateOnError()
          }

          return BackupState.SubscriptionMismatchMissingGooglePlay(
            messageBackupsType = type,
            renewalTime = activeSubscription.activeSubscription.endOfCurrentPeriod.seconds
          )
        }

        hasActiveSignalSubscription && hasActiveGooglePlayBillingSubscription -> {
          Log.d(TAG, "Found active signal subscription and active google play subscription. Clearing mismatch.")
          SignalStore.backup.subscriptionStateMismatchDetected = false
        }

        !hasActiveSignalSubscription && !hasActiveGooglePlayBillingSubscription -> {
          Log.d(TAG, "Found inactive signal subscription and inactive google play subscription. Clearing mismatch.")
          SignalStore.backup.subscriptionStateMismatchDetected = false
        }

        else -> {
          Log.w(TAG, "Hit unexpected subscription mismatch state: signal:false, google:true")
          return BackupState.NotFound
        }
      }
    }

    return when (SignalStore.backup.latestBackupTier) {
      MessageBackupTier.PAID -> {
        getPaidBackupState(lastPurchase)
      }

      MessageBackupTier.FREE -> {
        getFreeBackupState()
      }

      null -> {
        Log.d(TAG, "Updating UI state with NONE null tier.")
        return BackupState.None
      }
    }
  }

  /**
   * Helper function to fall back to database state if [useDatabaseFallbackOnNetworkError] is set to true.
   */
  private fun getStateOnError(): BackupState {
    return if (useDatabaseFallbackOnNetworkError) {
      getDatabaseBackupState()
    } else {
      BackupState.Error
    }
  }

  private suspend fun getPaidBackupState(lastPurchase: InAppPaymentTable.InAppPayment?): BackupState {
    Log.d(TAG, "Attempting to retrieve subscription details for active PAID backup.")

    val typeResult = withContext(Dispatchers.IO) {
      BackupRepository.getPaidType()
    }

    val type = if (typeResult is NetworkResult.Success) typeResult.result else null

    Log.d(TAG, "Attempting to retrieve current subscription...")
    val activeSubscription = withContext(Dispatchers.IO) {
      RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP)
    }

    return if (activeSubscription.isSuccess) {
      Log.d(TAG, "Retrieved subscription details.")

      val subscription = activeSubscription.getOrThrow().activeSubscription
      if (subscription != null) {
        Log.d(TAG, "Subscription found. Updating UI state with subscription details. Status: ${subscription.status}")

        val subscriberType = type ?: buildPaidTypeFromSubscription(subscription)
        if (subscriberType == null) {
          Log.d(TAG, "Failed to create backup type. Possible network error.")

          getStateOnError()
        } else {
          when {
            subscription.isCanceled && subscription.isActive -> BackupState.Canceled(
              messageBackupsType = subscriberType,
              renewalTime = subscription.endOfCurrentPeriod.seconds
            )

            subscription.isActive -> BackupState.ActivePaid(
              messageBackupsType = subscriberType,
              price = FiatMoney.fromSignalNetworkAmount(subscription.amount, Currency.getInstance(subscription.currency)),
              renewalTime = subscription.endOfCurrentPeriod.seconds
            )

            else -> BackupState.Inactive(
              messageBackupsType = subscriberType,
              renewalTime = subscription.endOfCurrentPeriod.seconds
            )
          }
        }
      } else {
        Log.d(TAG, "ActiveSubscription had null subscription object.")
        if (SignalStore.backup.areBackupsEnabled) {
          BackupState.NotFound
        } else if (lastPurchase != null && lastPurchase.endOfPeriod > System.currentTimeMillis().milliseconds) {
          val canceledType = type ?: buildPaidTypeFromInAppPayment(lastPurchase)
          if (canceledType == null) {
            Log.w(TAG, "Failed to load canceled type information. Possible network error.")
            getStateOnError()
          } else {
            BackupState.Canceled(
              messageBackupsType = canceledType,
              renewalTime = lastPurchase.endOfPeriod
            )
          }
        } else {
          val inactiveType = type ?: buildPaidTypeWithoutPricing()
          if (inactiveType == null) {
            Log.w(TAG, "Failed to load inactive type information. Possible network error.")
            getStateOnError()
          } else {
            BackupState.Inactive(
              messageBackupsType = inactiveType,
              renewalTime = lastPurchase?.endOfPeriod ?: 0.seconds
            )
          }
        }
      }
    } else {
      Log.d(TAG, "Failed to load ActiveSubscription data. Updating UI state with error.")
      getStateOnError()
    }
  }

  private suspend fun getFreeBackupState(): BackupState {
    val type = withContext(Dispatchers.IO) {
      BackupRepository.getFreeType()
    }

    if (type !is NetworkResult.Success) {
      Log.w(TAG, "Failed to load FREE type.", type.getCause())
      return getStateOnError()
    }

    val backupState = if (SignalStore.backup.areBackupsEnabled) {
      BackupState.ActiveFree(type.result)
    } else {
      BackupState.Inactive(type.result)
    }

    Log.d(TAG, "Updating UI state with $backupState FREE tier.")
    return backupState
  }

  /**
   * Builds out a Paid type utilizing pricing information stored in the user's active subscription object.
   *
   * @return A paid type, or null if we were unable to get the backup level configuration.
   */
  private fun buildPaidTypeFromSubscription(subscription: ActiveSubscription.Subscription): MessageBackupsType.Paid? {
    val config = BackupRepository.getBackupLevelConfiguration().successOrThrow()

    val price = FiatMoney.fromSignalNetworkAmount(subscription.amount, Currency.getInstance(subscription.currency))
    return MessageBackupsType.Paid(
      pricePerMonth = price,
      storageAllowanceBytes = config.storageAllowanceBytes,
      mediaTtl = config.mediaTtlDays.days
    )
  }

  /**
   * Builds out a Paid type utilizing pricing information stored in the given in-app payment.
   *
   * @return A paid type, or null if we were unable to get the backup level configuration.
   */
  private fun buildPaidTypeFromInAppPayment(inAppPayment: InAppPaymentTable.InAppPayment): MessageBackupsType.Paid? {
    val config = BackupRepository.getBackupLevelConfiguration().successOrThrow()

    val price = inAppPayment.data.amount!!.toFiatMoney()
    return MessageBackupsType.Paid(
      pricePerMonth = price,
      storageAllowanceBytes = config.storageAllowanceBytes,
      mediaTtl = config.mediaTtlDays.days
    )
  }

  /**
   * In the case of an Inactive subscription, we only care about the storage allowance and TTL, both of which we can
   * grab from the backup level configuration.
   *
   * @return A paid type, or null if we were unable to get the backup level configuration.
   */
  private fun buildPaidTypeWithoutPricing(): MessageBackupsType? {
    val config = BackupRepository.getBackupLevelConfiguration().successOrThrow()

    return MessageBackupsType.Paid(
      pricePerMonth = FiatMoney(BigDecimal.ZERO, Currency.getInstance(Locale.getDefault())),
      storageAllowanceBytes = config.storageAllowanceBytes,
      mediaTtl = config.mediaTtlDays.days
    )
  }
}
