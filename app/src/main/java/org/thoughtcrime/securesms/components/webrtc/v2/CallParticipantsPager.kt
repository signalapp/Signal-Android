/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.CallParticipantId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import kotlin.math.min

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
            renderInPip = callParticipantsPagerState.isRenderInPip,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallParticipantsLayoutComponent(
  callParticipantsPagerState: CallParticipantsPagerState,
  modifier: Modifier = Modifier
) {
  val layoutStrategy = rememberRemoteParticipantsLayoutStrategy()
  val count = min(callParticipantsPagerState.callParticipants.size, layoutStrategy.maxDeviceCount)

  val state = remember(count) {
    layoutStrategy.buildStateForCount(count)
  }

  BoxWithConstraints(
    contentAlignment = Alignment.Center,
    modifier = modifier.padding(state.outerInsets)
  ) {
    val width = maxWidth
    val height = maxHeight

    val nLines = (count + state.maxItemsInEachLine - 1) / state.maxItemsInEachLine

    when (layoutStrategy.lineType) {
      LayoutStrategyLineType.COLUMN -> {
        ColumnBasedLayout(
          containerWidth = width,
          containerHeight = height,
          numberOfLines = nLines,
          numberOfParticipants = count,
          state = state,
          callParticipantsPagerState = callParticipantsPagerState
        )
      }

      LayoutStrategyLineType.ROW -> {
        RowBasedLayout(
          containerWidth = width,
          containerHeight = height,
          numberOfLines = nLines,
          numberOfParticipants = count,
          state = state,
          callParticipantsPagerState = callParticipantsPagerState
        )
      }
    }
  }
}

@Composable
private fun RowBasedLayout(
  containerWidth: Dp,
  containerHeight: Dp,
  numberOfLines: Int,
  numberOfParticipants: Int,
  state: RemoteParticipantsLayoutState,
  callParticipantsPagerState: CallParticipantsPagerState
) {
  Row(
    horizontalArrangement = spacedBy(state.innerInsets),
    verticalAlignment = Alignment.CenterVertically
  ) {
    val batches = callParticipantsPagerState.callParticipants
      .take(numberOfParticipants)
      .chunked(state.maxItemsInEachLine) {
        it.reversed()
      }

    val lastParticipant = batches.last().last()

    batches.forEach { batch ->
      Column(
        verticalArrangement = spacedBy(state.innerInsets),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        batch.forEach { participant ->

          AutoSizedParticipant(
            lineType = LayoutStrategyLineType.ROW,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            numberOfLines = numberOfLines,
            numberOfParticipants = numberOfParticipants,
            isLastParticipant = lastParticipant == participant,
            isRenderInPip = callParticipantsPagerState.isRenderInPip,
            state = state,
            participant = participant
          )
        }
      }
    }
  }
}

@Composable
private fun ColumnBasedLayout(
  containerWidth: Dp,
  containerHeight: Dp,
  numberOfLines: Int,
  numberOfParticipants: Int,
  state: RemoteParticipantsLayoutState,
  callParticipantsPagerState: CallParticipantsPagerState
) {
  Column(
    verticalArrangement = spacedBy(state.innerInsets),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val batches = callParticipantsPagerState.callParticipants
      .take(numberOfParticipants)
      .chunked(state.maxItemsInEachLine)

    val lastParticipant = batches.last().last()

    batches.forEach { batch ->
      Row(
        horizontalArrangement = spacedBy(state.innerInsets),
        verticalAlignment = Alignment.CenterVertically
      ) {
        batch.forEach { participant ->
          AutoSizedParticipant(
            lineType = LayoutStrategyLineType.COLUMN,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            numberOfLines = numberOfLines,
            numberOfParticipants = numberOfParticipants,
            isLastParticipant = lastParticipant == participant,
            isRenderInPip = callParticipantsPagerState.isRenderInPip,
            state = state,
            participant = participant
          )
        }
      }
    }
  }
}

@Composable
private fun AutoSizedParticipant(
  lineType: LayoutStrategyLineType,
  containerWidth: Dp,
  containerHeight: Dp,
  numberOfLines: Int,
  numberOfParticipants: Int,
  isLastParticipant: Boolean,
  isRenderInPip: Boolean,
  state: RemoteParticipantsLayoutState,
  participant: CallParticipant
) {
  val maxSize = when (lineType) {
    LayoutStrategyLineType.COLUMN -> {
      val itemMaximumHeight = (containerHeight - (state.innerInsets * (numberOfLines - 1))) / numberOfLines.toFloat()
      val itemMaximumWidth = (containerWidth - (state.innerInsets * (state.maxItemsInEachLine - 1))) / state.maxItemsInEachLine.toFloat()

      DpSize(itemMaximumWidth, itemMaximumHeight)
    }

    LayoutStrategyLineType.ROW -> {
      val itemMaximumWidth = (containerWidth - (state.innerInsets * (numberOfLines - 1))) / numberOfLines.toFloat()
      val itemMaximumHeight = (containerHeight - (state.innerInsets * (state.maxItemsInEachLine - 1))) / state.maxItemsInEachLine.toFloat()

      DpSize(itemMaximumWidth, itemMaximumHeight)
    }
  }

  val aspectRatio = state.aspectRatio ?: -1f
  val sizeModifier = when {
    aspectRatio > 0f ->
      Modifier.size(
        largestRectangleWithAspectRatio(
          maxSize.width,
          maxSize.height,
          aspectRatio
        )
      )

    isLastParticipant && numberOfParticipants % 2 == 1 -> Modifier.fillMaxSize()
    else -> Modifier.size(DpSize(maxSize.width, maxSize.height))
  }

  CallParticipantRenderer(
    callParticipant = participant,
    renderInPip = isRenderInPip,
    modifier = sizeModifier
      .clip(RoundedCornerShape(state.cornerRadius))
  )
}

