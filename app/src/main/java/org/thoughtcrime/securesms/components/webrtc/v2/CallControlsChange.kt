/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.DarkPreview
import org.signal.core.ui.Previews
import org.thoughtcrime.securesms.R

/**
 * Enumeration of the different call states we can display in the CallStateUpdate component.
 * Shared between V1 and V2 code.
 */
enum class CallControlsChange(
  @DrawableRes val iconRes: Int?,
  @StringRes val stringRes: Int
) {
  RINGING_ON(R.drawable.symbol_bell_ring_compact_16, R.string.CallStateUpdatePopupWindow__ringing_on),
  RINGING_OFF(R.drawable.symbol_bell_slash_compact_16, R.string.CallStateUpdatePopupWindow__ringing_off),
  RINGING_DISABLED(null, R.string.CallStateUpdatePopupWindow__group_is_too_large),
  MIC_ON(R.drawable.symbol_mic_compact_16, R.string.CallStateUpdatePopupWindow__mic_on),
  MIC_OFF(R.drawable.symbol_mic_slash_compact_16, R.string.CallStateUpdatePopupWindow__mic_off),
  SPEAKER_ON(R.drawable.symbol_speaker_24, R.string.CallStateUpdatePopupWindow__speaker_on),
  SPEAKER_OFF(R.drawable.symbol_speaker_slash_24, R.string.CallStateUpdatePopupWindow__speaker_off)
}

/**
 * Small pop-over above controls that is displayed as different controls are toggled.
 */
@Composable
fun CallStateUpdatePopup(
  callControlsChange: CallControlsChange,
  modifier: Modifier = Modifier
) {
  Row(
    horizontalArrangement = spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .background(
        color = colorResource(id = R.color.signal_light_colorSecondaryContainer),
        shape = RoundedCornerShape(50)
      )
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    if (callControlsChange.iconRes != null) {
      Icon(
        painter = painterResource(id = callControlsChange.iconRes),
        contentDescription = null,
        tint = colorResource(id = R.color.signal_light_colorOnSecondaryContainer),
        modifier = Modifier.size(16.dp)
      )
    }

    Text(
      text = stringResource(id = callControlsChange.stringRes),
      style = MaterialTheme.typography.bodyMedium,
      color = colorResource(id = R.color.signal_light_colorOnSecondaryContainer)
    )
  }
}

@DarkPreview
@Composable
private fun CallStateUpdatePopupPreview() {
  Previews.Preview {
    Column(
      verticalArrangement = spacedBy(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      CallControlsChange.entries.forEach {
        CallStateUpdatePopup(callControlsChange = it)
      }
    }
  }
}
