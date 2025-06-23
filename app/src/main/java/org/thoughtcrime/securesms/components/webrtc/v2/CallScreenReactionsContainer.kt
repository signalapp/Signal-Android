/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import org.thoughtcrime.securesms.components.recyclerview.NoTouchingRecyclerView
import org.thoughtcrime.securesms.components.webrtc.WebRtcReactionsAlphaItemDecoration
import org.thoughtcrime.securesms.components.webrtc.WebRtcReactionsItemAnimator
import org.thoughtcrime.securesms.components.webrtc.WebRtcReactionsRecyclerAdapter
import org.thoughtcrime.securesms.events.GroupCallReactionEvent

/**
 * Displays a list of reactions sent during a group call.
 *
 * Due to how LazyColumn deals with touch events and how Column doesn't have proper
 * per-item animation support, we utilize a recycler view as we do in the old call
 * screen.
 */
@Composable
fun CallScreenReactionsContainer(
  reactions: List<GroupCallReactionEvent>
) {
  val adapter = remember { WebRtcReactionsRecyclerAdapter() }
  AndroidView(factory = {
    val view = NoTouchingRecyclerView(it)
    view.layoutManager = LinearLayoutManager(it, LinearLayoutManager.VERTICAL, true)
    view.adapter = adapter
    view.addItemDecoration(WebRtcReactionsAlphaItemDecoration())
    view.itemAnimator = WebRtcReactionsItemAnimator()
    view.isClickable = false
    view.isVerticalScrollBarEnabled = false

    view
  }, modifier = Modifier.fillMaxSize().padding(16.dp).padding(bottom = 16.dp)) {
    adapter.submitList(reactions.toMutableList())
  }
}
