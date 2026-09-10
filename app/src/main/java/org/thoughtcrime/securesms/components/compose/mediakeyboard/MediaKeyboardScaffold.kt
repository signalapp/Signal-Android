/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.compose.mediakeyboard

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.signal.core.ui.getWindowSizeClass
import org.signal.core.ui.isHeightCompact
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/** Shapes back gesture progress into travel. Matches the platform's IME hide curve. */
private val BACK_TRACKING_EASING = FastOutLinearInEasing

/** Settles the keyboard once a back gesture lets go. Lower stiffness is heavier. */
private val BACK_SETTLE_MOTION = spring<Float>(
  dampingRatio = Spring.DampingRatioNoBouncy,
  stiffness = Spring.StiffnessMediumLow
)

/** How long to hold space for a system keyboard that was asked for but never appeared. */
private val SYSTEM_KEYBOARD_ARRIVAL_TIMEOUT = 1.seconds

private val SHEET_POSITIONAL_THRESHOLD = 56.dp
private val SHEET_VELOCITY_THRESHOLD = 125.dp

/**
 * Displays [content] alongside keyboards of our own that stand in for the system keyboard.
 *
 * Only one is ever up, and never alongside the system keyboard. The scaffold owns every window inset,
 * handing [content] an already-inset space to lay out in.
 *
 * @param controller Requests which keyboard is up. Stable, so view code may hold one.
 * @param onEvent Receives everything the host may need to react to.
 * @param keyboardsProvider Declares the available keyboards.
 * @param keyboardHeight Bounds on how tall a keyboard may be.
 * @param adjustContentForInput False to let a keyboard cover [content] rather than resize it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaKeyboardScaffold(
  controller: MediaKeyboardController,
  onEvent: (MediaKeyboardEvents) -> Unit,
  keyboardsProvider: MediaKeyboardScope.() -> Unit,
  modifier: Modifier = Modifier,
  keyboardHeight: MediaKeyboardHeight = MediaKeyboardHeight(),
  adjustContentForInput: Boolean = true,
  content: @Composable () -> Unit
) {
  val registry = remember(keyboardsProvider) { MediaKeyboardRegistry().apply(keyboardsProvider) }

  val density = LocalDensity.current
  val imeInsets = WindowInsets.ime
  val imeAnimationTarget = WindowInsets.imeAnimationTarget
  val windowHeightPx = LocalWindowInfo.current.containerSize.height

  val resources = LocalResources.current
  val configuration = LocalConfiguration.current
  val isHeightCompact = remember(resources, configuration) { resources.getWindowSizeClass().isHeightCompact }

  val minimumHeightPx = with(density) { keyboardHeight.minimum.roundToPx() }
  val topMarginPx = with(density) { keyboardHeight.topMargin.roundToPx() }

  val activeKey = controller.current?.takeIf { registry.isEnabled(it) }

  // The target, so a hide reads as gone the moment it is asked for rather than a whole animation later.
  val systemKeyboardVisible = imeAnimationTarget.getBottom(density) > 0

  // Whether the live inset has yet to catch up with the target. Deliberately not imeAnimationSource,
  // which the platform leaves behind whenever an animation ends without a duration to run down, and
  // which would then read as animating until the next keyboard came and went. Derived, so the frames
  // the live inset walks through are not each a recomposition.
  val systemKeyboardAnimating by remember(imeInsets, imeAnimationTarget, density) {
    derivedStateOf { imeInsets.getBottom(density) != imeAnimationTarget.getBottom(density) }
  }

  SideEffect {
    controller.isSystemKeyboardVisible = systemKeyboardVisible
  }

  // The controller outlives us. current is left set so a rebuilt view restores its keyboard.
  DisposableEffect(controller) {
    onDispose {
      controller.isSystemKeyboardVisible = false
      controller.awaitingSystemKeyboard = false
    }
  }

  var hasReportedKeyboardVisibility by remember { mutableStateOf(false) }
  LaunchedEffect(systemKeyboardVisible) {
    if (hasReportedKeyboardVisibility) {
      onEvent(MediaKeyboardEvents.SystemKeyboardVisibilityChanged(systemKeyboardVisible))
    }
    hasReportedKeyboardVisibility = true
  }

  LaunchedEffect(controller.awaitingSystemKeyboard) {
    if (controller.awaitingSystemKeyboard) {
      delay(SYSTEM_KEYBOARD_ARRIVAL_TIMEOUT)
      controller.awaitingSystemKeyboard = false
    }
  }

  // From the animation target, not the live inset, which walks down through every closing frame.
  LaunchedEffect(imeAnimationTarget, density, minimumHeightPx, isHeightCompact) {
    if (isHeightCompact) {
      return@LaunchedEffect
    }

    snapshotFlow { imeAnimationTarget.getBottom(density) }
      .filter { it > minimumHeightPx }
      .distinctUntilChanged()
      .collect {
        controller.keyboardHeightPx = it
        onEvent(MediaKeyboardEvents.SystemKeyboardHeightMeasured(it))
      }
  }

  // dropWhile rather than drop, so a composition that starts mid-animation still reports the settle
  // it is in the middle of instead of swallowing it as the initial state.
  LaunchedEffect(imeInsets, imeAnimationTarget, density) {
    snapshotFlow { imeInsets.getBottom(density) == imeAnimationTarget.getBottom(density) }
      .distinctUntilChanged()
      .dropWhile { settled -> settled }
      .filter { settled -> settled }
      .collect {
        controller.awaitingSystemKeyboard = false
        onEvent(MediaKeyboardEvents.SystemKeyboardAnimationEnded)
      }
  }

  val heightPx = keyboardHeight.resolve(
    preferredPx = controller.keyboardHeightPx,
    windowHeightPx = windowHeightPx,
    minimumPx = minimumHeightPx,
    topMarginPx = topMarginPx
  )
  val height = with(density) { heightPx.toDp() }

  val backProgress = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()

  val sheetState = remember {
    SheetState(
      skipPartiallyExpanded = true,
      positionalThreshold = { with(density) { SHEET_POSITIONAL_THRESHOLD.toPx() } },
      velocityThreshold = { with(density) { SHEET_VELOCITY_THRESHOLD.toPx() } },
      initialValue = SheetValue.Hidden,
      skipHiddenState = false
    )
  }
  val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

  // This just makes sure the previously visible state doesn't go away too early while we're mid swap.
  var visibleKey by remember { mutableStateOf<MediaKeyboardKey?>(null) }

  LaunchedEffect(activeKey) {
    if (activeKey != null) {
      if (visibleKey != null && visibleKey != activeKey) {
        onEvent(MediaKeyboardEvents.KeyboardHidden)
      }

      visibleKey = activeKey
      backProgress.snapTo(0f)
      sheetState.expand()
      onEvent(MediaKeyboardEvents.KeyboardShown(activeKey))
    } else if (visibleKey != null) {
      // A gesture may already have carried it off screen; hold that until the hide completes.
      sheetState.hide()
      backProgress.snapTo(0f)
      visibleKey = null
      onEvent(MediaKeyboardEvents.KeyboardHidden)
    }
  }

  PredictiveBackHandler(enabled = activeKey != null) { progress ->
    try {
      progress.collect { backEvent -> backProgress.snapTo(BACK_TRACKING_EASING.transform(backEvent.progress)) }

      backProgress.animateTo(1f, BACK_SETTLE_MOTION)
      controller.hide()
      onEvent(MediaKeyboardEvents.DismissedByBack)
    } catch (cancelled: CancellationException) {
      // PredictiveBackHandler cancels this job, so the unwind must run somewhere that outlives it.
      scope.launch { backProgress.animateTo(0f, BACK_SETTLE_MOTION) }
      throw cancelled
    }
  }

  val systemKeyboardTakingOverSpace = activeKey == null &&
    (controller.awaitingSystemKeyboard || (systemKeyboardVisible && systemKeyboardAnimating))

  val claimedBottomPx = {
    if (activeKey != null) {
      (heightPx * (1f - backProgress.value)).roundToInt().coerceAtLeast(0)
    } else if (systemKeyboardTakingOverSpace) {
      heightPx
    } else {
      0
    }
  }

  var ancestorConsumedBottomPx by remember { mutableIntStateOf(0) }
  val safeDrawingInsets = WindowInsets.safeDrawing

  val windowInsets = if (adjustContentForInput) {
    safeDrawingInsets
  } else {
    WindowInsets.systemBars.add(WindowInsets.displayCutout)
  }

  Box(modifier = modifier.fillMaxSize()) {
    BottomSheetScaffold(
      scaffoldState = scaffoldState,
      sheetPeekHeight = 0.dp,
      sheetShape = RectangleShape,
      sheetDragHandle = null,
      sheetSwipeEnabled = false,
      sheetContainerColor = Color.Transparent,
      sheetTonalElevation = 0.dp,
      sheetShadowElevation = 0.dp,
      containerColor = Color.Transparent,
      sheetContent = {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { translationY = backProgress.value * heightPx }
            .background(registry.containerColorFor(visibleKey).takeOrElse { MaterialTheme.colorScheme.surfaceContainerLow })
            .navigationBarsPadding()
        ) {
          registry.contentFor(visibleKey)?.invoke()
        }
      }
    ) { _ ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .onConsumedWindowInsetsChanged { ancestorConsumedBottomPx = it.getBottom(density) }
          .windowInsetsPadding(windowInsets)
          .layout { measurable, constraints ->
            // Window insets are already out of these constraints; take only the excess claim.
            val windowBottomPx = (safeDrawingInsets.getBottom(this) - ancestorConsumedBottomPx).coerceAtLeast(0)
            val extraPx = if (adjustContentForInput) (claimedBottomPx() - windowBottomPx).coerceAtLeast(0) else 0

            val available = (constraints.maxHeight - extraPx).coerceAtLeast(0)
            val placeable = measurable.measure(constraints.copy(minHeight = available, maxHeight = available))
            layout(constraints.maxWidth, constraints.maxHeight) {
              placeable.place(0, 0)
            }
          }
      ) {
        content()
      }
    }
  }
}

/**
 * Bounds on how tall a keyboard may be. The height it wants comes from
 * [MediaKeyboardController.keyboardHeightPx].
 *
 * Holds a lambda, so callers should [remember] it or the scaffold cannot skip recomposition.
 *
 * @param minimum Floor, used before any keyboard has been measured.
 * @param topMargin Kept clear of the top of the window, capping the height.
 * @param overrideForWindow Derives a height from the window instead, for windows unlike the one a
 *   keyboard was measured in.
 */
@Immutable
data class MediaKeyboardHeight(
  val minimum: Dp = 260.dp,
  val topMargin: Dp = 170.dp,
  val overrideForWindow: ((windowHeightPx: Int) -> Int)? = null
) {
  internal fun resolve(preferredPx: Int, windowHeightPx: Int, minimumPx: Int, topMarginPx: Int): Int {
    if (windowHeightPx <= 0) {
      return maxOf(preferredPx, minimumPx)
    }

    val maximumPx = (windowHeightPx - topMarginPx).coerceAtLeast(minimumPx)

    overrideForWindow?.let { return it(windowHeightPx).coerceAtMost(maximumPx) }

    return preferredPx.coerceIn(minimumPx, maximumPx)
  }
}
