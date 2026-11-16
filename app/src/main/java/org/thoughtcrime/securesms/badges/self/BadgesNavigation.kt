/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry
import org.thoughtcrime.securesms.badges.self.featured.SelectFeaturedBadgeEntry
import org.thoughtcrime.securesms.badges.self.overview.BadgesOverviewEntry

/**
 * Registers the screens for managing the user's own badges.
 */
fun EntryProviderScope<NavKey>.registerBadgeScreens(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.DonationsRoute.Badges> {
    BadgesOverviewEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.DonationsRoute.Featured> {
    SelectFeaturedBadgeEntry(parentEventEmitter = parentEventEmitter)
  }
}
