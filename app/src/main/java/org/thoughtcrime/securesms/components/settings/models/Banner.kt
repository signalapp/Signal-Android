/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.models

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.R as CoreUiR

/**
 * Replicates the Banner DSL preference for use in compose components.
 */
@Composable
fun Banner(
  text: String,
  action: String,
  onActionClick: () -> Unit
) {
  OutlinedCard(
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(width = 1.dp, color = colorResource(CoreUiR.color.signal_colorOutline_38)),
    modifier = Modifier
      .horizontalGutters()
      .fillMaxWidth()
  ) {
    Column {
      Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
          .padding(horizontal = 16.57.dp)
          .padding(top = 16.dp, bottom = 10.dp)
      )

      TextButton(
        onClick = onActionClick,
        modifier = Modifier
          .align(Alignment.End)
          .padding(horizontal = 8.dp)
      ) {
        Text(text = action)
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun BannerPreview() {
  Previews.Preview {
    Banner(
      text = "Banner text will go here and probably be about something important",
      action = "Action",
      onActionClick = {}
    )
  }
}
