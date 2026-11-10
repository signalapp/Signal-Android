/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.AndroidNavHostFragment
import org.thoughtcrime.securesms.compose.FragmentBackHandler
import org.thoughtcrime.securesms.compose.FragmentBackPressedState
import org.thoughtcrime.securesms.compose.GraphNavHostFragment

/**
 * Registers the change number flow, which is hosted whole as its nav graph rather than as an entry per step.
 */
fun EntryProviderScope<NavKey>.registerChangeNumberRoutes() {
  detailEntry<AppSettingsRoute.ChangeNumberRoute.Start> {
    val backPressedState = remember { FragmentBackPressedState() }
    FragmentBackHandler(backPressedState)

    AndroidNavHostFragment(
      clazz = GraphNavHostFragment::class.java,
      arguments = GraphNavHostFragment.createArgs(R.navigation.app_settings_change_number),
      modifier = Modifier.fillMaxSize()
    ) { fragment ->
      backPressedState.attach(fragment)
    }
  }
}
