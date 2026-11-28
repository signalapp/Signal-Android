package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.created

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Shown at the end of the profile create flow.
 */
class NotificationProfileCreatedFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  @Composable
  override fun FragmentContent() {
    NotificationProfileCreatedEntry(
      profileId = NotificationProfileCreatedFragmentArgs.fromBundle(requireArguments()).profileId,
      parentEventEmitter = appSettingsRouter::onEvent
    )
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      is AppSettingsRoute.NotificationsRoute.ProfileDetails -> findNavController().safeNavigate(
        NotificationProfileCreatedFragmentDirections.actionNotificationProfileCreatedFragmentToNotificationProfileDetailsFragment(route.profileId)
      )
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}
