package org.thoughtcrime.securesms.components.settings.app.privacy

import android.content.Intent
import kotlinx.coroutines.withContext
import org.signal.core.util.ServiceUtil
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.crypto.MasterSecretUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * All of the storage access behind [PrivacySettingsViewModel].
 */
object PrivacySettingsRepository {

  suspend fun getBlockedCount(): Int {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.getBlocked().size
    }
  }

  fun hasPhoneNumber(): Boolean = !SignalStore.account.isPhoneNumberless

  fun isDeviceSecure(): Boolean = ServiceUtil.getKeyguardManager(AppDependencies.application).isKeyguardSecure

  fun isReadReceiptsEnabled(): Boolean = SignalStore.settings.isReadReceiptsEnabled

  suspend fun setReadReceiptsEnabled(enabled: Boolean) {
    SignalStore.settings.isReadReceiptsEnabled = enabled
    syncConfiguration()
  }

  fun isTypingIndicatorsEnabled(): Boolean = SignalStore.settings.isTypingIndicatorsEnabled

  suspend fun setTypingIndicatorsEnabled(enabled: Boolean) {
    SignalStore.settings.isTypingIndicatorsEnabled = enabled
    syncConfiguration()

    if (!enabled) {
      AppDependencies.typingStatusRepository.clear()
    }
  }

  fun getUniversalExpireTimer(): Duration = SignalStore.settings.universalExpireTimer.seconds

  fun isScreenLockEnabled(): Boolean = SignalStore.settings.screenLockEnabled

  fun getScreenLockTimeout(): Duration = SignalStore.settings.screenLockTimeout.seconds

  fun isScreenSecurityEnabled(): Boolean = SignalStore.settings.isScreenSecurityEnabled

  fun setScreenSecurityEnabled(enabled: Boolean) {
    SignalStore.settings.isScreenSecurityEnabled = enabled
  }

  fun isIncognitoKeyboardEnabled(): Boolean = SignalStore.settings.isIncognitoKeyboardEnabled

  fun setIncognitoKeyboardEnabled(enabled: Boolean) {
    SignalStore.settings.isIncognitoKeyboardEnabled = enabled
  }

  fun isPaymentLockEnabled(): Boolean = SignalStore.payments.paymentLock

  fun setPaymentLockEnabled(enabled: Boolean) {
    SignalStore.payments.paymentLock = enabled
  }

  fun isObsoletePassphraseEnabled(): Boolean = !SignalStore.settings.passphraseDisabled

  fun isObsoletePassphraseInitialized(): Boolean = MasterSecretUtil.isPassphraseInitialized(AppDependencies.application)

  /** Permanently replaces the legacy passphrase with the unencrypted one and tells [KeyCachingService] to stop locking. */
  suspend fun disableObsoletePassphrase() {
    withContext(SignalDispatchers.Default) {
      val application = AppDependencies.application
      MasterSecretUtil.changeMasterSecretPassphrase(application, KeyCachingService.getMasterSecret(application), MasterSecretUtil.UNENCRYPTED_PASSPHRASE)
      SignalStore.settings.passphraseDisabled = true

      val intent = Intent(application, KeyCachingService::class.java)
      intent.action = KeyCachingService.DISABLE_ACTION
      application.startService(intent)
    }
  }

  fun isObsoletePassphraseTimeoutEnabled(): Boolean = SignalStore.settings.passphraseTimeoutEnabled

  fun setObsoletePassphraseTimeoutEnabled(enabled: Boolean) {
    SignalStore.settings.passphraseTimeoutEnabled = enabled
  }

  fun getObsoletePassphraseTimeout(): Duration = SignalStore.settings.passphraseTimeout.minutes

  fun setObsoletePassphraseTimeout(timeout: Duration) {
    SignalStore.settings.passphraseTimeout = timeout.inWholeMinutes.toInt().coerceAtLeast(1)
  }

  private suspend fun syncConfiguration() {
    withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
      AppDependencies.jobManager.add(
        MultiDeviceConfigurationUpdateJob(
          SignalStore.settings.isReadReceiptsEnabled,
          SignalStore.settings.isTypingIndicatorsEnabled,
          SignalStore.settings.isShowUnidentifiedDeliveryIndicatorsEnabled,
          SignalStore.settings.isLinkPreviewsEnabled
        )
      )
    }
  }
}
