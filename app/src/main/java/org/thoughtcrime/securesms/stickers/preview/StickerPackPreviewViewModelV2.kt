/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.preview

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.Util
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.database.model.StickerPackParams
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.stickers.BlessedPacks
import org.thoughtcrime.securesms.stickers.StickerManifest
import org.thoughtcrime.securesms.stickers.StickerUrl
import org.thoughtcrime.securesms.stickers.manage.StickerManagementRepository
import org.thoughtcrime.securesms.stickers.preview.StickerPackPreviewUiState.ContentState
import org.thoughtcrime.securesms.stickers.preview.StickerPackPreviewUiState.UserPrompt
import kotlin.coroutines.resume

class StickerPackPreviewViewModelV2(
  private val params: StickerPackParams?
) : EventDrivenViewModel<StickerPackPreviewEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(StickerPackPreviewViewModelV2::class)
  }

  private val stickerPreviewRepo: StickerPackPreviewRepository = StickerPackPreviewRepository()
  private val internalUiState = MutableStateFlow(StickerPackPreviewUiState(contentState = ContentState.Loading))
  val uiState: StateFlow<StickerPackPreviewUiState> = internalUiState.asStateFlow()

  private val internalActions: Channel<StickerPackPreviewAction> = Channel(Channel.BUFFERED)
  val actions: Flow<StickerPackPreviewAction> = internalActions.receiveAsFlow()

  init {
    onEvent(StickerPackPreviewEvent.Initialize)
  }

  override suspend fun processEvent(event: StickerPackPreviewEvent) {
    if (params == null) {
      Log.w(TAG, "No pack params to work with.")
      showDataUnavailable()
      return
    }

    when (event) {
      StickerPackPreviewEvent.Initialize -> {
        viewModelScope.launch { loadManifest(params) }
      }

      StickerPackPreviewEvent.InstallClicked -> {
        if (!isPackInstalled()) {
          updateInstalledState(true)
          StickerManagementRepository.installStickerPack(packId = params.id, packKey = params.key, notify = true)
        }
      }

      StickerPackPreviewEvent.UninstallClicked -> showPrompt(UserPrompt.ConfirmRemovePack)

      StickerPackPreviewEvent.UninstallConfirmed -> {
        showPrompt(null)

        if (isPackInstalled()) {
          updateInstalledState(false)
          StickerManagementRepository.uninstallStickerPacks(mapOf(params.id to params.key))
        }
      }

      StickerPackPreviewEvent.UninstallCanceled -> showPrompt(null)

      StickerPackPreviewEvent.SendPackClicked -> {
        showPrompt(null)
        internalActions.send(StickerPackPreviewAction.SendPack(params))
      }

      StickerPackPreviewEvent.LinkPackClicked -> showPrompt(UserPrompt.ShareStickerPack)

      StickerPackPreviewEvent.ShareSheetDismissed -> showPrompt(null)

      StickerPackPreviewEvent.CopyLinkClicked -> {
        showPrompt(null)
        Util.copyToClipboard(AppDependencies.application, StickerUrl.createShareLink(params.id.value, params.key.value))
        internalActions.send(StickerPackPreviewAction.LinkCopied)
      }

      StickerPackPreviewEvent.ShareExternallyClicked -> {
        showPrompt(null)
        internalActions.send(StickerPackPreviewAction.ShareExternally(params))
      }
    }
  }

  private suspend fun loadManifest(params: StickerPackParams) {
    val loaded = fetchManifest(params)

    if (loaded == null) {
      showDataUnavailable()
      return
    }

    internalUiState.update { it.copy(contentState = loaded) }
  }

  private suspend fun fetchManifest(params: StickerPackParams): ContentState.HasData? {
    return suspendCancellableCoroutine { continuation ->
      stickerPreviewRepo.getStickerManifest(params.id.value, params.key.value) { result ->
        continuation.resume(
          result.map { pack ->
            ContentState.HasData(
              stickerManifest = pack.manifest,
              isPackInstalled = pack.isInstalled,
              isBlessed = BlessedPacks.contains(params.id.value)
            )
          }.orNull()
        )
      }
    }
  }

  private fun showPrompt(prompt: UserPrompt?) {
    internalUiState.update { it.copy(userPrompt = prompt) }
  }

  private fun isPackInstalled(): Boolean {
    return (internalUiState.value.contentState as? ContentState.HasData)?.isPackInstalled == true
  }

  private fun updateInstalledState(isInstalled: Boolean) {
    internalUiState.update {
      it.copy(
        contentState = if (it.contentState is ContentState.HasData) {
          it.contentState.copy(isPackInstalled = isInstalled)
        } else {
          it.contentState
        }
      )
    }
  }

  private suspend fun showDataUnavailable() {
    internalUiState.update {
      it.copy(
        contentState = ContentState.DataUnavailable,
        userPrompt = null
      )
    }

    internalActions.send(StickerPackPreviewAction.PackUnavailable)
  }
}

data class StickerPackPreviewUiState(
  val contentState: ContentState,
  val userPrompt: UserPrompt? = null
) {
  sealed interface ContentState {
    data object Loading : ContentState
    data object DataUnavailable : ContentState

    data class HasData(
      val stickerManifest: StickerManifest,
      val isPackInstalled: Boolean,
      val isBlessed: Boolean
    ) : ContentState
  }

  sealed interface UserPrompt {
    data object ConfirmRemovePack : UserPrompt
    data object ShareStickerPack : UserPrompt
  }
}
