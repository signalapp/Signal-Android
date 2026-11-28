/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.signal.core.ui.compose.Fragments
import org.signal.core.ui.compose.rememberIsSplitPaneSettings
import org.signal.core.ui.compose.split.LocalIsDetailPaneRoot

/**
 * Hosts a [DSLSettingsFragment] as a nav entry. Like a Compose settings scaffold, its toolbar has no navigation icon
 * at the bottom of a detail stack beside the list, which is closed by picking something else from the list.
 */
@Composable
inline fun <reified T : DSLSettingsFragment> DSLSettingsFragmentEntry(arguments: Bundle = Bundle.EMPTY) {
  val showsNavigationIcon = !(rememberIsSplitPaneSettings() && LocalIsDetailPaneRoot.current)

  Fragments.Fragment<T>(
    modifier = Modifier.fillMaxSize(),
    arguments = Bundle(arguments).apply { putBoolean(DSLSettingsFragment.ARG_SHOWS_NAVIGATION_ICON, showsNavigationIcon) }
  )
}
