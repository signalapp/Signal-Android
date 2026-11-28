/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.search

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.scribbles.StickerSelectResult

/**
 * Lets the user pick a sticker for the image editor, returning it as described by [StickerSearchContract]. Always dark, to
 * match the editor it is opened from.
 */
class StickerSearchActivity : PassphraseRequiredActivity() {

  companion object {
    internal const val EXTRA_FEATURE_STICKER = "stickerSearch.featureSticker"
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )

    setContent {
      SignalTheme(isDarkMode = true) {
        ImageEditorStickerSearch(onResult = ::finishWithResult)
      }
    }
  }

  private fun finishWithResult(result: StickerSelectResult?) {
    when (result) {
      is StickerSelectResult.Sticker -> setResult(RESULT_OK, Intent().setData(result.uri))
      is StickerSelectResult.Feature -> setResult(RESULT_OK, Intent().putExtra(EXTRA_FEATURE_STICKER, result.featureSticker.type))
      null -> Unit
    }

    supportFinishAfterTransition()
  }
}
