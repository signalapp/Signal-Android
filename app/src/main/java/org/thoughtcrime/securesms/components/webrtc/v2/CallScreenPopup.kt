/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.thoughtcrime.securesms.R
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Common popup container for call screen notifications that slide in from the top.
 * Used for participant updates, wifi-to-cellular notifications, etc.
 *
 * @param visible Whether the popup should be visible
 * @param onDismiss Called when the popup is dismissed (either by timeout or user interaction)
 * @param displayDuration How long to display the popup before auto-dismissing
 * @param onTransitionComplete Called when the exit transition completes, useful for queuing next popups
 * @param modifier Modifier for the outer container
 * @param content The content to display inside the pill-shaped popup
 */
@Composable
fun CallScreenPopup(
  visible: Boolean,
  onDismiss: () -> Unit,
  displayDuration: Duration = 4.seconds,
  onTransitionComplete: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit
) {
  val transitionState = remember { MutableTransitionState(visible) }
  transitionState.targetState = visible

  LaunchedEffect(transitionState.isIdle) {
    if (transitionState.isIdle && !transitionState.targetState) {
      onTransitionComplete?.invoke()
    }
  }

  AnimatedVisibility(
    visibleState = transitionState,
    enter = slideInVertically { fullHeight -> -fullHeight } + fadeIn(),
    exit = slideOutVertically { fullHeight -> -fullHeight } + fadeOut(),
    modifier = modifier
      .heightIn(min = 96.dp)
      .fillMaxWidth()
  ) {
    LaunchedEffect(visible) {
      if (visible) {
        delay(displayDuration)
        onDismiss()
      }
    }

    Box(
      contentAlignment = Alignment.TopCenter,
      modifier = Modifier
        .wrapContentSize()
        .padding(start = 12.dp, top = 30.dp, end = 12.dp)
        .background(
          color = colorResource(R.color.signal_light_colorSecondaryContainer),
          shape = RoundedCornerShape(percent = 50)
        )
        .clickable(
          onClick = onDismiss,
          role = Role.Button
        ),
      content = content
    )
  }
}
