/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.storage

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.ByteSize
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.storage.SignalStorageManifest
import org.whispersystems.signalservice.api.storage.SignalStorageRecord
import org.whispersystems.signalservice.api.storage.StorageServiceRepository

class InternalStorageServicePlaygroundViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(InternalStorageServicePlaygroundViewModel::class)
  }

  private val _manifest: MutableState<SignalStorageManifest> = mutableStateOf(SignalStorageManifest.EMPTY)
  val manifest: State<SignalStorageManifest>
    get() = _manifest

  private val _storageItems: MutableState<List<SignalStorageRecord>> = mutableStateOf(emptyList())
  val storageRecords: State<List<SignalStorageRecord>>
    get() = _storageItems

  private val _storageInsights: MutableState<StorageInsights> = mutableStateOf(StorageInsights())
  val storageInsights: State<StorageInsights>
    get() = _storageInsights

  private val _oneOffEvents: MutableState<OneOffEvent> = mutableStateOf(OneOffEvent.None)
  val oneOffEvents: State<OneOffEvent>
    get() = _oneOffEvents

  fun onViewTabSelected() {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        val repository = StorageServiceRepository(AppDependencies.storageServiceApi)
        val storageKey = SignalStore.storageService.storageKeyForInitialDataRestore ?: SignalStore.storageService.storageKey

        val manifest = when (val result = repository.getStorageManifest(storageKey)) {
          is StorageServiceRepository.ManifestResult.Success -> result.manifest
          is StorageServiceRepository.ManifestResult.NotFoundError -> {
            Log.w(TAG, "Manifest not found!")
            _oneOffEvents.value = OneOffEvent.ManifestNotFoundError
            return@withContext
          }
          else -> {
            Log.w(TAG, "Failed to fetch manifest!")
            _oneOffEvents.value = OneOffEvent.ManifestDecryptionError
            return@withContext
          }
        }
        _manifest.value = manifest

        val records = when (val result = repository.readStorageRecords(storageKey, manifest.recordIkm, manifest.storageIds)) {
          is StorageServiceRepository.StorageRecordResult.Success -> result.records
          else -> {
            Log.w(TAG, "Failed to fetch records!")
            _oneOffEvents.value = OneOffEvent.StorageRecordDecryptionError
            return@withContext
          }
        }

        _storageItems.value = records

        // TODO get total manifest size -- we need the raw proto, which we don't have
        val insights = StorageInsights(
          totalManifestSize = manifest.protoByteSize,
          totalRecordSize = records.sumOf { it.sizeInBytes() }.bytes,
          totalContactSize = records.filter { it.proto.contact != null }.sumOf { it.sizeInBytes() }.bytes,
          totalGroupV1Size = records.filter { it.proto.groupV1 != null }.sumOf { it.sizeInBytes() }.bytes,
          totalGroupV2Size = records.filter { it.proto.groupV2 != null }.sumOf { it.sizeInBytes() }.bytes,
          totalAccountRecordSize = records.filter { it.proto.account != null }.sumOf { it.sizeInBytes() }.bytes,
          totalCallLinkSize = records.filter { it.proto.callLink != null }.sumOf { it.sizeInBytes() }.bytes,
          totalDistributionListSize = records.filter { it.proto.storyDistributionList != null }.sumOf { it.sizeInBytes() }.bytes,
          totalChatFolderSize = records.filter { it.proto.chatFolder != null }.sumOf { it.sizeInBytes() }.bytes,
          totalNotificationProfileSize = records.filter { it.proto.notificationProfile != null }.sumOf { it.sizeInBytes() }.bytes,
          totalUnknownSize = records.filter { it.isUnknown }.sumOf { it.sizeInBytes() }.bytes
        )

        _storageInsights.value = insights
      }
    }
  }

  private fun SignalStorageRecord.sizeInBytes(): Int {
    return this.proto.encode().size
  }

  enum class OneOffEvent {
    None, ManifestDecryptionError, StorageRecordDecryptionError, ManifestNotFoundError
  }

  data class StorageInsights(
    val totalManifestSize: ByteSize = 0.bytes,
    val totalRecordSize: ByteSize = 0.bytes,
    val totalContactSize: ByteSize = 0.bytes,
    val totalGroupV1Size: ByteSize = 0.bytes,
    val totalGroupV2Size: ByteSize = 0.bytes,
    val totalAccountRecordSize: ByteSize = 0.bytes,
    val totalCallLinkSize: ByteSize = 0.bytes,
    val totalDistributionListSize: ByteSize = 0.bytes,
    val totalChatFolderSize: ByteSize = 0.bytes,
    val totalNotificationProfileSize: ByteSize = 0.bytes,
    val totalUnknownSize: ByteSize = 0.bytes
  )
}
