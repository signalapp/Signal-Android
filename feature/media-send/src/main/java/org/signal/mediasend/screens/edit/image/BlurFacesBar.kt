/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediasend.R

/**
 * Takes the place of the color bar while the user is blurring, offering to mask every face in the image for them.
 */
@Composable
internal fun BlurFacesBar(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .widthIn(max = 256.dp)
      .fillMaxWidth()
      .background(color = SignalTheme.colors.colorSurface5, shape = CircleShape)
      .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
  ) {
    Text(text = stringResource(R.string.BlurFacesBar__blur_faces))
    Spacer(modifier = Modifier.weight(1f))
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange
    )
  }
}

@DayNightPreviews
@Composable
private fun BlurFacesBarPreview() {
  Previews.Preview {
    BlurFacesBar(
      checked = true,
      onCheckedChange = {}
    )
  }
}
