/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.stickers.StickerManifest

class StickerPreviewViewModel(private val packId: String, private val packKey: String) : ViewModel() {

  companion object {
    private val TAG = Log.tag(StickerPreviewViewModel::class.java)
  }

  private val repository = StickerPackPreviewRepository()

  private val internalState = MutableStateFlow(StickerPreviewState())
  val state: StateFlow<StickerPreviewState> = internalState.asStateFlow()

  init {
    loadPack()
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
  val stickerManifest: StickerManifest? = null
)
