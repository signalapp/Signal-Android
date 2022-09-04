package org.thoughtcrime.securesms.conversation.v2.menus

import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import network.loki.messenger.R
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.utilities.SessionId
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.IdPrefix
import org.thoughtcrime.securesms.conversation.v2.ConversationAdapter
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.groups.OpenGroupManager

class ConversationActionModeCallback(private val adapter: ConversationAdapter, private val threadID: Long,
    private val context: Context) : ActionMode.Callback {
    var delegate: ConversationActionModeCallbackDelegate? = null

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val inflater = mode.menuInflater
        inflater.inflate(R.menu.menu_conversation_item_action, menu)
        updateActionModeMenu(menu)
        return true
    }

    fun updateActionModeMenu(menu: Menu) {
        // Prepare
        val selectedItems = adapter.selectedItems
        val containsControlMessage = selectedItems.any { it.isUpdate }
        val hasText = selectedItems.any { it.body.isNotEmpty() }
        if (selectedItems.isEmpty()) { return }
        val firstMessage = selectedItems.iterator().next()
        val openGroup = DatabaseComponent.get(context).lokiThreadDatabase().getOpenGroupChat(threadID)
        val thread = DatabaseComponent.get(context).threadDatabase().getRecipientForThreadId(threadID)!!
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
        val edKeyPair = MessagingModuleConfiguration.shared.getUserED25519KeyPair()!!
        val blindedPublicKey = openGroup?.publicKey?.let { SodiumUtilities.blindedKeyPair(it, edKeyPair)?.publicKey?.asBytes }
            ?.let { SessionId(IdPrefix.BLINDED, it) }?.hexString
        fun userCanDeleteSelectedItems(): Boolean {
            val allSentByCurrentUser = selectedItems.all { it.isOutgoing }
            val allReceivedByCurrentUser = selectedItems.all { !it.isOutgoing }
            if (openGroup == null) { return allSentByCurrentUser || allReceivedByCurrentUser }
            if (allSentByCurrentUser) { return true }
            return OpenGroupManager.isUserModerator(context, openGroup.groupId, userPublicKey, blindedPublicKey)
        }
        fun userCanBanSelectedUsers(): Boolean {
            if (openGroup == null) { return false }
            val anySentByCurrentUser = selectedItems.any { it.isOutgoing }
            if (anySentByCurrentUser) { return false } // Users can't ban themselves
            val selectedUsers = selectedItems.map { it.recipient.address.toString() }.toSet()
            if (selectedUsers.size > 1) { return false }
            return OpenGroupManager.isUserModerator(context, openGroup.groupId, userPublicKey, blindedPublicKey)
        }
        // Delete message
        menu.findItem(R.id.menu_context_delete_message).isVisible = userCanDeleteSelectedItems()
        // Ban user
        menu.findItem(R.id.menu_context_ban_user).isVisible = userCanBanSelectedUsers()
        // Ban and delete all
        menu.findItem(R.id.menu_context_ban_and_delete_all).isVisible = userCanBanSelectedUsers()
        // Copy message text
        menu.findItem(R.id.menu_context_copy).isVisible = !containsControlMessage && hasText
        // Copy Session ID
        menu.findItem(R.id.menu_context_copy_public_key).isVisible =
            (thread.isGroupRecipient && !thread.isOpenGroupRecipient && selectedItems.size == 1 && firstMessage.recipient.address.toString() != userPublicKey)
        // Message detail
        menu.findItem(R.id.menu_message_details).isVisible = (selectedItems.size == 1 && firstMessage.isFailed)
        // Resend
        menu.findItem(R.id.menu_context_resend).isVisible = (selectedItems.size == 1 && firstMessage.isFailed)
        // Save media
        menu.findItem(R.id.menu_context_save_attachment).isVisible = (selectedItems.size == 1
            && firstMessage.isMms && (firstMessage as MediaMmsMessageRecord).containsMediaSlide())
        // Reply
        menu.findItem(R.id.menu_context_reply).isVisible =
            (selectedItems.size == 1 && !firstMessage.isPending && !firstMessage.isFailed)
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu): Boolean {
        return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val selectedItems = adapter.selectedItems
        when (item.itemId) {
            R.id.menu_context_delete_message -> delegate?.deleteMessages(selectedItems)
            R.id.menu_context_ban_user -> delegate?.banUser(selectedItems)
            R.id.menu_context_ban_and_delete_all -> delegate?.banAndDeleteAll(selectedItems)
            R.id.menu_context_copy -> delegate?.copyMessages(selectedItems)
            R.id.menu_context_copy_public_key -> delegate?.copySessionID(selectedItems)
            R.id.menu_context_resend -> delegate?.resendMessage(selectedItems)
            R.id.menu_message_details -> delegate?.showMessageDetail(selectedItems)
            R.id.menu_context_save_attachment -> delegate?.saveAttachment(selectedItems)
            R.id.menu_context_reply -> delegate?.reply(selectedItems)
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        adapter.selectedItems.clear()
        adapter.notifyDataSetChanged()
    }
}

interface ConversationActionModeCallbackDelegate {

    fun selectMessages(messages: Set<MessageRecord>)
    fun deleteMessages(messages: Set<MessageRecord>)
    fun banUser(messages: Set<MessageRecord>)
    fun banAndDeleteAll(messages: Set<MessageRecord>)
    fun copyMessages(messages: Set<MessageRecord>)
    fun copySessionID(messages: Set<MessageRecord>)
    fun resendMessage(messages: Set<MessageRecord>)
    fun showMessageDetail(messages: Set<MessageRecord>)
    fun saveAttachment(messages: Set<MessageRecord>)
    fun reply(messages: Set<MessageRecord>)
}