/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.CallParticipantId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Replacement composable for the CallParticipants overflow recycler view.
 *
 * Displays a scrollable list of users that are in the call but are not displayed in the primary grid.
 */
@Composable
fun CallParticipantsOverflow(
  lineType: LayoutStrategyLineType,
  overflowParticipants: List<CallParticipant>,
  modifier: Modifier = Modifier
) {
  val callScreenMetrics = rememberCallScreenMetrics()
  val rendererSize = callScreenMetrics.overflowParticipantRendererSize

  if (lineType == LayoutStrategyLineType.ROW) {
    LazyRow(
      reverseLayout = true,
      modifier = Modifier.fillMaxWidth().then(modifier),
      contentPadding = PaddingValues(start = 16.dp, end = rendererSize + 32.dp),
      horizontalArrangement = spacedBy(4.dp)
    ) {
      appendItems(rendererSize, overflowParticipants)
    }
  } else {
    LazyColumn(
      reverseLayout = true,
      modifier = Modifier.fillMaxHeight().then(modifier),
      contentPadding = PaddingValues(top = 16.dp, bottom = rendererSize + 32.dp),
      verticalArrangement = spacedBy(4.dp)
    ) {
      appendItems(rendererSize, overflowParticipants)
    }
  }
}

private fun LazyListScope.appendItems(
  contentSize: Dp,
  overflowParticipants: List<CallParticipant>
) {
  items(
    items = overflowParticipants,
    key = { it.callParticipantId }
  ) { participant ->
    OverflowParticipantContent(
      participant = participant,
      modifier = Modifier
        .size(contentSize)
        .clip(CallScreenMetrics.OverflowParticipantRendererShape)
    )
  }
}

@AllNightPreviews
@Composable
private fun CallParticipantsOverflowPreview() {
  Previews.Preview {
    val participants = remember {
      (1..10).map {
        CallParticipant(
          callParticipantId = CallParticipantId(
            demuxId = 0,
            recipientId = RecipientId.from(it.toLong())
          ),
          recipient = Recipient(
            isResolving = false,
            chatColorsValue = ChatColorsPalette.UNKNOWN_CONTACT
          )
        )
      }
    }

    val callScreenMetrics = rememberCallScreenMetrics()
    CallParticipantsOverflow(
      lineType = LayoutStrategyLineType.ROW,
      overflowParticipants = participants,
      modifier = Modifier
        .padding(vertical = 16.dp)
        .height(callScreenMetrics.overflowParticipantRendererSize)
        .fillMaxWidth()
    )
  }
}

@AllNightPreviews
@Composable
private fun CallParticipantsOverflowColumnPreview() {
  Previews.Preview {
    val participants = remember {
      (1..10).map {
        CallParticipant(
          callParticipantId = CallParticipantId(
            demuxId = 0,
            recipientId = RecipientId.from(it.toLong())
          ),
          recipient = Recipient(
            isResolving = false,
            chatColorsValue = ChatColorsPalette.UNKNOWN_CONTACT
          )
        )
      }
    }

    val callScreenMetrics = rememberCallScreenMetrics()
    CallParticipantsOverflow(
      lineType = LayoutStrategyLineType.COLUMN,
      overflowParticipants = participants,
      modifier = Modifier
        .padding(horizontal = 16.dp)
        .width(callScreenMetrics.overflowParticipantRendererSize)
        .fillMaxHeight()
    )
  }
}
