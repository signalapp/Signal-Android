package org.thoughtcrime.securesms.stories.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.fragment.app.viewModels
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.main.MainNavigationBar
import org.thoughtcrime.securesms.main.MainNavigationDestination
import org.thoughtcrime.securesms.main.MainNavigationState

/**
 * Displays the "Chats" and "Stories" tab to a user.
 */
class ConversationListTabsFragment : ComposeFragment() {

  private val viewModel: ConversationListTabsViewModel by viewModels(ownerProducer = { requireActivity() })

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.subscribeAsState(ConversationListTabsState())

    val navState = remember(state) {
      MainNavigationState(
        chatsCount = state.unreadMessagesCount.toInt(),
        callsCount = state.unreadCallsCount.toInt(),
        storiesCount = state.unreadStoriesCount.toInt(),
        storyFailure = state.hasFailedStory,
        selectedDestination = when (state.tab) {
          ConversationListTab.CHATS -> MainNavigationDestination.CHATS
          ConversationListTab.CALLS -> MainNavigationDestination.CALLS
          ConversationListTab.STORIES -> MainNavigationDestination.STORIES
        },
        compact = state.compact
      )
    }

    if (state.visibilityState.isVisible()) {
      MainNavigationBar(
        state = navState,
        onDestinationSelected = {
          when (it) {
            MainNavigationDestination.CHATS -> viewModel.onChatsSelected()
            MainNavigationDestination.CALLS -> viewModel.onCallsSelected()
            MainNavigationDestination.STORIES -> viewModel.onStoriesSelected()
          }
        }
      )
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshNavigationBarState()
  }
}
