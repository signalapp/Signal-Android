package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsession.messaging.threads.recipients.RecipientModifiedListener
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Base64
import org.session.libsignal.service.internal.push.SignalServiceProtos
import java.util.*

import network.loki.messenger.R
import org.session.libsignal.utilities.logging.Log
import java.io.IOException

class GroupDescription(context: Context, groupContext: SignalServiceProtos.GroupContext?) {
    private val context: Context
    private val groupContext: SignalServiceProtos.GroupContext?
    private val newMembers: MutableList<Recipient>
    private val removedMembers: MutableList<Recipient>
    private var wasCurrentUserRemoved: Boolean
    private fun toRecipient(hexEncodedPublicKey: String): Recipient {
        val address = Address.fromSerialized(hexEncodedPublicKey)
        return Recipient.from(context, address, false)
    }

    fun toString(sender: Recipient): String {
        if (wasCurrentUserRemoved) {
            return context.getString(R.string.GroupUtil_you_were_removed_from_group)
        }
        val description = StringBuilder()
        description.append(context.getString(R.string.MessageRecord_s_updated_group, sender.toShortString()))
        if (groupContext == null) {
            return description.toString()
        }
        val title = groupContext.name
        if (!newMembers.isEmpty()) {
            description.append("\n")
            description.append(context.resources.getQuantityString(R.plurals.GroupUtil_joined_the_group,
                    newMembers.size, toString(newMembers)))
        }
        if (!removedMembers.isEmpty()) {
            description.append("\n")
            description.append(context.resources.getQuantityString(R.plurals.GroupUtil_removed_from_the_group,
                    removedMembers.size, toString(removedMembers)))
        }
        if (title != null && !title.trim { it <= ' ' }.isEmpty()) {
            val separator = if (!newMembers.isEmpty() || !removedMembers.isEmpty()) " " else "\n"
            description.append(separator)
            description.append(context.getString(R.string.GroupUtil_group_name_is_now, title))
        }
        return description.toString()
    }

    fun addListener(listener: RecipientModifiedListener?) {
        if (!newMembers.isEmpty()) {
            for (member in newMembers) {
                member.addListener(listener)
            }
        }
    }

    private fun toString(recipients: List<Recipient>): String {
        var result = ""
        for (i in recipients.indices) {
            result += recipients[i].toShortString()
            if (i != recipients.size - 1) result += ", "
        }
        return result
    }

    init {
        this.context = context.applicationContext
        this.groupContext = groupContext
        newMembers = LinkedList()
        removedMembers = LinkedList()
        wasCurrentUserRemoved = false
        if (groupContext != null) {
            val newMembers = groupContext.newMembersList
            for (member in newMembers) {
                this.newMembers.add(toRecipient(member))
            }
            val removedMembers = groupContext.removedMembersList
            for (member in removedMembers) {
                this.removedMembers.add(toRecipient(member))
            }

            // If we were the one that quit then we need to leave the group (only relevant for slave
            // devices in a multi device context)
            if (!removedMembers.isEmpty()) {
                val masterPublicKeyOrNull = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
                val masterPublicKey = masterPublicKeyOrNull
                        ?: TextSecurePreferences.getLocalNumber(context)
                wasCurrentUserRemoved = removedMembers.contains(masterPublicKey)
            }
        }
    }

    companion object {
        fun getDescription(context: Context, encodedGroup: String?): GroupDescription {
            return if (encodedGroup == null) {
                GroupDescription(context, null)
            } else try {
                val groupContext = SignalServiceProtos.GroupContext.parseFrom(Base64.decode(encodedGroup))
                GroupDescription(context, groupContext)
            } catch (e: IOException) {
                Log.w("Loki", e)
                GroupDescription(context, null)
            }
        }
    }
}