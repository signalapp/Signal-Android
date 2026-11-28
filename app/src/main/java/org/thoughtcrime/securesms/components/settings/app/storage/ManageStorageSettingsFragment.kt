/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Manage settings related to on-device storage including viewing usage and auto-delete settings.
 */
class ManageStorageSettingsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      AppSettingsRoute.DataAndStorageRoute.KeepMessages -> findNavController().safeNavigate(R.id.action_storagePreferenceFragment_to_keepMessagesSettingsFragment)
      AppSettingsRoute.DataAndStorageRoute.ChatLengthLimit -> findNavController().safeNavigate(R.id.action_storagePreferenceFragment_to_chatLengthLimitSettingsFragment)
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }

  @Composable
  override fun FragmentContent() {
    ManageStorageSettingsEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}
