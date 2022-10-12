package org.thoughtcrime.securesms.conversation.v2.menus

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.AsyncTask
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import network.loki.messenger.R
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.sending_receiving.leave
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.GroupUtil.doubleDecodeGroupID
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.getColorFromAttr
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.guava.Optional
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.MediaOverviewActivity
import org.thoughtcrime.securesms.MuteDialog
import org.thoughtcrime.securesms.ShortcutLauncherActivity
import org.thoughtcrime.securesms.calls.WebRtcCallActivity
import org.thoughtcrime.securesms.contacts.SelectContactsActivity
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.conversation.v2.utilities.NotificationUtils
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.groups.EditClosedGroupActivity
import org.thoughtcrime.securesms.groups.EditClosedGroupActivity.Companion.groupIDKey
import org.thoughtcrime.securesms.preferences.PrivacySettingsActivity
import org.thoughtcrime.securesms.service.WebRtcCallService
import org.thoughtcrime.securesms.util.BitmapUtil
import java.io.IOException

object ConversationMenuHelper {
    
    fun onPrepareOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
        thread: Recipient,
        threadId: Long,
        context: Context,
        onOptionsItemSelected: (MenuItem) -> Unit
    ) {
        // Prepare
        menu.clear()
        val isOpenGroup = thread.isOpenGroupRecipient
        // Base menu (options that should always be present)
        inflater.inflate(R.menu.menu_conversation, menu)
        // Expiring messages
        if (!isOpenGroup && (thread.hasApprovedMe() || thread.isClosedGroupRecipient)) {
            if (thread.expireMessages > 0) {
                inflater.inflate(R.menu.menu_conversation_expiration_on, menu)
                val item = menu.findItem(R.id.menu_expiring_messages)
                val actionView = item.actionView
                val iconView = actionView.findViewById<ImageView>(R.id.menu_badge_icon)
                val badgeView = actionView.findViewById<TextView>(R.id.expiration_badge)
                @ColorInt val color = context.getColorFromAttr(android.R.attr.textColorPrimary)
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

        if (thread.isGroupRecipient && !thread.isMuted) {
            inflater.inflate(R.menu.menu_conversation_notification_settings, menu)
        }

        if (!thread.isGroupRecipient && thread.hasApprovedMe()) {
            inflater.inflate(R.menu.menu_conversation_call, menu)
        }

        // Search
        val searchViewItem = menu.findItem(R.id.menu_search)
        (context as ConversationActivityV2).searchViewItem = searchViewItem
        val searchView = searchViewItem.actionView as SearchView
        val queryListener = object : OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                context.onSearchQueryUpdated(query)
                return true
            }
        }
        searchViewItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                searchView.setOnQueryTextListener(queryListener)
                context.onSearchOpened()
                for (i in 0 until menu.size()) {
                    if (menu.getItem(i) != searchViewItem) {
                        menu.getItem(i).isVisible = false
                    }
                }
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                searchView.setOnQueryTextListener(null)
                context.onSearchClosed()
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
            R.id.menu_block -> { block(context, thread, deleteThread = false) }
            R.id.menu_block_delete -> { blockAndDelete(context, thread) }
            R.id.menu_copy_session_id -> { copySessionID(context, thread) }
            R.id.menu_edit_group -> { editClosedGroup(context, thread) }
            R.id.menu_leave_group -> { leaveClosedGroup(context, thread) }
            R.id.menu_invite_to_open_group -> { inviteContacts(context, thread) }
            R.id.menu_unmute_notifications -> { unmute(context, thread) }
            R.id.menu_mute_notifications -> { mute(context, thread) }
            R.id.menu_notification_settings -> { setNotifyType(context, thread) }
            R.id.menu_call -> { call(context, thread) }
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
        val searchViewModel = (context as ConversationActivityV2).searchViewModel
        searchViewModel.onSearchOpened()
    }

    private fun call(context: Context, thread: Recipient) {

        if (!TextSecurePreferences.isCallNotificationsEnabled(context)) {
            AlertDialog.Builder(context)
                .setTitle(R.string.ConversationActivity_call_title)
                .setMessage(R.string.ConversationActivity_call_prompt)
                .setPositiveButton(R.string.activity_settings_title) { _, _ ->
                    val intent = Intent(context, PrivacySettingsActivity::class.java)
                    context.startActivity(intent)
                }
                .setNeutralButton(R.string.cancel) { d, _ ->
                    d.dismiss()
                }.show()
            return
        }

        val service = WebRtcCallService.createCall(context, thread)
        context.startService(service)

        val activity = Intent(context, WebRtcCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(activity)

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
        val listener = context as? ConversationMenuListener ?: return
        listener.showExpiringMessagesDialog(thread)
    }

    private fun unblock(context: Context, thread: Recipient) {
        if (!thread.isContactRecipient) { return }
        val listener = context as? ConversationMenuListener ?: return
        listener.unblock()
    }

    private fun block(context: Context, thread: Recipient, deleteThread: Boolean) {
        if (!thread.isContactRecipient) { return }
        val listener = context as? ConversationMenuListener ?: return
        listener.block(deleteThread)
    }

    private fun blockAndDelete(context: Context, thread: Recipient) {
        if (!thread.isContactRecipient) { return }
        val listener = context as? ConversationMenuListener ?: return
        listener.block(deleteThread = true)
    }

    private fun copySessionID(context: Context, thread: Recipient) {
        if (!thread.isContactRecipient) { return }
        val listener = context as? ConversationMenuListener ?: return
        listener.copySessionID(thread.address.toString())
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
        val group = DatabaseComponent.get(context).groupDatabase().getGroup(thread.address.toGroupString()).orNull()
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
                isClosedGroup = DatabaseComponent.get(context).lokiAPIDatabase().isClosedGroup(groupPublicKey)
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
        DatabaseComponent.get(context).recipientDatabase().setMuted(thread, 0)
    }

    private fun mute(context: Context, thread: Recipient) {
        MuteDialog.show(ContextThemeWrapper(context, context.theme)) { until: Long ->
            DatabaseComponent.get(context).recipientDatabase().setMuted(thread, until)
        }
    }

    private fun setNotifyType(context: Context, thread: Recipient) {
        NotificationUtils.showNotifyDialog(context, thread) { notifyType ->
            DatabaseComponent.get(context).recipientDatabase().setNotifyType(thread, notifyType)
        }
    }

    interface ConversationMenuListener {
        fun block(deleteThread: Boolean = false)
        fun unblock()
        fun copySessionID(sessionId: String)
        fun showExpiringMessagesDialog(thread: Recipient)
    }

}