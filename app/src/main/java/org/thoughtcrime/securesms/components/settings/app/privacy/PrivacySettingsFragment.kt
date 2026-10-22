package org.thoughtcrime.securesms.components.settings.app.privacy

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.AppSettingsRouter
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.collectFlowOnViewLifecycle
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Fragment host for [PrivacySettingsEntry] until app settings moves to Nav3. Only translates routes into the nav graph.
 */
class PrivacySettingsFragment : ComposeFragment() {

  private val args: PrivacySettingsFragmentArgs by navArgs()
  private val appSettingsRouter by viewModels<AppSettingsRouter>()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    collectFlowOnViewLifecycle(appSettingsRouter.events, Lifecycle.State.RESUMED) { event ->
      when (event) {
        AppSettingsNavigationEvent.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
        is AppSettingsNavigationEvent.NavigateToScreen -> navigateToScreen(event.route)
      }
    }
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      AppSettingsRoute.PrivacyRoute.PhoneNumberPrivacy -> findNavController().safeNavigate(R.id.action_privacySettingsFragment_to_phoneNumberPrivacySettingsFragment)
      AppSettingsRoute.PrivacyRoute.BlockedUsers -> findNavController().safeNavigate(R.id.action_privacySettingsFragment_to_blockedUsersActivity)
      AppSettingsRoute.PrivacyRoute.ExpiringMessages -> findNavController().safeNavigate(R.id.action_privacySettingsFragment_to_disappearingMessagesTimerSelectFragment)
      AppSettingsRoute.PrivacyRoute.ScreenLock -> findNavController().safeNavigate(R.id.action_privacySettingsFragment_to_screenLockSettingsFragment)
      AppSettingsRoute.PrivacyRoute.Advanced -> findNavController().safeNavigate(R.id.action_privacySettingsFragment_to_advancedPrivacySettingsFragment)
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }

  @Composable
  override fun FragmentContent() {
    PrivacySettingsEntry(
      scrollToPaymentLock = args.showPaymentLock,
      parentEventEmitter = appSettingsRouter::onEvent
    )
  }
}
