/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.CallParticipantId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

private val PIP_METRICS_SELF_PORTRAIT_WIDTH = 48.dp
private val PIP_METRICS_SELF_PORTRAIT_HEIGHT = 86.dp
private val PIP_METRICS_SELF_LANDSCAPE_WIDTH = 86.dp
private val PIP_METRICS_SELF_LANDSCAPE_HEIGHT = 48.dp

/**
 * Displayed when the user minimizes the call screen while a call is ongoing.
 */
@Composable
fun PictureInPictureCallScreen(
  localParticipant: CallParticipant,
  pendingParticipantsCount: Int,
  callParticipantsPagerState: CallParticipantsPagerState,
  savedLocalParticipantLandscape: Boolean = false
) {
  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    val remoteParticipant = callParticipantsPagerState.focusedParticipant ?: callParticipantsPagerState.callParticipants.first()
    val fullScreenParticipant = if (remoteParticipant == CallParticipant.EMPTY) {
      localParticipant
    } else {
      remoteParticipant
    }

    val isFullScreenLocalParticipant = localParticipant.callParticipantId == fullScreenParticipant.callParticipantId

    RemoteParticipantContent(
      participant = fullScreenParticipant,
      renderInPip = true,
      raiseHandAllowed = false,
      onInfoMoreInfoClick = null,
      mirrorVideo = isFullScreenLocalParticipant,
      modifier = Modifier.fillMaxSize()
    )

    if (!isFullScreenLocalParticipant) {
      val localAspectRatio = rememberParticipantAspectRatio(localParticipant.videoSink)
      val isLocalLandscape = localAspectRatio?.let { it > 1f } ?: savedLocalParticipantLandscape
      val (selfPipWidth, selfPipHeight) = if (isLocalLandscape) {
        PIP_METRICS_SELF_LANDSCAPE_WIDTH to PIP_METRICS_SELF_LANDSCAPE_HEIGHT
      } else {
        PIP_METRICS_SELF_PORTRAIT_WIDTH to PIP_METRICS_SELF_PORTRAIT_HEIGHT
      }

      PictureInPictureSelfPip(
        localParticipant = localParticipant,
        modifier = Modifier
          .padding(10.dp)
          .size(
            width = selfPipWidth,
            height = selfPipHeight
          )
          .align(Alignment.BottomEnd)
      )

      val handRaiseCount = (callParticipantsPagerState.callParticipants + localParticipant).count { it.isHandRaised }
      AnimatedInfoPillsRow(
        handRaiseCount = handRaiseCount,
        pendingParticipantsCount = pendingParticipantsCount,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
      )
    }
  }
}

private enum class InfoPillType {
  HAND_RAISE,
  PENDING_PARTICIPANTS
}

@Composable
private fun AnimatedInfoPillsRow(
  handRaiseCount: Int,
  pendingParticipantsCount: Int,
  modifier: Modifier = Modifier
) {
  val visiblePills = remember(handRaiseCount, pendingParticipantsCount) {
    buildList {
      if (handRaiseCount > 0) add(InfoPillType.HAND_RAISE to handRaiseCount)
      if (pendingParticipantsCount > 0) add(InfoPillType.PENDING_PARTICIPANTS to pendingParticipantsCount)
    }
  }

  LazyRow(
    horizontalArrangement = spacedBy(4.dp),
    modifier = modifier
  ) {
    items(
      count = visiblePills.size,
      key = { visiblePills[it].first }
    ) { index ->
      val (type, count) = visiblePills[index]
      InfoPill(
        icon = ImageVector.vectorResource(
          when (type) {
            InfoPillType.HAND_RAISE -> R.drawable.symbol_raise_hand_24
            InfoPillType.PENDING_PARTICIPANTS -> R.drawable.symbol_person_24
          }
        ),
        count = count,
        modifier = Modifier.animateItem()
      )
    }
  }
}

@Composable
private fun InfoPill(
  icon: ImageVector,
  count: Int,
  modifier: Modifier = Modifier
) {
  Row(
    horizontalArrangement = spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .background(color = colorResource(R.color.signal_dark_colorSurface3), shape = RoundedCornerShape(percent = 50))
      .padding(horizontal = 12.dp, vertical = 6.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = Color.White,
      modifier = Modifier.size(16.dp)
    )

    Text(
      text = "$count",
      color = Color.White
    )
  }
}

@Composable
private fun PictureInPictureSelfPip(
  localParticipant: CallParticipant,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(shape = RoundedCornerShape(8.dp))
      .background(color = Color.Black.copy(alpha = 0.7f))
  ) {
    if (localParticipant.isVideoEnabled) {
      SelfPipContent(
        participant = localParticipant,
        selfPipMode = SelfPipMode.OVERLAY_SELF_PIP,
        isMoreThanOneCameraAvailable = false,
        onSwitchCameraClick = null,
        showAudioIndicator = false,
        modifier = Modifier.fillMaxSize()
      )
    } else {
      BlurredBackgroundAvatar(
        recipient = localParticipant.recipient,
        modifier = Modifier.fillMaxSize()
      )

      Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.symbol_video_slash_fill_24),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
          .padding(10.dp)
          .size(28.dp)
          .background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), shape = CircleShape)
          .padding(6.dp)
          .align(Alignment.TopCenter)
      )
    }

    AudioIndicator(
      participant = localParticipant,
      modifier = Modifier
        .padding(10.dp)
        .size(28.dp)
        .background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), shape = CircleShape)
        .padding(6.dp)
        .align(Alignment.BottomCenter)
    )
  }
}

