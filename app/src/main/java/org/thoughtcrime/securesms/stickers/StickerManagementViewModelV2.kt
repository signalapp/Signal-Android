/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.thoughtcrime.securesms.database.model.StickerPackRecord

class StickerManagementViewModelV2 : ViewModel() {
  private val _uiState = MutableStateFlow(StickerManagementUiState())
  val uiState: StateFlow<StickerManagementUiState> = _uiState.asStateFlow()
}

data class StickerManagementUiState(
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
    data class InProgress(val progressPercent: Double) : DownloadStatus()
    data object Downloaded : DownloadStatus()
  }
}

data class InstalledStickerPack(
  private val record: StickerPackRecord,
  val sortOrder: Int,
  val isSelected: Boolean
)
