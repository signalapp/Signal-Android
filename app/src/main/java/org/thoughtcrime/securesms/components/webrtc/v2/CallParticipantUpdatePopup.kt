/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImageSmall
import org.thoughtcrime.securesms.components.webrtc.CallParticipantListUpdate
import org.thoughtcrime.securesms.components.webrtc.CallParticipantsListUpdatePopupWindow
import org.thoughtcrime.securesms.components.webrtc.v2.CallParticipantUpdatePopupController.DisplayState
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.CallParticipantId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Popup which displays at the top of the screen as people enter and leave the call. Only displayed in group calls.
 */
@Composable
fun CallParticipantUpdatePopup(
  controller: CallParticipantUpdatePopupController,
  modifier: Modifier = Modifier
) {
  val transitionState = remember { MutableTransitionState(controller.displayState != DisplayState.NONE) }
  transitionState.targetState = controller.displayState != DisplayState.NONE

  LaunchedEffect(transitionState.isIdle) {
    if (transitionState.isIdle && !transitionState.targetState) {
      controller.updateDisplay()
    }
  }

  AnimatedVisibility(
    visibleState = transitionState,
    enter = slideInVertically { fullHeight -> -fullHeight } + fadeIn(),
    exit = slideOutVertically { fullHeight -> -fullHeight } + fadeOut(),
    modifier = modifier
      .heightIn(min = 96.dp)
      .fillMaxWidth()
  ) {
    LaunchedEffect(controller.displayState, controller.participants) {
      if (controller.displayState != DisplayState.NONE) {
        delay(controller.displayDuration)
        controller.hide()
      }
    }

    PopupContent(
      displayState = controller.displayState,
      participants = controller.participants,
      onClick = {
        controller.hide()
      }
    )
  }
}

/**
 * Body of the call participants pop-up, displaying a description, avatar, and optional badge.
 */
@Composable
private fun PopupContent(
  displayState: DisplayState,
  participants: Set<CallParticipantListUpdate.Wrapper>,
  onClick: () -> Unit
) {
  val context = LocalContext.current

  var previousDisplayState by remember { mutableStateOf(DisplayState.NONE) }
  var previousDescription by remember { mutableStateOf("") }
  var previousAvatarRecipient by remember { mutableStateOf(Recipient.UNKNOWN) }

  val avatarRecipient by remember(displayState) {
    derivedStateOf {
      if (participants.isEmpty()) {
        previousAvatarRecipient
      } else {
        participants.first().callParticipant.recipient
      }
    }
  }

  val description by remember(displayState) {
    derivedStateOf {
      val displayStateForDescription = if (displayState != DisplayState.NONE) {
        displayState
      } else {
        previousDisplayState
      }

      previousDisplayState = displayStateForDescription

      if (participants.isNotEmpty()) {
        previousDescription = CallParticipantsListUpdatePopupWindow.getDescriptionForRecipients(
          context,
          participants,
          displayStateForDescription == DisplayState.ADD
        )
      }

      previousDescription
    }
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .wrapContentSize()
      .padding(start = 12.dp, top = 30.dp, end = 12.dp)
      .background(
        color = colorResource(R.color.signal_light_colorSecondaryContainer),
        shape = RoundedCornerShape(percent = 50)
      )
      .clickable(
        onClick = onClick,
        role = Role.Button
      )
  ) {
    Box(
      modifier = Modifier.size(48.dp)
    ) {
      AvatarImage(
        recipient = avatarRecipient,
        modifier = Modifier
          .padding(vertical = 8.dp)
          .padding(start = 8.dp)
          .size(32.dp)
      )

      BadgeImageSmall(
        badge = avatarRecipient.featuredBadge,
        modifier = Modifier.padding(top = 28.dp, start = 28.dp)
          .size(16.dp)
      )
    }

    Text(
      text = description,
      color = colorResource(R.color.signal_light_colorOnSecondaryContainer),
      modifier = Modifier.padding(vertical = 14.dp).padding(start = 10.dp, end = 24.dp)
    )
  }
}

/**
 * Controller owned by the [CallScreenMediator] which allows its callbacks to control this popup.
 */
@Stable
class CallParticipantUpdatePopupController(
  val displayDuration: Duration = 10.seconds
) {
  private var pendingAdditions = hashSetOf<CallParticipantListUpdate.Wrapper>()
  private var pendingRemovals = hashSetOf<CallParticipantListUpdate.Wrapper>()

  var participants = mutableStateSetOf<CallParticipantListUpdate.Wrapper>()
    private set

  var displayState: DisplayState by mutableStateOf(DisplayState.NONE)
    private set

  fun update(update: CallParticipantListUpdate) {
    pendingAdditions.addAll(update.added)
    pendingAdditions.removeAll(update.removed)
    pendingRemovals.addAll(update.removed)
    pendingRemovals.removeAll(update.added)

    if (displayState == DisplayState.NONE) {
      updateDisplay()
    }
  }

  fun hide() {
    displayState = DisplayState.NONE
  }

  fun updateDisplay() {
    displayState = when {
      pendingAdditions.isNotEmpty() -> {
        DisplayState.ADD
      }

      pendingRemovals.isNotEmpty() -> {
        DisplayState.REMOVE
      }

      else -> DisplayState.NONE
    }

    val toDisplay = pendingAdditions.ifEmpty {
      pendingRemovals
    }

    participants.clear()
    participants.addAll(toDisplay)
    toDisplay.clear()
  }

  enum class DisplayState {
    NONE,
    ADD,
    REMOVE
  }
}

@NightPreview
@Composable
private fun PopupContentPreview() {
  val participants = remember {
    (1..10).map {
      CallParticipant(
        callParticipantId = CallParticipantId(it.toLong(), RecipientId.from(it.toLong())),
        recipient = Recipient(
          id = RecipientId.from(it.toLong()),
          isResolving = false,
          systemContactName = "Participant $it"
        )
      )
    }
  }

  Previews.Preview {
    PopupContent(
      displayState = DisplayState.ADD,
      participants = participants.take(1).map { CallParticipantListUpdate.createWrapper(it) }.toSet(),
      onClick = {}
    )
  }
}

/**
 * Interactive preview
 */
@NightPreview
@Composable
fun CallParticipantUpdatePopupPreview() {
  val controller = remember { CallParticipantUpdatePopupController(displayDuration = 3.seconds) }

  val participants = remember {
    (1..10).map {
      CallParticipant(
        callParticipantId = CallParticipantId(it.toLong(), RecipientId.from(it.toLong())),
        recipient = Recipient(
          id = RecipientId.from(it.toLong()),
          isResolving = false,
          systemContactName = "Participant $it"
        )
      )
    }
  }

  Previews.Preview {
    Scaffold {
      Row(
        modifier = Modifier
          .padding(it)
          .fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Buttons.LargeTonal(
          onClick = {
            val randomParticipants = (1..2).map { participants.random() }.toSet().toList()
            controller.update(CallParticipantListUpdate.computeDeltaUpdate(emptyList(), randomParticipants))
          }
        ) {
          Text("Add User")
        }
        Buttons.LargeTonal(
          onClick = {
            val randomParticipants = (1..2).map { participants.random() }.toSet().toList()
            controller.update(CallParticipantListUpdate.computeDeltaUpdate(randomParticipants, emptyList()))
          }
        ) {
          Text("Remove User")
        }
      }

      CallParticipantUpdatePopup(controller = controller, modifier = Modifier.fillMaxWidth())
    }
  }
}
