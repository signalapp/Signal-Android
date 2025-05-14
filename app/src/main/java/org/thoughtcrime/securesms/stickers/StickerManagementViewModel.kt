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
import org.signal.core.util.swap
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.database.model.StickerPackKey
import org.thoughtcrime.securesms.database.model.StickerPackRecord
import org.thoughtcrime.securesms.stickers.AvailableStickerPack.DownloadStatus

class StickerManagementViewModel : ViewModel() {
  private val stickerManagementRepo = StickerManagementRepository

  private val _uiState = MutableStateFlow(StickerManagementUiState())
  val uiState: StateFlow<StickerManagementUiState> = _uiState.asStateFlow()

  private val downloadStatusByPackId: MutableStateFlow<Map<StickerPackId, DownloadStatus>> = MutableStateFlow(emptyMap())

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
        val recentlyInstalledPacks = stickerPacksResult.installedPacks.filter { downloadStatuses.contains(StickerPackId(it.packId)) }
        val allAvailablePacks = (stickerPacksResult.blessedPacks + stickerPacksResult.availablePacks + recentlyInstalledPacks)
          .map { record ->
            val packId = StickerPackId(record.packId)
            AvailableStickerPack(
              record = record,
              isBlessed = BlessedPacks.contains(record.packId),
              downloadStatus = downloadStatuses.getOrElse(packId) {
                downloadStatusByPackId.value.getOrDefault(packId, DownloadStatus.NotDownloaded)
              }
            )
          }
          .sortedBy { stickerPacksResult.sortOrderByPackId.getValue(it.id) }

        val (availableBlessedPacks, availableNotBlessedPacks) = allAvailablePacks.partition { it.isBlessed }
        val installedPacks = stickerPacksResult.installedPacks.map { record ->
          InstalledStickerPack(
            record = record,
            isBlessed = BlessedPacks.contains(record.packId),
            sortOrder = stickerPacksResult.sortOrderByPackId.getValue(StickerPackId(record.packId))
          )
        }

