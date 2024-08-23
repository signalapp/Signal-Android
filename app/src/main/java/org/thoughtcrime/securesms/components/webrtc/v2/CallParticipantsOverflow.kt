/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.WebRtcCallParticipantsRecyclerAdapter
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.util.visible

/**
 * Wrapper composable for the CallParticipants overflow recycler view.
 *
 * Displays a scrollable list of users that are in the call but are not displayed in the primary grid.
 */
@Composable
fun CallParticipantsOverflow(
  overflowParticipants: List<CallParticipant>,
  modifier: Modifier = Modifier
) {
  val adapter = remember { WebRtcCallParticipantsRecyclerAdapter() }

  AndroidView(
    factory = {
      val view = LayoutInflater.from(it).inflate(R.layout.webrtc_call_participant_overflow_recycler, FrameLayout(it), false) as RecyclerView
      view.adapter = adapter
      view
    },
    modifier = modifier,
    update = {
      it.visible = true
      adapter.submitList(overflowParticipants)
    }
  )
}
