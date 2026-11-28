/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Dual use Edit/Create notification profile fragment. Use to create in the create profile flow,
 * and then to edit from profile details. Responsible for naming and emoji.
 */
class EditNotificationProfileFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(
    onNavigateBack = { findNavController().navigateUp() },
    onNavigateToScreen = ::navigateToScreen
  )

  @Composable
  override fun FragmentContent() {
    EditNotificationProfileEntry(
      profileId = EditNotificationProfileFragmentArgs.fromBundle(requireArguments()).profileId,
      parentEventEmitter = appSettingsRouter::onEvent
    )
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      is AppSettingsRoute.NotificationsRoute.AddAllowedMembers -> findNavController().safeNavigate(
        EditNotificationProfileFragmentDirections.actionEditNotificationProfileFragmentToAddAllowedMembersFragment(route.profileId)
      )
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}
