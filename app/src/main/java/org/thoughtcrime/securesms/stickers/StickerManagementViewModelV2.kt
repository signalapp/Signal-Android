/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.database.model.StickerPackRecord
import org.thoughtcrime.securesms.stickers.AvailableStickerPack.DownloadStatus

class StickerManagementViewModelV2 : ViewModel() {
  private val stickerManagementRepo = StickerManagementRepository

  private val _uiState = MutableStateFlow(StickerManagementUiState())
  val uiState: StateFlow<StickerManagementUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      stickerManagementRepo.deleteOrphanedStickerPacks()
      stickerManagementRepo.fetchUnretrievedReferencePacks()
      loadStickerPacks()
    }
  }

  private suspend fun loadStickerPacks() {
    StickerManagementRepository.getStickerPacks()
      .collectLatest { result ->
        _uiState.value = _uiState.value.copy(
          availableBlessedPacks = result.blessedPacks
            .map { AvailableStickerPack(record = it, isBlessed = true, downloadStatus = DownloadStatus.NotDownloaded) },
          availablePacks = result.availablePacks
            .map { AvailableStickerPack(record = it, isBlessed = false, downloadStatus = DownloadStatus.NotDownloaded) },
          installedPacks = result.installedPacks
            .mapIndexed { index, record -> InstalledStickerPack(record = record, isBlessed = BlessedPacks.contains(record.packId), sortOrder = index) }
        )
      }
  }
}

data class StickerManagementUiState(
  val availableBlessedPacks: List<AvailableStickerPack> = emptyList(),
  val availablePacks: List<AvailableStickerPack> = emptyList(),
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
