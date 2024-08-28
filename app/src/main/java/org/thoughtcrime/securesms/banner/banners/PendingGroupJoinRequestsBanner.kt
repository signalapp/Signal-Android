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

class PendingGroupJoinRequestsBanner(override val enabled: Boolean, private val suggestionsSize: Int, private val onViewClicked: () -> Unit, private val onDismissListener: (() -> Unit)?) : Banner() {

  @Composable
  override fun DisplayBanner() {
    DefaultBanner(
      title = null,
      body = pluralStringResource(
        id = R.plurals.PendingGroupJoinRequestsReminder_d_pending_member_requests,
        count = suggestionsSize,
        suggestionsSize
      ),
      actions = listOf(
        Action(R.string.PendingGroupJoinRequestsReminder_view, onClick = onViewClicked)
      ),
      onDismissListener = onDismissListener
    )
  }

  private class Producer(suggestionsSize: Int, onViewClicked: () -> Unit) : DismissibleBannerProducer<PendingGroupJoinRequestsBanner>(bannerProducer = {
    PendingGroupJoinRequestsBanner(suggestionsSize > 0, suggestionsSize, onViewClicked, it)
  }) {
    override fun createDismissedBanner(): PendingGroupJoinRequestsBanner {
      return PendingGroupJoinRequestsBanner(false, 0, {}, null)
    }
  }

  companion object {
    fun createFlow(suggestionsSize: Int, onViewClicked: () -> Unit): Flow<PendingGroupJoinRequestsBanner> {
      return Producer(suggestionsSize, onViewClicked).flow
    }
  }
}
