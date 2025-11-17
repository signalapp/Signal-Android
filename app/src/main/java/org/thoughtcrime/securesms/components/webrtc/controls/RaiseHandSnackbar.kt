/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import org.signal.ringrtc.GroupCall
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.v2.WebRtcCallViewModel
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.GroupCallRaiseHandEvent
import org.thoughtcrime.securesms.events.GroupCallSpeechEvent
import org.thoughtcrime.securesms.recipients.Recipient
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * This is a UI element to display the status of one or more people with raised hands in a group call.
 * It supports both an expanded and collapsed mode.
 */
object RaiseHandSnackbar {
  const val TAG = "RaiseHandSnackbar"
  private val COLLAPSE_DELAY_MS = TimeUnit.SECONDS.toMillis(4L)

  @Composable
  fun View(webRtcCallViewModel: WebRtcCallViewModel, showCallInfoListener: () -> Unit, modifier: Modifier = Modifier) {
    val raisedHandsState: List<GroupCallRaiseHandEvent> by remember {
      webRtcCallViewModel.callParticipantsState
        .map { state ->
          val raisedHands = state.raisedHands.sortedBy {
            if (it.sender.isSelf) {
              if (it.sender.isPrimary) {
                0
              } else {
                1
              }
            } else {
              it.timestamp.inWholeMilliseconds
            }
          }

          raisedHands
        }
    }.collectAsState(initial = emptyList())

    val speechEvent: GroupCallSpeechEvent? by webRtcCallViewModel.groupCallSpeechEvents.collectAsStateWithLifecycle()

    View(raisedHandsState, speechEvent, showCallInfoListener, modifier)
  }

  @Composable
  fun View(raisedHandsState: List<GroupCallRaiseHandEvent>, speechEvent: GroupCallSpeechEvent?, showCallInfoListener: () -> Unit, modifier: Modifier = Modifier) {
    var expansionState by remember { mutableStateOf(ExpansionState(shouldExpand = false, forced = false, collapseTimestamp = Duration.ZERO)) }

    val state = remember(raisedHandsState, speechEvent, expansionState) {
      RaiseHandState(raisedHands = raisedHandsState, expansionState = expansionState, speechEvent = speechEvent)
    }

    LaunchedEffect(raisedHandsState, speechEvent) {
      val maxCollapseTimestamp = RaiseHandState.getMaxCollapseTimestamp(raisedHandsState, speechEvent)
      if (!expansionState.forced) {
        val shouldExpand = System.currentTimeMillis().milliseconds < maxCollapseTimestamp
        expansionState = ExpansionState(shouldExpand, false, maxCollapseTimestamp)
      }
    }

    LaunchedEffect(expansionState) {
      delay(COLLAPSE_DELAY_MS)
      expansionState = ExpansionState(shouldExpand = false, forced = false, collapseTimestamp = expansionState.collapseTimestamp)
    }

    RaiseHand(state, modifier, { expansionState = ExpansionState(shouldExpand = true, forced = true, collapseTimestamp = expansionState.collapseTimestamp) }, showCallInfoListener = showCallInfoListener)
  }
}

@Preview
@Composable
private fun RaiseHandSnackbarPreview() {
  RaiseHand(
    state = RaiseHandState(listOf(GroupCallRaiseHandEvent(CallParticipant(recipient = Recipient(isResolving = false, systemContactName = "Miles Morales")), System.currentTimeMillis())))
  )
}

