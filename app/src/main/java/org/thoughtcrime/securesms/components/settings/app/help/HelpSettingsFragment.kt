/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class HelpSettingsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  @Composable
  override fun FragmentContent() {
    HelpSettingsEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      is AppSettingsRoute.HelpRoute.Help -> findNavController().safeNavigate(R.id.action_helpSettingsFragment_to_helpFragment)
      AppSettingsRoute.HelpRoute.DebugLog -> findNavController().safeNavigate(R.id.action_helpSettingsFragment_to_submitDebugLogActivity)
      AppSettingsRoute.HelpRoute.Licenses -> findNavController().safeNavigate(R.id.action_helpSettingsFragment_to_licenseFragment)
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}
