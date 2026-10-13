/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.camera.CameraDisplay
import org.signal.camera.R
import org.signal.camera.test.TestTags
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.R as CoreUiR

private object ZoomBarColors {
  /** The selected level, on the bar's own background. */
  val SelectedLevelHorizontal: Color
    @Composable get() = colorResource(CoreUiR.color.signal_dark_colorTransparent3)

  /** The selected level, on the viewfinder itself, where there is no bar behind it. */
  val SelectedLevelVertical: Color
    @Composable get() = colorResource(R.color.CameraHud_control_background)
}

private object ZoomBarDimensions {
  /** Each level's tap target, which is also how thick the bar is. */
  val LevelSize = 40.dp
}

private val ZoomBarShape = RoundedCornerShape(percent = 50)

/**
 * Which way the levels run, following the controls the bar sits with: a portrait phone keeps its controls along the
 * bottom and the bar above the capture button; everything else runs them down the side.
 *
 * Keyed on the window rather than the breakpoint alone, since a phone turned landscape lays out the way a larger device
 * does.
 */
private val CameraDisplay.stacksLevels: Boolean
  get() = when (this) {
    CameraDisplay.LARGE_PORTRAIT, CameraDisplay.LARGE_LANDSCAPE -> true
    else -> false
  }

/**
 * Bar which displays predetermined zoom levels.
 *
 * It offers the levels the lens can reach and the viewfinder leaves room for, and shows as selected wherever the camera
 * actually is, so a zoom arrived at some other way — a pinch, or a drag along the capture button — is reflected here. A
 * ratio between two levels selects neither.
 *
 * @param zoomRatio Where the camera is now, as it reports it
 * @param zoomRange What the bound lens can reach
 * @param cameraDisplay The window the bar has to fit in, which decides how many levels it can offer at all
 * @param onZoomLevelClick A level was picked, which the camera is expected to jump straight to
 * @param levelRotation Degrees to rotate each level by so it stays upright as the device turns. The bar itself holds
 *   still; only the numbers read wrong when the device is rotated.
 * @param visible Whether the bar can be used. It fades rather than leaving, and keeps its place while it is gone, so
 *   whatever sits next to it cannot move out from under the finger that put it away.
 */
@Composable
fun ZoomBar(
  zoomRatio: Float,
  zoomRange: ClosedFloatingPointRange<Float>,
  cameraDisplay: CameraDisplay,
  onZoomLevelClick: (ZoomBarLevel) -> Unit,
  modifier: Modifier = Modifier,
  levelRotation: Float = 0f,
  visible: Boolean = true
) {
  val availableLevels = remember(zoomRange, cameraDisplay) { ZoomBarLevel.availableIn(zoomRange, cameraDisplay) }

  // A single level is nothing to switch between, so no bar goes up at all.
  if (availableLevels.size < 2) {
    return
  }

  val selectedLevel = ZoomBarLevel.of(zoomRatio, availableLevels)
  val barAlpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, label = "ZoomBarAlpha")

  val barModifier = modifier
    .graphicsLayer { alpha = barAlpha }
    .selectableGroup()
    .testTag(TestTags.CAMERA_HUD_ZOOM_BAR)

  if (cameraDisplay.stacksLevels) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = barModifier
    ) {
      ZoomLevels(
        availableLevels = availableLevels,
        selectedLevel = selectedLevel,
        selectedLevelColor = ZoomBarColors.SelectedLevelVertical,
        levelRotation = levelRotation,
        enabled = visible,
        onZoomLevelClick = onZoomLevelClick
      )
    }
  } else {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = barModifier
        .background(color = colorResource(R.color.CameraHud_control_background), shape = ZoomBarShape)
    ) {
      ZoomLevels(
        availableLevels = availableLevels,
        selectedLevel = selectedLevel,
        selectedLevelColor = ZoomBarColors.SelectedLevelHorizontal,
        levelRotation = levelRotation,
        enabled = visible,
        onZoomLevelClick = onZoomLevelClick
      )
    }
  }
}

