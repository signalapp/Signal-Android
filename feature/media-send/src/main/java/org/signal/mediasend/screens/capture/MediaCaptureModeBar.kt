/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.PhonePortraitNightPreview
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediasend.test.TestTags
import kotlin.math.roundToInt
import org.signal.camera.R as CameraR

private val MODE_BAR_SHAPE = RoundedCornerShape(percent = 50)

/** Read by whatever shares the bar's row, so it can be lined up with the bar. */
internal val MODE_BAR_HEIGHT = 44.dp

/** How far the modes are inset from the edges of the bar they sit on. */
private val MODE_BAR_PADDING = 6.dp

/** Minimum width for a text entry in the bar */
private val MODE_BAR_MINIMUM_ENTRY_WIDTH = 80.dp

/** No bounce, so a mode does not wobble once it has landed under the highlight. */
private val MODE_SETTLE_SPEC = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * Lets the user switch between the capture modes the flow offers, which are
 * [MediaCaptureState.availableCaptureModes].
 *
 * The highlight is fixed to the center of the screen and the bar slides so the selected mode is the one under it. A mode
 * can be picked by tapping it or by swiping the bar until it is under the highlight; a swipe only selects once it is
 * released, so the user can slide back and forth before committing.
 *
 * @param endReservation Width at the end of the row the bar stops short of claiming, so taps there reach whatever floats
 *   over it. Layout still uses the full row, so this moves nothing.
 */
