package org.thoughtcrime.securesms.components.settings.app.privacy.pnp

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRouter
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.collectFlowOnViewLifecycle

/**
 * Lets the user choose who can see their phone number and who can find them by it.
 */
class PhoneNumberPrivacySettingsFragment : ComposeFragment() {

  private val appSettingsRouter by viewModels<AppSettingsRouter>()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    collectFlowOnViewLifecycle(appSettingsRouter.events, Lifecycle.State.RESUMED) { event ->
      when (event) {
        AppSettingsNavigationEvent.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
        is AppSettingsNavigationEvent.NavigateToScreen -> error("Unexpected route: ${event.route.javaClass.name}")
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    PhoneNumberPrivacySettingsEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}
