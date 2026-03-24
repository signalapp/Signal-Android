/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Utilizes a circular reveal animation to display and hide the content given.
 * When the content is hidden via settings [isLoading] to true, we display a
 * circular progress indicator.
 *
 * This component will automatically size itself according to the content passed
 * in via [content]
 */
@Composable
fun CircularProgressWrapper(
  isLoading: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
  ) {
    var size by remember { mutableStateOf(IntSize.Zero) }

    val dpSize = with(LocalDensity.current) {
      DpSize(size.width.toDp(), size.height.toDp())
    }

    AnimatedVisibility(
      visible = isLoading,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(dpSize)
      ) {
        CircularProgressIndicator()
      }
    }

    AnimatedVisibility(
      visible = !isLoading,
      enter = EnterTransition.None,
      exit = ExitTransition.None
    ) {
      val visibility = transition.animateFloat(
        transitionSpec = { tween(durationMillis = 400, easing = LinearOutSlowInEasing) },
        label = "CircularProgressWrapper-Visibility"
      ) { state ->
        if (state == EnterExitState.Visible) 1f else 0f
      }

      Box(
        modifier = Modifier
          .onSizeChanged { s ->
            size = s
          }
          .circularReveal(visibility)
      ) {
        content()
      }
    }
  }
}

@DayNightPreviews
@Composable
fun CircularProgressWrapperPreview() {
  var isLoading by remember {
    mutableStateOf(false)
  }

  LaunchedEffect(isLoading) {
    if (isLoading) {
      delay(3.seconds)
      isLoading = false
    }
  }

  Previews.Preview {
    CircularProgressWrapper(
      isLoading = isLoading,
      content = {
        Buttons.LargeTonal(onClick = {
          isLoading = true
        }) {
          Text(text = "Next")
        }
      }
    )
  }
}
