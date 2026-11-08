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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimatable
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
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
 * The bottom control bar: the video seek row, the album rail, and the video transport buttons.
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

    AnimatedVisibility(
      visible = controlMode == MediaViewerState.ControlMode.VIDEO,
      enter = fadeIn() + expandVertically(),
      exit = fadeOut() + shrinkVertically()
    ) {
      TransportRow(
        playbackController = playbackController,
        modifier = Modifier.fillMaxWidth()
      )
    }
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
        .padding(horizontal = 12.dp)
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
  val interactionSource = remember { MutableInteractionSource() }
  val colors = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    activeTrackColor = MaterialTheme.colorScheme.onSurfaceVariant,
    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
  )

  Slider(
    value = fraction,
    onValueChange = { newFraction ->
      if (!playbackController.uiState.isScrubbing) {
        playbackController.beginScrub()
      }
      playbackController.scrubTo((newFraction * playbackController.uiState.durationMs).toLong())
    },
    onValueChangeFinished = { playbackController.endScrub() },
    interactionSource = interactionSource,
    thumb = {
      SliderDefaults.Thumb(
        interactionSource = interactionSource,
        colors = colors,
        thumbSize = DpSize(SEEK_BAR_THUMB_WIDTH, SEEK_BAR_HEIGHT)
      )
    },
    track = { sliderState ->
      SliderDefaults.Track(
        sliderState = sliderState,
        colors = colors,
        drawStopIndicator = null,
        modifier = Modifier.height(SEEK_BAR_TRACK_HEIGHT)
      )
    },
    modifier = modifier
      .height(SEEK_BAR_HEIGHT)
      .testTag(MediaViewerScreenTestTags.SEEK_BAR)
  )
}

@Composable
private fun TransportRow(
  playbackController: MediaViewerPlaybackController,
  modifier: Modifier = Modifier
) {
  val playback = playbackController.uiState
  val showSkipButtons = playback.durationMs > MINIMUM_DURATION_FOR_SKIP_MS

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
    modifier = modifier.padding(vertical = 4.dp)
  ) {
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
      onClick = { playbackController.togglePlayPause() }
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
      painter = painterResource(if (isPlaying) CoreUiR.drawable.symbol_pause_24 else CoreUiR.drawable.symbol_play_fill_24),
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
  val dynamicProperties = rememberLottieDynamicProperties(
    rememberLottieDynamicProperty(
      property = LottieProperty.COLOR,
      value = MaterialTheme.colorScheme.onSurface.toArgb(),
      keyPath = arrayOf("**")
    )
  )

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
      dynamicProperties = dynamicProperties,
      modifier = Modifier.size(SKIP_LOTTIE_SIZE)
    )
  }
}

/** m:ss with unpadded minutes (e.g. 0:07, 12:04). */
private fun formatTime(ms: Long): String {
  val totalSeconds = ms.milliseconds.inWholeSeconds.coerceAtLeast(0)
  return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private class LastRailItems(var value: List<MediaViewerItem?>)

private val SEEK_BAR_HEIGHT = 32.dp
private val SEEK_BAR_TRACK_HEIGHT = 8.dp
private val SEEK_BAR_THUMB_WIDTH = 4.dp

/** The skip Lottie artwork fills 75% of its canvas, so 32dp renders a 24dp glyph. */
private val SKIP_LOTTIE_SIZE = 32.dp

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
