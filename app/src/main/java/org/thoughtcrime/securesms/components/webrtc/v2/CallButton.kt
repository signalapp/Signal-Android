/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.Buttons
import org.signal.core.ui.DarkPreview
import org.signal.core.ui.IconButtons
import org.signal.core.ui.Previews
import org.thoughtcrime.securesms.R

private val defaultCallButtonIconSize: Dp = 24.dp

@Composable
private fun ToggleCallButton(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  imageVector: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  checkedImageVector: ImageVector = imageVector
) {
  val buttonSize = dimensionResource(id = R.dimen.webrtc_button_size)
  IconButtons.IconToggleButton(
    checked = checked,
    onCheckedChange = onCheckedChange,
    size = buttonSize,
    modifier = modifier.size(buttonSize),
    colors = IconButtons.run {
      iconToggleButtonColors(
        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        checkedContentColor = colorResource(id = R.color.signal_light_colorOnPrimary),
        containerColor = colorResource(id = R.color.signal_light_colorSecondaryContainer),
        contentColor = colorResource(id = R.color.signal_light_colorOnSecondaryContainer)
      )
    }
  ) {
    Icon(
      imageVector = if (checked) checkedImageVector else imageVector,
      contentDescription = contentDescription,
      modifier = Modifier.size(28.dp)
    )
  }
}

@Composable
private fun CallButton(
  onClick: () -> Unit,
  imageVector: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
  contentColor: Color = colorResource(id = R.color.signal_light_colorOnPrimary),
  iconSize: Dp = defaultCallButtonIconSize
) {
  val buttonSize = dimensionResource(id = R.dimen.webrtc_button_size)
  IconButtons.IconButton(
    onClick = onClick,
    size = buttonSize,
    modifier = modifier.size(buttonSize),
    colors = IconButtons.iconButtonColors(
      containerColor = containerColor,
      contentColor = contentColor
    )
  ) {
    Icon(
      imageVector = imageVector,
      contentDescription = contentDescription,
      modifier = Modifier.size(iconSize),
      tint = contentColor
    )
  }
}

@Composable
fun ToggleVideoButton(
  isVideoEnabled: Boolean,
  onChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  ToggleCallButton(
    checked = isVideoEnabled,
    onCheckedChange = onChange,
    imageVector = ImageVector.vectorResource(id = R.drawable.symbol_video_slash_fill_24),
    checkedImageVector = ImageVector.vectorResource(id = R.drawable.symbol_video_fill_24),
    contentDescription = stringResource(id = R.string.WebRtcCallView__toggle_camera),
    modifier = modifier
  )
}

@Composable
fun ToggleMicButton(
  isMicEnabled: Boolean,
  onChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  ToggleCallButton(
    checked = isMicEnabled,
    onCheckedChange = onChange,
    imageVector = ImageVector.vectorResource(id = R.drawable.symbol_mic_slash_fill_24),
    checkedImageVector = ImageVector.vectorResource(id = R.drawable.symbol_mic_fill_white_24),
    contentDescription = stringResource(id = R.string.WebRtcCallView__toggle_mute),
    modifier = modifier
  )
}

@Composable
fun ToggleRingButton(
  isRingEnabled: Boolean,
  isRingAllowed: Boolean,
  onChange: (Boolean, Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  ToggleCallButton(
    checked = isRingEnabled,
    onCheckedChange = { onChange(it, isRingAllowed) },
    imageVector = ImageVector.vectorResource(id = R.drawable.symbol_bell_slash_fill_24),
    checkedImageVector = ImageVector.vectorResource(id = R.drawable.symbol_bell_ring_fill_white_24),
    contentDescription = stringResource(id = R.string.WebRtcCallView__toggle_group_ringing),
    modifier = modifier
  )
}

@Composable
fun AdditionalActionsButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  CallButton(
    onClick = onClick,
    imageVector = ImageVector.vectorResource(id = R.drawable.symbol_more_white_24),
    contentDescription = stringResource(id = R.string.WebRtcCallView__additional_actions),
    modifier = modifier
  )
}

