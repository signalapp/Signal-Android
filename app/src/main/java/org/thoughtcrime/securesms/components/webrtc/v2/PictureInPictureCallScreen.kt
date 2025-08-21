/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * Displayed when the user minimizes the call screen while a call is ongoing.
 */
@Composable
fun PictureInPictureCallScreen(
  callParticipantsPagerState: CallParticipantsPagerState,
  callScreenController: CallScreenController
) {
  val scope = rememberCoroutineScope()

  CallParticipantsLayoutComponent(
    callParticipantsPagerState = callParticipantsPagerState,
    modifier = Modifier
      .fillMaxSize()
      .clickable(
        onClick = {
          scope.launch {
            callScreenController.handleEvent(CallScreenController.Event.TOGGLE_CONTROLS)
          }
        },
        enabled = false
      )
  )
}
