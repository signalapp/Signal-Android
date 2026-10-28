/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.overview

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.badges.BadgesOverviewAction
import org.signal.appsettings.badges.BadgesOverviewEvent
import org.signal.appsettings.badges.BadgesOverviewScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.thoughtcrime.securesms.badges.self.expired.ExpiredOneTimeBadgeBottomSheetDialogFragment
import org.thoughtcrime.securesms.badges.view.ViewBadgeBottomSheet
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImage64
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Hosts [BadgesOverviewScreen] and carries out its [BadgesOverviewAction]s. The view model navigates by sending events
 * to [parentEventEmitter], so this works from whichever host owns the back stack.
 */
@Composable
fun BadgesOverviewEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: BadgesOverviewViewModel = viewModel { BadgesOverviewViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val activity = LocalActivity.current as FragmentActivity

  CollectActions(viewModel.actions) { action ->
    when (action) {
      is BadgesOverviewAction.ShowExpiredBadge -> ExpiredOneTimeBadgeBottomSheetDialogFragment.show(action.badge, null, null, activity.supportFragmentManager)
    }
  }

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
}
