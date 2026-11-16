/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.demo.data

import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import kotlinx.coroutines.delay
import org.signal.core.util.Result
import org.signal.mediakeyboard.data.GifFetchError
import org.signal.mediakeyboard.data.GifKeyboardRepository
import org.signal.mediakeyboard.data.GifPage
import org.signal.mediakeyboard.data.KeyboardGif
import org.signal.mediakeyboard.demo.R

/**
 * A fake gif source backed by bundled mp4 clips. Simulates network latency and pagination.
 * Search for "error" to see the failure state, or "empty" to see the no-results state.
 */
class DemoGifKeyboardRepository(private val context: Context) : GifKeyboardRepository {

  private data class Template(
    @field:RawRes val video: Int,
    @field:DrawableRes val still: Int,
    val width: Int,
    val height: Int
  )

  private val templates = listOf(
    Template(R.raw.demo_gif_1, R.drawable.demo_gif_still_1, 240, 240),
    Template(R.raw.demo_gif_2, R.drawable.demo_gif_still_2, 320, 240),
    Template(R.raw.demo_gif_3, R.drawable.demo_gif_still_3, 240, 320),
    Template(R.raw.demo_gif_4, R.drawable.demo_gif_still_4, 320, 180),
    Template(R.raw.demo_gif_5, R.drawable.demo_gif_still_5, 180, 320),
    Template(R.raw.demo_gif_6, R.drawable.demo_gif_still_6, 240, 180)
  )

  override suspend fun getGifs(query: String, offset: Int, limit: Int): Result<GifPage, GifFetchError> {
    delay(600)

    val normalized = query.trim().lowercase()

    if (normalized == "error") {
      return Result.failure(GifFetchError.Network)
    }

    if (normalized == "empty") {
      return Result.success(GifPage(gifs = emptyList(), hasMore = false))
    }

    val total = if (normalized.isEmpty()) 60 else 40
    if (offset >= total) {
      return Result.success(GifPage(gifs = emptyList(), hasMore = false))
    }

    val count = minOf(limit, total - offset)
    val seed = normalized.hashCode()

    val gifs = (0 until count).map { i ->
      val template = templates[(seed + offset + i).mod(templates.size)]
      KeyboardGif(
        id = "$normalized:${offset + i}",
        still = template.still,
        mp4PreviewUri = Uri.parse("android.resource://${context.packageName}/${template.video}"),
        width = template.width,
        height = template.height
      )
    }

    return Result.success(GifPage(gifs = gifs, hasMore = offset + count < total))
  }
}
