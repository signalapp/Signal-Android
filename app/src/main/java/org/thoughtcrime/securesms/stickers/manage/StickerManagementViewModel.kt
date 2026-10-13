/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.Util
import org.signal.core.util.swap
import org.thoughtcrime.securesms.database.model.StickerPackId
import org.thoughtcrime.securesms.database.model.StickerPackKey
import org.thoughtcrime.securesms.database.model.StickerPackRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.stickers.BlessedPacks
import org.thoughtcrime.securesms.stickers.StickerUrl
import org.thoughtcrime.securesms.stickers.manage.StickerPack.DownloadStatus

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
        val allPacks = (stickerPacksResult.blessedPacks + stickerPacksResult.availablePacks + stickerPacksResult.installedPacks)
          .map { record ->
            val packId = StickerPackId(record.packId)
            StickerPack(
              record = record,
              isBlessed = BlessedPacks.contains(record.packId),
              downloadStatus = if (record.isInstalled) {
                DownloadStatus.Downloaded
              } else {
                downloadStatuses.getOrDefault(packId, DownloadStatus.NotDownloaded)
              }
            )
          }
          .sortedBy { stickerPacksResult.sortOrderByPackId.getValue(it.id) }

        val (blessedPacks, notBlessedPacks) = allPacks.partition { it.isBlessed }
        val installedPacks = allPacks.filter { it.isInstalled }

        internalUiState.update {
          it.copy(
            blessedPacks = blessedPacks,
            notBlessedPacks = notBlessedPacks,
            installedPacks = installedPacks,
            multiSelectEnabled = if (installedPacks.isEmpty()) false else it.multiSelectEnabled
          )
        }
      }
  }

  fun installStickerPack(pack: StickerPack) {
    viewModelScope.launch {
      updatePackDownloadStatus(pack.id, DownloadStatus.InProgress)

      StickerManagementRepository.installStickerPack(packId = pack.id, packKey = pack.key, notify = true)
      updatePackDownloadStatus(pack.id, DownloadStatus.Downloaded)

      internalUiState.update {
        it.copy(actionConfirmation = StickerManagementConfirmation.InstalledPack(pack.record.title))
      }
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
    val installedPackIds = internalUiState.value.installedPacks.map { it.id }.filter { packIds.contains(it) }.toSet()

    if (installedPackIds.isEmpty()) {
      return
    }

    internalUiState.update {
      it.copy(userPrompt = ConfirmRemoveStickerPacksPrompt(packIds = installedPackIds))
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

      packsToUninstall.forEach { updatePackDownloadStatus(it.id, null) }

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

  fun toggleSelection(pack: StickerPack) {
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
      val visiblePackIds = it.filteredInstalledPacks.map { pack -> pack.id }.toSet()

      it.copy(
        multiSelectEnabled = true,
        selectedPackIds = if (it.selectedPackIds.containsAll(visiblePackIds)) {
          it.selectedPackIds.minus(visiblePackIds)
        } else {
          it.selectedPackIds.plus(visiblePackIds)
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

  fun setSearchModeEnabled(isEnabled: Boolean) {
    internalUiState.update {
      it.copy(
        searchMode = isEnabled,
        searchQuery = if (isEnabled) it.searchQuery else ""
      )
    }
  }

  fun onSearchQueryChanged(query: String) {
    internalUiState.update { it.copy(searchQuery = query) }
  }

  fun onCopyPack(id: StickerPackId, key: StickerPackKey) {
    Util.copyToClipboard(AppDependencies.application, StickerUrl.createShareLink(id.value, key.value))
    internalUiState.update {
      it.copy(actionConfirmation = StickerManagementConfirmation.CopiedPack)
    }
  }

  fun onSnackbarDismiss() {
    internalUiState.update {
      it.copy(actionConfirmation = null)
    }
  }
}

data class StickerManagementUiState(
  val blessedPacks: List<StickerPack> = emptyList(),
  val notBlessedPacks: List<StickerPack> = emptyList(),
  val installedPacks: List<StickerPack> = emptyList(),
  val multiSelectEnabled: Boolean = false,
  val selectedPackIds: Set<StickerPackId> = emptySet(),
  val userPrompt: ConfirmRemoveStickerPacksPrompt? = null,
  val actionConfirmation: StickerManagementConfirmation? = null,
  val searchMode: Boolean = false,
  val searchQuery: String = ""
) {
  val searchActive: Boolean = searchQuery.isNotBlank()
  val filteredBlessedPacks: List<StickerPack> = blessedPacks.filter { it.record.title.contains(searchQuery.trim(), ignoreCase = true) }
  val filteredNotBlessedPacks: List<StickerPack> = notBlessedPacks.filter { it.record.title.contains(searchQuery.trim(), ignoreCase = true) }
  val filteredInstalledPacks: List<StickerPack> = installedPacks.filter { it.record.title.contains(searchQuery.trim(), ignoreCase = true) }
}

data class ConfirmRemoveStickerPacksPrompt(
  val packIds: Set<StickerPackId>
)

sealed interface StickerManagementConfirmation {
  data class InstalledPack(val packTitle: String) : StickerManagementConfirmation
  data class UninstalledPack(val packTitle: String) : StickerManagementConfirmation
  data class UninstalledPacks(val numPacksUninstalled: Int) : StickerManagementConfirmation
  data object CopiedPack : StickerManagementConfirmation
}

data class StickerPack(
  val record: StickerPackRecord,
  val isBlessed: Boolean,
  val downloadStatus: DownloadStatus
) {
  val id = StickerPackId(record.packId)
  val key = StickerPackKey(record.packKey)
  val isInstalled = record.isInstalled

  sealed class DownloadStatus {
    data object NotDownloaded : DownloadStatus()
    data object InProgress : DownloadStatus()
    data object Downloaded : DownloadStatus()
  }
}
