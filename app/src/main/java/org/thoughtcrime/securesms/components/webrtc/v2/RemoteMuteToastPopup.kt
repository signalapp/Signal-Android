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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import kotlin.time.Duration.Companion.seconds

/**
 * Popup shown when a user is remotely muted during a call.
 */
@Composable
fun RemoteMuteToastPopup(
  message: String?,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  CallScreenPopup(
    visible = message != null,
    onDismiss = onDismiss,
    displayDuration = 3.seconds,
    modifier = modifier
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
      Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.ic_mic_off_solid_18),
        contentDescription = null,
        tint = colorResource(R.color.signal_light_colorOnSecondaryContainer),
        modifier = Modifier.size(18.dp)
      )

      Text(
        text = message ?: "",
        color = colorResource(R.color.signal_light_colorOnSecondaryContainer),
        modifier = Modifier.padding(start = 8.dp)
      )
    }
  }
}

@NightPreview
@Composable
private fun RemoteMuteToastPopupPreview() {
  Previews.Preview {
    RemoteMuteToastPopup(
      message = "Alex muted you",
      onDismiss = {}
    )
  }
}
