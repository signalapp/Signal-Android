/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.keyboard

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.navigationBarsCompat
import org.signal.core.ui.compose.safeDrawingCompat
import org.signal.core.ui.compose.systemBarsCompat
import org.signal.core.ui.getWindowSizeClass
import org.signal.core.ui.isHeightCompact
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/** Shapes back gesture progress into travel. Matches the platform's IME hide curve. */
private val BACK_TRACKING_EASING = FastOutLinearInEasing

/** Settles the keyboard once a back gesture lets go. */
private val BACK_SETTLE_MOTION = spring<Float>(
  dampingRatio = Spring.DampingRatioNoBouncy,
  stiffness = Spring.StiffnessMediumLow
)

/** How long to hold space for a system keyboard that was asked for but never appeared. */
private val SYSTEM_KEYBOARD_ARRIVAL_TIMEOUT = 1.seconds

private val SHEET_POSITIONAL_THRESHOLD = 56.dp
private val SHEET_VELOCITY_THRESHOLD = 125.dp

/** How far an expandable sheet dims what is behind it once at full height. */
private const val EXPANDED_SCRIM_ALPHA = 0.32f

/** How much taller than a keyboard an expandable sheet grows, short of what it is allowed to cover. */
private const val EXPANDED_HEIGHT_MULTIPLIER = 2f

