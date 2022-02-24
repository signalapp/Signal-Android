package org.thoughtcrime.securesms.stories.tabs

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.visible
import java.text.NumberFormat

/**
 * Displays the "Chats" and "Stories" tab to a user.
 */
class ConversationListTabsFragment : Fragment(R.layout.conversation_list_tabs) {

  private val viewModel: ConversationListTabsViewModel by viewModels(ownerProducer = { requireActivity() })

  private lateinit var chatsUnreadIndicator: TextView
  private lateinit var storiesUnreadIndicator: TextView
  private lateinit var chatsIcon: View
  private lateinit var storiesIcon: View

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    chatsUnreadIndicator = view.findViewById(R.id.chats_unread_indicator)
    storiesUnreadIndicator = view.findViewById(R.id.stories_unread_indicator)
    chatsIcon = view.findViewById(R.id.chats_tab_icon)
    storiesIcon = view.findViewById(R.id.stories_tab_icon)

    view.findViewById<View>(R.id.chats_tab_touch_point).setOnClickListener {
      viewModel.onChatsSelected()
    }

    view.findViewById<View>(R.id.stories_tab_touch_point).setOnClickListener {
      viewModel.onStoriesSelected()
    }

    viewModel.state.observe(viewLifecycleOwner, this::update)
  }

  private fun update(state: ConversationListTabsState) {
    chatsIcon.isSelected = state.tab == ConversationListTab.CHATS
    storiesIcon.isSelected = state.tab == ConversationListTab.STORIES

    chatsUnreadIndicator.visible = state.unreadChatsCount > 0
    chatsUnreadIndicator.text = formatCount(state.unreadChatsCount)

    storiesUnreadIndicator.visible = state.unreadStoriesCount > 0
    storiesUnreadIndicator.text = formatCount(state.unreadStoriesCount)
  }

  private fun formatCount(count: Long): String {
    if (count > 99L) {
      return getString(R.string.ConversationListTabs__99p)
    }
    return NumberFormat.getInstance().format(count)
  }
}
