/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.chats

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * Allows the setting of a "fake" bitmap driven by a graphics layer to coordinate delayed animations
 * in lieu of proper support for postponing enter transitions.
 */
@Stable
class ConversationTransitionState private constructor(
  val isSplitPane: Boolean,
  val graphicsLayer: GraphicsLayer
) {
  companion object {
    @Composable
    fun remember(isSplitPane: Boolean): ConversationTransitionState {
      val graphicsLayer = rememberGraphicsLayer()

      return remember(isSplitPane) {
        ConversationTransitionState(isSplitPane, graphicsLayer)
      }
    }
  }

  var chatBitmap: ImageBitmap? by mutableStateOf(null)
    private set

  private var hasWrittenToGraphicsLayer: Boolean by mutableStateOf(false)

  suspend fun writeGraphicsLayerToBitmap() {
    // toImageBitmap() uses LayerSnapshot which has format compatibility issues on Android 7 and below
    if (Build.VERSION.SDK_INT >= 26 && !isSplitPane && hasWrittenToGraphicsLayer) {
      chatBitmap = graphicsLayer.toImageBitmap()
    }
  }

  fun writeContentToGraphicsLayer(): Modifier {
    if (isSplitPane) return Modifier

    return Modifier.drawWithContent {
      graphicsLayer.record {
        this@drawWithContent.drawContent()
        hasWrittenToGraphicsLayer = true
      }

      drawLayer(graphicsLayer)
    }
  }

  fun clearBitmap() {
    chatBitmap = null
  }
}
