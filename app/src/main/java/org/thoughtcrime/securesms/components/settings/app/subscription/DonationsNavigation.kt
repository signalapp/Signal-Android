/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragmentEntry
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.ManageDonationsFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.ManageDonationsFragmentArgs
import org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail.DonationReceiptDetailEntry
import org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list.DonationReceiptListEntry

/**
 * Registers the donation screens of app settings. Managing donations is still a View-based fragment, so it is hosted
 * as one.
 */
fun EntryProviderScope<NavKey>.registerDonationsRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.DonationsRoute.Donations> { route ->
    DSLSettingsFragmentEntry<ManageDonationsFragment>(
      arguments = ManageDonationsFragmentArgs.Builder().setDirectToCheckoutType(route.directToCheckoutType).build().toBundle()
    )
  }

  detailEntry<AppSettingsRoute.DonationsRoute.Receipts> {
    DonationReceiptListEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.DonationsRoute.Receipt> { route ->
    DonationReceiptDetailEntry(receiptId = route.id, parentEventEmitter = parentEventEmitter)
  }
}
