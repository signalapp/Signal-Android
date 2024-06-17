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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rxjava3.subscribeAsState
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
import io.reactivex.rxjava3.core.BackpressureStrategy
import kotlinx.coroutines.delay
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.WebRtcCallViewModel
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.GroupCallRaiseHandEvent
import java.util.concurrent.TimeUnit

/**
 * This is a UI element to display the status of one or more people with raised hands in a group call.
 * It supports both an expanded and collapsed mode.
 */
object RaiseHandSnackbar {
  const val TAG = "RaiseHandSnackbar"
  private val COLLAPSE_DELAY_MS = TimeUnit.SECONDS.toMillis(4L)

  @Composable
  fun View(webRtcCallViewModel: WebRtcCallViewModel, showCallInfoListener: () -> Unit, modifier: Modifier = Modifier) {
    var expansionState by remember { mutableStateOf(ExpansionState(shouldExpand = false, forced = false)) }

    val raisedHandsState by webRtcCallViewModel.callParticipantsState
      .toFlowable(BackpressureStrategy.LATEST)
      .map { state ->
        val raisedHands = state.raisedHands.sortedBy {
          if (it.sender.isSelf) {
            if (it.sender.isPrimary) {
              0
            } else {
              1
            }
          } else {
            it.timestamp
          }
        }
        val shouldExpand = RaiseHandState.shouldExpand(raisedHands)
        if (!expansionState.forced) {
          expansionState = ExpansionState(shouldExpand, false)
        }
        raisedHands
      }.subscribeAsState(initial = emptyList())

    val state by remember {
      derivedStateOf {
        RaiseHandState(raisedHands = raisedHandsState, expansionState = expansionState)
      }
    }

    LaunchedEffect(expansionState) {
      delay(COLLAPSE_DELAY_MS)
      expansionState = ExpansionState(shouldExpand = false, forced = false)
    }

    RaiseHand(state, modifier, { expansionState = ExpansionState(shouldExpand = true, forced = true) }, showCallInfoListener = showCallInfoListener)
  }
}

@Preview
@Composable
private fun RaiseHandSnackbarPreview() {
  RaiseHand(
    state = RaiseHandState(listOf(GroupCallRaiseHandEvent(CallParticipant.EMPTY, System.currentTimeMillis())))
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
    exit = shrinkOut(shrinkTowards = Alignment.CenterEnd) + fadeOut()
  ) {
    SignalTheme(
      isDarkMode = true
    ) {
      Surface(
        modifier = modifier
          .padding(horizontal = 16.dp)
          .clip(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 16.dp))
          .background(SignalTheme.colors.colorSurface1)
          .animateContentSize()
      ) {
        val boxModifier = modifier
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
  val expansionState: ExpansionState = ExpansionState(shouldExpand = false, forced = false)
) {
  val isExpanded = expansionState.shouldExpand && raisedHands.isNotEmpty()

  val isEmpty = raisedHands.isEmpty()

  companion object {
    @JvmStatic
    fun shouldExpand(raisedHands: List<GroupCallRaiseHandEvent>): Boolean {
      val now = System.currentTimeMillis()
      return raisedHands.any { it.getCollapseTimestamp() > now }
    }
  }
}

private data class ExpansionState(
  val shouldExpand: Boolean,
  val forced: Boolean
)
