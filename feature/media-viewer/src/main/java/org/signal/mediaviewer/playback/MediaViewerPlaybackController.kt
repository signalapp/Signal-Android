/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer.playback

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.signal.video.VideoPlayer

/**
 * Bridges the active page's [VideoPlayer] to the Compose playback controls. Transient playback
 * state (position, duration, play state) lives here as snapshot state rather than in
 * [org.signal.mediaviewer.MediaViewerState] so that position ticks never enter the view model's
 * logged event queue.
 */
@Stable
open class MediaViewerPlaybackController(initialState: PlaybackUiState = PlaybackUiState()) {

  var uiState: PlaybackUiState by mutableStateOf(initialState)
    protected set

  private var player: VideoPlayer? = null
  private var isLooping = false
  private var wasPlayingBeforeScrub = false

  /** [isLooping] marks a video GIF, which restarts on its own whenever the screen resumes. */
  open fun attach(player: VideoPlayer, isLooping: Boolean) {
    this.player = player
    this.isLooping = isLooping
    uiState = PlaybackUiState(isAttached = true)
  }

  open fun detach(player: VideoPlayer) {
    if (this.player === player) {
      this.player = null
      isLooping = false
      uiState = PlaybackUiState()
    }
  }

  fun notifyReady() {
    uiState = uiState.copy(durationMs = player?.duration?.coerceAtLeast(0) ?: 0)
  }

  fun notifyPlaying() {
    uiState = uiState.copy(isPlaying = true)
  }

  fun notifyStopped() {
    uiState = uiState.copy(isPlaying = false)
  }

  open fun togglePlayPause() {
    val player = this.player ?: return
    if (uiState.isPlaying) {
      player.pause()
    } else {
      player.play()
    }
  }

  open fun beginScrub() {
    wasPlayingBeforeScrub = uiState.isPlaying
    player?.pause()
    uiState = uiState.copy(isScrubbing = true)
  }

  open fun scrubTo(positionMs: Long) {
    uiState = uiState.copy(positionMs = positionMs.coerceIn(0, uiState.durationMs))
  }

  open fun endScrub() {
    val player = this.player ?: return
    player.playbackPosition = uiState.positionMs
    if (wasPlayingBeforeScrub) {
      player.play()
    }
    uiState = uiState.copy(isScrubbing = false)
  }

  open fun rewind() {
    seekBy(-SKIP_INTERVAL_MS)
  }

  open fun fastForward() {
    seekBy(SKIP_INTERVAL_MS)
  }

  fun pause() {
    player?.pause()
  }

  fun resumeIfLooping() {
    if (isLooping) {
      player?.play()
    }
  }

  private fun seekBy(deltaMs: Long) {
    val player = this.player ?: return
    val duration = player.duration.coerceAtLeast(0)
    val target = (player.truePlaybackPosition + deltaMs).coerceIn(0, duration)
    player.playbackPosition = target
    // The ticker only runs during playback, so paused seeks must update the UI state themselves.
    uiState = uiState.copy(positionMs = target, durationMs = duration)
  }

  /** Polls the player position while playing. Run from a LaunchedEffect keyed on this controller. */
  suspend fun runPositionTicker() {
    snapshotFlow { uiState.isPlaying && !uiState.isScrubbing }
      .collectLatest { ticking ->
        while (ticking) {
          val player = this.player
          if (player != null) {
            uiState = uiState.copy(
              positionMs = player.truePlaybackPosition.coerceAtLeast(0),
              durationMs = player.duration.coerceAtLeast(0)
            )
          }
          delay(POSITION_POLL_INTERVAL_MS)
        }
      }
  }

  companion object {
    private const val POSITION_POLL_INTERVAL_MS = 200L
    private const val SKIP_INTERVAL_MS = 15_000L
  }
}

data class PlaybackUiState(
  val isAttached: Boolean = false,
  val isPlaying: Boolean = false,
  val isScrubbing: Boolean = false,
  val positionMs: Long = 0,
  val durationMs: Long = 0
)
