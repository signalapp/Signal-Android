package org.session.libsession.messaging.utilities

import android.content.Context
import org.session.libsession.R
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsignal.service.api.messages.SignalServiceGroup
import org.session.libsignal.service.internal.push.SignalServiceProtos

object UpdateMessageBuilder {

    fun buildGroupUpdateMessage(context: Context, groupInfo: SignalServiceGroup, sender: String, isOutgoing: Boolean = false): String {
        val updateType = groupInfo.type
        val senderName: String = if (!isOutgoing) {
            MessagingConfiguration.shared.storage.getDisplayNameForRecipient(sender) ?: sender
        } else { sender }
        var message: String = ""
        when (updateType) {
            SignalServiceGroup.Type.NEW_GROUP -> {
                message = if (isOutgoing) {
                    context.getString(R.string.MessageRecord_you_created_a_new_group)
                } else {
                    context.getString(R.string.MessageRecord_s_added_you_to_the_group, senderName)
                }
            }
            SignalServiceGroup.Type.NAME_CHANGE -> {
                message = if (isOutgoing) {
                    context.getString(R.string.MessageRecord_you_renamed_the_group_to_s, groupInfo.name.get())
                } else {
                    context.getString(R.string.MessageRecord_s_renamed_the_group_to_s, senderName, groupInfo.name.get())
                }
            }
            SignalServiceGroup.Type.MEMBER_ADDED -> {
                val members = groupInfo.members.get().joinToString(", ") {
                    MessagingConfiguration.shared.storage.getDisplayNameForRecipient(it) ?: it
                }
                message = if (isOutgoing) {
                    context.getString(R.string.MessageRecord_you_added_s_to_the_group, members)
                } else {
                    context.getString(R.string.MessageRecord_s_added_s_to_the_group, senderName, members)
                }
            }
            SignalServiceGroup.Type.MEMBER_REMOVED -> {
                val members = groupInfo.members.get().joinToString(", ") {
                    MessagingConfiguration.shared.storage.getDisplayNameForRecipient(it) ?: it
                }
                message = if (isOutgoing) {
                    context.getString(R.string.MessageRecord_you_removed_s_from_the_group, members)
                } else {
                    context.getString(R.string.MessageRecord_s_removed_s_from_the_group, senderName, members)
                }
            }
            SignalServiceGroup.Type.QUIT -> {
                message = if (isOutgoing) {
                    context.getString(R.string.MessageRecord_left_group)
                } else {
                    context.getString(R.string.ConversationItem_group_action_left, senderName)
                }
            }
            else -> {
                message = context.getString(R.string.MessageRecord_s_updated_group, senderName)
            }
        }
        return message
    }

    fun buildExpirationTimerMessage(): String {
        return ""
    }

    fun buildDataExtractionMessage(): String {
        return ""
    }

}