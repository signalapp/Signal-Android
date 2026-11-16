/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.badges.BadgesOverviewEvent
import org.signal.appsettings.badges.BadgesOverviewScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.thoughtcrime.securesms.badges.self.expired.ExpiredBadgeBottomSheet
import org.thoughtcrime.securesms.badges.view.ViewBadgeBottomSheet
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImage64
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Hosts [BadgesOverviewScreen] and the sheets it opens. The view model navigates by sending events to
 * [parentEventEmitter], so this works from whichever host owns the back stack.
 */
@Composable
fun BadgesOverviewEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: BadgesOverviewViewModel = viewModel { BadgesOverviewViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  BadgesOverviewScreen(
    state = state,
    onEvent = viewModel::onEvent,
    badgeImage = { badge, modifier -> BadgeImage64(badge = badge, modifier = modifier) }
  )

  state.viewedBadge?.let { badge ->
    ViewBadgeBottomSheet(
      recipientId = Recipient.self().id,
      startBadge = badge,
      onDismissRequest = { viewModel.onEvent(BadgesOverviewEvent.BadgeSheetDismissed) }
    )
  }

  state.expiredBadge?.let { badge ->
    ExpiredBadgeBottomSheet(
      badge = badge,
      onDismissRequest = { viewModel.onEvent(BadgesOverviewEvent.ExpiredBadgeSheetDismissed) }
    )
  }
}