@Composable
fun HangupButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  iconSize: Dp = defaultCallButtonIconSize
) {
  CallButton(
    onClick = onClick,
    imageVector = ImageVector.vectorResource(id = R.drawable.symbol_phone_down_fill_24),
    contentDescription = stringResource(id = R.string.WebRtcCallView__end_call),
    containerColor = colorResource(id = R.color.webrtc_hangup_background),
    modifier = modifier,
    iconSize = iconSize
  )
}

@Composable
fun AcceptCallButton(
  onClick: () -> Unit,
  isVideoCall: Boolean,
  modifier: Modifier = Modifier,
  iconSize: Dp = defaultCallButtonIconSize
) {
  CallButton(
    onClick = onClick,
    imageVector = if (isVideoCall) {
      ImageVector.vectorResource(id = R.drawable.symbol_video_fill_24)
    } else {
      ImageVector.vectorResource(id = R.drawable.symbol_phone_fill_white_24)
    },
    contentDescription = stringResource(id = R.string.WebRtcCallScreen__answer),
    containerColor = colorResource(id = R.color.webrtc_answer_background),
    iconSize = iconSize,
    modifier = modifier
  )
}

@Composable
fun AnswerWithoutVideoButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  CallButton(
    onClick = onClick,
    imageVector = ImageVector.vectorResource(id = R.drawable.symbol_video_slash_fill_24),
    contentDescription = stringResource(id = R.string.WebRtcCallScreen__answer_without_video),
    containerColor = Color.White,
    contentColor = Color.Black,
    modifier = modifier
  )
}

@Composable
fun StartCallButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Buttons.LargePrimary(
    onClick = onClick,
    modifier = modifier.height(56.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = colorResource(id = R.color.signal_light_colorPrimary),
      contentColor = colorResource(id = R.color.signal_light_colorOnPrimary)
    ),
    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 18.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge
    )
  }
}

@DarkPreview
@Composable
private fun ToggleMicButtonPreview() {
  Previews.Preview {
    Row {
      ToggleMicButton(
        isMicEnabled = true,
        onChange = {}
      )

      ToggleMicButton(
        isMicEnabled = false,
        onChange = {}
      )
    }
  }
}

@DarkPreview
@Composable
private fun ToggleVideoButtonPreview() {
  Previews.Preview {
    Row {
      ToggleVideoButton(
        isVideoEnabled = true,
        onChange = {}
      )

      ToggleVideoButton(
        isVideoEnabled = false,
        onChange = {}
      )
    }
  }
}

@DarkPreview
@Composable
private fun ToggleRingButtonPreview() {
  Previews.Preview {
    Row {
      ToggleRingButton(
        isRingEnabled = true,
        isRingAllowed = true,
        onChange = { _, _ -> }
      )

      ToggleRingButton(
        isRingEnabled = false,
        isRingAllowed = true,
        onChange = { _, _ -> }
      )
    }
  }
}

@DarkPreview
@Composable
private fun AdditionalActionsButtonPreview() {
  Previews.Preview {
    AdditionalActionsButton(
      onClick = {}
    )
  }
}

@DarkPreview
@Composable
private fun HangupButtonPreview() {
  Previews.Preview {
    HangupButton(
      onClick = {}
    )
  }
}

@DarkPreview
@Composable
private fun VideoAcceptCallButtonPreview() {
  Previews.Preview {
    AcceptCallButton(
      onClick = {},
      isVideoCall = true
    )
  }
}

@DarkPreview
@Composable
private fun AcceptCallButtonPreview() {
  Previews.Preview {
    AcceptCallButton(
      onClick = {},
      isVideoCall = false
    )
  }
}

@DarkPreview
@Composable
private fun AnswerWithoutVideoButtonPreview() {
  Previews.Preview {
    AnswerWithoutVideoButton(
      onClick = {}
    )
  }
}

@DarkPreview
@Composable
private fun StartCallButtonPreview() {
  Previews.Preview {
    StartCallButton(
      stringResource(id = R.string.WebRtcCallView__start_call),
      onClick = {}
    )
  }
}

@DarkPreview
@Composable
private fun JoinCallButtonPreview() {
  Previews.Preview {
    StartCallButton(
      stringResource(id = R.string.WebRtcCallView__join_call),
      onClick = {}
    )
  }
}
