/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediakeyboard

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.signal.core.util.JsonUtils
import org.signal.core.util.Result
import org.signal.mediakeyboard.data.GifFetchError
import org.signal.mediakeyboard.data.GifKeyboardRepository
import org.signal.mediakeyboard.data.GifPage
import org.signal.mediakeyboard.data.KeyboardGif
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.giph.model.ChunkedImageUrl
import org.thoughtcrime.securesms.giph.model.GiphyImage
import org.thoughtcrime.securesms.giph.model.GiphyResponse
import org.thoughtcrime.securesms.net.ContentProxySelector
import java.io.IOException

/**
 * [GifKeyboardRepository] backed by the Giphy API, fetched through the content proxy.
 *
 * Fetched [GiphyImage]s are cached by id so that the host can recover the full image metadata
 * (e.g. the full-size mp4 url) for a selected [KeyboardGif] via [getGiphyImage].
 */
class SignalGifKeyboardRepository : GifKeyboardRepository {

  companion object {
    private val BASE_GIPHY_URI: Uri = Uri.parse("https://api.giphy.com/v1/gifs/")
      .buildUpon()
      .appendQueryParameter("api_key", BuildConfig.GIPHY_API_KEY)
      .build()

    private val TRENDING_URI: Uri = BASE_GIPHY_URI.buildUpon().appendPath("trending").build()
    private val SEARCH_URI: Uri = BASE_GIPHY_URI.buildUpon().appendPath("search").build()

    private const val IMAGE_CACHE_SIZE = 300
  }

  private val client: OkHttpClient by lazy {
    AppDependencies.okHttpClient.newBuilder().proxySelector(ContentProxySelector()).build()
  }

  private val imagesById = object : LinkedHashMap<String, GiphyImage>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GiphyImage>): Boolean = size > IMAGE_CACHE_SIZE
  }

  override suspend fun getGifs(query: String, offset: Int, limit: Int): Result<GifPage, GifFetchError> {
    return withContext(Dispatchers.IO) {
      try {
        val response = fetch(query.trim(), offset, limit)
        val gifs = response.data
          .filter { !it.gifUrl.isNullOrEmpty() && !it.mp4Url.isNullOrEmpty() && !it.mp4PreviewUrl.isNullOrEmpty() && !it.stillUrl.isNullOrEmpty() }
          .map { image ->
            synchronized(imagesById) {
              imagesById[image.mp4Url] = image
            }

            KeyboardGif(
              id = image.mp4Url,
              still = ChunkedImageUrl(image.stillUrl),
              mp4PreviewUri = Uri.parse(image.mp4PreviewUrl),
              width = image.gifWidth,
              height = image.gifHeight
            )
          }

        Result.success(GifPage(gifs = gifs, hasMore = offset + response.data.size < response.pagination.totalCount))
      } catch (e: IOException) {
        Result.failure(GifFetchError.Network)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(GifFetchError.Unknown(e))
      }
    }
  }

  fun getGiphyImage(id: String): GiphyImage? {
    return synchronized(imagesById) {
      imagesById[id]
    }
  }

  private fun fetch(query: String, offset: Int, limit: Int): GiphyResponse {
    val url = (if (query.isEmpty()) TRENDING_URI else SEARCH_URI)
      .buildUpon()
      .appendQueryParameter("offset", offset.toString())
      .appendQueryParameter("limit", limit.toString())
      .apply {
        if (query.isNotEmpty()) {
          appendQueryParameter("q", query)
        }
      }
      .build()
      .toString()

    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
      if (!response.isSuccessful) {
        throw IOException("Unexpected code $response")
      }

      return JsonUtils.fromJson(response.body.byteStream(), GiphyResponse::class.java)
    }
  }
}
