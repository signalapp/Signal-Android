package org.thoughtcrime.securesms.conversation.v2.menus

import android.content.Context
import org.session.libsession.messaging.open_groups.OpenGroup
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.groups.OpenGroupManager

object ConversationMenuItemHelper {

    @JvmStatic
    fun userCanDeleteSelectedItems(context: Context, message: MessageRecord, openGroup: OpenGroup?, userPublicKey: String, blindedPublicKey: String?): Boolean {
        if (openGroup  == null) return message.isOutgoing || !message.isOutgoing
        if (message.isOutgoing) return true
        return OpenGroupManager.isUserModerator(context, openGroup.groupId, userPublicKey, blindedPublicKey)
    }

    @JvmStatic
    fun userCanBanSelectedUsers(context: Context, message: MessageRecord, openGroup: OpenGroup?, userPublicKey: String, blindedPublicKey: String?): Boolean {
        if (openGroup == null)  return false
        if (message.isOutgoing) return false // Users can't ban themselves
        return OpenGroupManager.isUserModerator(context, openGroup.groupId, userPublicKey, blindedPublicKey)
    }

}
