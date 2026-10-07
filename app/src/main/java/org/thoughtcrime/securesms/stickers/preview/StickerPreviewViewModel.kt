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
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.stickers.StickerManifest

class StickerPreviewViewModel(
  private val packId: String,
  private val packKey: String,
  private val stickerId: Int
) : EventDrivenViewModel<StickerPreviewEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(StickerPreviewViewModel::class)
  }

  private val repository = StickerPackPreviewRepository()

  private val internalState = MutableStateFlow(StickerPreviewState())
  val state: StateFlow<StickerPreviewState> = internalState.asStateFlow()

  private val internalActions: Channel<StickerPreviewAction> = Channel(Channel.BUFFERED)
  val actions: Flow<StickerPreviewAction> = internalActions.receiveAsFlow()

  init {
    onEvent(StickerPreviewEvent.Initialize)
  }

  override suspend fun processEvent(event: StickerPreviewEvent) {
    when (event) {
      StickerPreviewEvent.Initialize -> {
        loadPack()
        loadFavorited()
      }

      StickerPreviewEvent.FavoriteClicked -> {
        when (internalState.value.isFavorite) {
          true -> internalState.update { it.copy(showRemoveFavoriteDialog = true) }
          false -> setFavorite(true)
          null -> Unit
        }
      }

      StickerPreviewEvent.RemoveFavoriteConfirmed -> {
        internalState.update { it.copy(showRemoveFavoriteDialog = false) }
        setFavorite(false)
      }

      StickerPreviewEvent.RemoveFavoriteCanceled -> {
        internalState.update { it.copy(showRemoveFavoriteDialog = false) }
      }
    }
  }

  private suspend fun setFavorite(isFavorite: Boolean) {
    val result = withContext(SignalDispatchers.IO) {
      StickerFavoriteRepository.setFavorite(packId, packKey, stickerId, isFavorite)
    }

    when (result) {
      StickerFavoriteRepository.SetFavoriteResult.SUCCESS -> {
        internalState.update { it.copy(isFavorite = isFavorite) }
        internalActions.send(if (isFavorite) StickerPreviewAction.AddedToFavorites else StickerPreviewAction.RemovedFromFavorites)
      }
      StickerFavoriteRepository.SetFavoriteResult.LIMIT_REACHED -> {
        internalActions.send(StickerPreviewAction.FavoritesLimitReached)
      }
      StickerFavoriteRepository.SetFavoriteResult.FAILURE -> {
        loadFavorited()
      }
    }
  }

  private suspend fun loadFavorited() {
    val isFavorited = withContext(SignalDispatchers.IO) {
      StickerFavoriteRepository.isFavorite(packId, stickerId)
    }
    internalState.update { it.copy(isFavorite = isFavorited) }
  }

  private fun loadPack() {
    viewModelScope.launch(SignalDispatchers.IO) {
      repository.getStickerManifest(packId, packKey) { result ->
        val pack = result.orNull()
        if (pack != null) {
          internalState.update {
            it.copy(stickerManifest = pack.manifest)
          }
        } else {
          Log.w(TAG, "Unable to load manifest")
        }
      }
    }
  }
}

data class StickerPreviewState(
  val stickerManifest: StickerManifest? = null,
  val isFavorite: Boolean? = null,
  val showRemoveFavoriteDialog: Boolean = false
)

sealed interface StickerPreviewAction {
  data object AddedToFavorites : StickerPreviewAction
  data object RemovedFromFavorites : StickerPreviewAction
  data object FavoritesLimitReached : StickerPreviewAction
}
