@file:Suppress("NAME_SHADOWING")

package org.session.libsession.messaging.sending_receiving

import com.google.protobuf.ByteString
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.messages.control.ClosedGroupControlMessage
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationAPI
import org.session.libsession.messaging.sending_receiving.MessageSender.Error
import org.session.libsession.messaging.threads.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsignal.utilities.Hex

import org.session.libsignal.libsignal.ecc.Curve
import org.session.libsignal.libsignal.ecc.ECKeyPair
import org.session.libsignal.libsignal.util.guava.Optional
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.utilities.hexEncodedPublicKey
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
import org.session.libsignal.utilities.ThreadUtils
import org.session.libsignal.utilities.logging.Log
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val pendingKeyPair = ConcurrentHashMap<String, Optional<ECKeyPair>>()

fun MessageSender.createClosedGroup(name: String, members: Collection<String>): Promise<String, Exception> {
    val deferred = deferred<String, Exception>()
    ThreadUtils.queue {
        // Prepare
        val context = MessagingConfiguration.shared.context
        val storage = MessagingConfiguration.shared.storage
        val userPublicKey = storage.getUserPublicKey()!!
        val membersAsData = members.map { ByteString.copyFrom(Hex.fromStringCondensed(it)) }
        // Generate the group's public key
        val groupPublicKey = Curve.generateKeyPair().hexEncodedPublicKey // Includes the "05" prefix
        // Generate the key pair that'll be used for encryption and decryption
        val encryptionKeyPair = Curve.generateKeyPair()
        // Create the group
        val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
        val admins = setOf( userPublicKey )
        val adminsAsData = admins.map { ByteString.copyFrom(Hex.fromStringCondensed(it)) }
        storage.createGroup(groupID, name, LinkedList(members.map { Address.fromSerialized(it) }),
                null, null, LinkedList(admins.map { Address.fromSerialized(it) }))
        storage.setProfileSharing(Address.fromSerialized(groupID), true)
        // Send a closed group update message to all members individually
        val closedGroupUpdateKind = ClosedGroupControlMessage.Kind.New(ByteString.copyFrom(Hex.fromStringCondensed(groupPublicKey)), name, encryptionKeyPair, membersAsData, adminsAsData)
        for (member in members) {
            val closedGroupControlMessage = ClosedGroupControlMessage(closedGroupUpdateKind)
            sendNonDurably(closedGroupControlMessage, Address.fromSerialized(groupID)).get()
        }
        // Add the group to the user's set of public keys to poll for
        storage.addClosedGroupPublicKey(groupPublicKey)
        // Store the encryption key pair
        storage.addClosedGroupEncryptionKeyPair(encryptionKeyPair, groupPublicKey)
        // Notify the user
        val threadID = storage.getOrCreateThreadIdFor(Address.fromSerialized(groupID))
        storage.insertOutgoingInfoMessage(context, groupID, SignalServiceProtos.GroupContext.Type.UPDATE, name, members, admins, threadID)
        // Notify the PN server
        PushNotificationAPI.performOperation(PushNotificationAPI.ClosedGroupOperation.Subscribe, groupPublicKey, userPublicKey)
        // Fulfill the promise
        deferred.resolve(groupID)
    }
    // Return
    return deferred.promise
}

fun MessageSender.v2_update(groupPublicKey: String, members: List<String>, name: String) {
    val context = MessagingConfiguration.shared.context
    val storage = MessagingConfiguration.shared.storage
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Can't update nonexistent closed group.")
        throw Error.NoThread
    }
    // Update name if needed
    if (name != group.title) { setName(groupPublicKey, name) }
    // Add members if needed
    val addedMembers = members - group.members.map { it.serialize() }
    if (!addedMembers.isEmpty()) { addMembers(groupPublicKey, addedMembers) }
    // Remove members if needed
    val removedMembers = group.members.map { it.serialize() } - members
    if (removedMembers.isEmpty()) { removeMembers(groupPublicKey, removedMembers) }
}

fun MessageSender.setName(groupPublicKey: String, newName: String) {
    val context = MessagingConfiguration.shared.context
    val storage = MessagingConfiguration.shared.storage
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Can't change name for nonexistent closed group.")
        throw Error.NoThread
    }
    val members = group.members.map { it.serialize() }.toSet()
    val admins = group.admins.map { it.serialize() }
    // Send the update to the group
    val kind = ClosedGroupControlMessage.Kind.NameChange(newName)
    val closedGroupControlMessage = ClosedGroupControlMessage(kind)
    send(closedGroupControlMessage, Address.fromSerialized(groupID))
    // Update the group
    storage.updateTitle(groupID, newName)
    // Notify the user
    val infoType = SignalServiceProtos.GroupContext.Type.UPDATE
    val threadID = storage.getOrCreateThreadIdFor(Address.fromSerialized(groupID))
    storage.insertOutgoingInfoMessage(context, groupID, infoType, newName, members, admins, threadID)
}

