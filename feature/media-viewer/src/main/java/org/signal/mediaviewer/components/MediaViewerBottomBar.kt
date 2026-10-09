/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer.components

import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimatable
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.mediaviewer.MediaViewerEvent
import org.signal.mediaviewer.MediaViewerItem
import org.signal.mediaviewer.MediaViewerPreviewFixtures
import org.signal.mediaviewer.MediaViewerScreenTestTags
import org.signal.mediaviewer.MediaViewerState
import org.signal.mediaviewer.R
import org.signal.mediaviewer.playback.MediaViewerPlaybackController
import org.signal.mediaviewer.playback.PlaybackUiState
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import org.signal.core.ui.R as CoreUiR

/**
 * The bottom control bar: the video seek row, the album rail, and the share / transport / forward
 * button row.
 */
@Composable
internal fun MediaViewerBottomBar(
  controlMode: MediaViewerState.ControlMode,
  railItems: List<MediaViewerItem?>,
  selectedRailItem: MediaViewerItem?,
  playbackController: MediaViewerPlaybackController,
  onEvent: (MediaViewerEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier.fillMaxWidth()) {
    AnimatedVisibility(
      visible = controlMode == MediaViewerState.ControlMode.VIDEO,
      enter = fadeIn() + expandVertically(),
      exit = fadeOut() + shrinkVertically()
    ) {
      SeekBarRow(
        playbackController = playbackController,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp)
          .padding(top = 12.dp)
      )
    }

    // Keeps the last populated list so the rail's exit animation still has thumbnails to fade out.
    val railVisible = railItems.size > 1
    val lastRailItems = remember { LastRailItems(railItems) }
    if (railVisible) {
      lastRailItems.value = railItems
    }
    val displayedRailItems = lastRailItems.value

    AnimatedVisibility(
      visible = railVisible,
      enter = fadeIn() + expandVertically(),
      exit = fadeOut() + shrinkVertically()
    ) {
      AlbumRail(
        items = displayedRailItems,
        selectedItem = selectedRailItem,
        onEvent = onEvent,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 8.dp)
      )
    }

    ButtonRow(
      controlMode = controlMode,
      playbackController = playbackController,
      onEvent = onEvent,
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@Composable
private fun SeekBarRow(
  playbackController: MediaViewerPlaybackController,
  modifier: Modifier = Modifier
) {
  val playback = playbackController.uiState

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
  ) {
    Text(
      text = formatTime(playback.positionMs),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.testTag(MediaViewerScreenTestTags.POSITION_LABEL)
    )

    SeekBarSlider(
      playbackController = playbackController,
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 16.dp)
    )

    Text(
      text = stringResource(R.string.MediaViewerScreen__remaining_time, formatTime((playback.durationMs - playback.positionMs).coerceAtLeast(0))),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.testTag(MediaViewerScreenTestTags.DURATION_LABEL)
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBarSlider(
  playbackController: MediaViewerPlaybackController,
  modifier: Modifier = Modifier
) {
  val playback = playbackController.uiState
  val fraction = if (playback.durationMs > 0) playback.positionMs.toFloat() / playback.durationMs else 0f

  Slider(
    value = fraction,
    onValueChange = { newFraction ->
      if (!playbackController.uiState.isScrubbing) {
        playbackController.beginScrub()
      }
      playbackController.scrubTo((newFraction * playbackController.uiState.durationMs).toLong())
    },
    onValueChangeFinished = { playbackController.endScrub() },
    thumb = {
      Box(
        modifier = Modifier
          .requiredSize(8.dp)
          .offset(y = 4.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.onSurface)
      )
    },
    track = { sliderState ->
      Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
          .fillMaxWidth()
          .height(SEEK_BAR_HEIGHT)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth(sliderState.value.coerceIn(0f, 1f))
              .fillMaxHeight()
              .background(MaterialTheme.colorScheme.onSurface)
          )
        }
      }
    },
    modifier = modifier
      .height(SEEK_BAR_HEIGHT)
      .testTag(MediaViewerScreenTestTags.SEEK_BAR)
  )
}

