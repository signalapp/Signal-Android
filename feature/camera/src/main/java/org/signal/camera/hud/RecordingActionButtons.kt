/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import org.signal.camera.R
import org.signal.camera.test.TestTags
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons

/**
 * Matches [GalleryThumbnailButton], whose place these take while a recording runs. The capture button reads it too: the
 * circle it carries to the lock is this size, and so is how near the lock counts as over it.
 */
internal val RecordingActionButtonSize = 52.dp

private val ActionIconSize = 24.dp

/**
 * Offers to leave a recording running without the capture button being held. It takes the gallery button's place while a
 * recording is held, which puts it within reach of the finger already on the capture button.
 *
 * There is nothing to tap: sliding onto it is what takes the offer up.
 *
 * @param isEngaged Whether a thumb has arrived over it, which it answers in the recording red so that letting go there
 *   reads as taking the offer rather than as landing on an offer still being made. It is swapped for rather than
 *   animated in place, so the two looks are told apart the same way every other control in this corner is.
 */
@Composable
fun RecordingLockButton(
  isEngaged: Boolean = false,
  modifier: Modifier = Modifier
) {
  RecordingActionButton(
    backgroundColor = colorResource(if (isEngaged) R.color.CameraHud_control_red_background else R.color.CameraHud_control_background),
    modifier = modifier.testTag(TestTags.CAMERA_HUD_LOCK_BUTTON)
  ) {
    Icon(
      imageVector = if (isEngaged) SignalIcons.LockFill.imageVector else SignalIcons.Lock.imageVector,
      contentDescription = null,
      tint = colorResource(R.color.CameraHud_control_foreground),
      modifier = Modifier.size(ActionIconSize)
    )
  }
}

/**
 * Takes the lock's place once a recording is running without being held, and offers to resume once it has been used.
 *
 * @param isPaused What the recorder reports rather than what was last asked of it, so the button cannot offer to undo a
 *   pause that never took.
 */
@Composable
fun RecordingPauseButton(
  isPaused: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  RecordingActionButton(
    onClick = onClick,
    modifier = modifier.testTag(TestTags.CAMERA_HUD_PAUSE_BUTTON)
  ) {
    Crossfade(
      targetState = isPaused,
      label = "RecordingPaused"
    ) { paused ->
      if (!paused) {
        Icon(
          imageVector = SignalIcons.Pause.imageVector,
          contentDescription = null,
          tint = colorResource(R.color.CameraHud_control_foreground),
          modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
        )
      } else {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .background(
              color = colorResource(R.color.CameraHud_control_red_background),
              shape = CircleShape
            )
        )
      }
    }
  }
}

/**
 * The circle these buttons share with the gallery button, so one can stand in for another in place.
 *
 * A button that can be pressed is clipped to the circle first, so its press indication stays inside the circle rather
 * than filling the square it is drawn in.
 */
@Composable
private fun RecordingActionButton(
  modifier: Modifier = Modifier,
  backgroundColor: Color = colorResource(R.color.CameraHud_control_background),
  onClick: (() -> Unit)? = null,
  content: @Composable () -> Unit
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .size(RecordingActionButtonSize)
      .clip(CircleShape)
      .background(backgroundColor, CircleShape)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
  ) {
    content()
  }
}

@NightPreview
@Composable
private fun RecordingLockButtonPreview() {
  Previews.Preview {
    RecordingLockButton()
  }
}

@NightPreview
@Composable
private fun RecordingLockButtonEngagedPreview() {
  Previews.Preview {
    RecordingLockButton(isEngaged = true)
  }
}

@NightPreview
@Composable
private fun RecordingPauseButtonPreview() {
  Previews.Preview {
    RecordingPauseButton(isPaused = false, onClick = {})
  }
}

@NightPreview
@Composable
private fun RecordingResumeButtonPreview() {
  Previews.Preview {
    RecordingPauseButton(isPaused = true, onClick = {})
  }
}
