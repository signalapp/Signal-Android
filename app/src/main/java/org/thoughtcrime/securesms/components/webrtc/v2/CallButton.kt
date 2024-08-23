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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.Buttons
import org.signal.core.ui.DarkPreview
import org.signal.core.ui.IconButtons
import org.signal.core.ui.Previews
import org.thoughtcrime.securesms.R

@Composable
private fun ToggleCallButton(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  painter: Painter,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  checkedPainter: Painter = painter
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
      painter = if (checked) checkedPainter else painter,
      contentDescription = contentDescription,
      modifier = Modifier.size(28.dp)
    )
  }
}

@Composable
private fun CallButton(
  onClick: () -> Unit,
  painter: Painter,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
  contentColor: Color = colorResource(id = R.color.signal_light_colorOnPrimary)
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
      painter = painter,
      contentDescription = contentDescription,
      modifier = Modifier.size(28.dp)
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
    painter = painterResource(id = R.drawable.symbol_video_slash_fill_24),
    checkedPainter = painterResource(id = R.drawable.symbol_video_fill_24),
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
    painter = painterResource(id = R.drawable.symbol_mic_slash_fill_24),
    checkedPainter = painterResource(id = R.drawable.symbol_mic_fill_white_24),
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
    painter = painterResource(id = R.drawable.symbol_bell_slash_fill_24),
    checkedPainter = painterResource(id = R.drawable.symbol_bell_ring_fill_white_24),
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
    painter = painterResource(id = R.drawable.symbol_more_white_24),
    contentDescription = stringResource(id = R.string.WebRtcCallView__additional_actions),
    modifier = modifier
  )
}

@Composable
fun HangupButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  CallButton(
    onClick = onClick,
    painter = painterResource(id = R.drawable.symbol_phone_down_fill_24),
    contentDescription = stringResource(id = R.string.WebRtcCallView__end_call),
    containerColor = colorResource(id = R.color.webrtc_hangup_background),
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
