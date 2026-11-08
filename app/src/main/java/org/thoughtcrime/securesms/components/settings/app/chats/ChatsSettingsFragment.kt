package org.thoughtcrime.securesms.components.settings.app.chats

import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Fragment host for [ChatsSettingsEntry] until app settings moves to Nav3. Only translates routes into the nav graph.
 */
class ChatsSettingsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      AppSettingsRoute.ChatFoldersRoute.ChatFolders -> findNavController().safeNavigate(R.id.action_chatsSettingsFragment_to_chatFoldersFragment)
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }

  @Composable
  override fun FragmentContent() {
    ChatsSettingsEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}
