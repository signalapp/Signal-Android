package org.thoughtcrime.securesms.components.settings.app.usernamelinks.colorpicker

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
 * Fragment host for [UsernameLinkQrColorPickerEntry] until the username link moves to Nav3.
 */
class UsernameLinkQrColorPickerFragment : ComposeFragment() {

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
    UsernameLinkQrColorPickerEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}
