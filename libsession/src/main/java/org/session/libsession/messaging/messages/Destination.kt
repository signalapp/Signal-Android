package org.session.libsession.messaging.messages

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.threads.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.service.loki.utilities.toHexString

typealias OpenGroupModel = org.session.libsession.messaging.opengroups.OpenGroup
typealias OpenGroupV2Model = org.session.libsession.messaging.opengroups.OpenGroupV2

sealed class Destination {

    class Contact(var publicKey: String) : Destination() {
        internal constructor(): this("")
    }
    class ClosedGroup(var groupPublicKey: String) : Destination() {
        internal constructor(): this("")
    }
    class OpenGroup(var channel: Long, var server: String) : Destination() {
        internal constructor(): this(0, "")
    }
    class OpenGroupV2(var room: String, var server: String): Destination() {
        internal constructor(): this("", "")
    }

    companion object {
        fun from(address: Address): Destination {
            return when {
                address.isContact -> {
                    Contact(address.contactIdentifier())
                }
                address.isClosedGroup -> {
                    val groupID = address.toGroupString()
                    val groupPublicKey = GroupUtil.doubleDecodeGroupID(groupID).toHexString()
                    ClosedGroup(groupPublicKey)
                }
                address.isOpenGroup -> {
                    val storage = MessagingConfiguration.shared.storage
                    val threadID = storage.getThreadID(address.contactIdentifier())!!
                    when (val openGroup = storage.getOpenGroup(threadID) ?: storage.getV2OpenGroup(threadID)) {
                        is OpenGroupModel -> OpenGroup(openGroup.channel, openGroup.server)
                        is OpenGroupV2Model -> OpenGroupV2(openGroup.room, openGroup.server)
                        else -> throw Exception("Invalid OpenGroup $openGroup")
                    }
                }
                else -> {
                    throw Exception("TODO: Handle legacy closed groups.")
                }
            }
        }
    }
}