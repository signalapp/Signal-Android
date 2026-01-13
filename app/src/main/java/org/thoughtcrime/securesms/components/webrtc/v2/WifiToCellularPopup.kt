/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.VibrateUtil
import kotlin.time.Duration.Companion.seconds

private const val VIBRATE_DURATION_MS = 50

/**
 * Popup shown when the device is connected to a WiFi and cellular network, and WiFi is unusable for
 * RingRTC, causing a switch to cellular.
 */
@Composable
fun WifiToCellularPopup(
  visible: Boolean,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  LaunchedEffect(visible) {
    if (visible) {
      VibrateUtil.vibrate(context, VIBRATE_DURATION_MS)
    }
  }

  CallScreenPopup(
    visible = visible,
    onDismiss = onDismiss,
    displayDuration = 4.seconds,
    modifier = modifier
  ) {
    Text(
      text = stringResource(R.string.WifiToCellularPopupWindow__weak_wifi_switched_to_cellular),
      color = colorResource(R.color.signal_light_colorOnSecondaryContainer),
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
    )
  }
}

@NightPreview
@Composable
private fun WifiToCellularPopupPreview() {
  Previews.Preview {
    WifiToCellularPopup(
      visible = true,
      onDismiss = {}
    )
  }
}
