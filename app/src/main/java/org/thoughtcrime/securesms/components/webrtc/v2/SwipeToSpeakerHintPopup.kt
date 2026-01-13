/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import kotlin.time.Duration.Companion.seconds

/**
 * Popup shown to hint the user that they can swipe to view screen share.
 */
@Composable
fun SwipeToSpeakerHintPopup(
  visible: Boolean,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  CallScreenPopup(
    visible = visible,
    onDismiss = onDismiss,
    displayDuration = 3.seconds,
    modifier = modifier
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
      Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.symbol_arrow_down_24),
        contentDescription = null,
        tint = colorResource(R.color.signal_light_colorOnSecondaryContainer),
        modifier = Modifier.size(24.dp)
      )

      Text(
        text = stringResource(R.string.CallToastPopupWindow__swipe_to_view_screen_share),
        color = colorResource(R.color.signal_light_colorOnSecondaryContainer),
        modifier = Modifier.padding(start = 8.dp)
      )
    }
  }
}

@NightPreview
@Composable
private fun SwipeToSpeakerHintPopupPreview() {
  Previews.Preview {
    SwipeToSpeakerHintPopup(
      visible = true,
      onDismiss = {}
    )
  }
}
