package org.thoughtcrime.securesms.components.settings.app.data

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Lets the user control automatic media downloads, sent media quality, call data usage, and network connectivity.
 */
class DataAndStorageSettingsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  @Composable
  override fun FragmentContent() {
    DataAndStorageSettingsEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      AppSettingsRoute.DataAndStorageRoute.Storage -> findNavController().safeNavigate(R.id.action_dataAndStorageSettingsFragment_to_storagePreferenceFragment)
      AppSettingsRoute.DataAndStorageRoute.Proxy -> findNavController().safeNavigate(R.id.action_dataAndStorageSettingsFragment_to_editProxyFragment)
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}
