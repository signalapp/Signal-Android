package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Primary entry point for Notification Profiles. When user has no profiles, shows empty state, otherwise shows
 * all current profiles.
 */
class NotificationProfilesFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  @Composable
  override fun FragmentContent() {
    NotificationProfilesEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      is AppSettingsRoute.NotificationsRoute.EditProfile -> findNavController().safeNavigate(R.id.action_notificationProfilesFragment_to_editNotificationProfileFragment)
      is AppSettingsRoute.NotificationsRoute.ProfileDetails -> findNavController().safeNavigate(NotificationProfilesFragmentDirections.actionNotificationProfilesFragmentToNotificationProfileDetailsFragment(route.profileId))
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}
