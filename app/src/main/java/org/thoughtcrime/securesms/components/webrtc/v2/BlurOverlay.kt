/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import kotlin.time.Duration.Companion.seconds

/**
 * Blurrable container. Wrap whatever you want blurred in this.
 */
@Composable
fun BlurContainer(
  isBlurred: Boolean,
  modifier: Modifier = Modifier,
  blurRadius: Dp = 20.dp,
  skipOverlay: Boolean = false,
  content: @Composable BoxScope.() -> Unit
) {
  val blurRadius = if (isBlurred) blurRadius else 0.dp
  val blur by animateDpAsState(blurRadius)

  Box(
    modifier = modifier.blur(blur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
  ) {
    content()

    if (!skipOverlay) {
      BlurOverlay(visible = isBlurred, modifier = Modifier.fillMaxSize())
    }
  }
}

/**
 * 'Glass' blur overlay.
 * ```
 */
@Composable
private fun BlurOverlay(visible: Boolean, modifier: Modifier) {
  AnimatedVisibility(
    visible = visible,
    modifier = modifier
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(color = Color(0xB3252525))
        .background(color = Color(0x109C9C9C))
    )
  }
}

@NightPreview
@Composable
fun BlurContainerPreview() {
  Previews.Preview {
    var isBlurred by remember { mutableStateOf(false) }

    LaunchedEffect(isBlurred) {
      if (isBlurred) {
        delay(3.seconds)
        isBlurred = false
      }
    }

    BlurContainer(
      isBlurred = isBlurred,
      modifier = Modifier.fillMaxSize()
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
      ) {
        Image(
          painter = painterResource(R.drawable.ic_add_a_profile_megaphone_image),
          contentDescription = null,
          modifier = Modifier.fillMaxSize()
        )

        Buttons.LargeTonal(
          onClick = {
            isBlurred = true
          }
        ) {
          Text("Blur")
        }
      }
    }
  }
}
