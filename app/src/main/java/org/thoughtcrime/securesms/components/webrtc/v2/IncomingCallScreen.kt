/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.DarkPreview
import org.signal.core.ui.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.compose.GlideImage
import org.thoughtcrime.securesms.recipients.Recipient

private val textShadow = Shadow(
  color = Color(0f, 0f, 0f, 0.25f),
  blurRadius = 4f
)

@Composable
fun IncomingCallScreen(
  callRecipient: Recipient,
  callStatus: String?,
  isVideoCall: Boolean,
  callScreenControlsListener: CallScreenControlsListener
) {
  val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
  val callTypePadding = remember(isLandscape) {
    if (isLandscape) {
      PaddingValues(top = 0.dp, bottom = 20.dp)
    } else {
      PaddingValues(top = 22.dp, bottom = 30.dp)
    }
  }

  Scaffold { contentPadding ->

    GlideImage(
      model = callRecipient.contactPhoto,
      modifier = Modifier.fillMaxSize()
        .blur(
          radiusX = 25.dp,
          radiusY = 25.dp,
          edgeTreatment = BlurredEdgeTreatment.Rectangle
        )
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(color = Color.Black.copy(alpha = 0.4f))
    ) {}

    CallScreenTopAppBar(
      callRecipient = null,
      callStatus = null,
      onNavigationClick = callScreenControlsListener::onNavigateUpClicked,
      onCallInfoClick = callScreenControlsListener::onCallInfoClicked,
      modifier = Modifier.padding(contentPadding)
    )

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .padding(contentPadding)
        .fillMaxSize()
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(callTypePadding)
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.ic_signal_logo_small),
          contentDescription = null,
          modifier = Modifier.padding(end = 6.dp)
        )

        Text(
          text = if (isVideoCall) {
            stringResource(R.string.WebRtcCallView__signal_video_call)
          } else {
            stringResource(R.string.WebRtcCallView__signal_call)
          },
          style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow)
        )
      }

      AvatarImage(
        recipient = callRecipient,
        modifier = Modifier.size(80.dp)
      )

      Text(
        text = callRecipient.getDisplayName(LocalContext.current),
        style = if (isLandscape) {
          MaterialTheme.typography.titleLarge.copy(shadow = textShadow)
        } else {
          MaterialTheme.typography.headlineMedium.copy(shadow = textShadow)
        },
        color = Color.White,
        modifier = Modifier.padding(top = 16.dp)
      )

      if (callStatus != null) {
        Text(
          text = callStatus,
          style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
          color = Color.White,
          modifier = Modifier.padding(top = 8.dp)
        )
      }

      Spacer(modifier = Modifier.weight(1f))

      if (isLandscape) {
        LandscapeButtons(isVideoCall, callScreenControlsListener)
      } else {
        PortraitButtons(isVideoCall, callScreenControlsListener)
      }
    }
  }
}

@Composable
private fun PortraitButtons(
  isVideoCall: Boolean,
  callScreenControlsListener: CallScreenControlsListener
) {
  Column(
    verticalArrangement = spacedBy(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .padding(horizontal = 64.dp)
      .padding(bottom = 24.dp)
  ) {
    if (isVideoCall) {
      AnswerWithoutVideoButtonAndLabel(
        onClick = callScreenControlsListener::onAcceptCallWithVoiceOnlyPressed
      )
    }

    Row {
      DeclineButtonAndLabel(
        onClick = callScreenControlsListener::onDenyCallPressed
      )

      Spacer(modifier = Modifier.weight(1f))

      AnswerCallButtonAndLabel(
        isVideoCall = isVideoCall,
        onClick = callScreenControlsListener::onAcceptCallPressed
      )
    }
  }
}

@Composable
private fun LandscapeButtons(
  isVideoCall: Boolean,
  callScreenControlsListener: CallScreenControlsListener
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(20.dp),
    horizontalArrangement = spacedBy(45.dp)
  ) {
    DeclineButtonAndLabel(
      onClick = callScreenControlsListener::onDenyCallPressed
    )

    if (isVideoCall) {
      AnswerWithoutVideoButtonAndLabel(
        onClick = callScreenControlsListener::onAcceptCallWithVoiceOnlyPressed
      )
    }

    AnswerCallButtonAndLabel(
      isVideoCall = isVideoCall,
      onClick = callScreenControlsListener::onAcceptCallPressed
    )
  }
}

@Composable
private fun DeclineButtonAndLabel(
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = spacedBy(6.dp)
  ) {
    HangupButton(
      onClick = onClick,
      iconSize = 32.dp,
      modifier = Modifier.size(78.dp)
    )
    Text(
      text = stringResource(R.string.WebRtcCallScreen__decline),
      style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow)
    )
  }
}

@Composable
private fun AnswerWithoutVideoButtonAndLabel(
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = spacedBy(6.dp)
  ) {
    Box(
      modifier = Modifier.size(78.dp)
    ) {
      AnswerWithoutVideoButton(
        onClick = onClick,
        modifier = Modifier
          .size(56.dp)
          .align(Alignment.Center)
      )
    }

    Text(
      text = stringResource(R.string.WebRtcCallScreen__answer_without_video),
      style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow)
    )
  }
}

@Composable
private fun AnswerCallButtonAndLabel(
  isVideoCall: Boolean,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = spacedBy(6.dp)
  ) {
    AcceptCallButton(
      isVideoCall = isVideoCall,
      onClick = onClick,
      iconSize = 32.dp,
      modifier = Modifier.size(78.dp)
    )
    Text(
      text = stringResource(R.string.WebRtcCallScreen__answer),
      style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow)
    )
  }
}

@DarkPreview
@Preview(device = "spec:parent=pixel_5,orientation=landscape", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun IncomingVideoCallScreenPreview() {
  Previews.Preview {
    IncomingCallScreen(
      callRecipient = Recipient(
        systemContactName = "Test User"
      ),
      callScreenControlsListener = CallScreenControlsListener.Empty,
      isVideoCall = true,
      callStatus = "Spiderman is calling the group"
    )
  }
}

@DarkPreview
@Preview(device = "spec:parent=pixel_5,orientation=landscape", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun IncomingAudioCallScreenPreview() {
  Previews.Preview {
    IncomingCallScreen(
      callRecipient = Recipient(
        systemContactName = "Test User"
      ),
      callScreenControlsListener = CallScreenControlsListener.Empty,
      isVideoCall = false,
      callStatus = "Spiderman is calling the group"
    )
  }
}
