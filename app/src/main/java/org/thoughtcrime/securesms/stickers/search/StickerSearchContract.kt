/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import org.signal.core.util.logging.Log
import org.signal.imageeditor.core.stickers.FeatureSticker
import org.thoughtcrime.securesms.scribbles.StickerSelectResult

/**
 * Launches [StickerSearchActivity] and describes what was picked, or null if nothing was.
 */
internal class StickerSearchContract : ActivityResultContract<Unit, StickerSelectResult?>() {

  companion object {
    private val TAG = Log.tag(StickerSearchContract::class)
  }

  override fun createIntent(context: Context, input: Unit): Intent {
    return Intent(context, StickerSearchActivity::class.java)
  }

  override fun parseResult(resultCode: Int, intent: Intent?): StickerSelectResult? {
    if (resultCode != Activity.RESULT_OK || intent == null) {
      return null
    }

    val featureStickerType: String? = intent.getStringExtra(StickerSearchActivity.EXTRA_FEATURE_STICKER)
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