private fun largestRectangleWithAspectRatio(
  containerWidth: Dp,
  containerHeight: Dp,
  aspectRatio: Float
): DpSize {
  val containerAspectRatio = containerWidth / containerHeight

  return if (containerAspectRatio > aspectRatio) {
    DpSize(
      width = containerHeight * aspectRatio,
      height = containerHeight
    )
  } else {
    DpSize(
      width = containerWidth,
      height = containerWidth / aspectRatio
    )
  }
}

@Composable
private fun rememberRemoteParticipantsLayoutStrategy(): RemoteParticipantsLayoutStrategy {
  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

  return remember(windowSizeClass) {
    when {
      windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT -> RemoteParticipantsLayoutStrategy.SmallLandscape()
      windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT -> RemoteParticipantsLayoutStrategy.SmallPortrait()
      windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM -> RemoteParticipantsLayoutStrategy.Medium()
      else -> RemoteParticipantsLayoutStrategy.Large()
    }
  }
}

@AllNightPreviews
@Composable
private fun CallParticipantsLayoutComponentPreview() {
  Previews.Preview {
    val participants = remember {
      (1..5).map {
        CallParticipant(
          callParticipantId = CallParticipantId(0, RecipientId.from(it.toLong())),
          recipient = Recipient(
            isResolving = false,
            chatColorsValue = ChatColorsPalette.UNKNOWN_CONTACT
          )
        )
      }
    }

    val state = remember {
      CallParticipantsPagerState(
        callParticipants = participants,
        focusedParticipant = participants.first(),
        isRenderInPip = false,
        hideAvatar = false
      )
    }

    Surface(
      modifier = Modifier.fillMaxSize()
    ) {
      CallParticipantsLayoutComponent(
        callParticipantsPagerState = state,
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

@Immutable
data class CallParticipantsPagerState(
  val callParticipants: List<CallParticipant> = emptyList(),
  val focusedParticipant: CallParticipant? = null,
  val isRenderInPip: Boolean = false,
  val hideAvatar: Boolean = false
)

private sealed class RemoteParticipantsLayoutStrategy(
  val maxDeviceCount: Int,
  val lineType: LayoutStrategyLineType = LayoutStrategyLineType.COLUMN
) {

  abstract fun buildStateForCount(count: Int): RemoteParticipantsLayoutState

  class SmallLandscape : RemoteParticipantsLayoutStrategy(6, LayoutStrategyLineType.ROW) {
    override fun buildStateForCount(count: Int): RemoteParticipantsLayoutState {
      return RemoteParticipantsLayoutState(
        outerInsets = if (count < 2) 0.dp else 16.dp,
        innerInsets = if (count < 2) 0.dp else 12.dp,
        cornerRadius = if (count < 2) 0.dp else CallScreenMetrics.FocusedRendererCornerSize,
        maxItemsInEachLine = if (count < 3) 1 else 2
      )
    }
  }

  class SmallPortrait : RemoteParticipantsLayoutStrategy(6) {

    override fun buildStateForCount(count: Int): RemoteParticipantsLayoutState {
      return RemoteParticipantsLayoutState(
        outerInsets = if (count < 2) 0.dp else 16.dp,
        innerInsets = if (count < 2) 0.dp else 12.dp,
        cornerRadius = if (count < 2) 0.dp else CallScreenMetrics.FocusedRendererCornerSize,
        maxItemsInEachLine = if (count < 3) 1 else 2
      )
    }
  }

  class Medium : RemoteParticipantsLayoutStrategy(9) {
    override fun buildStateForCount(count: Int): RemoteParticipantsLayoutState {
      return RemoteParticipantsLayoutState(
        outerInsets = 24.dp,
        innerInsets = 12.dp,
        cornerRadius = CallScreenMetrics.FocusedRendererCornerSize,
        aspectRatio = if (count < 2) 9 / 16f else 5 / 4f,
        maxItemsInEachLine = when {
          count < 3 -> 1
          count < 7 -> 2
          else -> 3
        }
      )
    }
  }

  class Large : RemoteParticipantsLayoutStrategy(12) {
    override fun buildStateForCount(count: Int): RemoteParticipantsLayoutState {
      return RemoteParticipantsLayoutState(
        outerInsets = 24.dp,
        innerInsets = 12.dp,
        cornerRadius = CallScreenMetrics.FocusedRendererCornerSize,
        aspectRatio = if (count < 2) 9 / 16f else 5 / 4f,
        maxItemsInEachLine = when {
          count < 4 -> 3
          count == 4 -> 2
          count < 7 -> 3
          else -> 4
        }
      )
    }
  }
}

@Immutable
private data class RemoteParticipantsLayoutState(
  val outerInsets: Dp,
  val innerInsets: Dp,
  val cornerRadius: Dp,
  val maxItemsInEachLine: Int,
  val aspectRatio: Float? = null
)
