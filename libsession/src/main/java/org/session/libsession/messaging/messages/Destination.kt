package org.session.libsession.messaging.messages

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.threads.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.service.loki.utilities.toHexString

sealed class Destination {

    class Contact() : Destination() {
        var publicKey: String = ""
        internal constructor(publicKey: String): this() {
            this.publicKey = publicKey
        }
    }
    class ClosedGroup() : Destination() {
        var groupPublicKey: String = ""
        internal constructor(groupPublicKey: String): this() {
            this.groupPublicKey = groupPublicKey
        }
    }
    class OpenGroup() : Destination() {
        var channel: Long = 0
        var server: String = ""
        internal constructor(channel: Long, server: String): this() {
            this.channel = channel
            this.server = server
        }
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
                    val threadID = MessagingConfiguration.shared.storage.getThreadID(address.contactIdentifier())!!
                    val openGroup = MessagingConfiguration.shared.storage.getOpenGroup(threadID)!!
                    OpenGroup(openGroup.channel, openGroup.server)
                }
                else -> {
                    throw Exception("TODO: Handle legacy closed groups.")
                }
            }
        }
    }
}