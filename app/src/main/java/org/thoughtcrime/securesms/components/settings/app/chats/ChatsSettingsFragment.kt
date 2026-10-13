package org.thoughtcrime.securesms.components.settings.app.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.chats.ChatsSettingsEvents
import org.signal.appsettings.chats.ChatsSettingsScreen
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Displays a list of chats settings options to the user, including
 * generating link previews and keeping muted chats archived.
 */
class ChatsSettingsFragment : ComposeFragment() {

  private val viewModel: ChatsSettingsViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatsSettingsScreen(
      state = state,
      onEvent = { event ->
        when (event) {
          ChatsSettingsEvents.ChatFoldersClicked -> findNavController().safeNavigate(R.id.action_chatsSettingsFragment_to_chatFoldersFragment)
          else -> viewModel.onEvent(event)
        }
      }
    )
  }
}