@NightPreview
@Composable
private fun PictureInPictureCallScreenPreview() {
  Previews.Preview {
    val participants = remember {
      (2..4).map {
        CallParticipant(
          callParticipantId = CallParticipantId(0, RecipientId.from(it.toLong())),
          recipient = Recipient(
            isResolving = false,
            systemContactName = "Contact $it"
          ),
          handRaisedTimestamp = 1L
        )
      }
    }

    val localParticipant = rememberLocalParticipantForPreview()

    val state = remember {
      CallParticipantsPagerState(
        callParticipants = participants,
        focusedParticipant = participants.first(),
        isRenderInPip = true,
        hideAvatar = false
      )
    }

    PictureInPictureCallScreen(
      localParticipant = localParticipant,
      pendingParticipantsCount = 2,
      callParticipantsPagerState = state
    )
  }
}

@NightPreview
@Composable
private fun PictureInPictureCallScreenLocalOnlyPreview() {
  Previews.Preview {
    val localParticipant = rememberLocalParticipantForPreview()

    val state = remember {
      CallParticipantsPagerState(
        callParticipants = listOf(CallParticipant.EMPTY),
        focusedParticipant = CallParticipant.EMPTY,
        isRenderInPip = true,
        hideAvatar = false
      )
    }

    PictureInPictureCallScreen(
      localParticipant = localParticipant,
      pendingParticipantsCount = 0,
      callParticipantsPagerState = state
    )
  }
}

@NightPreview
@Composable
private fun PictureInPictureCallScreenBlockedPreview() {
  Previews.Preview {
    val participants = remember {
      listOf(
        CallParticipant(
          callParticipantId = CallParticipantId(0, RecipientId.from(2L)),
          recipient = Recipient(
            isResolving = false,
            systemContactName = "Blocked Contact",
            isBlocked = true
          )
        )
      )
    }

    val localParticipant = rememberLocalParticipantForPreview()

    val state = remember {
      CallParticipantsPagerState(
        callParticipants = participants,
        focusedParticipant = participants.first(),
        isRenderInPip = true,
        hideAvatar = false
      )
    }

    PictureInPictureCallScreen(
      localParticipant = localParticipant,
      pendingParticipantsCount = 0,
      callParticipantsPagerState = state
    )
  }
}

@NightPreview
@Composable
private fun PictureInPictureCallScreenVideoErrorPreview() {
  Previews.Preview {
    val participants = remember {
      listOf(
        CallParticipant(
          callParticipantId = CallParticipantId(0, RecipientId.from(2L)),
          recipient = Recipient(
            isResolving = false,
            systemContactName = "Error Contact"
          ),
          isMediaKeysReceived = false,
          addedToCallTime = System.currentTimeMillis() - 10000
        )
      )
    }

    val localParticipant = rememberLocalParticipantForPreview()

    val state = remember {
      CallParticipantsPagerState(
        callParticipants = participants,
        focusedParticipant = participants.first(),
        isRenderInPip = true,
        hideAvatar = false
      )
    }

    PictureInPictureCallScreen(
      localParticipant = localParticipant,
      pendingParticipantsCount = 0,
      callParticipantsPagerState = state
    )
  }
}

@NightPreview
@Composable
private fun InfoPillPreview() {
  Previews.Preview {
    InfoPill(
      icon = ImageVector.vectorResource(id = R.drawable.symbol_person_24),
      count = 5
    )
  }
}

@NightPreview
@Composable
private fun AnimatedInfoPillsRowPreview() {
  Previews.Preview {
    var handRaiseCount by remember { mutableIntStateOf(0) }
    var pendingCount by remember { mutableIntStateOf(0) }

    Column {
      AnimatedInfoPillsRow(
        handRaiseCount = handRaiseCount,
        pendingParticipantsCount = pendingCount,
        modifier = Modifier.fillMaxWidth()
      )

      Row(
        horizontalArrangement = spacedBy(8.dp),
        modifier = Modifier
          .padding(16.dp)
      ) {
        TextButton(
          onClick = { handRaiseCount = if (handRaiseCount > 0) 0 else 3 }
        ) {
          Text(
            text = if (handRaiseCount > 0) "Hide Hands" else "Show Hands",
            color = Color.White
          )
        }
        TextButton(
          onClick = { pendingCount = if (pendingCount > 0) 0 else 2 }
        ) {
          Text(
            text = if (pendingCount > 0) "Hide Pending" else "Show Pending",
            color = Color.White
          )
        }
      }
    }
  }
}

@NightPreview
@Composable
private fun PictureInPictureSelfPipPreview() {
  Previews.Preview {
    val localParticipant = rememberLocalParticipantForPreview()

    PictureInPictureSelfPip(
      localParticipant = localParticipant,
      modifier = Modifier.size(
        width = PIP_METRICS_SELF_PORTRAIT_WIDTH,
        height = PIP_METRICS_SELF_PORTRAIT_HEIGHT
      )
    )
  }
}

private fun rememberLocalParticipantForPreview(): CallParticipant {
  return CallParticipant(
    callParticipantId = CallParticipantId(0, RecipientId.from(1L)),
    recipient = Recipient(
      isResolving = false,
      isSelf = true,
      systemContactName = "Local User"
    )
  )
}
