/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.screenlock

import android.content.Intent
import org.signal.core.ui.biometrics.BiometricDeviceAuthentication
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.ConversationUtil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * All of the storage access behind [ScreenLockSettingsViewModel].
 */
object ScreenLockSettingsRepository {

  fun isScreenLockEnabled(): Boolean = SignalStore.settings.screenLockEnabled

  /** Stores the new setting, then tells [KeyCachingService] and refreshes shortcuts so the lock takes effect. */
  fun setScreenLockEnabled(enabled: Boolean) {
    SignalStore.settings.screenLockEnabled = enabled

    val intent = Intent(AppDependencies.application, KeyCachingService::class.java)
    intent.action = KeyCachingService.LOCK_TOGGLED_EVENT
    AppDependencies.application.startService(intent)

    ConversationUtil.refreshRecipientShortcuts()
  }

  fun getTimeout(): Duration = SignalStore.settings.screenLockTimeout.seconds

  fun setTimeout(timeout: Duration) {
    SignalStore.settings.screenLockTimeout = timeout.inWholeSeconds
  }

  fun canAuthenticate(): Boolean = BiometricDeviceAuthentication.isAvailable(AppDependencies.application)
}
