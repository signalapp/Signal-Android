/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance.appicon

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class AppIconSelectionFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  @Composable
  override fun FragmentContent() {
    AppIconSelectionEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      AppSettingsRoute.AppearanceRoute.AppIconTutorial -> findNavController().safeNavigate(R.id.action_appIconSelectionFragment_to_appIconTutorialFragment)
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}
