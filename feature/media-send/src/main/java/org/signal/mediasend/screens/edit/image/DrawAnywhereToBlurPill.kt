/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediasend.R

@Composable
internal fun DrawAnywhereToBlurPill() {
  Text(
    text = stringResource(R.string.DrawAnywhereToBlurPill__draw_anywhere_to_blur),
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier
      .background(
        color = SignalTheme.colors.colorSurface5,
        shape = CircleShape
      )
      .padding(
        horizontal = 12.dp,
        vertical = 8.dp
      )
  )
}

@DayNightPreviews
@Composable
private fun DrawAnywhereToBlurPillPreview() {
  Previews.Preview {
    DrawAnywhereToBlurPill()
  }
}
