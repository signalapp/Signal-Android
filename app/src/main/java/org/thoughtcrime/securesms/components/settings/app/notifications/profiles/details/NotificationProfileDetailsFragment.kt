package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.details

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Shows a notification profile and lets the user change who it allows, its schedule and exceptions, or delete it.
 */
class NotificationProfileDetailsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  @Composable
  override fun FragmentContent() {
    NotificationProfileDetailsEntry(
      profileId = NotificationProfileDetailsFragmentArgs.fromBundle(requireArguments()).profileId,
      parentEventEmitter = appSettingsRouter::onEvent
    )
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      is AppSettingsRoute.NotificationsRoute.EditProfile -> findNavController().safeNavigate(
        NotificationProfileDetailsFragmentDirections.actionNotificationProfileDetailsFragmentToEditNotificationProfileFragment().setProfileId(route.profileId)
      )
      is AppSettingsRoute.NotificationsRoute.SelectRecipients -> findNavController().safeNavigate(
        NotificationProfileDetailsFragmentDirections.actionNotificationProfileDetailsFragmentToSelectRecipientsFragment(route.profileId).setCurrentSelection(route.currentSelection.toTypedArray())
      )
      is AppSettingsRoute.NotificationsRoute.EditSchedule -> findNavController().safeNavigate(
        NotificationProfileDetailsFragmentDirections.actionNotificationProfileDetailsFragmentToEditNotificationProfileScheduleFragment(route.profileId, route.createMode)
      )
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}
