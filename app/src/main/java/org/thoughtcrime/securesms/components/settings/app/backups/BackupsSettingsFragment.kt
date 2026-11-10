/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.components.settings.app.backups.local.usesOnDeviceBackupsScreen
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Top-level backups settings screen, under the legacy nav graph.
 */
class BackupsSettingsFragment : ComposeFragment() {

  private val args: BackupsSettingsFragmentArgs by navArgs()

  private val appSettingsRouter by appSettingsRouter(
    onNavigateBack = { requireActivity().onNavigateUp() },
    onNavigateToScreen = ::navigateToScreen
  )

  @Composable
  override fun FragmentContent() {
    BackupsSettingsEntry(
      parentEventEmitter = appSettingsRouter::onEvent,
      launchCheckoutFlow = args.launchCheckoutFlow
    )
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      is AppSettingsRoute.BackupsRoute.Remote -> findNavController().safeNavigate(R.id.action_backupsSettingsFragment_to_remoteBackupsSettingsFragment)
      is AppSettingsRoute.BackupsRoute.Local -> {
        if (route.usesOnDeviceBackupsScreen()) {
          findNavController().safeNavigate(R.id.action_backupsSettingsFragment_to_localBackupsFragment)
        } else {
          findNavController().safeNavigate(R.id.action_backupsSettingsFragment_to_backupsPreferenceFragment)
        }
      }
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}
