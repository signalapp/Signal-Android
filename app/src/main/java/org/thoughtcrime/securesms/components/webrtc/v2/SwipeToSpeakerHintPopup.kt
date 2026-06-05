/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.layout.Column
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
import org.signal.core.ui.R as CoreUiR

/**
 * Popup shown to hint the user that they should swipe between the grid view and
 * the focused page for speaker/screen share when available.
 */
@Composable
fun SwipeToSpeakerHintPopup(
  hintType: SwipeHintType,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val textResId = when (hintType) {
    SwipeHintType.SCREEN_SHARE -> R.string.CallToastPopupWindow__swipe_to_view_screen_share
    SwipeHintType.SPEAKER_VIEW,
    SwipeHintType.NONE -> R.string.CallToastPopupWindow__swipe_to_view_speaker
  }

  CallScreenPopup(
    visible = hintType != SwipeHintType.NONE,
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
        tint = colorResource(CoreUiR.color.signal_light_colorOnSecondaryContainer),
        modifier = Modifier.size(24.dp)
      )

      Text(
        text = stringResource(textResId),
        color = colorResource(CoreUiR.color.signal_light_colorOnSecondaryContainer),
        modifier = Modifier.padding(start = 8.dp)
      )
    }
  }
}

@NightPreview
@Composable
private fun SwipeToSpeakerHintPopupPreview() {
  Previews.Preview {
    Column {
      SwipeToSpeakerHintPopup(
        hintType = SwipeHintType.SPEAKER_VIEW,
        onDismiss = {}
      )

      SwipeToSpeakerHintPopup(
        hintType = SwipeHintType.SCREEN_SHARE,
        onDismiss = {}
      )
    }
  }
}
