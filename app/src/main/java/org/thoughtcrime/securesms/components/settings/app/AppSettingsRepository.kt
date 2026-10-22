/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app

import androidx.lifecycle.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.signal.appsettings.home.AppSettingsState
import org.signal.appsettings.home.BackupFailureState
import org.signal.core.util.Util
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.conversationlist.model.UnreadPaymentsLiveData
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.SignalE164Util

/**
 * All of the storage, network and platform access behind [AppSettingsViewModel].
 */
object AppSettingsRepository {

  fun observeSelf(): Flow<Recipient> {
    return Recipient.self().live().observable().asFlow()
  }

  fun observeProfile(): Flow<AppSettingsState.Profile> {
    return observeSelf().map { self ->
      AppSettingsState.Profile(
        name = self.profileName.toString(),
        formattedPhoneNumber = self.e164.map { SignalE164Util.prettyPrint(it) }.orElse(""),
        username = self.username.orElse(""),
        about = self.combinedAboutAndEmoji
      )
    }
  }

  fun observeUnreadPaymentsCount(): Flow<Int> {
    return UnreadPaymentsLiveData().asFlow().map { payments -> payments.map { it.unreadCount }.orElse(0) }
  }

  fun isPrimaryDevice(): Boolean = SignalStore.account.isPrimaryDevice

  fun isUserUnregistered(): Boolean = SignalStore.account.isUnauthorizedReceived || !SignalStore.account.isRegistered

  fun isClientDeprecated(): Boolean = SignalStore.misc.isClientDeprecated

  fun hasExpiredGiftBadge(): Boolean = SignalStore.inAppPayments.getExpiredGiftBadge() != null

  fun showInternalPreferences(): Boolean = RemoteConfig.internalUser

  fun showPayments(): Boolean = SignalStore.payments.paymentsAvailability.showPaymentsMenu()

  fun showAppUpdates(): Boolean = Environment.IS_NIGHTLY

  /** A quick guess at whether the user can manage donations in the app, before [canManageDonations] has an answer. */
  fun likelyCanManageDonations(): Boolean = SignalStore.inAppPayments.isLikelyASustainer() || InAppDonations.hasAtLeastOnePaymentMethodAvailable()

  /** Whether the user can manage donations in the app, or null if their subscription couldn't be fetched. */
  suspend fun canManageDonations(): Boolean? {
    return withContext(SignalDispatchers.IO) {
      RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.DONATION).successOrNull()?.let { activeSubscription ->
        SignalStore.account.isRegistered && (activeSubscription.isActive || InAppDonations.hasAtLeastOnePaymentMethodAvailable())
      }
    }
  }

  fun getBackupFailureState(): BackupFailureState {
    return when {
      !SignalStore.account.isRegistered || !SignalStore.backup.areBackupsEnabled -> BackupFailureState.NONE
      SignalStore.backup.isNotEnoughRemoteStorageSpace -> BackupFailureState.OUT_OF_STORAGE_SPACE
      SignalStore.backup.hasBackupCreationError -> BackupFailureState.COULD_NOT_COMPLETE_BACKUP
      SignalStore.backup.subscriptionStateMismatchDetected -> BackupFailureState.SUBSCRIPTION_STATE_MISMATCH
      SignalStore.backup.hasBackupAlreadyRedeemedError -> BackupFailureState.ALREADY_REDEEMED
      else -> BackupFailureState.NONE
    }
  }

  fun markBackupFailedIndicatorClicked() {
    BackupRepository.markBackupFailedIndicatorClicked()
  }

  fun markBackupAlreadyRedeemedIndicatorClicked() {
    BackupRepository.markBackupAlreadyRedeemedIndicatorClicked()
  }

  /** Copies the subscriber id for [type] to the clipboard. Returns false if the user has no such subscriber. */
  suspend fun copySubscriberIdToClipboard(type: InAppPaymentSubscriberRecord.Type): Boolean {
    val subscriber = withContext(SignalDispatchers.Default) {
      InAppPaymentsRepository.getSubscriber(type)
    } ?: return false

    Util.copyToClipboard(AppDependencies.application, subscriber.subscriberId.serialize())
    return true
  }
}
