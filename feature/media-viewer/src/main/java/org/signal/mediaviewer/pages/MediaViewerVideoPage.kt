/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer.pages

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType
import org.signal.glide.decryptableuri.DecryptableUri
import org.signal.mediaviewer.MediaViewerDependencies
import org.signal.mediaviewer.MediaViewerEvent
import org.signal.mediaviewer.MediaViewerItem
import org.signal.mediaviewer.MediaViewerPreviewFixtures
import org.signal.mediaviewer.MediaViewerScreenTestTags
import org.signal.mediaviewer.playback.MediaViewerPlaybackController
import org.signal.video.VideoPlayer
import org.signal.core.ui.R as CoreUiR

/**
 * A video page. Only the settled page holds a pooled player; inactive pages render a first-frame
 * thumbnail so that at most one ExoPlayer is ever reserved by the viewer.
 *
 * While [isReturningToSharedElement], the thumbnail is laid back over the player and the player is
 * released once the thumbnail has pixels, so the shared element return never has a SurfaceView
 * punching through it.
 */
@Composable
internal fun MediaViewerVideoPage(
  item: MediaViewerItem,
  isActive: Boolean,
  isReturningToSharedElement: Boolean,
  playbackController: MediaViewerPlaybackController,
  onEvent: (MediaViewerEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  // No opaque background: until the video or its thumbnail has pixels, the host's transition
  // image beneath this screen must stay visible.
  Box(
    modifier = modifier
      .fillMaxSize()
      .testTag(MediaViewerScreenTestTags.PAGE_VIDEO)
  ) {
    var firstFrameRendered by remember(isActive) { mutableStateOf(false) }
    var thumbnailEverLoaded by remember { mutableStateOf(false) }
    val showThumbnail = !isActive || !firstFrameRendered || isReturningToSharedElement
    var thumbnailLoaded by remember(showThumbnail) { mutableStateOf(false) }

    // The player must only exist while the thumbnail above it has pixels: attaching its SurfaceView
    // punches a transparent hole through everything else the window has drawn (the host's
    // transition image included), and that hole is exposed until both the thumbnail above it has
    // rendered and the video has produced a frame. Likewise on the way out, the player stays until
    // the thumbnail covering it has rendered.
    val showPlayer = isActive && thumbnailEverLoaded && !(isReturningToSharedElement && thumbnailLoaded)
    if (showPlayer) {
      ActiveVideoPlayer(
        item = item,
        playbackController = playbackController,
        onEvent = onEvent,
        onFirstFrameRendered = { firstFrameRendered = true },
        modifier = Modifier.fillMaxSize()
      )
    }

    if (showThumbnail) {
      VideoThumbnail(
        item = item,
        onEvent = onEvent,
        onLoadCompleted = {
          thumbnailLoaded = true
          thumbnailEverLoaded = true
        },
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

@Composable
private fun ActiveVideoPlayer(
  item: MediaViewerItem,
  playbackController: MediaViewerPlaybackController,
  onEvent: (MediaViewerEvent) -> Unit,
  onFirstFrameRendered: () -> Unit,
  modifier: Modifier = Modifier
) {
  if (LocalInspectionMode.current) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.background))
    return
  }

  val window = LocalActivity.current?.window
  val notifyFirstFrameRendered = onFirstFrameRendered

  AndroidView(
    factory = { context ->
      VideoPlayer(context).apply {
        setExoPlayerPool(MediaViewerDependencies.exoPlayerPool)
        setWindow(window)
        hideControls()
        playbackController.attach(this, item.isVideoGif)
      }
    },
    update = { player ->
      player.setOnClickListener { onEvent(MediaViewerEvent.MediaTapped) }
      player.setPlayerCallback(object : VideoPlayer.PlayerCallback {
        override fun onReady() {
          playbackController.notifyReady()
        }

        // MediaReady gates the activity's transition-image fade-out, so it must not fire until
        // the player has actually put a frame on screen.
        override fun onFirstFrameRendered() {
          notifyFirstFrameRendered()
          onEvent(MediaViewerEvent.MediaReady)
        }

        override fun onPlaying() {
          playbackController.notifyPlaying()
          onEvent(MediaViewerEvent.VideoPlaybackStarted)
        }

        override fun onStopped() {
          playbackController.notifyStopped()
          onEvent(MediaViewerEvent.VideoPlaybackStopped)
        }

        override fun onError(e: Exception) {
          onEvent(MediaViewerEvent.VideoPlaybackError(item.uri, e))
        }
      })

      if (player.tag != item.uri) {
        player.tag = item.uri
        player.setVideoSource(item.playbackUri ?: item.uri, true, VIDEO_PLAYER_POOL_TAG)
        // Must come after setVideoSource, which acquires the pooled player these calls configure.
        if (item.isVideoGif) {
          player.loopForever()
          player.disableAudioFocus()
        }
        if (item.width > 0 && item.height > 0) {
          player.setInitialAspectRatio(item.width.toFloat() / item.height)
        }
      }
    },
    onRelease = { player ->
      playbackController.detach(player)
      player.cleanup()
    },
    modifier = modifier
  )
}

@Composable
private fun VideoThumbnail(
  item: MediaViewerItem,
  onEvent: (MediaViewerEvent) -> Unit,
  modifier: Modifier = Modifier,
  onLoadCompleted: (() -> Unit)? = null
) {
  Box(
    modifier = modifier.clickable(
      interactionSource = remember { MutableInteractionSource() },
      indication = null
    ) { onEvent(MediaViewerEvent.MediaTapped) },
    contentAlignment = Alignment.Center
  ) {
    GlideImage(
      model = remember(item.uri) { DecryptableUri(item.uri, thumbnailTimeUs = VIDEO_THUMBNAIL_TIME_US) },
      scaleType = GlideImageScaleType.FIT_CENTER,
      contentScale = ContentScale.Fit,
      onLoadCompleted = onLoadCompleted,
      modifier = Modifier.fillMaxSize()
    )

    if (!item.isVideoGif) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .background(if (isSystemInDarkTheme()) SignalTheme.colors.colorTransparentInverse4 else SignalTheme.colors.colorTransparent4, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          painter = painterResource(CoreUiR.drawable.symbol_play_fill_24),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(32.dp)
        )
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun MediaViewerVideoPagePreview() {
  Previews.Preview {
    MediaViewerVideoPage(
      item = MediaViewerPreviewFixtures.previewItem(contentType = "video/mp4"),
      isActive = false,
      isReturningToSharedElement = false,
      playbackController = MediaViewerPlaybackController(),
      onEvent = {},
      modifier = Modifier.fillMaxSize()
    )
  }
}

private const val VIDEO_PLAYER_POOL_TAG = "MediaViewer"
private const val VIDEO_THUMBNAIL_TIME_US = 1000L
