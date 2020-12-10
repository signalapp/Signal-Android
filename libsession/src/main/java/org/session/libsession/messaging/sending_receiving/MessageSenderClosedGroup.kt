@file:Suppress("NAME_SHADOWING")

package org.session.libsession.messaging.sending_receiving

import android.util.Log
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.messages.control.ClosedGroupUpdate
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationAPI
import org.session.libsession.messaging.sending_receiving.MessageSender.Error
import org.session.libsession.messaging.threads.Address
import org.session.libsession.utilities.GroupUtil


import org.session.libsignal.libsignal.ecc.Curve
import org.session.libsignal.libsignal.util.Hex
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupRatchetCollectionType
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupSenderKey
import org.session.libsignal.service.loki.protocol.closedgroups.SharedSenderKeysImplementation
import org.session.libsignal.service.loki.utilities.hexEncodedPrivateKey
import org.session.libsignal.service.loki.utilities.hexEncodedPublicKey
import java.util.*

fun MessageSender.createClosedGroup(name: String, members: Collection<String>): Promise<String, Exception> {
    val deferred = deferred<String, Exception>()
    // Prepare
    val storage = MessagingConfiguration.shared.storage
    val members = members
    val userPublicKey = storage.getUserPublicKey()!!
    // Generate a key pair for the group
    val groupKeyPair = Curve.generateKeyPair()
    val groupPublicKey = groupKeyPair.hexEncodedPublicKey // Includes the "05" prefix
    members.plus(userPublicKey)
    val membersAsData = members.map { Hex.fromStringCondensed(it) }
    // Create ratchets for all members
    val senderKeys: List<ClosedGroupSenderKey> = members.map { publicKey ->
        val ratchet = SharedSenderKeysImplementation.shared.generateRatchet(groupPublicKey, publicKey)
        ClosedGroupSenderKey(Hex.fromStringCondensed(ratchet.chainKey), ratchet.keyIndex, Hex.fromStringCondensed(publicKey))
    }
    // Create the group
    val admins = setOf( userPublicKey )
    val adminsAsData = admins.map { Hex.fromStringCondensed(it) }
    val groupID = GroupUtil.getEncodedClosedGroupID(groupPublicKey)
    storage.createGroup(groupID, name, LinkedList(members.map { Address.fromSerialized(it) }), null, null, LinkedList(admins.map { Address.fromSerialized(it) }))
    storage.setProfileSharing(Address.fromSerialized(groupID), true)
    // Send a closed group update message to all members using established channels
    val promises = mutableListOf<Promise<Unit, Exception>>()
    for (member in members) {
        if (member == userPublicKey) { continue }
        val closedGroupUpdateKind = ClosedGroupUpdate.Kind.New(Hex.fromStringCondensed(groupPublicKey), name, groupKeyPair.privateKey.serialize(),
                senderKeys, membersAsData, adminsAsData)
        val closedGroupUpdate = ClosedGroupUpdate()
        closedGroupUpdate.kind = closedGroupUpdateKind
        val address = Address.fromSerialized(member)
        val promise = MessageSender.sendNonDurably(closedGroupUpdate, address)
        promises.add(promise)
    }
    // Add the group to the user's set of public keys to poll for
    MessagingConfiguration.shared.sskDatabase.setClosedGroupPrivateKey(groupPublicKey, groupKeyPair.hexEncodedPrivateKey)
    // Notify the PN server
    PushNotificationAPI.performOperation(PushNotificationAPI.ClosedGroupOperation.Subscribe, groupPublicKey, userPublicKey)
    // Notify the user
    val threadID =storage.getOrCreateThreadIdFor(Address.fromSerialized(groupID))
    /* TODO
    insertOutgoingInfoMessage(context, groupID, GroupContext.Type.UPDATE, name, members, admins, threadID)
     */
    // Fulfill the promise
    deferred.resolve(groupPublicKey)
    // Return
    return deferred.promise
}

