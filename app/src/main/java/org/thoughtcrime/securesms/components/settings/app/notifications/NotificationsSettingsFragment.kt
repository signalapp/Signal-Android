package org.thoughtcrime.securesms.components.settings.app.notifications

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class NotificationsSettingsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  @Composable
  override fun FragmentContent() {
    NotificationsSettingsEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      AppSettingsRoute.NotificationsRoute.NotificationProfiles -> findNavController().safeNavigate(R.id.action_notificationsSettingsFragment_to_notificationProfilesFragment)
      AppSettingsRoute.NotificationsRoute.MutedNotifications -> findNavController().safeNavigate(R.id.action_notificationsSettingsFragment_to_mutedNotificationsFragment)
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}