@Composable
private fun ZoomLevels(
  availableLevels: List<ZoomBarLevel>,
  selectedLevel: ZoomBarLevel?,
  selectedLevelColor: Color,
  levelRotation: Float,
  enabled: Boolean,
  onZoomLevelClick: (ZoomBarLevel) -> Unit
) {
  for (level in availableLevels) {
    val isSelected = level == selectedLevel
    val background by animateColorAsState(
      targetValue = if (isSelected) selectedLevelColor else Color.Transparent,
      label = "ZoomBarSelectedLevel"
    )

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .defaultMinSize(minWidth = ZoomBarDimensions.LevelSize, minHeight = ZoomBarDimensions.LevelSize)
        .clip(CircleShape)
        .background(color = background, shape = CircleShape)
        .selectable(
          selected = isSelected,
          enabled = enabled,
          onClick = { onZoomLevelClick(level) }
        )
    ) {
      // Only the text rotates: the level keeps its place on the bar and its tap target.
      Text(
        text = level.label,
        color = colorResource(R.color.CameraHud_control_foreground),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.rotate(levelRotation)
      )
    }
  }
}

@AllNightPreviews
@Composable
private fun ZoomBarPreviews() {
  ZoomBarPreview()
}

/** Anything larger than a portrait phone runs the levels down the start side instead. */
@AllNightPreviews
@Composable
private fun ZoomBarStackedPreviews() {
  ZoomBarPreview(cameraDisplay = CameraDisplay.LARGE_PORTRAIT)
}

/** A lens that reaches neither the ultra-wide nor the long end offers only the levels in between. */
@Preview(name = "Limited lens", showBackground = true, backgroundColor = 0xFF444444)
@Composable
private fun ZoomBarLimitedLensPreview() {
  ZoomBarPreview(zoomRange = 1f..3f)
}

/** The tallest window has room for every level the lens reaches. */
@Preview(name = "Roomy window", showBackground = true, backgroundColor = 0xFF444444)
@Composable
private fun ZoomBarRoomyWindowPreview() {
  ZoomBarPreview(cameraDisplay = CameraDisplay.DISPLAY_20_9)
}

/** The next window up from the shortest has room for two levels, whatever else the lens can reach. */
@Preview(name = "Room for two", showBackground = true, backgroundColor = 0xFF444444)
@Composable
private fun ZoomBarRoomForTwoPreview() {
  ZoomBarPreview(cameraDisplay = CameraDisplay.DISPLAY_18_9)
}

/** The shortest window has no room for the bar, so this draws nothing. */
@Preview(name = "No room", showBackground = true, backgroundColor = 0xFF444444)
@Composable
private fun ZoomBarNoRoomPreview() {
  ZoomBarPreview(cameraDisplay = CameraDisplay.DISPLAY_16_9)
}

/** Where a pinch tends to leave the camera: at no level in particular. */
@Preview(name = "Between levels", showBackground = true, backgroundColor = 0xFF444444)
@Composable
private fun ZoomBarBetweenLevelsPreview() {
  ZoomBarPreview(initialZoomRatio = 3.4f)
}

/** A phone turned on its side: the bar holds its place and only the numbers rotate. */
@Preview(name = "Device turned", showBackground = true, backgroundColor = 0xFF444444)
@Composable
private fun ZoomBarRotatedPreview() {
  ZoomBarPreview(levelRotation = 90f)
}

@Composable
private fun ZoomBarPreview(
  zoomRange: ClosedFloatingPointRange<Float> = 0.5f..10f,
  cameraDisplay: CameraDisplay = CameraDisplay.DISPLAY_20_9,
  initialZoomRatio: Float = 1f,
  levelRotation: Float = 0f
) {
  var zoomRatio by remember { mutableFloatStateOf(initialZoomRatio) }

  Previews.Preview {
    ZoomBar(
      zoomRatio = zoomRatio,
      zoomRange = zoomRange,
      cameraDisplay = cameraDisplay,
      onZoomLevelClick = { zoomRatio = it.zoomLevel },
      levelRotation = levelRotation
    )
  }
}