@Composable
internal fun MediaCaptureModeBar(
  availableCaptureModes: List<MediaCaptureMode>,
  selectedCaptureMode: MediaCaptureMode,
  onEvent: (MediaCaptureScreenEvents) -> Unit,
  endReservation: Dp = 0.dp,
  modifier: Modifier = Modifier
) {
  val coroutineScope = rememberCoroutineScope()
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val selectedIndex = availableCaptureModes.indexOf(selectedCaptureMode).coerceAtLeast(0)

  // Which mode is under the highlight, as an index into availableCaptureModes, fractional while a swipe has the bar
  // between two of them.
  val centeredMode = remember(availableCaptureModes) { Animatable(selectedIndex.toFloat()) }

  // How far a swipe has to travel to move the bar along by a mode. Only known once the bar has been laid out.
  var modeWidth by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(availableCaptureModes, selectedIndex) {
    centeredMode.animateTo(selectedIndex.toFloat(), MODE_SETTLE_SPEC)
  }

  Layout(
    content = {
      Box(modifier = Modifier.background(color = colorResource(CameraR.color.CameraHud_control_background), shape = MODE_BAR_SHAPE))

      Box(
        modifier = Modifier
          .onSizeChanged { modeWidth = it.width.toFloat() }
          .background(color = SignalTheme.colors.colorTransparent3, shape = MODE_BAR_SHAPE)
      )

      availableCaptureModes.forEach { captureMode ->
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .widthIn(min = MODE_BAR_MINIMUM_ENTRY_WIDTH)
            .clip(MODE_BAR_SHAPE)
            .selectable(
              selected = captureMode == selectedCaptureMode,
              onClick = { onEvent(MediaCaptureScreenEvents.CaptureModeSelected(captureMode)) }
            )
            .padding(horizontal = 20.dp)
            .testTag(captureMode.testTag)
        ) {
          Text(
            text = stringResource(captureMode.label),
            color = SignalTheme.colors.colorOnCustom,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    },
    modifier = modifier
      .height(MODE_BAR_HEIGHT)
      .selectableGroup()
      .testTag(TestTags.MEDIA_CAPTURE_MODE_BAR)
      .pointerInput(availableCaptureModes, isRtl) {
        detectHorizontalDragGestures(
          onHorizontalDrag = { _, dragAmount ->
            if (modeWidth > 0f) {
              val slid = if (isRtl) dragAmount else -dragAmount
              coroutineScope.launch {
                centeredMode.snapTo((centeredMode.value + slid / modeWidth).coerceIn(0f, availableCaptureModes.lastIndex.toFloat()))
              }
            }
          },
          onDragEnd = {
            val landedOn = centeredMode.value.roundToInt().coerceIn(availableCaptureModes.indices)
            coroutineScope.launch { centeredMode.animateTo(landedOn.toFloat(), MODE_SETTLE_SPEC) }
            onEvent(MediaCaptureScreenEvents.CaptureModeSelected(availableCaptureModes[landedOn]))
          },
          onDragCancel = {
            val landedOn = centeredMode.value.roundToInt().coerceIn(availableCaptureModes.indices)
            coroutineScope.launch { centeredMode.animateTo(landedOn.toFloat(), MODE_SETTLE_SPEC) }
          }
        )
      }
  ) { measurables, constraints ->
    val barHeight = MODE_BAR_HEIGHT.roundToPx()
    val barPadding = MODE_BAR_PADDING.roundToPx()
    val modeHeight = barHeight - barPadding * 2
    val modeWidthLimit = if (constraints.hasBoundedWidth) (constraints.maxWidth - barPadding * 2) / availableCaptureModes.size else Constraints.Infinity

    val modes = measurables.drop(2).map { it.measure(Constraints(maxWidth = modeWidthLimit, minHeight = modeHeight, maxHeight = modeHeight)) }
    val measuredModeWidth = modes.maxOf { it.width }
    val barWidth = measuredModeWidth * modes.size + barPadding * 2
    val rowWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else barWidth
    val containerWidth = (rowWidth - endReservation.roundToPx()).coerceAtLeast(0)

    val bar = measurables[0].measure(Constraints.fixed(barWidth, barHeight))
    val highlight = measurables[1].measure(Constraints.fixed(measuredModeWidth, modeHeight))

    layout(width = containerWidth, height = barHeight) {
      // The bar slides by however far the centered mode is from the middle of the row, which is where the highlight
      // always sits.
      val slide = rowWidth / 2f - barPadding - (centeredMode.value + 0.5f) * measuredModeWidth

      bar.placeRelative(x = slide.roundToInt(), y = 0)
      highlight.placeRelative(x = (rowWidth - measuredModeWidth) / 2, y = barPadding)

      modes.forEachIndexed { index, mode ->
        mode.placeRelative(
          x = (slide + barPadding + index * measuredModeWidth + (measuredModeWidth - mode.width) / 2f).roundToInt(),
          y = barPadding
        )
      }
    }
  }
}

@NightPreview
@Composable
private fun MediaCaptureModeBarPhotoPreview() {
  MediaCaptureModeBarPreview(MediaCaptureMode.PHOTO)
}

@NightPreview
@Composable
private fun MediaCaptureModeBarVideoPreview() {
  MediaCaptureModeBarPreview(MediaCaptureMode.VIDEO)
}

@NightPreview
@Composable
private fun MediaCaptureModeBarTextStoryPreview() {
  MediaCaptureModeBarPreview(MediaCaptureMode.TEXT_STORY)
}

/**
 * Every mode on offer, opened on [initialCaptureMode] and switchable from there, so each preview shows both a selection
 * and where the bar comes to rest for it.
 *
 * The bar centers its selection on whatever width it is given, so the preview is pinned to a phone's width rather than
 * left to wrap its content.
 */
@Composable
private fun MediaCaptureModeBarPreview(initialCaptureMode: MediaCaptureMode) {
  var selectedCaptureMode: MediaCaptureMode by remember { mutableStateOf(initialCaptureMode) }

  Previews.Preview {
    Box(
      modifier = Modifier
        .width(360.dp)
        .background(color = Color.Black)
    ) {
      MediaCaptureModeBar(
        availableCaptureModes = MediaCaptureMode.entries,
        selectedCaptureMode = selectedCaptureMode,
        onEvent = { event ->
          if (event is MediaCaptureScreenEvents.CaptureModeSelected) {
            selectedCaptureMode = event.mode
          }
        }
      )
    }
  }
}

/**
 * A harness for the sliding itself. Run it with the interactive preview — a swipe drives the animation imperatively, so
 * the animation inspector has nothing to show:
 *
 * - Drag anywhere along the bar to slide the modes, and let go to pick whatever is under the highlight.
 * - Tap a mode either side of the highlight to have the bar slide it in.
 * - The buttons pick a mode from outside the bar, which is the path navigation takes to and from the text story editor.
 *
 * The readout is what the bar has asked for and how many times, so a swipe that has not been released leaves it alone no
 * matter how far the modes have moved.
 */
@PhonePortraitNightPreview
@Composable
private fun MediaCaptureModeBarInteractivePreview() {
  var selectedCaptureMode: MediaCaptureMode by remember { mutableStateOf(MediaCaptureMode.PHOTO) }
  var selectionCount: Int by remember { mutableIntStateOf(0) }

  Previews.Preview {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(color = Color.Black)
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.align(Alignment.Center)
      ) {
        Text(
          text = "Asked for ${selectedCaptureMode.name} ($selectionCount times)",
          color = Color.White,
          style = MaterialTheme.typography.bodyMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MediaCaptureMode.entries.forEach { captureMode ->
            Buttons.Small(onClick = { selectedCaptureMode = captureMode }) {
              Text(text = captureMode.name)
            }
          }
        }
      }

      MediaCaptureModeBar(
        availableCaptureModes = MediaCaptureMode.entries,
        selectedCaptureMode = selectedCaptureMode,
        onEvent = { event ->
          if (event is MediaCaptureScreenEvents.CaptureModeSelected) {
            selectedCaptureMode = event.mode
            selectionCount++
          }
        },
        modifier = Modifier.align(Alignment.BottomCenter)
      )
    }
  }
}
