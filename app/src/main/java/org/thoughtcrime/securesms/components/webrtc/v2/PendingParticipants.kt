/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.components.webrtc.PendingParticipantsView
import org.thoughtcrime.securesms.service.webrtc.PendingParticipantCollection

/**
 * Re-implementation of PendingParticipantsView in compose.
 */
@Composable
fun PendingParticipants(
  pendingParticipantsState: PendingParticipantsState,
  pendingParticipantsListener: PendingParticipantsListener
) {
  if (pendingParticipantsState.isInPipMode) {
    return
  }

  var hasDisplayedContent by remember { mutableStateOf(false) }

  if (hasDisplayedContent || pendingParticipantsState.pendingParticipantCollection.getUnresolvedPendingParticipants().isNotEmpty()) {
    hasDisplayedContent = true

    AndroidView(
      ::PendingParticipantsView
    ) { view ->
      view.listener = pendingParticipantsListener
      view.applyState(pendingParticipantsState.pendingParticipantCollection)
    }
  }
}

@DayNightPreviews
@Composable
fun PendingParticipantsPreview() {
  Previews.Preview {
    PendingParticipants(
      pendingParticipantsState = PendingParticipantsState(
        pendingParticipantCollection = PendingParticipantCollection(),
        isInPipMode = false
      ),
      pendingParticipantsListener = PendingParticipantsListener.Empty
    )
  }
}
