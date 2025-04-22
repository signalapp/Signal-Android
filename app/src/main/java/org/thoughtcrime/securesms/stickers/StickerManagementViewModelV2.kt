/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.database.model.StickerPackRecord
import org.thoughtcrime.securesms.stickers.AvailableStickerPack.DownloadStatus

class StickerManagementViewModelV2 : ViewModel() {
  private val stickerManagementRepo = StickerManagementRepository

  private val _uiState = MutableStateFlow(StickerManagementUiState())
  val uiState: StateFlow<StickerManagementUiState> = _uiState.asStateFlow()

  private val downloadStatusByPackId: MutableStateFlow<Map<String, DownloadStatus>> = MutableStateFlow(emptyMap())

  init {
    viewModelScope.launch {
      stickerManagementRepo.fetchUnretrievedReferencePacks()
      loadStickerPacks()
    }
  }

  fun onScreenVisible() {
    viewModelScope.launch {
      stickerManagementRepo.deleteOrphanedStickerPacks()
    }
  }

  private suspend fun loadStickerPacks() {
    combine(stickerManagementRepo.getStickerPacks(), downloadStatusByPackId, ::Pair)
      .collectLatest { (stickerPacksResult, downloadStatuses) ->
        val recentlyInstalledPacks = stickerPacksResult.installedPacks.filter { downloadStatuses.contains(it.packId) }
        val allAvailablePacks = (stickerPacksResult.blessedPacks + stickerPacksResult.availablePacks + recentlyInstalledPacks)
          .map { record ->
            AvailableStickerPack(
              record = record,
              isBlessed = BlessedPacks.contains(record.packId),
              downloadStatus = downloadStatuses.getOrElse(record.packId) {
                downloadStatusByPackId.value.getOrDefault(record.packId, DownloadStatus.NotDownloaded)
              }
            )
          }
          .sortedBy { stickerPacksResult.sortOrderByPackId.getValue(it.record.packId) }

        val (availableBlessedPacks, availableNotBlessedPacks) = allAvailablePacks.partition { it.isBlessed }
        val installedPacks = stickerPacksResult.installedPacks.map { record ->
          InstalledStickerPack(
            record = record,
            isBlessed = BlessedPacks.contains(record.packId),
            sortOrder = stickerPacksResult.sortOrderByPackId.getValue(record.packId)
          )
        }

        _uiState.update { previousState ->
          previousState.copy(
            availableBlessedPacks = availableBlessedPacks,
            availableNotBlessedPacks = availableNotBlessedPacks,
            installedPacks = installedPacks
          )
        }
      }
  }

  fun installStickerPack(pack: AvailableStickerPack) = viewModelScope.launch {
    updatePackDownloadStatus(pack.record.packId, DownloadStatus.InProgress)

    StickerManagementRepository.installStickerPack(packId = pack.record.packId, packKey = pack.record.packKey, notify = true)
    updatePackDownloadStatus(pack.record.packId, DownloadStatus.Downloaded)

    delay(1500) // wait, so we show the downloaded status for a bit before removing this row from the available sticker packs list
    updatePackDownloadStatus(pack.record.packId, null)
  }

  private fun updatePackDownloadStatus(packId: String, newStatus: DownloadStatus?) {
    downloadStatusByPackId.value = if (newStatus == null) {
      downloadStatusByPackId.value.minus(packId)
    } else {
      downloadStatusByPackId.value.plus(packId to newStatus)
    }
  }

  fun uninstallStickerPack(pack: AvailableStickerPack) = viewModelScope.launch {
    StickerManagementRepository.uninstallStickerPack(packId = pack.record.packId, packKey = pack.record.packKey)
  }
}

data class StickerManagementUiState(
  val availableBlessedPacks: List<AvailableStickerPack> = emptyList(),
  val availableNotBlessedPacks: List<AvailableStickerPack> = emptyList(),
  val installedPacks: List<InstalledStickerPack> = emptyList(),
  val isMultiSelectMode: Boolean = false
)

data class AvailableStickerPack(
  val record: StickerPackRecord,
  val isBlessed: Boolean,
  val downloadStatus: DownloadStatus
) {
  sealed class DownloadStatus {
    data object NotDownloaded : DownloadStatus()
    data object InProgress : DownloadStatus()
    data object Downloaded : DownloadStatus()
  }
}

data class InstalledStickerPack(
  val record: StickerPackRecord,
  val isBlessed: Boolean,
  val sortOrder: Int,
  val isSelected: Boolean = false
)