fun MessageSender.update(groupPublicKey: String, members: Collection<String>, name: String): Promise<Unit, Exception> {
    val deferred = deferred<Unit, Exception>()
    val storage = MessagingConfiguration.shared.storage
    val userPublicKey = storage.getUserPublicKey()!!
    val sskDatabase = MessagingConfiguration.shared.sskDatabase
    val groupID = GroupUtil.getEncodedClosedGroupID(groupPublicKey)
    val group = storage.getGroup(groupID)
    if (group == null) {
        Log.d("Loki", "Can't update nonexistent closed group.")
        deferred.reject(Error.NoThread)
        return deferred.promise
    }
    val oldMembers = group.members.map { it.serialize() }.toSet()
    val newMembers = members.minus(oldMembers)
    val membersAsData = members.map { Hex.fromStringCondensed(it) }
    val admins = group.admins.map { it.serialize() }
    val adminsAsData = admins.map { Hex.fromStringCondensed(it) }
    val groupPrivateKey = sskDatabase.getClosedGroupPrivateKey(groupPublicKey)
    if (groupPrivateKey == null) {
        Log.d("Loki", "Couldn't get private key for closed group.")
        deferred.reject(Error.NoPrivateKey)
        return deferred.promise
    }
    val wasAnyUserRemoved = members.toSet().intersect(oldMembers) != oldMembers.toSet()
    val removedMembers = oldMembers.minus(members)
    val isUserLeaving = removedMembers.contains(userPublicKey)
    val newSenderKeys: List<ClosedGroupSenderKey>
    if (wasAnyUserRemoved) {
        if (isUserLeaving && removedMembers.count() != 1) {
            Log.d("Loki", "Can't remove self and others simultaneously.")
            deferred.reject(Error.InvalidClosedGroupUpdate)
            return deferred.promise
        }
        // Send the update to the existing members using established channels (don't include new ratchets as everyone should regenerate new ratchets individually)
        val promises = oldMembers.map { member ->
            val closedGroupUpdateKind = ClosedGroupUpdate.Kind.Info(Hex.fromStringCondensed(groupPublicKey),
                    name, setOf(), membersAsData, adminsAsData)
            val closedGroupUpdate = ClosedGroupUpdate()
            closedGroupUpdate.kind = closedGroupUpdateKind
            val address = Address.fromSerialized(member)
            MessageSender.sendNonDurably(closedGroupUpdate, address).get()
        }

        val allOldRatchets = sskDatabase.getAllClosedGroupRatchets(groupPublicKey, ClosedGroupRatchetCollectionType.Current)
        for (pair in allOldRatchets) {
            val senderPublicKey = pair.first
            val ratchet = pair.second
            val collection = ClosedGroupRatchetCollectionType.Old
            sskDatabase.setClosedGroupRatchet(groupPublicKey, senderPublicKey, ratchet, collection)
        }
        // Delete all ratchets (it's important that this happens * after * sending out the update)
        sskDatabase.removeAllClosedGroupRatchets(groupPublicKey, ClosedGroupRatchetCollectionType.Current)
        // Remove the group from the user's set of public keys to poll for if the user is leaving. Otherwise generate a new ratchet and
        // send it out to all members (minus the removed ones) using established channels.
        if (isUserLeaving) {
            sskDatabase.removeClosedGroupPrivateKey(groupPublicKey)
            storage.setActive(groupID, false)
            storage.removeMember(groupID, Address.fromSerialized(userPublicKey))
            // Notify the PN server
            PushNotificationAPI.performOperation(PushNotificationAPI.ClosedGroupOperation.Unsubscribe, groupPublicKey, userPublicKey)
        } else {
            // Send closed group update messages to any new members using established channels
            for (member in newMembers) {
                val closedGroupUpdateKind = ClosedGroupUpdate.Kind.New(Hex.fromStringCondensed(groupPublicKey), name,
                        Hex.fromStringCondensed(groupPrivateKey), listOf(), membersAsData, adminsAsData)
                val closedGroupUpdate = ClosedGroupUpdate()
                closedGroupUpdate.kind = closedGroupUpdateKind
                val address = Address.fromSerialized(member)
                MessageSender.sendNonDurably(closedGroupUpdate, address)
            }
            // Send out the user's new ratchet to all members (minus the removed ones) using established channels
            val userRatchet = SharedSenderKeysImplementation.shared.generateRatchet(groupPublicKey, userPublicKey)
            val userSenderKey = ClosedGroupSenderKey(Hex.fromStringCondensed(userRatchet.chainKey), userRatchet.keyIndex, Hex.fromStringCondensed(userPublicKey))
            for (member in members) {
                if (member == userPublicKey) { continue }
                val closedGroupUpdateKind = ClosedGroupUpdate.Kind.SenderKey(Hex.fromStringCondensed(groupPublicKey), userSenderKey)
                val closedGroupUpdate = ClosedGroupUpdate()
                closedGroupUpdate.kind = closedGroupUpdateKind
                val address = Address.fromSerialized(member)
                MessageSender.sendNonDurably(closedGroupUpdate, address)
            }
        }
    } else if (newMembers.isNotEmpty()) {
        // Generate ratchets for any new members
        newSenderKeys = newMembers.map { publicKey ->
            val ratchet = SharedSenderKeysImplementation.shared.generateRatchet(groupPublicKey, publicKey)
            ClosedGroupSenderKey(Hex.fromStringCondensed(ratchet.chainKey), ratchet.keyIndex, Hex.fromStringCondensed(publicKey))
        }
        // Send a closed group update message to the existing members with the new members' ratchets (this message is aimed at the group)
        val closedGroupUpdateKind = ClosedGroupUpdate.Kind.Info(Hex.fromStringCondensed(groupPublicKey), name,
                newSenderKeys, membersAsData, adminsAsData)
        val closedGroupUpdate = ClosedGroupUpdate()
        closedGroupUpdate.kind = closedGroupUpdateKind
        val address = Address.fromSerialized(groupID)
        MessageSender.send(closedGroupUpdate, address)
        // Send closed group update messages to the new members using established channels
        var allSenderKeys = sskDatabase.getAllClosedGroupSenderKeys(groupPublicKey, ClosedGroupRatchetCollectionType.Current)
        allSenderKeys = allSenderKeys.union(newSenderKeys)
        for (member in newMembers) {
            val closedGroupUpdateKind = ClosedGroupUpdate.Kind.New(Hex.fromStringCondensed(groupPublicKey), name,
                    Hex.fromStringCondensed(groupPrivateKey), allSenderKeys, membersAsData, adminsAsData)
            val closedGroupUpdate = ClosedGroupUpdate()
            closedGroupUpdate.kind = closedGroupUpdateKind
            val address = Address.fromSerialized(member)
            MessageSender.send(closedGroupUpdate, address)
        }
    } else {
        val allSenderKeys = sskDatabase.getAllClosedGroupSenderKeys(groupPublicKey, ClosedGroupRatchetCollectionType.Current)
        val closedGroupUpdateKind = ClosedGroupUpdate.Kind.Info(Hex.fromStringCondensed(groupPublicKey), name,
                allSenderKeys, membersAsData, adminsAsData)
        val closedGroupUpdate = ClosedGroupUpdate()
        closedGroupUpdate.kind = closedGroupUpdateKind
        val address = Address.fromSerialized(groupID)
        MessageSender.send(closedGroupUpdate, address)
    }
    // Update the group
    storage.updateTitle(groupID, name)
    if (!isUserLeaving) {
        // The call below sets isActive to true, so if the user is leaving we have to use groupDB.remove(...) instead
        storage.updateMembers(groupID, members.map { Address.fromSerialized(it) })
    }
    // Notify the user
    val infoType = if (isUserLeaving) SignalServiceProtos.GroupContext.Type.QUIT else SignalServiceProtos.GroupContext.Type.UPDATE
    val threadID = storage.getOrCreateThreadIdFor(Address.fromSerialized(groupID))
    /* TODO
    insertOutgoingInfoMessage(context, groupID, infoType, name, members, admins, threadID)
     */
    deferred.resolve(Unit)
    return deferred.promise
}

fun MessageSender.leave(groupPublicKey: String) {
    val storage = MessagingConfiguration.shared.storage
    val userPublicKey = storage.getUserPublicKey()!!
    val groupID = GroupUtil.getEncodedClosedGroupID(groupPublicKey)
    val group = storage.getGroup(groupID)
    if (group == null) {
        Log.d("Loki", "Can't leave nonexistent closed group.")
        return
    }
    val name = group.title
    val oldMembers = group.members.map { it.serialize() }.toSet()
    val newMembers = oldMembers.minus(userPublicKey)
    return update(groupPublicKey, newMembers, name).get()
}

fun MessageSender.requestSenderKey(groupPublicKey: String, senderPublicKey: String) {
    Log.d("Loki", "Requesting sender key for group public key: $groupPublicKey, sender public key: $senderPublicKey.")
    val address = Address.fromSerialized(senderPublicKey)
    val closedGroupUpdateKind = ClosedGroupUpdate.Kind.SenderKeyRequest(Hex.fromStringCondensed(groupPublicKey))
    val closedGroupUpdate = ClosedGroupUpdate()
    closedGroupUpdate.kind = closedGroupUpdateKind
    MessageSender.send(closedGroupUpdate, address)
}