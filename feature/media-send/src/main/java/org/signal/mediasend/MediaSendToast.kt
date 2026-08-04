/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * Renders a [ToastEvent]. Showing and hiding it is the caller's job.
 */
@Composable
internal fun MediaSendToast(
  event: ToastEvent,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = SignalTheme.colors.colorSurface2,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shadowElevation = 8.dp,
    modifier = modifier
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = spacedBy(8.dp),
      modifier = Modifier
        .heightIn(min = 44.dp)
        .padding(horizontal = 21.dp)
    ) {
      Icon(
        imageVector = event.icon.imageVector,
        contentDescription = null,
        modifier = Modifier.size(24.dp)
      )

      Text(
        text = when (val message = event.message) {
          is ToastMessage.Text -> stringResource(message.id)
          is ToastMessage.Quantity -> pluralStringResource(message.id, message.count, message.count)
        },
        style = MaterialTheme.typography.bodyMedium
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun MediaSendToastPreview() {
  Previews.Preview {
    MediaSendToast(
      event = ToastEvent(
        icon = SignalIcons.QualityHigh,
        message = ToastMessage.Text(R.string.MediaReviewFragment__photo_set_to_high_quality)
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaSendToastQuantityPreview() {
  Previews.Preview {
    MediaSendToast(
      event = ToastEvent(
        icon = SignalIcons.QualityHighSlash,
        message = ToastMessage.Quantity(R.plurals.MediaReviewFragment__items_set_to_standard_quality, 3)
      )
    )
  }
}