fun MessageSender.addMembers(groupPublicKey: String, membersToAdd: List<String>) {
    val context = MessagingConfiguration.shared.context
    val storage = MessagingConfiguration.shared.storage
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Can't add members to nonexistent closed group.")
        throw Error.NoThread
    }
    if (membersToAdd.isEmpty()) {
        Log.d("Loki", "Invalid closed group update.")
        throw Error.InvalidClosedGroupUpdate
    }
    val updatedMembers = group.members.map { it.serialize() }.toSet() + membersToAdd
    // Save the new group members
    storage.updateMembers(groupID, updatedMembers.map { Address.fromSerialized(it) })
    val membersAsData = updatedMembers.map { ByteString.copyFrom(Hex.fromStringCondensed(it)) }
    val newMembersAsData = membersToAdd.map { ByteString.copyFrom(Hex.fromStringCondensed(it)) }
    val admins = group.admins.map { it.serialize() }
    val adminsAsData = admins.map { ByteString.copyFrom(Hex.fromStringCondensed(it)) }
    val encryptionKeyPair = storage.getLatestClosedGroupEncryptionKeyPair(groupPublicKey) ?: run {
        Log.d("Loki", "Couldn't get encryption key pair for closed group.")
        throw Error.NoKeyPair
    }
    val name = group.title
    // Send the update to the group
    val memberUpdateKind = ClosedGroupControlMessage.Kind.MembersAdded(newMembersAsData)
    val closedGroupControlMessage = ClosedGroupControlMessage(memberUpdateKind)
    send(closedGroupControlMessage, Address.fromSerialized(groupID))
    // Send closed group update messages to any new members individually
    for (member in membersToAdd) {
        val closedGroupNewKind = ClosedGroupControlMessage.Kind.New(ByteString.copyFrom(Hex.fromStringCondensed(groupPublicKey)), name, encryptionKeyPair, membersAsData, adminsAsData)
        val closedGroupControlMessage = ClosedGroupControlMessage(closedGroupNewKind)
        send(closedGroupControlMessage, Address.fromSerialized(member))
    }
    // Notify the user
    val infoType = SignalServiceProtos.GroupContext.Type.UPDATE
    val threadID = storage.getOrCreateThreadIdFor(Address.fromSerialized(groupID))
    storage.insertOutgoingInfoMessage(context, groupID, infoType, name, updatedMembers, admins, threadID)
}

fun MessageSender.removeMembers(groupPublicKey: String, membersToRemove: List<String>) {
    val context = MessagingConfiguration.shared.context
    val storage = MessagingConfiguration.shared.storage
    val userPublicKey = storage.getUserPublicKey()!!
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Can't remove members from nonexistent closed group.")
        throw Error.NoThread
    }
    if (membersToRemove.isEmpty() || membersToRemove.contains(userPublicKey)) {
        Log.d("Loki", "Invalid closed group update.")
        throw Error.InvalidClosedGroupUpdate
    }
    val updatedMembers = group.members.map { it.serialize() }.toSet() - membersToRemove
    // Save the new group members
    storage.updateMembers(groupID, updatedMembers.map { Address.fromSerialized(it) })
    val removeMembersAsData = membersToRemove.map { ByteString.copyFrom(Hex.fromStringCondensed(it)) }
    val admins = group.admins.map { it.serialize() }
    if (membersToRemove.any { it in admins } && updatedMembers.isNotEmpty()) {
        Log.d("Loki", "Can't remove admin from closed group unless the group is destroyed entirely.")
        throw Error.InvalidClosedGroupUpdate
    }
    val name = group.title
    // Send the update to the group
    val memberUpdateKind = ClosedGroupControlMessage.Kind.MembersRemoved(removeMembersAsData)
    val closedGroupControlMessage = ClosedGroupControlMessage(memberUpdateKind)
    send(closedGroupControlMessage, Address.fromSerialized(groupID))
    val isCurrentUserAdmin = admins.contains(userPublicKey)
    if (isCurrentUserAdmin) {
        generateAndSendNewEncryptionKeyPair(groupPublicKey, updatedMembers)
    }
    // Notify the user
    val infoType = SignalServiceProtos.GroupContext.Type.UPDATE
    val threadID = storage.getOrCreateThreadIdFor(Address.fromSerialized(groupID))
    storage.insertOutgoingInfoMessage(context, groupID, infoType, name, updatedMembers, admins, threadID)
}

fun MessageSender.v2_leave(groupPublicKey: String) {
    val context = MessagingConfiguration.shared.context
    val storage = MessagingConfiguration.shared.storage
    val userPublicKey = storage.getUserPublicKey()!!
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Can't leave nonexistent closed group.")
        throw Error.NoThread
    }
    val updatedMembers = group.members.map { it.serialize() }.toSet() - userPublicKey
    val admins = group.admins.map { it.serialize() }
    val name = group.title
    // Send the update to the group
    val closedGroupControlMessage = ClosedGroupControlMessage(ClosedGroupControlMessage.Kind.MemberLeft)
    sendNonDurably(closedGroupControlMessage, Address.fromSerialized(groupID)).success {
        // Remove the group private key and unsubscribe from PNs
        MessageReceiver.disableLocalGroupAndUnsubscribe(groupPublicKey, groupID, userPublicKey)
    }
    // Notify the user
    val infoType = SignalServiceProtos.GroupContext.Type.QUIT
    val threadID = storage.getOrCreateThreadIdFor(Address.fromSerialized(groupID))
    storage.insertOutgoingInfoMessage(context, groupID, infoType, name, updatedMembers, admins, threadID)
}

