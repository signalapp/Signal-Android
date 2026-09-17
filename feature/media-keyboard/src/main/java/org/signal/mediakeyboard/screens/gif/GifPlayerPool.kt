/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.gif

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * A small pool of muted, looping [ExoPlayer]s used to autoplay gif previews in the grid.
 * At most [maxSimultaneous] players exist at once; cells beyond that render their still image.
 */
class GifPlayerPool(
  private val context: Context,
  private val maxSimultaneous: Int
) {

  private val active = mutableMapOf<String, ExoPlayer>()

  fun acquire(id: String, uri: Uri): Player? {
    active[id]?.let { return it }

    if (active.size >= maxSimultaneous) {
      return null
    }

    val player = ExoPlayer.Builder(context).build().apply {
      volume = 0f
      repeatMode = Player.REPEAT_MODE_ALL
      setMediaItem(MediaItem.fromUri(uri))
      prepare()
      playWhenReady = true
    }

    active[id] = player
    return player
  }

  fun release(id: String) {
    active.remove(id)?.release()
  }

  fun releaseAll() {
    active.values.forEach { it.release() }
    active.clear()
  }
}

@Composable
fun rememberGifPlayerPool(maxSimultaneous: Int = 3): GifPlayerPool {
  val context = LocalContext.current.applicationContext
  val pool = remember(context, maxSimultaneous) { GifPlayerPool(context, maxSimultaneous) }

  DisposableEffect(pool) {
    onDispose { pool.releaseAll() }
  }

  return pool
}
