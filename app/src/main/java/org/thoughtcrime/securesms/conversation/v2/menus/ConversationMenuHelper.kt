package org.thoughtcrime.securesms.conversation.v2.menus

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.activity_conversation_v2.*
import kotlinx.android.synthetic.main.session_logo_action_bar_content.*
import network.loki.messenger.R
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.conversation.v2.search.SearchViewModel
import org.thoughtcrime.securesms.loki.utilities.getColorWithID

object ConversationMenuHelper {
    
    fun onPrepareOptionsMenu(menu: Menu, inflater: MenuInflater, thread: Recipient, threadId: Long, context: Context, onOptionsItemSelected: (MenuItem) -> Unit) {
        // Prepare
        menu.clear()
        val isOpenGroup = thread.isOpenGroupRecipient
        // Base menu (options that should always be present)
        inflater.inflate(R.menu.menu_conversation, menu)
        // Expiring messages
        if (!isOpenGroup) {
            if (thread.expireMessages > 0) {
                inflater.inflate(R.menu.menu_conversation_expiration_on, menu)
                val item = menu.findItem(R.id.menu_expiring_messages)
                val actionView = item.actionView
                val iconView = actionView.findViewById<ImageView>(R.id.menu_badge_icon)
                val badgeView = actionView.findViewById<TextView>(R.id.expiration_badge)
                @ColorInt val color = context.resources.getColorWithID(R.color.text, context.theme)
                iconView.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
                badgeView.text = ExpirationUtil.getExpirationAbbreviatedDisplayValue(context, thread.expireMessages)
                actionView.setOnClickListener { onOptionsItemSelected(item) }
            } else {
                inflater.inflate(R.menu.menu_conversation_expiration_off, menu)
            }
        }
        // One-on-one chat menu (options that should only be present for one-on-one chats)
        if (thread.isContactRecipient) {
            if (thread.isBlocked) {
                inflater.inflate(R.menu.menu_conversation_unblock, menu)
            } else {
                inflater.inflate(R.menu.menu_conversation_block, menu)
            }
            inflater.inflate(R.menu.menu_conversation_copy_session_id, menu)
        }
        // Closed group menu (options that should only be present in closed groups)
        if (thread.isClosedGroupRecipient) {
            inflater.inflate(R.menu.menu_conversation_closed_group, menu)
        }
        // Open group menu
        if (isOpenGroup) {
            inflater.inflate(R.menu.menu_conversation_open_group, menu)
        }
        // Muting
        if (thread.isMuted) {
            inflater.inflate(R.menu.menu_conversation_muted, menu)
        } else {
            inflater.inflate(R.menu.menu_conversation_unmuted, menu)
        }
        // Search
        val searchViewItem = menu.findItem(R.id.menu_search)
        val searchView = searchViewItem.actionView as SearchView
        val searchViewModel = (context as ConversationActivityV2).getSearchViewModel()!!
        val queryListener = object : OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchViewModel.onQueryUpdated(query, threadId)
                context.searchBottomBar.showLoading()
                context.onSearchQueryUpdated(query)
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                searchViewModel.onQueryUpdated(query, threadId)
                context.searchBottomBar.showLoading()
                context.onSearchQueryUpdated(query)
                return true
            }
        }
        searchViewItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                searchView.setOnQueryTextListener(queryListener)
                searchViewModel.onSearchOpened()
                context.searchBottomBar.visibility = View.VISIBLE
                context.searchBottomBar.setData(0, 0)
                context.inputBar.visibility = View.GONE
                for (i in 0 until menu.size()) {
                    if (menu.getItem(i) != searchViewItem) {
                        menu.getItem(i).isVisible = false
                    }
                }
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                searchView.setOnQueryTextListener(null)
                searchViewModel.onSearchClosed()
                context.searchBottomBar.visibility = View.GONE
                context.inputBar.visibility = View.VISIBLE
                context.onSearchQueryUpdated(null)
                context.invalidateOptionsMenu()
                return true
            }
        })
    }
}