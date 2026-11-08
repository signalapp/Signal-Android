package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.allowedmembers

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Show and allow addition of recipients to a profile during the create flow.
 */
class AddAllowedMembersFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  @Composable
  override fun FragmentContent() {
    AddAllowedMembersEntry(
      profileId = AddAllowedMembersFragmentArgs.fromBundle(requireArguments()).profileId,
      parentEventEmitter = appSettingsRouter::onEvent
    )
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      is AppSettingsRoute.NotificationsRoute.SelectRecipients -> findNavController().safeNavigate(
        AddAllowedMembersFragmentDirections.actionAddAllowedMembersFragmentToSelectRecipientsFragment(route.profileId).setCurrentSelection(route.currentSelection.toTypedArray())
      )
      is AppSettingsRoute.NotificationsRoute.EditSchedule -> findNavController().safeNavigate(
        AddAllowedMembersFragmentDirections.actionAddAllowedMembersFragmentToEditNotificationProfileScheduleFragment(route.profileId, route.createMode)
      )
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}
