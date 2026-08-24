/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.scribbles

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.scribbles.stickers.FeatureSticker

/**
 * Launches [ImageEditorStickerSelectActivity] and describes what was picked.
 */
internal class StickerSelectActivityContract : ActivityResultContract<Unit, StickerSelectResult?>() {

  companion object {
    private val TAG = Log.tag(StickerSelectActivityContract::class)
  }

  override fun createIntent(context: Context, input: Unit): Intent {
    return Intent(context, ImageEditorStickerSelectActivity::class.java)
  }

  override fun parseResult(resultCode: Int, intent: Intent?): StickerSelectResult? {
    if (resultCode != Activity.RESULT_OK || intent == null) {
      return null
    }

    val featureStickerType: String? = intent.getStringExtra(ImageEditorStickerSelectActivity.EXTRA_FEATURE_STICKER)
    if (featureStickerType != null) {
      val featureSticker = FeatureSticker.entries.firstOrNull { it.type == featureStickerType }
      if (featureSticker == null) {
        Log.w(TAG, "Unrecognized feature sticker type.")
        return null
      }

      return StickerSelectResult.Feature(featureSticker)
    }

    return intent.data?.let { StickerSelectResult.Sticker(it) }
  }
}

internal sealed interface StickerSelectResult {
  data class Sticker(val uri: Uri) : StickerSelectResult
  data class Feature(val featureSticker: FeatureSticker) : StickerSelectResult
}
