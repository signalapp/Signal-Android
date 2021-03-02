package org.session.libsession.messaging.messages

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.threads.Address
import org.session.libsession.utilities.GroupUtil

sealed class Destination {

    class Contact(val publicKey: String) : Destination()
    class ClosedGroup(val groupPublicKey: String) : Destination()
    class OpenGroup(val channel: Long, val server: String) : Destination()

    companion object {
        fun from(address: Address): Destination {
            if (address.isContact) {
                return Contact(address.contactIdentifier())
            } else if (address.isClosedGroup) {
                val groupID = address.contactIdentifier()
                val groupPublicKey = GroupUtil.getDecodedGroupID(groupID)
                return ClosedGroup(groupPublicKey)
            } else if (address.isOpenGroup) {
                val threadID = MessagingConfiguration.shared.storage.getThreadID(address.contactIdentifier())!!
                val openGroup = MessagingConfiguration.shared.storage.getOpenGroup(threadID)!!
                return OpenGroup(openGroup.channel, openGroup.server)
            } else {
                throw Exception("TODO: Handle legacy closed groups.")
            }
        }
    }
}