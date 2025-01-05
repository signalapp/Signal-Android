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
      }
    }
  }

  enum class OneOffEvent {
    None, ManifestDecryptionError, StorageRecordDecryptionError, ManifestNotFoundError
  }
}
