/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import kotlinx.coroutines.flow.Flow
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.DismissibleBannerProducer
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner

class GroupsV1MigrationSuggestionsBanner(private val suggestionsSize: Int, private val onAddMembers: () -> Unit, private val onNoThanks: () -> Unit) : Banner() {
  override val enabled: Boolean = suggestionsSize > 0

  @Composable
  override fun DisplayBanner() {
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
      )
    )
  }

  private class Producer(suggestionsSize: Int, onAddMembers: () -> Unit, onNoThanks: () -> Unit) : DismissibleBannerProducer<GroupsV1MigrationSuggestionsBanner>(bannerProducer = {
    GroupsV1MigrationSuggestionsBanner(
      suggestionsSize,
      onAddMembers
    ) {
      onNoThanks()
      it()
    }
  }) {
    override fun createDismissedBanner(): GroupsV1MigrationSuggestionsBanner {
      return GroupsV1MigrationSuggestionsBanner(0, {}, {})
    }
  }

  companion object {
    fun createFlow(suggestionsSize: Int, onAddMembers: () -> Unit, onNoThanks: () -> Unit): Flow<GroupsV1MigrationSuggestionsBanner> {
      return Producer(suggestionsSize, onAddMembers, onNoThanks).flow
    }
  }
}
