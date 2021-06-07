package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import network.loki.messenger.R
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.loki.utilities.getColorWithID

object ConversationMenuHelper {
    
    fun onPrepareOptionsMenu(menu: Menu, inflater: MenuInflater, thread: Recipient, context: Context, onOptionsItemSelected: (MenuItem) -> Unit): Boolean {
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
        // TODO: Implement search
        // Return
        return true
    }
}