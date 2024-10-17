/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
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
 * After migrating a group from v1 -> v2, this banner is used to show suggestions for members to add who couldn't be added automatically.
 * Intended to be shown only in a conversation.
 */
class GroupsV1MigrationSuggestionsBanner(
  private val suggestionsSize: Int,
  private val onAddMembers: () -> Unit,
  private val onNoThanks: () -> Unit
) : Banner<Int>() {

  override val enabled: Boolean
    get() = suggestionsSize > 0

  override val dataFlow: Flow<Int>
    get() = flowOf(suggestionsSize)

  @Composable
  override fun DisplayBanner(model: Int, contentPadding: PaddingValues) {
    Banner(
      contentPadding = contentPadding,
      suggestionsSize = model,
      onAddMembers = onAddMembers,
      onNoThanks = onNoThanks
    )
  }
}

@Composable
private fun Banner(contentPadding: PaddingValues, suggestionsSize: Int, onAddMembers: () -> Unit = {}, onNoThanks: () -> Unit = {}) {
  DefaultBanner(
    title = null,
    body = pluralStringResource(
      id = R.plurals.GroupsV1MigrationSuggestionsReminder_members_couldnt_be_added_to_the_new_group,
      count = suggestionsSize,
      suggestionsSize
    ),
    actions = listOf(
      Action(R.plurals.GroupsV1MigrationSuggestionsReminder_add_members, isPluralizedLabel = true, pluralQuantity = suggestionsSize, onAddMembers),
      Action(R.string.GroupsV1MigrationSuggestionsReminder_no_thanks, onClick = onNoThanks)
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
