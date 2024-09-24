/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import kotlinx.coroutines.flow.drop
import org.signal.core.ui.DarkPreview
import org.signal.core.ui.Previews
import org.thoughtcrime.securesms.R

/**
 * Tooltip box appropriately styled for the call screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreenTooltipBox(
  text: String,
  displayTooltip: Boolean,
  onTooltipDismissed: () -> Unit = {},
  content: @Composable () -> Unit
) {
  val state = rememberTooltipState()

  TooltipBox(
    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
    state = state,
    tooltip = {
      PlainTooltip(
        caretSize = TooltipDefaults.caretSize,
        shape = TooltipDefaults.plainTooltipContainerShape,
        containerColor = colorResource(R.color.signal_light_colorPrimary),
        contentColor = colorResource(R.color.signal_light_colorOnPrimary)
      ) {
        Text(text = text)
      }
    },
    content = content
  )

  LaunchedEffect(displayTooltip) {
    if (displayTooltip) {
      state.show()
    } else {
      state.dismiss()
    }
  }

  LaunchedEffect(state) {
    snapshotFlow { state.isVisible }
      .drop(1)
      .collect { onTooltipDismissed() }
  }
}

@DarkPreview
@Composable
fun SwitchCameraTooltipBoxPreview() {
  Previews.Preview {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxSize()
    ) {
      CallScreenTooltipBox(
        text = "Test Tooltip",
        displayTooltip = true
      ) {
        Text(text = "Test Content")
      }
    }
  }
}
