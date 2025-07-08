/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Manages BackupState information gathering for the UI.
 */
object BackupStateRepository {

  private val TAG = Log.tag(BackupStateRepository::class)

  suspend fun resolveBackupState(lastPurchase: InAppPaymentTable.InAppPayment?): BackupState {
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
            return BackupState.Error
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

  private suspend fun getPaidBackupState(lastPurchase: InAppPaymentTable.InAppPayment?): BackupState {
    Log.d(TAG, "Attempting to retrieve subscription details for active PAID backup.")

    val type = withContext(Dispatchers.IO) {
      BackupRepository.getBackupsType(MessageBackupTier.PAID) as? MessageBackupsType.Paid
    }

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

          BackupState.Error
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
            BackupState.Error
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
            BackupState.Error
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
      BackupState.Error
    }
  }

  private suspend fun getFreeBackupState(): BackupState {
    val type = withContext(Dispatchers.IO) {
      BackupRepository.getBackupsType(MessageBackupTier.FREE) as MessageBackupsType.Free?
    }

    if (type == null) {
      Log.w(TAG, "Failed to load FREE type. Possible network error.")
      return BackupState.Error
    }

    val backupState = if (SignalStore.backup.areBackupsEnabled) {
      BackupState.ActiveFree(type)
    } else {
      BackupState.Inactive(type)
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
