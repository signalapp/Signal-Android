package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.schedule

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Can edit existing or use during create flow to setup a profile schedule.
 */
class EditNotificationProfileScheduleFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(
    onNavigateBack = { findNavController().navigateUp() },
    onNavigateToScreen = ::navigateToScreen
  )

  @Composable
  override fun FragmentContent() {
    val args = EditNotificationProfileScheduleFragmentArgs.fromBundle(requireArguments())

    EditNotificationProfileScheduleEntry(
      profileId = args.profileId,
      createMode = args.createMode,
      parentEventEmitter = appSettingsRouter::onEvent
    )
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      is AppSettingsRoute.NotificationsRoute.Created -> findNavController().safeNavigate(
        EditNotificationProfileScheduleFragmentDirections.actionEditNotificationProfileScheduleFragmentToNotificationProfileCreatedFragment(route.profileId)
      )
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}