        _uiState.update { previousState ->
          previousState.copy(
            availableBlessedPacks = availableBlessedPacks,
            availableNotBlessedPacks = availableNotBlessedPacks,
            installedPacks = installedPacks,
            multiSelectEnabled = if (installedPacks.isEmpty()) false else previousState.multiSelectEnabled
          )
        }
      }
  }

  fun installStickerPack(pack: AvailableStickerPack) {
    viewModelScope.launch {
      updatePackDownloadStatus(pack.id, DownloadStatus.InProgress)

      StickerManagementRepository.installStickerPack(packId = pack.id, packKey = pack.key, notify = true)
      updatePackDownloadStatus(pack.id, DownloadStatus.Downloaded)

      _uiState.update { previousState ->
        previousState.copy(
          actionConfirmation = StickerManagementConfirmation.InstalledPack(pack.record.title)
        )
      }

      delay(1500) // wait, so we show the downloaded status for a bit before removing this row from the available sticker packs list
      updatePackDownloadStatus(pack.id, null)
    }
  }

  private fun updatePackDownloadStatus(packId: StickerPackId, newStatus: DownloadStatus?) {
    downloadStatusByPackId.value = if (newStatus == null) {
      downloadStatusByPackId.value.minus(packId)
    } else {
      downloadStatusByPackId.value.plus(packId to newStatus)
    }
  }

  fun onUninstallStickerPacksRequested(packIds: Set<StickerPackId>) {
    if (packIds.isEmpty()) {
      return
    }

    if (_uiState.value.multiSelectEnabled) {
      _uiState.update { previousState ->
        previousState.copy(
          userPrompt = ConfirmRemoveStickerPacksPrompt(numItemsToDelete = packIds.size)
        )
      }
    } else {
      uninstallStickerPacks(packIds)
    }
  }

  fun onUninstallStickerPacksConfirmed(packIds: Set<StickerPackId>) {
    _uiState.update { previousState -> previousState.copy(userPrompt = null) }
    uninstallStickerPacks(packIds)
  }

  fun onUninstallStickerPacksCanceled() {
    _uiState.update { previousState -> previousState.copy(userPrompt = null) }
  }

  private fun uninstallStickerPacks(packIds: Set<StickerPackId>) {
    val packsToUninstall = _uiState.value.installedPacks.filter { packIds.contains(it.id) }
    viewModelScope.launch {
      StickerManagementRepository.uninstallStickerPacks(packsToUninstall.associate { it.id to it.key })

      _uiState.update { previousState ->
        previousState.copy(
          actionConfirmation = if (packsToUninstall.size == 1) {
            StickerManagementConfirmation.UninstalledPack(packsToUninstall.single().record.title)
          } else {
            StickerManagementConfirmation.UninstalledPacks(packsToUninstall.size)
          },
          selectedPackIds = previousState.selectedPackIds.minus(packIds)
        )
      }
    }
  }

  fun updatePosition(fromIndex: Int, toIndex: Int) {
    _uiState.update { it.copy(installedPacks = _uiState.value.installedPacks.swap(fromIndex, toIndex)) }
  }

  fun saveInstalledPacksSortOrder() {
    viewModelScope.launch {
      StickerManagementRepository.setStickerPacksOrder(_uiState.value.installedPacks.map { it.record })
    }
  }

  fun toggleSelection(pack: InstalledStickerPack) {
    _uiState.update { previousState ->
      val wasItemSelected = previousState.selectedPackIds.contains(pack.id)
      previousState.copy(
        multiSelectEnabled = true,
        selectedPackIds = if (wasItemSelected) previousState.selectedPackIds.minus(pack.id) else previousState.selectedPackIds.plus(pack.id)
      )
    }
  }

  fun toggleSelectAll() {
    _uiState.update { previousState ->
      previousState.copy(
        multiSelectEnabled = true,
        selectedPackIds = if (previousState.selectedPackIds.size == previousState.installedPacks.size) {
          emptySet()
        } else {
          previousState.installedPacks.map { it.id }.toSet()
        }
      )
    }
  }

  fun setMultiSelectEnabled(isEnabled: Boolean) {
    _uiState.update { previousState ->
      previousState.copy(
        multiSelectEnabled = isEnabled,
        selectedPackIds = emptySet()
      )
    }
  }

  fun onSnackbarDismiss() {
    _uiState.update { previousState ->
      previousState.copy(actionConfirmation = null)
    }
  }
}

data class StickerManagementUiState(
  val availableBlessedPacks: List<AvailableStickerPack> = emptyList(),
  val availableNotBlessedPacks: List<AvailableStickerPack> = emptyList(),
  val installedPacks: List<InstalledStickerPack> = emptyList(),
  val multiSelectEnabled: Boolean = false,
  val selectedPackIds: Set<StickerPackId> = emptySet(),
  val userPrompt: ConfirmRemoveStickerPacksPrompt? = null,
  val actionConfirmation: StickerManagementConfirmation? = null
)

data class ConfirmRemoveStickerPacksPrompt(
  val numItemsToDelete: Int
)

sealed interface StickerManagementConfirmation {
  data class InstalledPack(val packTitle: String) : StickerManagementConfirmation
  data class UninstalledPack(val packTitle: String) : StickerManagementConfirmation
  data class UninstalledPacks(val numPacksUninstalled: Int) : StickerManagementConfirmation
}

data class AvailableStickerPack(
  val record: StickerPackRecord,
  val isBlessed: Boolean,
  val downloadStatus: DownloadStatus
) {
  val id = StickerPackId(record.packId)
  val key = StickerPackKey(record.packKey)

  sealed class DownloadStatus {
    data object NotDownloaded : DownloadStatus()
    data object InProgress : DownloadStatus()
    data object Downloaded : DownloadStatus()
  }
}

data class InstalledStickerPack(
  val record: StickerPackRecord,
  val isBlessed: Boolean,
  val sortOrder: Int
) {
  val id = StickerPackId(record.packId)
  val key = StickerPackKey(record.packKey)
}
