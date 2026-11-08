/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens.gif

import android.net.Uri
import androidx.annotation.MainThread
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.signal.core.util.logging.Log
import org.signal.mediakeyboard.MediaKeyboardDependencies
import org.signal.video.exo.ExoPlayerPool
import kotlin.math.abs

private val TAG = Log.tag(GifPlayers::class)

/**
 * Holds the players currently assigned to gif cells, borrowed from the app-wide [ExoPlayerPool].
 *
 * Players are handed out per gif id rather than per cell, and a cell renders its still image whenever it has none.
 * [setPlaying] returns players the grid no longer wants before asking for new ones, so scrolling reassigns the same
 * few players instead of stranding cells that lost the first race for them.
 */
class GifPlayers(private val pool: ExoPlayerPool<ExoPlayer>) {

  private val players = mutableStateMapOf<String, ExoPlayer>()

  fun playerFor(id: String): Player? = players[id]

  @MainThread
  fun setPlaying(playing: Map<String, Uri>) {
    (players.keys - playing.keys).forEach { id ->
      players.remove(id)?.let { pool.pool(it) }
    }

    playing.forEach { (id, uri) ->
      if (!players.containsKey(id)) {
        val player = pool.get(TAG)
        if (player == null) {
          Log.d(TAG, "No player available for $id, leaving it still.")
          return@forEach
        }

        players[id] = player.apply {
          // get() configures for ordinary video playback, which is neither muted nor looping.
          volume = 0f
          repeatMode = Player.REPEAT_MODE_ALL
          setMediaItem(MediaItem.fromUri(uri))
          prepare()
          playWhenReady = true
        }
      }
    }
  }

  @MainThread
  fun releaseAll() {
    players.values.forEach { pool.pool(it) }
    players.clear()
  }
}

@Composable
fun rememberGifPlayers(): GifPlayers {
  val pool = MediaKeyboardDependencies.exoPlayerPool
  val players = remember(pool) { GifPlayers(pool) }

  DisposableEffect(players) {
    onDispose { players.releaseAll() }
  }

  return players
}

/**
 * How many gifs may play at once: the whole unreserved pool, as the pre-Compose keyboard also used.
 */
@Composable
fun rememberMaxSimultaneousGifs(): Int {
  val pool = MediaKeyboardDependencies.exoPlayerPool
  return remember(pool) { pool.getPoolStats().maxUnreserved }
}

/**
 * The item indices that should be playing: the ones nearest the middle of the viewport, which are the gifs the user is
 * most likely looking at. Mirrors what the pre-Compose keyboard's playback controller worked out from adapter
 * positions, in terms of where items actually sit instead, since a staggered grid's index order is not its visual one.
 */
@Composable
fun rememberGifPlaybackSet(gridState: LazyStaggeredGridState, maxSimultaneous: Int): Set<Int> {
  val playbackSet by remember(gridState, maxSimultaneous) {
    derivedStateOf {
      val layoutInfo = gridState.layoutInfo
      val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2

      layoutInfo.visibleItemsInfo
        .sortedBy { abs(it.offset.y + it.size.height / 2 - viewportCenter) }
        .take(maxSimultaneous)
        .map { it.index }
        .toSet()
    }
  }

  return playbackSet
}
