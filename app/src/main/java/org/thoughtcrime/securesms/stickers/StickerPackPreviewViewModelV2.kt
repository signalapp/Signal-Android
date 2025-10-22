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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.database.model.StickerPackParams
import org.thoughtcrime.securesms.stickers.StickerPackPreviewUiState.ContentState
import kotlin.jvm.optionals.getOrElse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class StickerPackPreviewViewModelV2(
  params: StickerPackParams?
) : ViewModel() {
  private val stickerPreviewRepo: StickerPackPreviewRepository = StickerPackPreviewRepository()
  private val internalUiState = MutableStateFlow(StickerPackPreviewUiState(contentState = ContentState.Loading))
  val uiState: StateFlow<StickerPackPreviewUiState> = internalUiState.asStateFlow()

  init {
    if (params != null) {
      loadData(params)
    } else {
      showDataUnavailableState()
    }
  }

  private fun loadData(params: StickerPackParams) {
    stickerPreviewRepo.getStickerManifest(params.id.value, params.key.value) { result ->
      val stickerManifest = result.map { it.manifest }.orNull()
      if (stickerManifest != null) {
        internalUiState.update {
          it.copy(
            contentState = ContentState.HasData(
              stickerManifest = stickerManifest,
              isPackInstalled = result.map { pack -> pack.isInstalled }.getOrElse { false }
            )
          )
        }
      } else {
        showDataUnavailableState()
      }
    }
  }

  fun installStickerPack(params: StickerPackParams) {
    viewModelScope.launch {
      StickerManagementRepository.installStickerPack(packId = params.id, packKey = params.key, notify = true)
      updateInstalledState(true)
    }
  }

  fun uninstallStickerPack(params: StickerPackParams) {
    viewModelScope.launch {
      StickerManagementRepository.uninstallStickerPacks(mapOf(params.id to params.key))
      updateInstalledState(false)
    }
  }

  private fun updateInstalledState(isInstalled: Boolean) {
    internalUiState.update {
      it.copy(
        contentState = if (it.contentState is ContentState.HasData) {
          it.contentState.copy(isPackInstalled = isInstalled)
        } else {
          it.contentState
        },
        navTarget = StickerPackPreviewUiState.NavTarget.Up(delay = 500.milliseconds)
      )
    }
  }

  private fun showDataUnavailableState() {
    internalUiState.update {
      it.copy(
        contentState = ContentState.DataUnavailable,
        userMessage = StickerPackPreviewUiState.MessageType.STICKER_PACK_LOAD_FAILED,
        navTarget = StickerPackPreviewUiState.NavTarget.Up(delay = 1.seconds)
      )
    }
  }

  fun setNavTargetConsumed() {
    internalUiState.update { it.copy(navTarget = null) }
  }

  fun setUserMessageConsumed() {
    internalUiState.update { it.copy(userMessage = null) }
  }
}

data class StickerPackPreviewUiState(
  val contentState: ContentState,
  val userMessage: MessageType? = null,
  val navTarget: NavTarget? = null
) {
  sealed interface ContentState {
    data object Loading : ContentState
    data object DataUnavailable : ContentState

    data class HasData(
      val stickerManifest: StickerManifest,
      val isPackInstalled: Boolean
    ) : ContentState
  }

  sealed interface NavTarget {
    data class Up(val delay: Duration? = null) : NavTarget
  }

  enum class MessageType {
    STICKER_PACK_LOAD_FAILED
  }
}
