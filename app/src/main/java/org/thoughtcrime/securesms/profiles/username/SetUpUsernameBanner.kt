/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.username

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.R
import org.signal.core.ui.R as CoreUiR

/**
 * Prompts a phone-numberless user without a username to create one. Dismissal is permanent.
 */
@Composable
fun SetUpUsernameBanner(
  onSetUpClick: () -> Unit,
  onDismissClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(36.dp))
      .background(colorResource(R.color.set_up_username_banner_background))
      .defaultMinSize(minHeight = 72.dp)
      .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(CoreUiR.drawable.symbol_at_24),
      contentDescription = null,
      tint = colorResource(CoreUiR.color.signal_light_colorOnSurface),
      modifier = Modifier.size(24.dp)
    )

    Text(
      text = stringResource(R.string.SetUpUsernameBanner__choose_a_username_so_others_can_connect_with_you),
      style = MaterialTheme.typography.bodyMedium,
      color = colorResource(CoreUiR.color.signal_light_colorOnSurface),
      modifier = Modifier
        .weight(1f)
        .padding(start = 12.dp)
    )

    Buttons.Small(
      onClick = onSetUpClick,
      colors = ButtonDefaults.buttonColors(
        containerColor = colorResource(R.color.set_up_username_banner_button_background),
        contentColor = colorResource(CoreUiR.color.signal_light_colorNeutralInverse)
      )
    ) {
      Text(text = stringResource(R.string.SetUpUsernameBanner__set_up))
    }

    IconButtons.IconButton(
      onClick = onDismissClick,
      size = 32.dp
    ) {
      Icon(
        imageVector = SignalIcons.X.imageVector,
        contentDescription = stringResource(R.string.SetUpUsernameBanner__dismiss),
        tint = colorResource(CoreUiR.color.signal_light_colorOnSurface)
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun SetUpUsernameBannerPreview() {
  Previews.Preview {
    SetUpUsernameBanner(
      onSetUpClick = {},
      onDismissClick = {},
      modifier = Modifier.padding(16.dp)
    )
  }
}
