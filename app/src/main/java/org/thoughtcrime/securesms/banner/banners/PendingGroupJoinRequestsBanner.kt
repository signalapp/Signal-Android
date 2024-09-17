/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner

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

  DefaultBanner(
    title = null,
    body = pluralStringResource(
      id = R.plurals.PendingGroupJoinRequestsReminder_d_pending_member_requests,
      count = suggestionsSize,
      suggestionsSize
    ),
    onDismissListener = { visible = false },
    actions = listOf(
      Action(R.string.PendingGroupJoinRequestsReminder_view, onClick = onViewClicked)
    ),
    paddingValues = contentPadding
  )
}

@SignalPreview
@Composable
private fun BannerPreviewSingular() {
  Previews.Preview {
    Banner(contentPadding = PaddingValues(0.dp), suggestionsSize = 1)
  }
}

@SignalPreview
@Composable
private fun BannerPreviewPlural() {
  Previews.Preview {
    Banner(contentPadding = PaddingValues(0.dp), suggestionsSize = 2)
  }
}
