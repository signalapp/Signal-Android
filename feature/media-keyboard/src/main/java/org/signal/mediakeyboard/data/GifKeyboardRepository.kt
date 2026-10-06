/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.data

import android.net.Uri
import org.signal.core.util.Result

/**
 * Data source for the gif page of the media keyboard.
 */
interface GifKeyboardRepository {

  /**
   * Fetches a page of gifs. An empty [query] means trending.
   */
  suspend fun getGifs(query: String, offset: Int, limit: Int): Result<GifPage, GifFetchError>
}

data class GifPage(
  val gifs: List<KeyboardGif>,
  val hasMore: Boolean
)

sealed interface GifFetchError {
  data object Network : GifFetchError
  data class Unknown(val cause: Throwable? = null) : GifFetchError
}

/**
 * A single selectable gif.
 *
 * @param still A Glide-loadable model for a static preview image (e.g. a url or resource id).
 * @param mp4PreviewUri A looping mp4 rendition to autoplay in the grid, if available.
 */
data class KeyboardGif(
  val id: String,
  val still: Any?,
  val mp4PreviewUri: Uri?,
  val width: Int,
  val height: Int
) {
  val aspectRatio: Float = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f
}
