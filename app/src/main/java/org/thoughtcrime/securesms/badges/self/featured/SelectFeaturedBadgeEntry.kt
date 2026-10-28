/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.featured

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.badges.SelectFeaturedBadgeScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImage64
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImageLarge
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Hosts [SelectFeaturedBadgeScreen]. The view model navigates by sending events to [parentEventEmitter], so this works
 * from whichever host owns the back stack.
 */
@Composable
fun SelectFeaturedBadgeEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: SelectFeaturedBadgeViewModel = viewModel { SelectFeaturedBadgeViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val self = remember { Recipient.self() }

  SelectFeaturedBadgeScreen(
    state = state,
    onEvent = viewModel::onEvent,
    avatarPreview = { badge ->
      Box(modifier = Modifier.fillMaxSize()) {
        AvatarImage(recipient = self, modifier = Modifier.fillMaxSize())

        if (badge != null) {
          BadgeImageLarge(
            badge = badge,
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .size(32.dp)
          )
        }
      }
    },
    badgeImage = { badge, modifier -> BadgeImage64(badge = badge, modifier = modifier) }
  )
}
