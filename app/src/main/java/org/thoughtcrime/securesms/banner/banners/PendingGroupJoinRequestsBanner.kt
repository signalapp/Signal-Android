/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.signal.core.ui.R as CoreUiR

/**
 * Shows the number of pending requests to join the group.
 * Intended to be shown at the top of a conversation.
 */
class PendingGroupJoinRequestsBanner(private val suggestionsSize: Int, private val onViewClicked: () -> Unit) : Banner<Int>() {

  override val enabled: Boolean
    get() = suggestionsSize > 0

  override val dataFlow: Flow<Int> = flowOf(suggestionsSize)

  @Composable
  override fun DisplayBanner(model: Int, contentPadding: PaddingValues) {
    Banner(
      contentPadding = contentPadding,
      suggestionsSize = model,
      onViewClicked = onViewClicked
    )
  }
}

@Composable
private fun Banner(contentPadding: PaddingValues, suggestionsSize: Int, onViewClicked: () -> Unit = {}) {
  var visible by remember { mutableStateOf(true) }

  if (!visible) {
    return
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .clip(RoundedCornerShape(24.dp))
      .background(color = colorResource(CoreUiR.color.signal_colorSurface2))
      .padding(horizontal = 16.dp)
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_group_24),
      tint = MaterialTheme.colorScheme.onSurface,
      contentDescription = null,
      modifier = Modifier.padding(end = 16.dp)
    )

    Text(
      text = pluralStringResource(
        id = R.plurals.PendingGroupJoinRequestsReminder_d_pending_member_requests,
        count = suggestionsSize,
        suggestionsSize
      ),
      color = MaterialTheme.colorScheme.onSurface,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier
        .weight(1f)
        .padding()
    )

    TextButton(
      onClick = onViewClicked,
      colors = ButtonDefaults.textButtonColors(
        containerColor = colorResource(CoreUiR.color.signal_colorSurface5),
        contentColor = MaterialTheme.colorScheme.onSurface
      ),
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
      Text(
        text = stringResource(id = R.string.PendingGroupJoinRequestsReminder_view),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 12.dp)
      )
    }

    IconButton(
      onClick = { visible = false },
      modifier = Modifier.size(24.dp)

    ) {
      Icon(
        imageVector = SignalIcons.X.imageVector,
        contentDescription = stringResource(id = R.string.InviteActivity_cancel),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun BannerPreviewSingular() {
  Previews.Preview {
    Banner(contentPadding = PaddingValues(0.dp), suggestionsSize = 1)
  }
}

@DayNightPreviews
@Composable
private fun BannerPreviewPlural() {
  Previews.Preview {
    Banner(contentPadding = PaddingValues(0.dp), suggestionsSize = 2)
  }
}