/**
 * Displays [content] alongside keyboards of our own that stand in for the system keyboard.
 *
 * Only one is ever up, and never alongside the system keyboard. The scaffold owns every window inset,
 * handing [content] an already-inset space to lay out in.
 *
 * A keyboard declared expandable can be dragged past keyboard height to fill the window; the
 * scaffold gives it a drag handle and dims [content] behind it as it grows. [content] keeps the size
 * it had at keyboard height throughout, so nothing reflows while the sheet moves.
 *
 * @param controller Requests which keyboard is up. Stable, so view code may hold one.
 * @param onAction Receives everything the host may need to act on.
 * @param keyboardsProvider Declares the available keyboards.
 * @param keyboardHeight Bounds on how tall a keyboard may be.
 * @param adjustContentForInput False to let a keyboard cover [content] rather than resize it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KeyboardSheetScaffold(
  controller: KeyboardSheetController,
  onAction: (KeyboardSheetAction) -> Unit,
  keyboardsProvider: KeyboardSheetScope.() -> Unit,
  modifier: Modifier = Modifier,
  keyboardHeight: KeyboardSheetHeight = KeyboardSheetHeight(),
  adjustContentForInput: Boolean = true,
  content: @Composable () -> Unit
) {
  val registry = remember(keyboardsProvider) { KeyboardSheetRegistry().apply(keyboardsProvider) }

  val currentOnAction by rememberUpdatedState(onAction)

  val density = LocalDensity.current
  val imeInsets = WindowInsets.ime
  val imeAnimationTarget = WindowInsets.imeAnimationTarget
  val windowHeightPx = LocalWindowInfo.current.containerSize.height

  val systemKeyboard = LocalSoftwareKeyboardController.current

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
      currentOnAction(KeyboardSheetAction.SystemKeyboardVisibilityChanged(systemKeyboardVisible))
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
        currentOnAction(KeyboardSheetAction.SystemKeyboardHeightMeasured(it))
      }
  }

  // Keeps ours and the system keyboard from disagreeing in both directions, since the system keyboard
  // can come and go without the scaffold being told. Read off the inset rather than
  // systemKeyboardVisible, which is computed in composition and would leave a snapshotFlow emitting
  // one captured value for good.
  LaunchedEffect(imeAnimationTarget, density) {
    snapshotFlow { imeAnimationTarget.getBottom(density) > 0 }
      .distinctUntilChanged()
      .dropWhile { visible -> !visible }
      .collect { visible ->
        if (visible) {
          if (controller.current != null && !controller.isEnteringText.value && !controller.isBehindHostWindow) {
            controller.hideForSystemKeyboard()
          }
        } else if (controller.isEnteringText.value) {
          controller.endTextEntry()
        }
      }
  }

  // Text entry and the system keyboard it needs go away together, from here, because this is also
  // where the sheet comes back down. Leaving it to the content's field would let the IME service take
  // its own time noticing nothing is focused, trailing the keyboard out a beat behind the height.
  LaunchedEffect(controller, systemKeyboard) {
    controller.isEnteringText
      .dropWhile { entering -> !entering }
      .filter { entering -> !entering }
      .collect {
        // Unless the host is taking the keyboard over for a field of its own, which is the one way
        // out of text entry that wants to keep it. [KeyboardSheetController.hideForSystemKeyboard]
        // sets both in the one call, so this reads the same state the emission was made from.
        if (!controller.awaitingSystemKeyboard) {
          systemKeyboard?.hide()
        }
      }
  }

  // dropWhile rather than drop, so a composition that starts mid-animation still reports that settle.
  LaunchedEffect(imeInsets, imeAnimationTarget, density) {
    snapshotFlow { imeInsets.getBottom(density) == imeAnimationTarget.getBottom(density) }
      .distinctUntilChanged()
      .dropWhile { settled -> settled }
      .filter { settled -> settled }
      .collect {
        controller.awaitingSystemKeyboard = false
        currentOnAction(KeyboardSheetAction.SystemKeyboardAnimationEnded)
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

  // Read through rememberUpdatedState rather than captured: these lambdas outlive the composition
  // that built the sheet, and keying the remember on density instead would rebuild the sheet at its
  // initial value, putting a keyboard away because the font scale changed.
  val currentDensity by rememberUpdatedState(density)
  val sheetState = remember {
    SheetState(
      skipPartiallyExpanded = false,
      positionalThreshold = { with(currentDensity) { SHEET_POSITIONAL_THRESHOLD.toPx() } },
      velocityThreshold = { with(currentDensity) { SHEET_VELOCITY_THRESHOLD.toPx() } },
      initialValue = SheetValue.Hidden,
      skipHiddenState = false
    )
  }
  val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

  // This just makes sure the previously visible state doesn't go away too early while we're mid swap.
  var visibleKey by remember { mutableStateOf<KeyboardSheetKey?>(null) }

  // True from the moment a back gesture commits until the keyboard is actually put away, which the
  // settle animation makes a window wide enough to gesture in again.
  var dismissingByBack by remember { mutableStateOf(false) }

  LaunchedEffect(activeKey) {
    dismissingByBack = false
  }

  // Set while one of ours is coming up in place of the system keyboard. The sheet's rise is not that
  // keyboard's fall, so the space is held flat for the crossing rather than tracking either curve.
  var handingOverFromSystemKeyboard by remember { mutableStateOf(false) }

  val expandable = registry.isExpandable(visibleKey)

  // BottomSheetScaffold measures its anchors from the sheet, so the sheet is always as tall as it
  // could ever need to be and the part below the fold simply hangs off the bottom of the window.
  var scaffoldHeightPx by remember { mutableIntStateOf(0) }
  val layoutHeightPx = if (scaffoldHeightPx > 0) scaffoldHeightPx else windowHeightPx
  val minimumContentPx = with(density) { keyboardHeight.minimumContentVisible.roundToPx() }
  val windowLimitPx = (layoutHeightPx - WindowInsets.statusBars.getTop(density) - minimumContentPx).coerceAtLeast(heightPx)
  val expandedHeightPx = (heightPx * EXPANDED_HEIGHT_MULTIPLIER).roundToInt().coerceIn(heightPx, windowLimitPx)
  val sheetHeightPx = if (expandable) expandedHeightPx else heightPx
  val sheetHeight = with(density) { sheetHeightPx.toDp() }

  /**
   * How much of the sheet the window actually shows. Reads the live sheet offset, so anything that
   * calls it from layout or draw follows a drag frame by frame.
   */
  val visibleSheetHeightPx = {
    val offset = try {
      // Throws until the sheet has been measured for the first time.
      sheetState.requireOffset()
    } catch (ignored: IllegalStateException) {
      null
    }

    if (offset == null) {
      if (visibleKey != null) heightPx else 0
    } else {
      (layoutHeightPx - offset).roundToInt().coerceIn(0, sheetHeightPx)
    }
  }

  /** 0 at keyboard height, 1 at full height. */
  val expansionFraction = {
    val travelPx = sheetHeightPx - heightPx
    if (!expandable || travelPx <= 0) 0f else ((visibleSheetHeightPx() - heightPx).toFloat() / travelPx).coerceIn(0f, 1f)
  }

  /**
   * Where the sheet belongs, from what has been asked for rather than from where it happens to be.
   * Every request folds into this one value: a keyboard shown, swapped or put away, and an expansion
   * asked for or given up.
   */
  val sheetTarget = when {
    activeKey == null -> SheetValue.Hidden
    expandable && controller.expansionTarget -> SheetValue.Expanded
    else -> SheetValue.PartiallyExpanded
  }

  // The one place the sheet is driven from. Keyed on the target, so every change comes through here
  // in order and a new one cancels its own predecessor rather than queueing behind it.
  LaunchedEffect(sheetTarget, activeKey) {
    val key = activeKey

    if (key == null) {
      if (visibleKey == null) {
        return@LaunchedEffect
      }

      // A gesture may already have carried it off screen; hold that until the hide completes.
      handingOverFromSystemKeyboard = false
      sheetState.hide()
      backProgress.snapTo(0f)
      visibleKey = null
      currentOnAction(KeyboardSheetAction.KeyboardHidden)
      return@LaunchedEffect
    }

    val arriving = visibleKey != key
    if (arriving) {
      if (visibleKey != null) {
        currentOnAction(KeyboardSheetAction.KeyboardHidden)
      }

      handingOverFromSystemKeyboard = systemKeyboardVisible || systemKeyboardAnimating
      visibleKey = key
      backProgress.snapTo(0f)
    }

    when {
      sheetTarget == SheetValue.Expanded && sheetState.hasExpandedState -> sheetState.expand()
      sheetState.currentValue == SheetValue.Hidden -> sheetState.show()
      else -> sheetState.partialExpand()
    }

    if (arriving) {
      handingOverFromSystemKeyboard = false
      currentOnAction(KeyboardSheetAction.KeyboardShown(key))
    }
  }

  // Picks up drags as well as requests, so the controller reports where the sheet actually went.
  LaunchedEffect(sheetState) {
    snapshotFlow { sheetState.targetValue == SheetValue.Expanded }
      .distinctUntilChanged()
      .collect { expanded ->
        controller.isExpanded = expanded

        if (expanded) {
          controller.expansionTarget = true
        } else {
          controller.endTextEntry()
        }
      }
  }

  // A swipe can carry the sheet away without anyone having asked it to, leaving the controller
  // believing a keyboard is still up and content still holding space for one.
  LaunchedEffect(sheetState) {
    snapshotFlow { sheetState.currentValue }
      .filter { it == SheetValue.Hidden }
      .collect {
        if (controller.isShowing) {
          controller.hide()
        }
      }
  }

  // Disabled the moment a gesture commits, so a second one during the settle goes to whoever is
  // behind us rather than starting this dismissal over.
  PredictiveBackHandler(enabled = activeKey != null && !dismissingByBack) { progress ->
    try {
      progress.collect { backEvent -> backProgress.snapTo(BACK_TRACKING_EASING.transform(backEvent.progress)) }
    } catch (cancelled: CancellationException) {
      // PredictiveBackHandler cancels this job, so the unwind must run somewhere that outlives it.
      scope.launch { backProgress.animateTo(0f, BACK_SETTLE_MOTION) }
      throw cancelled
    }

    dismissingByBack = true

    // Outside the gesture's job, which the next gesture cancels before this one has put the keyboard away.
    scope.launch {
      backProgress.animateTo(1f, BACK_SETTLE_MOTION)
      controller.hide()
      currentOnAction(KeyboardSheetAction.DismissedByBack)
    }
  }

  val systemKeyboardTakingOverSpace = activeKey == null &&
    (controller.awaitingSystemKeyboard || (systemKeyboardVisible && systemKeyboardAnimating))

  val claimedBottomPx = {
    if (systemKeyboardTakingOverSpace || handingOverFromSystemKeyboard) {
      heightPx
    } else if (activeKey != null || visibleKey != null) {
      // Tracks the sheet rather than its resting height, so content rides along with a drag.
      (visibleSheetHeightPx() * (1f - backProgress.value)).roundToInt().coerceAtLeast(0)
    } else {
      0
    }
  }

  var ancestorConsumedBottomPx by remember { mutableIntStateOf(0) }
  val safeDrawingInsets = WindowInsets.safeDrawingCompat

  val windowInsets = if (adjustContentForInput) {
    safeDrawingInsets
  } else {
    WindowInsets.systemBarsCompat.add(WindowInsets.displayCutout)
  }

  val containerColor = registry.containerColorFor(visibleKey).takeOrElse { MaterialTheme.colorScheme.surfaceContainerLow }
  val scrimColor = MaterialTheme.colorScheme.scrim
  val sheetShape = if (expandable) {
    MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
  } else {
    RectangleShape
  }
  // Keyed, because expansionFraction closes over this composition's heights and visible key; an
  // unkeyed remember would hold the first composition's, from before anything was up.
  val scrimShowing by remember(expandable, heightPx, sheetHeightPx, layoutHeightPx, visibleKey) {
    derivedStateOf { expansionFraction() > 0f }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .onSizeChanged { scaffoldHeightPx = it.height }
  ) {
    BottomSheetScaffold(
      scaffoldState = scaffoldState,
      sheetPeekHeight = height,
      sheetShape = sheetShape,
      sheetDragHandle = null,
      sheetSwipeEnabled = expandable,
      sheetContainerColor = Color.Transparent,
      sheetTonalElevation = 0.dp,
      sheetShadowElevation = 0.dp,
      containerColor = Color.Transparent,
      sheetContent = {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(sheetHeight)
            // Off the bottom by whatever is actually on screen, which an expanded sheet outgrows.
            .graphicsLayer { translationY = backProgress.value * visibleSheetHeightPx() }
            .background(containerColor)
        ) {
          Column(
            modifier = Modifier
              .layout { measurable, constraints ->
                // Only the part of the sheet the window shows is worth laying content out in, and
                // never less than a keyboard: below that the sheet is on its way out, not resizing.
                val visible = visibleSheetHeightPx().coerceIn(heightPx, constraints.maxHeight)
                val placeable = measurable.measure(constraints.copy(minHeight = visible, maxHeight = visible))
                layout(constraints.maxWidth, constraints.maxHeight) {
                  placeable.place(0, 0)
                }
              }
              .windowInsetsPadding(WindowInsets.navigationBarsCompat)
          ) {
            if (expandable) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth()
              ) {
                BottomSheets.Handle()
              }
            }

            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
            ) {
              CompositionLocalProvider(LocalKeyboardSheetController provides controller) {
                registry.contentFor(visibleKey)?.invoke()
              }
            }
          }
        }
      }
    ) { _ ->
      Box(modifier = Modifier.fillMaxSize()) {
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

        if (expandable) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .drawBehind { drawRect(color = scrimColor, alpha = EXPANDED_SCRIM_ALPHA * expansionFraction()) }
              .then(
                if (scrimShowing) {
                  // Collapses rather than dismisses: the dim means the sheet has grown past keyboard
                  // height, and keyboard height is where it belongs the rest of the time, so undoing
                  // the growth is what the gesture is for. Dismissing would also be indiscriminate,
                  // since this covers the whole of [content] -- a host's own text field included.
                  Modifier.pointerInput(Unit) { detectTapGestures { controller.collapse() } }
                } else {
                  Modifier
                }
              )
          )
        }
      }
    }
  }
}

/**
 * Bounds on how tall a keyboard may be. The height it wants comes from
 * [KeyboardSheetController.keyboardHeightPx].
 *
 * Holds a lambda, so callers should [remember] it or the scaffold cannot skip recomposition.
 *
 * @param minimum Floor, used before any keyboard has been measured.
 * @param topMargin Kept clear of the top of the window, capping the height.
 * @param minimumContentVisible How much of the content an expanded keyboard must leave in view,
 *   capping how far it can grow. Only the host can know what this should be, since it covers
 *   whatever chrome the content puts between its edges and the part worth keeping visible.
 * @param overrideForWindow Derives a height from the window instead, for windows unlike the one a
 *   keyboard was measured in.
 */
@Immutable
data class KeyboardSheetHeight(
  val minimum: Dp = 260.dp,
  val topMargin: Dp = 170.dp,
  val minimumContentVisible: Dp = 0.dp,
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
