/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.signal.appsettings.dataandstorage.AutoDownloadMediaType
import org.signal.appsettings.dataandstorage.AutoDownloadNetwork
import org.signal.core.models.calls.CallDataMode
import org.signal.core.models.media.SentMediaQuality
import org.signal.core.util.PlayServicesUtil
import org.thoughtcrime.securesms.components.settings.app.storage.StorageUsageRepository
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SettingsValues.ForceWebsocketMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.IncomingMessageObserver
import org.thoughtcrime.securesms.util.AttachmentUtil

/**
 * All of the storage access behind [DataAndStorageSettingsViewModel].
 */
object DataAndStorageSettingsRepository {

  fun totalStorageUseBytes(): Flow<Long?> = StorageUsageRepository.instance.usage.map { it?.total }

  fun refreshStorageUsage() {
    StorageUsageRepository.instance.refresh()
  }

  fun getAutoDownload(network: AutoDownloadNetwork): Set<AutoDownloadMediaType> {
    val values = when (network) {
      AutoDownloadNetwork.MOBILE -> SignalStore.settings.mobileMediaDownloadAllowed
      AutoDownloadNetwork.WIFI -> SignalStore.settings.wifiMediaDownloadAllowed
      AutoDownloadNetwork.ROAMING -> SignalStore.settings.roamingMediaDownloadAllowed
    }

    return values.mapNotNull { value -> AutoDownloadMediaType.entries.firstOrNull { it.storageValue == value } }.toSet()
  }

  fun setAutoDownload(network: AutoDownloadNetwork, mediaTypes: Set<AutoDownloadMediaType>) {
    val values = mediaTypes.map { it.storageValue }.toSet()
    when (network) {
      AutoDownloadNetwork.MOBILE -> SignalStore.settings.mobileMediaDownloadAllowed = values
      AutoDownloadNetwork.WIFI -> SignalStore.settings.wifiMediaDownloadAllowed = values
      AutoDownloadNetwork.ROAMING -> SignalStore.settings.roamingMediaDownloadAllowed = values
    }
  }

  fun getAlwaysAutoDownloadedBytes(): Long = AttachmentUtil.SMALL_ATTACHMENT_SIZE.inWholeBytes

  fun getSentMediaQuality(): SentMediaQuality = SignalStore.settings.sentMediaQuality

  fun setSentMediaQuality(quality: SentMediaQuality) {
    SignalStore.settings.sentMediaQuality = quality
  }

  fun getCallDataMode(): CallDataMode = SignalStore.settings.callDataMode

  fun setCallDataMode(mode: CallDataMode) {
    SignalStore.settings.callDataMode = mode
    AppDependencies.signalCallManager.dataModeUpdate()
  }

  fun isForceWebsocketModeEnabled(): Boolean = SignalStore.settings.forceWebsocketMode.isEnabled

  /** Stores the new mode and restarts the network so it takes effect. */
  suspend fun setForceWebsocketModeEnabled(enabled: Boolean) {
    withContext(Dispatchers.Default) {
      SignalStore.settings.forceWebsocketMode = if (enabled) ForceWebsocketMode.ENABLED_BY_USER else ForceWebsocketMode.DISABLED_BY_USER
      if (!enabled) {
        IncomingMessageObserver.stopForegroundService(AppDependencies.application)
      }
      AppDependencies.resetNetwork()
      AppDependencies.startNetwork()
    }
  }

  fun isPlayServicesAvailable(): Boolean {
    return PlayServicesUtil.getPlayServicesStatus(AppDependencies.application) == PlayServicesUtil.PlayServicesStatus.SUCCESS
  }

  fun isProxyEnabled(): Boolean = SignalStore.proxy.isProxyEnabled

  /** The discrete MIME type (the part before the "/") stored in [SignalStore.settings] for each media type. */
  private val AutoDownloadMediaType.storageValue: String
    get() = when (this) {
      AutoDownloadMediaType.IMAGES -> "image"
      AutoDownloadMediaType.AUDIO -> "audio"
      AutoDownloadMediaType.VIDEO -> "video"
      AutoDownloadMediaType.DOCUMENTS -> "documents"
    }
}
