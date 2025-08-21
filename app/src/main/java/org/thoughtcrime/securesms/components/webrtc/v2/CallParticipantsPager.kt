/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.content.res.Configuration
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.CallParticipantsLayout
import org.thoughtcrime.securesms.components.webrtc.CallParticipantsLayoutStrategies
import org.thoughtcrime.securesms.events.CallParticipant

@Composable
fun CallParticipantsPager(
  callParticipantsPagerState: CallParticipantsPagerState,
  pagerState: PagerState,
  modifier: Modifier = Modifier
) {
  if (callParticipantsPagerState.focusedParticipant == null) {
    return
  }

  if (callParticipantsPagerState.callParticipants.size > 1) {
    VerticalPager(
      state = pagerState,
      modifier = modifier
    ) { page ->
      when (page) {
        0 -> {
          CallParticipantsLayoutComponent(
            callParticipantsPagerState = callParticipantsPagerState,
            modifier = Modifier.fillMaxSize()
          )
        }
        1 -> {
          CallParticipantRenderer(
            callParticipant = callParticipantsPagerState.focusedParticipant,
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
  } else {
    CallParticipantsLayoutComponent(
      callParticipantsPagerState = callParticipantsPagerState,
      modifier = modifier
    )
  }
}

@Composable
fun CallParticipantsLayoutComponent(
  callParticipantsPagerState: CallParticipantsPagerState,
  modifier: Modifier = Modifier
) {
  if (callParticipantsPagerState.focusedParticipant == null) {
    return
  }

  val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

  AndroidView(
    factory = {
      LayoutInflater.from(it).inflate(R.layout.webrtc_call_participants_layout, FrameLayout(it), false) as CallParticipantsLayout
    },
    modifier = modifier
  ) {
    it.update(
      callParticipantsPagerState.callParticipants,
      callParticipantsPagerState.focusedParticipant,
      callParticipantsPagerState.isRenderInPip,
      isPortrait,
      callParticipantsPagerState.hideAvatar,
      0,
      CallParticipantsLayoutStrategies.getStrategy(isPortrait, true)
    )
  }
}

@Immutable
data class CallParticipantsPagerState(
  val callParticipants: List<CallParticipant> = emptyList(),
  val focusedParticipant: CallParticipant? = null,
  val isRenderInPip: Boolean = false,
  val hideAvatar: Boolean = false
)
