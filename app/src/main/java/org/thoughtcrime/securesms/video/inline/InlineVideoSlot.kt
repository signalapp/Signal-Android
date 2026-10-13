/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.inline

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import org.thoughtcrime.securesms.video.exo.configureForGifPlayback

/**
 * A player paired with its own [textureView]. Cells borrow the whole slot, so the player's surface never changes when
 * playback moves between cells.
 */
@OptIn(UnstableApi::class)
@MainThread
class InlineVideoSlot(context: Context) : Player.Listener, RetainedTextureView.Callback {

  val textureView: RetainedTextureView = RetainedTextureView(context).apply {
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    callback = this@InlineVideoSlot
  }

  var listener: Listener? = null

  private var player: ExoPlayer? = null

  var mediaItem: MediaItem? = null
    private set

  var hasRenderedFirstFrame: Boolean = false
    private set

  private var surface: Surface? = null
  private val window = Timeline.Window()

  private var loopPolicy: InlineVideoLoopPolicy? = null
  private var loopPolicyHasDuration = false
  private var loopPolicyEnded = false

  /** Ignores late first frames from the previous media. */
  private val firstFrameListener = object : AnalyticsListener {
    override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
      if (eventTime.timeline.isEmpty || eventTime.timeline.getWindow(eventTime.windowIndex, window).mediaItem != mediaItem) {
        return
      }

      hasRenderedFirstFrame = true
      listener?.onFirstFrameRendered(this@InlineVideoSlot)
    }
  }

  fun bindPlayer(player: ExoPlayer) {
    check(this.player == null) { "Slot already has a player." }

    player.configureForGifPlayback()
    player.playWhenReady = false
    player.addListener(this)
    player.addAnalyticsListener(firstFrameListener)
    surface?.let { player.setVideoSurface(it) }
    mediaItem?.let {
      player.setMediaItem(it)
      player.prepare()
    }

    this.player = player
    hasRenderedFirstFrame = false
    loopPolicyHasDuration = false
    loopPolicyEnded = false
  }

  fun unbindPlayer(): ExoPlayer? {
    val player = this.player ?: return null

    player.removeListener(this)
    player.removeAnalyticsListener(firstFrameListener)
    player.setVideoSurface(null)

    this.player = null
    hasRenderedFirstFrame = false
    return player
  }

  fun setMedia(mediaItem: MediaItem) {
    if (mediaItem == this.mediaItem) {
      return
    }

    this.mediaItem = mediaItem
    hasRenderedFirstFrame = false
    loopPolicyHasDuration = false
    loopPolicyEnded = false
    player?.run {
      setMediaItem(mediaItem)
      prepare()
    }
  }

  /** Null loops forever. */
  fun setLoopPolicy(loopPolicy: InlineVideoLoopPolicy?) {
    this.loopPolicy = loopPolicy
    loopPolicyHasDuration = false
    loopPolicyEnded = false
    player?.let { applyLoopPolicyDuration(it) }
  }

  val hasPlaybackEnded: Boolean
    get() = loopPolicyEnded

  fun replay(loopPolicy: InlineVideoLoopPolicy?) {
    setLoopPolicy(loopPolicy)
    hasRenderedFirstFrame = false
    player?.seekTo(0)
  }

  /** No-op once the loop limit is reached; see [replay]. */
  fun play() {
    if (!loopPolicyEnded) {
      player?.playWhenReady = true
    }
  }

  fun pause() {
    player?.pause()
  }

  fun release(): ExoPlayer? {
    val player = unbindPlayer()
    surface?.release()
    surface = null
    textureView.release()
    return player
  }

  override fun onSurfaceTextureCreated(surfaceTexture: SurfaceTexture) {
    val surface = Surface(surfaceTexture)
    this.surface = surface
    player?.setVideoSurface(surface)
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    player?.let { applyLoopPolicyDuration(it) }
  }

  override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && loopPolicyHasDuration && loopPolicy?.shouldEndPlayback() == true) {
      loopPolicyEnded = true
      player?.pause()
      listener?.onPlaybackEnded(this)
    }
  }

  override fun onVideoSizeChanged(videoSize: VideoSize) {
    textureView.setContentSize((videoSize.width * videoSize.pixelWidthHeightRatio).toInt(), videoSize.height)
  }

  interface Listener {
    fun onFirstFrameRendered(slot: InlineVideoSlot)
    fun onPlaybackEnded(slot: InlineVideoSlot)
  }

  private fun applyLoopPolicyDuration(player: ExoPlayer) {
    val loopPolicy = loopPolicy ?: return
    if (!loopPolicyHasDuration && player.playbackState == Player.STATE_READY && player.duration != C.TIME_UNSET && player.duration > 0) {
      loopPolicy.setMediaDuration(player.duration)
      loopPolicyHasDuration = true
    }
  }
}
