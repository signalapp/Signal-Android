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

  private val internalUiState = MutableStateFlow(StickerManagementUiState())
  val uiState: StateFlow<StickerManagementUiState> = internalUiState.asStateFlow()

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

        internalUiState.update {
          it.copy(
            availableBlessedPacks = availableBlessedPacks,
            availableNotBlessedPacks = availableNotBlessedPacks,
            installedPacks = installedPacks,
            multiSelectEnabled = if (installedPacks.isEmpty()) false else it.multiSelectEnabled
          )
        }
      }
  }

  fun installStickerPack(pack: AvailableStickerPack) {
    viewModelScope.launch {
      updatePackDownloadStatus(pack.id, DownloadStatus.InProgress)

      StickerManagementRepository.installStickerPack(packId = pack.id, packKey = pack.key, notify = true)
      updatePackDownloadStatus(pack.id, DownloadStatus.Downloaded)

      internalUiState.update {
        it.copy(actionConfirmation = StickerManagementConfirmation.InstalledPack(pack.record.title))
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

    if (internalUiState.value.multiSelectEnabled) {
      internalUiState.update {
        it.copy(userPrompt = ConfirmRemoveStickerPacksPrompt(numItemsToDelete = packIds.size))
      }
    } else {
      uninstallStickerPacks(packIds)
    }
  }

  fun onUninstallStickerPacksConfirmed(packIds: Set<StickerPackId>) {
    internalUiState.update { it.copy(userPrompt = null) }
    uninstallStickerPacks(packIds)
  }

  fun onUninstallStickerPacksCanceled() {
    internalUiState.update { it.copy(userPrompt = null) }
  }

  private fun uninstallStickerPacks(packIds: Set<StickerPackId>) {
    val packsToUninstall = internalUiState.value.installedPacks.filter { packIds.contains(it.id) }
    viewModelScope.launch {
      StickerManagementRepository.uninstallStickerPacks(packsToUninstall.associate { it.id to it.key })

      internalUiState.update {
        it.copy(
          actionConfirmation = if (packsToUninstall.size == 1) {
            StickerManagementConfirmation.UninstalledPack(packsToUninstall.single().record.title)
          } else {
            StickerManagementConfirmation.UninstalledPacks(packsToUninstall.size)
          },
          selectedPackIds = it.selectedPackIds.minus(packIds)
        )
      }
    }
  }

  fun updatePosition(fromIndex: Int, toIndex: Int) {
    internalUiState.update { it.copy(installedPacks = internalUiState.value.installedPacks.swap(fromIndex, toIndex)) }
  }

  fun saveInstalledPacksSortOrder() {
    viewModelScope.launch {
      StickerManagementRepository.setStickerPacksOrder(internalUiState.value.installedPacks.map { it.record })
    }
  }

  fun toggleSelection(pack: InstalledStickerPack) {
    internalUiState.update {
      val wasItemSelected = it.selectedPackIds.contains(pack.id)
      val selectedPackIds = if (wasItemSelected) it.selectedPackIds.minus(pack.id) else it.selectedPackIds.plus(pack.id)

      it.copy(
        multiSelectEnabled = selectedPackIds.isNotEmpty(),
        selectedPackIds = selectedPackIds
      )
    }
  }

  fun toggleSelectAll() {
    internalUiState.update {
      it.copy(
        multiSelectEnabled = true,
        selectedPackIds = if (it.selectedPackIds.size == it.installedPacks.size) {
          emptySet()
        } else {
          it.installedPacks.map { pack -> pack.id }.toSet()
        }
      )
    }
  }

  fun setMultiSelectEnabled(isEnabled: Boolean) {
    internalUiState.update {
      it.copy(
        multiSelectEnabled = isEnabled,
        selectedPackIds = emptySet()
      )
    }
  }

  fun onSnackbarDismiss() {
    internalUiState.update {
      it.copy(actionConfirmation = null)
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