fun MessageSender.generateAndSendNewEncryptionKeyPair(groupPublicKey: String, targetMembers: Collection<String>) {
    // Prepare
    val storage = MessagingConfiguration.shared.storage
    val userPublicKey = storage.getUserPublicKey()!!
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Can't update nonexistent closed group.")
        throw Error.NoThread
    }
    if (!group.admins.map { it.toString() }.contains(userPublicKey)) {
        Log.d("Loki", "Can't distribute new encryption key pair as non-admin.")
        throw Error.InvalidClosedGroupUpdate
    }
    // Generate the new encryption key pair
    val newKeyPair = Curve.generateKeyPair()
    // replace call will not succeed if no value already set
    pendingKeyPair.putIfAbsent(groupPublicKey,Optional.absent())
    do {
        // make sure we set the pendingKeyPair or wait until it is not null
    } while (!pendingKeyPair.replace(groupPublicKey,Optional.absent(),Optional.fromNullable(newKeyPair)))
    // Distribute it
    val proto = SignalServiceProtos.KeyPair.newBuilder()
    proto.publicKey = ByteString.copyFrom(newKeyPair.publicKey.serialize().removing05PrefixIfNeeded())
    proto.privateKey = ByteString.copyFrom(newKeyPair.privateKey.serialize())
    val plaintext = proto.build().toByteArray()
    val wrappers = targetMembers.map { publicKey ->
        val ciphertext = MessageSenderEncryption.encryptWithSessionProtocol(plaintext, publicKey)
        ClosedGroupControlMessage.KeyPairWrapper(publicKey, ByteString.copyFrom(ciphertext))
    }
    val kind = ClosedGroupControlMessage.Kind.EncryptionKeyPair(null, wrappers)
    val closedGroupControlMessage = ClosedGroupControlMessage(kind)
    sendNonDurably(closedGroupControlMessage, Address.fromSerialized(groupID)).success {
        // Store it * after * having sent out the message to the group
        storage.addClosedGroupEncryptionKeyPair(newKeyPair, groupPublicKey)
        pendingKeyPair[groupPublicKey] = Optional.absent()
    }
}

/// Note: Shouldn't currently be in use.
fun MessageSender.requestEncryptionKeyPair(groupPublicKey: String) {
    val storage = MessagingConfiguration.shared.storage
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Can't request encryption key pair for nonexistent closed group.")
        throw Error.NoThread
    }
    val members = group.members.map { it.serialize() }.toSet()
    if (!members.contains(storage.getUserPublicKey()!!)) return
    // Send the request to the group
    val closedGroupControlMessage = ClosedGroupControlMessage(ClosedGroupControlMessage.Kind.EncryptionKeyPairRequest)
    send(closedGroupControlMessage, Address.fromSerialized(groupID))
}

fun MessageSender.sendLatestEncryptionKeyPair(publicKey: String, groupPublicKey: String) {
    val storage = MessagingConfiguration.shared.storage
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Can't send encryption key pair for nonexistent closed group.")
        throw Error.NoThread
    }
    val members = group.members.map { it.serialize() }
    if (!members.contains(publicKey)) {
        Log.d("Loki", "Refusing to send latest encryption key pair to non-member.")
        return
    }
    // Get the latest encryption key pair
    val encryptionKeyPair = pendingKeyPair[groupPublicKey]?.orNull()
            ?: storage.getLatestClosedGroupEncryptionKeyPair(groupPublicKey) ?: return
    // Send it
    val proto = SignalServiceProtos.KeyPair.newBuilder()
    proto.publicKey = ByteString.copyFrom(encryptionKeyPair.publicKey.serialize())
    proto.privateKey = ByteString.copyFrom(encryptionKeyPair.privateKey.serialize())
    val plaintext = proto.build().toByteArray()
    val ciphertext = MessageSenderEncryption.encryptWithSessionProtocol(plaintext, publicKey)
    Log.d("Loki", "Sending latest encryption key pair to: $publicKey.")
    val wrapper = ClosedGroupControlMessage.KeyPairWrapper(publicKey, ByteString.copyFrom(ciphertext))
    val kind = ClosedGroupControlMessage.Kind.EncryptionKeyPair(ByteString.copyFrom(Hex.fromStringCondensed(groupPublicKey)), listOf(wrapper))
    val closedGroupControlMessage = ClosedGroupControlMessage(kind)
    MessageSender.send(closedGroupControlMessage, Address.fromSerialized(publicKey))
}