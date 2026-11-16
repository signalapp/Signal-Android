package org.thoughtcrime.securesms.components.settings.app.appearance

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Fragment host for [AppearanceSettingsEntry] until app settings moves to Nav3. Only translates routes into the nav graph.
 */
class AppearanceSettingsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      AppSettingsRoute.AppearanceRoute.Wallpaper -> findNavController().safeNavigate(R.id.action_appearanceSettings_to_wallpaperActivity)
      AppSettingsRoute.AppearanceRoute.AppIconSelection -> findNavController().safeNavigate(R.id.action_appearanceSettings_to_appIconActivity)
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }

  @Composable
  override fun FragmentContent() {
    AppearanceSettingsEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}
