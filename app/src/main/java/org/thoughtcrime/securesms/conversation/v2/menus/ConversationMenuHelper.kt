package org.thoughtcrime.securesms.conversation.v2.menus

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.AsyncTask
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.android.synthetic.main.activity_conversation_v2.*
import network.loki.messenger.R
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.sending_receiving.leave
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.GroupUtil.doubleDecodeGroupID
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.guava.Optional
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.*
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.activities.EditClosedGroupActivity
import org.thoughtcrime.securesms.loki.activities.EditClosedGroupActivity.Companion.groupIDKey
import org.thoughtcrime.securesms.loki.activities.SelectContactsActivity
import org.thoughtcrime.securesms.loki.utilities.getColorWithID
import org.thoughtcrime.securesms.util.BitmapUtil
import java.io.IOException

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
        (context as ConversationActivityV2).searchViewItem = searchViewItem
        val searchView = searchViewItem.actionView as SearchView
        val searchViewModel = context.searchViewModel!!
        val queryListener = object : OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
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

    fun onOptionItemSelected(context: Context, item: MenuItem, thread: Recipient): Boolean {
        when (item.itemId) {
            R.id.menu_view_all_media -> { showAllMedia(context, thread) }
            R.id.menu_search -> { search(context) }
            R.id.menu_add_shortcut -> { addShortcut(context, thread) }
            R.id.menu_expiring_messages -> { showExpiringMessagesDialog(context, thread) }
            R.id.menu_expiring_messages_off -> { showExpiringMessagesDialog(context, thread) }
            R.id.menu_unblock -> { unblock(context, thread) }
            R.id.menu_block -> { block(context, thread) }
            R.id.menu_copy_session_id -> { copySessionID(context, thread) }
            R.id.menu_edit_group -> { editClosedGroup(context, thread) }
            R.id.menu_leave_group -> { leaveClosedGroup(context, thread) }
            R.id.menu_invite_to_open_group -> { inviteContacts(context, thread) }
            R.id.menu_unmute_notifications -> { unmute(context, thread) }
            R.id.menu_mute_notifications -> { mute(context, thread) }
        }
        return true
    }

    private fun showAllMedia(context: Context, thread: Recipient) {
        val intent = Intent(context, MediaOverviewActivity::class.java)
        intent.putExtra(MediaOverviewActivity.ADDRESS_EXTRA, thread.address)
        val activity = context as AppCompatActivity
        activity.startActivity(intent)
    }

    private fun search(context: Context) {
        val searchViewModel = (context as ConversationActivityV2).searchViewModel!!
        searchViewModel.onSearchOpened()
    }

    @SuppressLint("StaticFieldLeak")
    private fun addShortcut(context: Context, thread: Recipient) {
        object : AsyncTask<Void?, Void?, IconCompat?>() {

            override fun doInBackground(vararg params: Void?): IconCompat? {
                var icon: IconCompat? = null
                val contactPhoto = thread.contactPhoto
                if (contactPhoto != null) {
                    try {
                        var bitmap = BitmapFactory.decodeStream(contactPhoto.openInputStream(context))
                        bitmap = BitmapUtil.createScaledBitmap(bitmap, 300, 300)
                        icon = IconCompat.createWithAdaptiveBitmap(bitmap)
                    } catch (e: IOException) {
                        // Do nothing
                    }
                }
                if (icon == null) {
                    icon = IconCompat.createWithResource(context, if (thread.isGroupRecipient) R.mipmap.ic_group_shortcut else R.mipmap.ic_person_shortcut)
                }
                return icon
            }

            override fun onPostExecute(icon: IconCompat?) {
                val name = Optional.fromNullable<String>(thread.name)
                    .or(Optional.fromNullable<String>(thread.profileName))
                    .or(thread.toShortString())
                val shortcutInfo = ShortcutInfoCompat.Builder(context, thread.address.serialize() + '-' + System.currentTimeMillis())
                    .setShortLabel(name)
                    .setIcon(icon)
                    .setIntent(ShortcutLauncherActivity.createIntent(context, thread.address))
                    .build()
                if (ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)) {
                    Toast.makeText(context, context.resources.getString(R.string.ConversationActivity_added_to_home_screen), Toast.LENGTH_LONG).show()
                }
            }
        }.execute()
    }

    private fun showExpiringMessagesDialog(context: Context, thread: Recipient) {
        if (thread.isClosedGroupRecipient) {
            val group = DatabaseFactory.getGroupDatabase(context).getGroup(thread.address.toGroupString()).orNull()
            if (group?.isActive == false) { return }
        }
        ExpirationDialog.show(context, thread.expireMessages) { expirationTime: Int ->
            DatabaseFactory.getRecipientDatabase(context).setExpireMessages(thread, expirationTime)
            val message = ExpirationTimerUpdate(expirationTime)
            message.recipient = thread.address.serialize()
            message.sentTimestamp = System.currentTimeMillis()
            val expiringMessageManager = ApplicationContext.getInstance(context).expiringMessageManager
            expiringMessageManager.setExpirationTimer(message)
            MessageSender.send(message, thread.address)
            val activity = context as AppCompatActivity
            activity.invalidateOptionsMenu()
        }
    }

    private fun unblock(context: Context, thread: Recipient) {
        if (!thread.isContactRecipient) { return }
        val title = R.string.ConversationActivity_unblock_this_contact_question
        val message = R.string.ConversationActivity_you_will_once_again_be_able_to_receive_messages_and_calls_from_this_contact
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ConversationActivity_unblock) { _, _ ->
                DatabaseFactory.getRecipientDatabase(context)
                    .setBlocked(thread, false)
            }.show()
    }

    private fun block(context: Context, thread: Recipient) {
        if (!thread.isContactRecipient) { return }
        val title = R.string.RecipientPreferenceActivity_block_this_contact_question
        val message = R.string.RecipientPreferenceActivity_you_will_no_longer_receive_messages_and_calls_from_this_contact
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.RecipientPreferenceActivity_block) { _, _ ->
                DatabaseFactory.getRecipientDatabase(context)
                    .setBlocked(thread, true)
            }.show()
    }

    private fun copySessionID(context: Context, thread: Recipient) {
        if (!thread.isContactRecipient) { return }
        val sessionID = thread.address.toString()
        val clip = ClipData.newPlainText("Session ID", sessionID)
        val activity = context as AppCompatActivity
        val manager = activity.getSystemService(PassphraseRequiredActionBarActivity.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(clip)
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun editClosedGroup(context: Context, thread: Recipient) {
        if (!thread.isClosedGroupRecipient) { return }
        val intent = Intent(context, EditClosedGroupActivity::class.java)
        val groupID: String = thread.address.toGroupString()
        intent.putExtra(groupIDKey, groupID)
        context.startActivity(intent)
    }

    private fun leaveClosedGroup(context: Context, thread: Recipient) {
        if (!thread.isClosedGroupRecipient) { return }
        val builder = AlertDialog.Builder(context)
        builder.setTitle(context.resources.getString(R.string.ConversationActivity_leave_group))
        builder.setCancelable(true)
        val group = DatabaseFactory.getGroupDatabase(context).getGroup(thread.address.toGroupString()).orNull()
        val admins = group.admins
        val sessionID = TextSecurePreferences.getLocalNumber(context)
        val isCurrentUserAdmin = admins.any { it.toString() == sessionID }
        val message = if (isCurrentUserAdmin) {
            "Because you are the creator of this group it will be deleted for everyone. This cannot be undone."
        } else {
            context.resources.getString(R.string.ConversationActivity_are_you_sure_you_want_to_leave_this_group)
        }
        builder.setMessage(message)
        builder.setPositiveButton(R.string.yes) { _, _ ->
            var groupPublicKey: String?
            var isClosedGroup: Boolean
            try {
                groupPublicKey = doubleDecodeGroupID(thread.address.toString()).toHexString()
                isClosedGroup = DatabaseFactory.getLokiAPIDatabase(context).isClosedGroup(groupPublicKey)
            } catch (e: IOException) {
                groupPublicKey = null
                isClosedGroup = false
            }
            try {
                if (isClosedGroup) {
                    MessageSender.leave(groupPublicKey!!, true)
                } else {
                    Toast.makeText(context, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show()
            }
        }
        builder.setNegativeButton(R.string.no, null)
        builder.show()
    }

    private fun inviteContacts(context: Context, thread: Recipient) {
        if (!thread.isOpenGroupRecipient) { return }
        val intent = Intent(context, SelectContactsActivity::class.java)
        val activity = context as AppCompatActivity
        activity.startActivityForResult(intent, ConversationActivityV2.INVITE_CONTACTS)
    }

    private fun unmute(context: Context, thread: Recipient) {
        DatabaseFactory.getRecipientDatabase(context).setMuted(thread, 0)
    }

    private fun mute(context: Context, thread: Recipient) {
        MuteDialog.show(context) { until: Long ->
            DatabaseFactory.getRecipientDatabase(context).setMuted(thread, until)
        }
    }
}