@Composable
private fun RaiseHand(
  state: RaiseHandState,
  modifier: Modifier = Modifier,
  setExpanded: (Boolean) -> Unit = {},
  showCallInfoListener: () -> Unit = {}
) {
  AnimatedVisibility(
    visible = state.raisedHands.isNotEmpty(),
    enter = fadeIn() + expandIn(expandFrom = Alignment.CenterEnd),
    exit = shrinkOut(shrinkTowards = Alignment.CenterEnd) + fadeOut(),
    modifier = modifier
  ) {
    SignalTheme(
      isDarkMode = true
    ) {
      Surface(
        modifier = Modifier
          .padding(horizontal = 16.dp)
          .clip(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 16.dp))
          .background(SignalTheme.colors.colorSurface1)
          .animateContentSize()
      ) {
        val boxModifier = Modifier
          .padding(horizontal = 16.dp)
          .clickable(
            !state.isExpanded,
            stringResource(id = R.string.CallOverflowPopupWindow__expand_snackbar_accessibility_label),
            Role.Button
          ) { setExpanded(true) }

        Box(
          contentAlignment = Alignment.CenterStart,
          modifier = if (state.isExpanded) {
            boxModifier.fillMaxWidth()
          } else {
            boxModifier.wrapContentWidth()
          }
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = ImageVector.vectorResource(id = R.drawable.symbol_raise_hand_24),
              contentDescription = null,
              modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(vertical = 8.dp)
            )

            Text(
              text = getSnackbarText(state),
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f, fill = state.isExpanded)
                .wrapContentWidth(Alignment.Start)
                .padding(vertical = 16.dp)
            )
            if (state.isExpanded) {
              if (state.raisedHands.any { it.sender.isSelf && it.sender.isPrimary }) {
                TextButton(
                  onClick = {
                    AppDependencies.signalCallManager.raiseHand(false)
                  },
                  modifier = Modifier.wrapContentWidth(Alignment.End)
                ) {
                  Text(text = stringResource(id = R.string.CallOverflowPopupWindow__lower_hand), maxLines = 1)
                }
              } else {
                TextButton(
                  onClick = showCallInfoListener,
                  modifier = Modifier.wrapContentWidth(Alignment.End)
                ) {
                  Text(text = stringResource(id = R.string.CallOverflowPopupWindow__view), maxLines = 1)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun getSnackbarText(state: RaiseHandState): String {
  if (state.isEmpty) {
    return ""
  }

  val shouldDisplayLowerYourHand = remember(state) {
    val now = System.currentTimeMillis().milliseconds
    val hasUnexpiredSelf = state.raisedHands.any { it.sender.isSelf && it.sender.isPrimary && it.getCollapseTimestamp() >= now }
    val expiration = state.speechEvent?.getCollapseTimestamp() ?: Duration.ZERO
    val isUnexpired = expiration >= now

    state.speechEvent?.speechEvent == GroupCall.SpeechEvent.LOWER_HAND_SUGGESTION && isUnexpired && hasUnexpiredSelf
  }

  if (shouldDisplayLowerYourHand && state.isExpanded) {
    return stringResource(id = R.string.CallRaiseHandSnackbar__lower_your_hand)
  }

  val displayedName = getShortDisplayName(raisedHands = state.raisedHands)
  val additionalHandsCount = state.raisedHands.size - 1
  return if (!state.isExpanded) {
    if (state.raisedHands.size == 1) {
      if (state.raisedHands.first().sender.isSelf) {
        stringResource(id = R.string.CallRaiseHandSnackbar__collapsed_second_person_raised_hand_single, displayedName)
      } else {
        stringResource(id = R.string.CallRaiseHandSnackbar__collapsed_third_person_raised_hands_singular, displayedName)
      }
    } else {
      if (state.raisedHands.first().sender.isSelf) {
        pluralStringResource(id = R.plurals.CallRaiseHandSnackbar__collapsed_second_person_raised_hands_multiple, count = additionalHandsCount, displayedName, additionalHandsCount)
      } else {
        pluralStringResource(id = R.plurals.CallRaiseHandSnackbar__collapsed_third_person_raised_hands_multiple, count = additionalHandsCount, displayedName, additionalHandsCount)
      }
    }
  } else {
    if (state.raisedHands.size == 1) {
      if (state.raisedHands.first().sender.isSelf) {
        stringResource(id = R.string.CallRaiseHandSnackbar__expanded_second_person_raised_a_hand_single, displayedName)
      } else {
        stringResource(id = R.string.CallRaiseHandSnackbar__expanded_third_person_raised_a_hand_single, displayedName)
      }
    } else {
      if (state.raisedHands.first().sender.isSelf) {
        pluralStringResource(id = R.plurals.CallRaiseHandSnackbar__expanded_second_person_raised_a_hand_multiple, count = additionalHandsCount, displayedName, additionalHandsCount)
      } else {
        pluralStringResource(id = R.plurals.CallRaiseHandSnackbar__expanded_third_person_raised_a_hand_multiple, count = additionalHandsCount, displayedName, additionalHandsCount)
      }
    }
  }
}

@Composable
private fun getShortDisplayName(raisedHands: List<GroupCallRaiseHandEvent>): String {
  return raisedHands.first().sender.getShortRecipientDisplayName(LocalContext.current)
}

private data class RaiseHandState(
  val raisedHands: List<GroupCallRaiseHandEvent> = emptyList(),
  val expansionState: ExpansionState = ExpansionState(shouldExpand = false, forced = false, collapseTimestamp = Duration.ZERO),
  val speechEvent: GroupCallSpeechEvent? = null
) {
  val isExpanded = expansionState.shouldExpand && raisedHands.isNotEmpty()

  val isEmpty = raisedHands.isEmpty()

  companion object {
    @JvmStatic
    fun getMaxCollapseTimestamp(raisedHands: List<GroupCallRaiseHandEvent>, speechEvent: GroupCallSpeechEvent?): Duration {
      val maxRaisedHandTimestamp = raisedHands.maxByOrNull { it.getCollapseTimestamp() }?.getCollapseTimestamp() ?: Duration.ZERO
      return max(maxRaisedHandTimestamp.inWholeMilliseconds, (speechEvent?.getCollapseTimestamp() ?: Duration.ZERO).inWholeMilliseconds).milliseconds
    }
  }
}

private data class ExpansionState(
  val shouldExpand: Boolean,
  val forced: Boolean,
  val collapseTimestamp: Duration
)
