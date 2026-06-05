/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.donations

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews

/**
 * Compose "Donate with Google Pay" button utilizing the same styling as the layout.
 */
@Composable
fun DonateWithGooglePayButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  val contentOverlay = colorResource(R.color.donate_with_google_pay_content_overlay)

  Buttons.LargeTonal(
    enabled = enabled,
    onClick = onClick,
    modifier = modifier.drawWithContent {
      drawContent()

      if (!enabled) {
        drawRoundRect(
          color = contentOverlay,
          cornerRadius = CornerRadius(500f, 500f)
        )
      }
    },
    colors = ButtonDefaults.buttonColors(
      containerColor = colorResource(R.color.donate_with_google_pay_background_color),
      disabledContainerColor = colorResource(R.color.donate_with_google_pay_background_color)
    )
  ) {
    Image(
      imageVector = ImageVector.vectorResource(R.drawable.donate_with_googlepay_button_content),
      contentDescription = stringResource(R.string.donate_with_googlepay_button_content_description)
    )
  }
}

@DayNightPreviews
@Composable
private fun DonateWithGooglePayButtonPreview() {
  Previews.Preview {
    DonateWithGooglePayButton(
      onClick = {},
      modifier = Modifier
        .fillMaxWidth()
        .height(44.dp)
    )
  }
}

@DayNightPreviews
@Composable
private fun DonateWithGooglePayButtonDisabledPreview() {
  Previews.Preview {
    DonateWithGooglePayButton(
      onClick = {},
      enabled = false,
      modifier = Modifier
        .fillMaxWidth()
        .height(44.dp)
    )
  }
}