@Composable
private fun ButtonRow(
  controlMode: MediaViewerState.ControlMode,
  playbackController: MediaViewerPlaybackController,
  onEvent: (MediaViewerEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val playback = playbackController.uiState
  val isVideo = controlMode == MediaViewerState.ControlMode.VIDEO
  val showSkipButtons = isVideo && playback.durationMs > MINIMUM_DURATION_FOR_SKIP_MS

  Box(modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
    IconButton(
      onClick = {
        playbackController.pause()
        onEvent(MediaViewerEvent.ShareClicked)
      },
      modifier = Modifier
        .align(Alignment.CenterStart)
        .testTag(MediaViewerScreenTestTags.SHARE_BUTTON)
    ) {
      Icon(
        painter = painterResource(CoreUiR.drawable.symbol_share_android_24),
        contentDescription = stringResource(R.string.MediaViewerScreen__share),
        tint = MaterialTheme.colorScheme.onSurface
      )
    }

    AnimatedVisibility(
      visible = isVideo,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.Center)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (showSkipButtons) {
          SkipButton(
            rawRes = R.raw.lottie_15s_skip_back,
            contentDescription = stringResource(R.string.MediaViewerScreen__rewind),
            onClick = { playbackController.rewind() },
            modifier = Modifier.testTag(MediaViewerScreenTestTags.REWIND_BUTTON)
          )
        }

        PlayPauseButton(
          isPlaying = playback.isPlaying,
          onClick = { playbackController.togglePlayPause() },
          modifier = Modifier.padding(horizontal = 8.dp)
        )

        if (showSkipButtons) {
          SkipButton(
            rawRes = R.raw.lottie_15s_skip_forward,
            contentDescription = stringResource(R.string.MediaViewerScreen__fast_forward),
            onClick = { playbackController.fastForward() },
            modifier = Modifier.testTag(MediaViewerScreenTestTags.FAST_FORWARD_BUTTON)
          )
        }
      }
    }

    IconButton(
      onClick = {
        playbackController.pause()
        onEvent(MediaViewerEvent.ForwardClicked)
      },
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .testTag(MediaViewerScreenTestTags.FORWARD_BUTTON)
    ) {
      Icon(
        painter = painterResource(CoreUiR.drawable.symbol_forward_24),
        contentDescription = stringResource(R.string.MediaViewerScreen__forward),
        tint = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}

@Composable
private fun PlayPauseButton(
  isPlaying: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  IconButton(
    onClick = onClick,
    modifier = modifier.testTag(MediaViewerScreenTestTags.PLAY_PAUSE_BUTTON)
  ) {
    Icon(
      painter = painterResource(if (isPlaying) CoreUiR.drawable.symbol_pause_24 else CoreUiR.drawable.symbol_play_24),
      contentDescription = stringResource(if (isPlaying) R.string.MediaViewerScreen__pause else R.string.MediaViewerScreen__play),
      tint = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun SkipButton(
  @RawRes rawRes: Int,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
  val animatable: LottieAnimatable = rememberLottieAnimatable()
  val scope = rememberCoroutineScope()

  IconButton(
    onClick = {
      onClick()
      scope.launch { animatable.animate(composition) }
    },
    modifier = modifier.semantics { this.contentDescription = contentDescription }
  ) {
    LottieAnimation(
      composition = composition,
      progress = { animatable.progress },
      modifier = Modifier.size(20.dp)
    )
  }
}

/** m:ss with unpadded minutes (e.g. 0:07, 12:04). */
private fun formatTime(ms: Long): String {
  val totalSeconds = ms.milliseconds.inWholeSeconds.coerceAtLeast(0)
  return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private class LastRailItems(var value: List<MediaViewerItem?>)

private val SEEK_BAR_HEIGHT = 24.dp

private const val MINIMUM_DURATION_FOR_SKIP_MS = 30_000L

@DayNightPreviews
@Composable
private fun MediaViewerBottomBarImagePreview() {
  Previews.Preview {
    MediaViewerBottomBar(
      controlMode = MediaViewerState.ControlMode.IMAGE,
      railItems = emptyList(),
      selectedRailItem = null,
      playbackController = MediaViewerPlaybackController(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaViewerBottomBarAlbumPreview() {
  val items = MediaViewerPreviewFixtures.previewItems(3)

  Previews.Preview {
    MediaViewerBottomBar(
      controlMode = MediaViewerState.ControlMode.IMAGE,
      railItems = items,
      selectedRailItem = items[1],
      playbackController = MediaViewerPlaybackController(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaViewerBottomBarVideoPreview() {
  Previews.Preview {
    MediaViewerBottomBar(
      controlMode = MediaViewerState.ControlMode.VIDEO,
      railItems = emptyList(),
      selectedRailItem = null,
      playbackController = MediaViewerPlaybackController(
        PlaybackUiState(isAttached = true, isPlaying = true, positionMs = 7_000, durationMs = 112_000)
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaViewerBottomBarShortVideoPreview() {
  Previews.Preview {
    MediaViewerBottomBar(
      controlMode = MediaViewerState.ControlMode.VIDEO,
      railItems = emptyList(),
      selectedRailItem = null,
      playbackController = MediaViewerPlaybackController(
        PlaybackUiState(isAttached = true, positionMs = 1_000, durationMs = 20_000)
      ),
      onEvent = {}
    )
  }
}
