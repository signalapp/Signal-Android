/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.chats

import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.signal.core.ui.navigation.TransitionSpecs
import kotlin.time.Duration.Companion.seconds

/**
 * Wraps [content] with an animation that crossfades a snapshotted chat list bitmap over the conversation fragment while it loads its first frame.
 *
 * @param contentReady emits when the fragment's first frame has been rendered.
 * @param onFirstRender signals that this composable has content ready to display, so the parent activity can proceed with its first draw.
 * @param content will be animated in as the overlay fades out.
 */
@Composable
fun ConversationLoadingMask(
  transitionState: ConversationTransitionState,
  contentReady: StateFlow<Boolean>,
  onFirstRender: () -> Unit,
  content: @Composable (chatModifier: Modifier) -> Unit
) {
  // it can take a long time to load content, so we use a "fake" chat list image to delay displaying the fragment
  // and prevent pop-in. When there's no bitmap (e.g. returning from a sub-route), skip the animation.
  var shouldDisplayFragment by remember {
    val hasBitmap = transitionState.chatBitmap != null
    mutableStateOf(!hasBitmap)
  }
  val transition: Transition<Boolean> = updateTransition(shouldDisplayFragment)
  val bitmap = transitionState.chatBitmap

  val fakeChatListAnimationState = transition.fakeChatListAnimationState()
  val chatAnimationState = transition.chatAnimationState(hasFake = bitmap != null)

  LaunchedEffect(transition.currentState, transition.isRunning) {
    if (transition.currentState && !transition.isRunning) {
      transitionState.clearBitmap()
    }
  }

  LaunchedEffect(shouldDisplayFragment) {
    onFirstRender()
  }

  LaunchedEffect(contentReady) {
    if (!shouldDisplayFragment) {
      withTimeoutOrNull(5.seconds) {
        contentReady.first { it }
      }
      shouldDisplayFragment = true
    }
  }

  val chatModifier = Modifier.graphicsLayer {
    with(chatAnimationState) { applyTo() }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    content(chatModifier)

    if (bitmap != null) {
      Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier
          .graphicsLayer {
            with(fakeChatListAnimationState) { applyTo() }
          }
          .fillMaxSize()
      )
    }
  }
}

@Composable
private fun Transition<Boolean>.fakeChatListAnimationState(): MaskAnimationState {
  val shift = paneShiftOffset
  val alpha = animateFloat(transitionSpec = { TransitionSpecs.paneShiftSpec() }) { if (it) 0f else 1f }
  val offset = animateDp(transitionSpec = { TransitionSpecs.paneShiftSpec() }) { if (it) -shift else 0.dp }
  return remember {
    MaskAnimationState(
      offset = offset,
      alpha = alpha
    )
  }
}

@Composable
private fun Transition<Boolean>.chatAnimationState(hasFake: Boolean): MaskAnimationState {
  val shift = paneShiftOffset
  val alpha = animateFloat(transitionSpec = { TransitionSpecs.paneShiftSpec() }) { if (it) 1f else 0f }
  return if (!hasFake) {
    remember {
      MaskAnimationState(
        offset = mutableStateOf(0.dp),
        alpha = alpha
      )
    }
  } else {
    val offset = animateDp(transitionSpec = { TransitionSpecs.paneShiftSpec() }) { if (it) 0.dp else shift }
    remember {
      MaskAnimationState(
        offset = offset,
        alpha = alpha
      )
    }
  }
}

private val paneShiftOffset: Dp
  @Composable get() = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -TransitionSpecs.PANE_SHIFT_OFFSET else TransitionSpecs.PANE_SHIFT_OFFSET

/**
 * The alpha and horizontal offset a layer is drawn with.
 */
private class MaskAnimationState(
  private val offset: State<Dp>,
  private val alpha: State<Float>
) {
  fun GraphicsLayerScope.applyTo() {
    this.alpha = this@MaskAnimationState.alpha.value
    this.translationX = offset.value.toPx()
    this.clip = true
  }
}
