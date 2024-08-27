/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner

class PendingGroupJoinRequestsBanner(override val enabled: Boolean, private val suggestionsSize: Int, private val onViewClicked: () -> Unit, private val onDismissListener: (() -> Unit)?) : Banner() {

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
        Action(R.string.PendingGroupJoinRequestsReminder_view, onClick = onViewClicked)
      ),
      onDismissListener = onDismissListener
    )
  }

  companion object {

    @JvmStatic
    fun createFlow(suggestionsSize: Int, onViewClicked: () -> Unit): Flow<PendingGroupJoinRequestsBanner> = Producer(suggestionsSize, onViewClicked).flow
  }

  private class Producer(suggestionsSize: Int, onViewClicked: () -> Unit) {
    val dismissListener: () -> Unit = {
      mutableStateFlow.tryEmit(PendingGroupJoinRequestsBanner(false, suggestionsSize, onViewClicked, null))
    }
    private val mutableStateFlow: MutableStateFlow<PendingGroupJoinRequestsBanner> = MutableStateFlow(PendingGroupJoinRequestsBanner(true, suggestionsSize, onViewClicked, dismissListener))
    val flow: Flow<PendingGroupJoinRequestsBanner> = mutableStateFlow
  }
}
