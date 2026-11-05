/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.advanced

import com.google.android.gms.tasks.Tasks
import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.signal.appsettings.advancedprivacy.AdvancedPrivacySettingsState
import org.signal.appsettings.advancedprivacy.CensorshipCircumventionState
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.DeviceDeregisteredException
import org.signal.libsignal.net.RequestResult
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.InternetConnectionObserver
import org.thoughtcrime.securesms.util.SignalE164Util
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import java.io.IOException
import java.util.concurrent.ExecutionException

private val TAG = Log.tag(AdvancedPrivacySettingsRepository::class.java)

object AdvancedPrivacySettingsRepository {

  fun getState(): AdvancedPrivacySettingsState {
    val censorshipCircumventionState = getCensorshipCircumventionState()

    return AdvancedPrivacySettingsState(
      alwaysRelayCalls = SignalStore.settings.isTurnOnly,
      censorshipCircumventionState = censorshipCircumventionState,
      censorshipCircumventionEnabled = getCensorshipCircumventionEnabled(censorshipCircumventionState),
      showSealedSenderStatusIcon = SignalStore.settings.isShowUnidentifiedDeliveryIndicatorsEnabled,
      allowSealedSenderFromAnyone = SignalStore.settings.isUniversalUnidentifiedAccess,
      allowAutomaticKeyVerification = SignalStore.settings.automaticVerificationEnabled,
      isPrimaryDevice = SignalStore.account.isPrimaryDevice
    )
  }

  /** Emits whenever the websocket or the device's network changes, either of which can change the censorship circumvention state. */
  fun connectionChanges(): Flow<Unit> {
    return merge(
      AppDependencies.webSocketObserver.asFlow().map { },
      networkChanges()
    )
  }

  fun setAlwaysRelayCalls(enabled: Boolean) {
    SignalStore.settings.isTurnOnly = enabled
  }

  fun setCensorshipCircumventionEnabled(enabled: Boolean) {
    SignalStore.settings.setCensorshipCircumventionEnabled(enabled)
    SignalStore.misc.isServiceReachableWithoutCircumvention = false
    AppDependencies.resetNetwork()
  }

  fun setShowSealedSenderStatusIcon(enabled: Boolean) {
    SignalStore.settings.isShowUnidentifiedDeliveryIndicatorsEnabled = enabled
    syncShowSealedSenderIconState()
  }

  fun setAllowSealedSenderFromAnyone(enabled: Boolean) {
    SignalStore.settings.isUniversalUnidentifiedAccess = enabled
    AppDependencies.jobManager.startChain(RefreshAttributesJob()).then(RefreshOwnProfileJob()).enqueue()
  }

  fun setAutomaticKeyVerificationEnabled(enabled: Boolean) {
    SignalStore.settings.automaticVerificationEnabled = enabled
    SignalStore.misc.hasKeyTransparencyFailure = false
    SignalStore.misc.hasSeenKeyTransparencyFailure = false
  }

  /** Clears key transparency data when automatic verification is turned off, and syncs the change to linked devices. */
  suspend fun syncAutomaticKeyVerification(enabled: Boolean) = withContext(Dispatchers.Default) {
    if (!enabled) {
      SignalDatabase.recipients.clearAllKeyTransparencyData()
      SignalStore.account.distinguishedHead = null
    }
    SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  suspend fun disablePushMessages(): DisablePushMessagesResult = withContext(Dispatchers.IO) {
    val clearTokenError: Throwable? = when (val result = SignalNetwork.accountApi.clearFcmToken()) {
      is RequestResult.Success, is RequestResult.NonSuccess -> null
      is RequestResult.RetryableNetworkError -> result.networkError
      is RequestResult.ApplicationError -> result.cause
    }

    if (clearTokenError != null) {
      Log.w(TAG, clearTokenError)
      if (clearTokenError !is DeviceDeregisteredException) {
        return@withContext DisablePushMessagesResult.NETWORK_ERROR
      }
    }

    try {
      if (SignalStore.account.fcmEnabled) {
        Tasks.await(FirebaseInstallations.getInstance().delete())
      }
      DisablePushMessagesResult.SUCCESS
    } catch (ioe: IOException) {
      Log.w(TAG, ioe)
      DisablePushMessagesResult.NETWORK_ERROR
    } catch (e: InterruptedException) {
      Log.w(TAG, "Interrupted while deleting", e)
      DisablePushMessagesResult.NETWORK_ERROR
    } catch (e: ExecutionException) {
      Log.w(TAG, "Error deleting", e.cause)
      DisablePushMessagesResult.NETWORK_ERROR
    }
  }

  private fun syncShowSealedSenderIconState() {
    SignalExecutors.BOUNDED.execute {
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

  /** [InternetConnectionObserver] handles the API 24+ network callback and the broadcast receiver fallback for older devices. */
  private fun networkChanges(): Flow<Unit> {
    return InternetConnectionObserver.observe().asFlow().map { }
  }

  private fun getCensorshipCircumventionState(): CensorshipCircumventionState {
    val countryCode: Int = SignalE164Util.getLocalCountryCode()
    val isCountryCodeCensoredByDefault: Boolean = AppDependencies.signalServiceNetworkAccess.isCountryCodeCensoredByDefault(countryCode)
    val enabledState: SettingsValues.CensorshipCircumventionEnabled = SignalStore.settings.censorshipCircumventionEnabled
    val hasInternet: Boolean = NetworkConstraint.isMet(AppDependencies.application)
    val websocketConnected: Boolean = AppDependencies.authWebSocket.stateSnapshot == WebSocketConnectionState.CONNECTED

    return when {
      SignalStore.internal.allowChangingCensorshipSetting -> CensorshipCircumventionState.AVAILABLE
      isCountryCodeCensoredByDefault && enabledState == SettingsValues.CensorshipCircumventionEnabled.DISABLED -> CensorshipCircumventionState.AVAILABLE_MANUALLY_DISABLED
      isCountryCodeCensoredByDefault -> CensorshipCircumventionState.AVAILABLE_AUTOMATICALLY_ENABLED
      !hasInternet && enabledState != SettingsValues.CensorshipCircumventionEnabled.ENABLED -> CensorshipCircumventionState.UNAVAILABLE_NO_INTERNET
      websocketConnected && enabledState != SettingsValues.CensorshipCircumventionEnabled.ENABLED -> CensorshipCircumventionState.UNAVAILABLE_CONNECTED
      else -> CensorshipCircumventionState.AVAILABLE
    }
  }

  private fun getCensorshipCircumventionEnabled(state: CensorshipCircumventionState): Boolean {
    return when (state) {
      CensorshipCircumventionState.UNAVAILABLE_CONNECTED,
      CensorshipCircumventionState.UNAVAILABLE_NO_INTERNET,
      CensorshipCircumventionState.AVAILABLE_MANUALLY_DISABLED -> false
      CensorshipCircumventionState.AVAILABLE_AUTOMATICALLY_ENABLED -> true
      CensorshipCircumventionState.AVAILABLE -> SignalStore.settings.censorshipCircumventionEnabled == SettingsValues.CensorshipCircumventionEnabled.ENABLED
    }
  }

  enum class DisablePushMessagesResult {
    SUCCESS,
    NETWORK_ERROR
  }
}
