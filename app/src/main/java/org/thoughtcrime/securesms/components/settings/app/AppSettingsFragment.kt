package org.thoughtcrime.securesms.components.settings.app

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.AppSettingsRouter
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.collectFlowOnViewLifecycle
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.completed.InAppPaymentsBottomSheetDelegate
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Fragment host for [AppSettingsEntry] until app settings moves to Nav3. Only translates routes into the nav graph.
 */
class AppSettingsFragment : ComposeFragment() {

  private val appSettingsRouter by viewModels<AppSettingsRouter>()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    viewLifecycleOwner.lifecycle.addObserver(InAppPaymentsBottomSheetDelegate(childFragmentManager, viewLifecycleOwner))

    collectFlowOnViewLifecycle(appSettingsRouter.events, Lifecycle.State.RESUMED) { event ->
      when (event) {
        AppSettingsNavigationEvent.NavigateBack -> requireActivity().finishAfterTransition()
        is AppSettingsNavigationEvent.NavigateToScreen -> navigateToScreen(event.route)
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    AppSettingsEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      is AppSettingsRoute.BackupsRoute.Remote -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_remoteBackupsSettingsFragment)
      is AppSettingsRoute.AccountRoute.Account -> if (SignalStore.account.isPrimaryDevice) {
        findNavController().safeNavigate(R.id.action_appSettingsFragment_to_accountSettingsFragment)
      } else {
        findNavController().safeNavigate(R.id.action_appSettingsFragment_to_linkedDeviceAccountSettingsFragment)
      }
      is AppSettingsRoute.LinkDeviceRoute.LinkDevice -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_linkDeviceFragment)
      is AppSettingsRoute.DonationsRoute.Donations -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_manageDonationsFragment)
      is AppSettingsRoute.AppearanceRoute.Appearance -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_appearanceSettingsFragment)
      is AppSettingsRoute.ChatsRoute.Chats -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_chatsSettingsFragment)
      is AppSettingsRoute.StoriesRoute.Privacy -> findNavController().safeNavigate(AppSettingsFragmentDirections.actionAppSettingsFragmentToStoryPrivacySettings(route.titleId))
      is AppSettingsRoute.NotificationsRoute.Notifications -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_notificationsSettingsFragment)
      is AppSettingsRoute.PrivacyRoute.Privacy -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_privacySettingsFragment)
      is AppSettingsRoute.BackupsRoute.Backups -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_backupsSettingsFragment)
      is AppSettingsRoute.DataAndStorageRoute.DataAndStorage -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_dataAndStorageSettingsFragment)
      is AppSettingsRoute.AppUpdates -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_appUpdatesSettingsFragment)
      is AppSettingsRoute.Payments -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_paymentsActivity)
      is AppSettingsRoute.HelpRoute.Settings -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_helpSettingsFragment)
      is AppSettingsRoute.Invite -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_inviteFragment)
      is AppSettingsRoute.LabsRoute.Labs -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_labsSettingsFragment)
      is AppSettingsRoute.InternalRoute.Internal -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_internalSettingsFragment)
      is AppSettingsRoute.AccountRoute.ManageProfile -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_manageProfileActivity)
      is AppSettingsRoute.UsernameLinkRoute.UsernameLink -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_usernameLinkSettingsFragment)
      else -> error("Unsupported route: ${route.javaClass.name}")
    }
  }
}
