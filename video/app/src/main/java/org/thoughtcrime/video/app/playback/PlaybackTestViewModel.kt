/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import java.io.File

/**
 * Main screen view model for the video sample app.
 */
@OptIn(UnstableApi::class)
class PlaybackTestViewModel : ViewModel() {
  // Initialize an silent media source before the user selects a video. This is the closest I could find to an "empty" media source while still being nullsafe.
  private val value by lazy {
    val factory = SilenceMediaSource.Factory()
    factory.setDurationUs(1000)
    factory.createMediaSource()
  }

  private lateinit var cache: Cache

  var selectedVideo: Uri? by mutableStateOf(null)
  var mediaSource: MediaSource by mutableStateOf(value)
    private set

  /**
   * Initialize the backing cache. This is a file in the app's cache directory that has a random suffix to ensure you get cache misses on a new app launch.
   *
   * @param context required to get the file path of the cache directory.
   */
  fun initialize(context: Context) {
    val cacheDir = File(context.cacheDir.absolutePath)
    cache = SimpleCache(File(cacheDir, getRandomString(12)), NoOpCacheEvictor())
  }

  fun updateMediaSource(context: Context) {
    selectedVideo?.let {
      mediaSource = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)).createMediaSource(MediaItem.fromUri(it))
    }
  }

  /**
   * Replaces the media source with one that has a latency to each read from the media source, simulating network latency.
   * It stores the result in a cache (that does not have a penalty) to better mimic real-world performance:
   * once a chunk is downloaded from the network, it will not have to be re-fetched.
   *
   * @param context
   */
  fun updateMediaSourceTrickle(context: Context) {
    selectedVideo?.let {
      val cacheFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(SlowDataSource.Factory(context, 10))
      mediaSource = ProgressiveMediaSource.Factory(cacheFactory).createMediaSource(MediaItem.fromUri(it))
    }
  }

  fun releaseCache() {
    cache.release()
  }

  /**
   * Get random string. Will always return at least one character.
   *
   * @param length length of the returned string.
   * @return a string composed of random alphanumeric characters of the specified length (minimum of 1).
   */
  private fun getRandomString(length: Int): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length.coerceAtLeast(1))
      .map { allowedChars.random() }
      .joinToString("")
  }
}
