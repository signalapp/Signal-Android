package org.session.libsession.messaging.sending_receiving

import org.session.libsession.messaging.Configuration
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.ClosedGroupUpdate
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.messages.control.ReadReceipt
import org.session.libsession.messaging.messages.control.TypingIndicator
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationAPI
import org.session.libsession.utilities.LKGroupUtilities
import org.session.libsignal.libsignal.util.Hex
import org.session.libsignal.service.api.messages.SignalServiceGroup

import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupRatchet
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupRatchetCollectionType
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupSenderKey
import org.session.libsignal.service.loki.protocol.closedgroups.SharedSenderKeysImplementation
import org.session.libsignal.service.loki.utilities.toHexString
import java.util.*

internal fun MessageReceiver.isBlock(publicKey: String): Boolean {
    // TODO: move isBlocked from Recipient to BlockManager
    return false
}

fun MessageReceiver.handle(message: Message, proto: SignalServiceProtos.Content, openGroupID: String?) {
    when (message) {
        is ReadReceipt -> handleReadReceipt(message)
        is TypingIndicator -> handleTypingIndicator(message)
        is ClosedGroupUpdate -> handleClosedGroupUpdate(message)
        is ExpirationTimerUpdate -> handleExpirationTimerUpdate(message)
        is VisibleMessage -> handleVisibleMessage(message, proto, openGroupID)
    }
}

private fun MessageReceiver.handleReadReceipt(message: ReadReceipt) {

}

private fun MessageReceiver.handleTypingIndicator(message: TypingIndicator) {
    when (message.kind!!) {
        TypingIndicator.Kind.STARTED -> showTypingIndicatorIfNeeded(message.sender!!)
        TypingIndicator.Kind.STOPPED -> hideTypingIndicatorIfNeeded(message.sender!!)
    }
}

fun MessageReceiver.showTypingIndicatorIfNeeded(senderPublicKey: String) {

}

fun MessageReceiver.hideTypingIndicatorIfNeeded(senderPublicKey: String) {

}

fun MessageReceiver.cancelTypingIndicatorsIfNeeded(senderPublicKey: String) {

}

private fun MessageReceiver.handleExpirationTimerUpdate(message: ExpirationTimerUpdate) {
    if (message.duration!! > 0) {
        setExpirationTimer(message.duration!!, message.sender!!, message.groupPublicKey)
    } else {
        disableExpirationTimer(message.sender!!, message.groupPublicKey)
    }
}

fun MessageReceiver.setExpirationTimer(duration: Int, senderPublicKey: String, groupPublicKey: String?) {

}

fun MessageReceiver.disableExpirationTimer(senderPublicKey: String, groupPublicKey: String?) {

}

fun MessageReceiver.handleVisibleMessage(message: VisibleMessage, proto: SignalServiceProtos.Content, openGroupID: String?) {

}

private fun MessageReceiver.handleClosedGroupUpdate(message: ClosedGroupUpdate) {
    when (message.kind!!) {
        is ClosedGroupUpdate.Kind.New -> handleNewGroup(message)
        is ClosedGroupUpdate.Kind.Info -> handleGroupUpdate(message)
        is ClosedGroupUpdate.Kind.SenderKeyRequest -> handleSenderKeyRequest(message)
        is ClosedGroupUpdate.Kind.SenderKey -> handleSenderKey(message)
    }
}

private fun MessageReceiver.handleNewGroup(message: ClosedGroupUpdate) {
    val storage = Configuration.shared.storage
    val sskDatabase = Configuration.shared.sskDatabase
    val kind = message.kind!! as ClosedGroupUpdate.Kind.New
    val groupPublicKey = kind.groupPublicKey.toHexString()
    val name = kind.name
    val groupPrivateKey = kind.groupPrivateKey
    val senderKeys = kind.senderKeys
    val members = kind.members.map { it.toHexString() }
    val admins = kind.admins.map { it.toHexString() }
    // Persist the ratchets
    senderKeys.forEach { senderKey ->
        if (!members.contains(senderKey.publicKey.toHexString())) { return@forEach }
        val ratchet = ClosedGroupRatchet(senderKey.chainKey.toHexString(), senderKey.keyIndex, listOf())
        sskDatabase.setClosedGroupRatchet(groupPublicKey, senderKey.publicKey.toHexString(), ratchet, ClosedGroupRatchetCollectionType.Current)
    }
    // Sort out any discrepancies between the provided sender keys and what's required
    val missingSenderKeys = members.toSet().subtract(senderKeys.map { Hex.toStringCondensed(it.publicKey) })
    val userPublicKey = storage.getUserPublicKey()!!
    if (missingSenderKeys.contains(userPublicKey)) {
        val userRatchet = SharedSenderKeysImplementation.shared.generateRatchet(groupPublicKey, userPublicKey)
        val userSenderKey = ClosedGroupSenderKey(Hex.fromStringCondensed(userRatchet.chainKey), userRatchet.keyIndex, Hex.fromStringCondensed(userPublicKey))
        members.forEach { member ->
            if (member == userPublicKey) return@forEach
            val closedGroupUpdateKind = ClosedGroupUpdate.Kind.SenderKey(groupPublicKey.toByteArray(), userSenderKey)
            val closedGroupUpdate = ClosedGroupUpdate()
            closedGroupUpdate.kind = closedGroupUpdateKind
            MessageSender.send(closedGroupUpdate, Destination.ClosedGroup(groupPublicKey))
        }
    }
    missingSenderKeys.minus(userPublicKey).forEach { publicKey ->
        MessageSender.requestSenderKey(groupPublicKey, publicKey)
    }
    // Create the group
    val groupID = LKGroupUtilities.getEncodedClosedGroupIDAsData(groupPublicKey)
    val groupDB = DatabaseFactory.getGroupDatabase(context)
    if (groupDB.getGroup(groupID).orNull() != null) {
        // Update the group
        groupDB.updateTitle(groupID, name)
        groupDB.updateMembers(groupID, members.map { Address.fromSerialized(it) })
    } else {
        groupDB.create(groupID, name, LinkedList<Address>(members.map { Address.fromSerialized(it) }),
                null, null, LinkedList<Address>(admins.map { Address.fromSerialized(it) }))
    }
    DatabaseFactory.getRecipientDatabase(context).setProfileSharing(Recipient.from(context, Address.fromSerialized(groupID), false), true)
    // Add the group to the user's set of public keys to poll for
    sskDatabase.setClosedGroupPrivateKey(groupPublicKey, groupPrivateKey.toHexString())
    // Notify the PN server
    PushNotificationAPI.performOperation(context, ClosedGroupOperation.Subscribe, groupPublicKey, userPublicKey)
    // Notify the user
    insertIncomingInfoMessage(context, senderPublicKey, groupID, SignalServiceProtos.GroupContext.Type.UPDATE, SignalServiceGroup.Type.UPDATE, name, members, admins)
    // Establish sessions if needed
    establishSessionsWithMembersIfNeeded(context, members)


}

private fun MessageReceiver.handleGroupUpdate(message: ClosedGroupUpdate) {

}

private fun MessageReceiver.handleSenderKeyRequest(message: ClosedGroupUpdate) {

}

private fun MessageReceiver.handleSenderKey(message: ClosedGroupUpdate) {

}