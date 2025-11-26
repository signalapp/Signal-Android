/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Post pre-join app bar that displays call information and status.
 */
@Composable
fun CallScreenTopBar(
  callRecipient: Recipient,
  callStatus: String?,
  modifier: Modifier = Modifier,
  onNavigationClick: () -> Unit = {},
  onCallInfoClick: () -> Unit = {}
) {
  Box(
    modifier = modifier
      .height(240.dp)
      .background(
        brush = Brush.verticalGradient(
          0.0f to Color(0f, 0f, 0f, 0.7f),
          1.0f to Color.Transparent
        )
      )
  ) {
    CallScreenTopAppBar(
      callRecipient = callRecipient,
      callStatus = callStatus,
      onNavigationClick = onNavigationClick,
      onCallInfoClick = onCallInfoClick
    )
  }
}
