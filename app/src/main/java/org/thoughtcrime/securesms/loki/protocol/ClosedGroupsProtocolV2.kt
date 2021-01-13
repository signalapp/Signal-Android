package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import android.util.Log
import com.google.protobuf.ByteString
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsignal.libsignal.ecc.Curve
import org.session.libsignal.libsignal.ecc.DjbECPrivateKey
import org.session.libsignal.libsignal.ecc.DjbECPublicKey
import org.session.libsignal.libsignal.ecc.ECKeyPair
import org.session.libsignal.libsignal.util.guava.Optional
import org.session.libsignal.service.api.messages.SignalServiceGroup
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.utilities.hexEncodedPublicKey
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
import org.session.libsignal.service.loki.utilities.toHexString
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.api.LokiPushNotificationManager
import org.thoughtcrime.securesms.loki.api.LokiPushNotificationManager.ClosedGroupOperation
import org.thoughtcrime.securesms.loki.api.SessionProtocolImpl
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.IncomingGroupMessage
import org.thoughtcrime.securesms.sms.IncomingTextMessage
import org.thoughtcrime.securesms.util.GroupUtil
import org.thoughtcrime.securesms.util.Hex
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.io.IOException
import java.util.*
import kotlin.jvm.Throws

object ClosedGroupsProtocolV2 {
    val groupSizeLimit = 20

    sealed class Error(val description: String) : Exception() {
        object NoThread : Error("Couldn't find a thread associated with the given group public key")
        object NoKeyPair : Error("Couldn't find an encryption key pair associated with the given group public key.")
        object InvalidUpdate : Error("Invalid group update.")
    }

    public fun createClosedGroup(context: Context, name: String, members: Collection<String>): Promise<String, Exception> {
        val deferred = deferred<String, Exception>()
        Thread {
            // Prepare
            val userPublicKey = TextSecurePreferences.getLocalNumber(context)
            val membersAsData = members.map { Hex.fromStringCondensed(it) }
            val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
            // Generate the group's public key
            val groupPublicKey = Curve.generateKeyPair().hexEncodedPublicKey // Includes the "05" prefix
            // Generate the key pair that'll be used for encryption and decryption
            val encryptionKeyPair = Curve.generateKeyPair()
            // Create the group
            val groupID = doubleEncodeGroupID(groupPublicKey)
            val admins = setOf( userPublicKey )
            val adminsAsData = admins.map { Hex.fromStringCondensed(it) }
            DatabaseFactory.getGroupDatabase(context).create(groupID, name, LinkedList(members.map { Address.fromSerialized(it) }),
                    null, null, LinkedList(admins.map { Address.fromSerialized(it) }))
            DatabaseFactory.getRecipientDatabase(context).setProfileSharing(Recipient.from(context, Address.fromSerialized(groupID), false), true)
            // Send a closed group update message to all members individually
            val closedGroupUpdateKind = ClosedGroupUpdateMessageSendJobV2.Kind.New(Hex.fromStringCondensed(groupPublicKey), name, encryptionKeyPair, membersAsData, adminsAsData)
            for (member in members) {
                if (member == userPublicKey) { continue }
                val job = ClosedGroupUpdateMessageSendJobV2(member, closedGroupUpdateKind)
                job.setContext(context)
                job.onRun() // Run the job immediately to make all of this sync
            }
            // Add the group to the user's set of public keys to poll for
            apiDB.addClosedGroupPublicKey(groupPublicKey)
            // Store the encryption key pair
            apiDB.addClosedGroupEncryptionKeyPair(encryptionKeyPair, groupPublicKey)
            // Notify the user
            val threadID = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(Recipient.from(context, Address.fromSerialized(groupID), false))
            insertOutgoingInfoMessage(context, groupID, SignalServiceProtos.GroupContext.Type.UPDATE, name, members, admins, threadID)
            // Notify the PN server
            LokiPushNotificationManager.performOperation(context, ClosedGroupOperation.Subscribe, groupPublicKey, userPublicKey)
            // Fulfill the promise
            deferred.resolve(groupID)
        }.start()
        // Return
        return deferred.promise
    }

    @JvmStatic
    public fun leave(context: Context, groupPublicKey: String): Promise<Unit, Exception> {
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val groupDB = DatabaseFactory.getGroupDatabase(context)
        val groupID = doubleEncodeGroupID(groupPublicKey)
        val group = groupDB.getGroup(groupID).orNull()
        if (group == null) {
            Log.d("Loki", "Can't leave nonexistent closed group.")
            return Promise.ofFail(Error.NoThread)
        }
        val name = group.title
        val oldMembers = group.members.map { it.serialize() }.toSet()
        val newMembers: Set<String>
        val isCurrentUserAdmin = group.admins.map { it.toPhoneString() }.contains(userPublicKey)
        if (!isCurrentUserAdmin) {
            newMembers = oldMembers.minus(userPublicKey)
        } else {
            newMembers = setOf() // If the admin leaves the group is destroyed
        }
        return update(context, groupPublicKey, newMembers, name)
    }

    public fun update(context: Context, groupPublicKey: String, members: Collection<String>, name: String): Promise<Unit, Exception> {
        val deferred = deferred<Unit, Exception>()
        Thread {
            val userPublicKey = TextSecurePreferences.getLocalNumber(context)
            val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
            val groupDB = DatabaseFactory.getGroupDatabase(context)
            val groupID = doubleEncodeGroupID(groupPublicKey)
            val group = groupDB.getGroup(groupID).orNull()
            if (group == null) {
                Log.d("Loki", "Can't update nonexistent closed group.")
                return@Thread deferred.reject(Error.NoThread)
            }
            val oldMembers = group.members.map { it.serialize() }.toSet()
            val newMembers = members.minus(oldMembers)
            val membersAsData = members.map { Hex.fromStringCondensed(it) }
            val admins = group.admins.map { it.serialize() }
            val adminsAsData = admins.map { Hex.fromStringCondensed(it) }
            val encryptionKeyPair = apiDB.getLatestClosedGroupEncryptionKeyPair(groupPublicKey)
            if (encryptionKeyPair == null) {
                Log.d("Loki", "Couldn't get encryption key pair for closed group.")
                return@Thread deferred.reject(Error.NoKeyPair)
            }
            val removedMembers = oldMembers.minus(members)
            if (removedMembers.contains(admins.first()) && members.isNotEmpty()) {
                Log.d("Loki", "Can't remove admin from closed group unless the group is destroyed entirely.")
                return@Thread deferred.reject(Error.InvalidUpdate)
            }
            val isUserLeaving = removedMembers.contains(userPublicKey)
            if (isUserLeaving && members.isNotEmpty()) {
                if (removedMembers.count() != 1 || newMembers.isNotEmpty()) {
                    Log.d("Loki", "Can't remove self and add or remove others simultaneously.")
                    return@Thread deferred.reject(Error.InvalidUpdate)
                }
            }
            // Send the update to the group
            @Suppress("NAME_SHADOWING")
            val closedGroupUpdateKind = ClosedGroupUpdateMessageSendJobV2.Kind.Update(name, membersAsData)
            @Suppress("NAME_SHADOWING")
            val job = ClosedGroupUpdateMessageSendJobV2(groupPublicKey, closedGroupUpdateKind)
            job.setContext(context)
            job.onRun() // Run the job immediately
            if (isUserLeaving) {
                // Remove the group private key and unsubscribe from PNs
                apiDB.removeAllClosedGroupEncryptionKeyPairs(groupPublicKey)
                apiDB.removeClosedGroupPublicKey(groupPublicKey)
                // Mark the group as inactive
                groupDB.setActive(groupID, false)
                groupDB.removeMember(groupID, Address.fromSerialized(userPublicKey))
                // Notify the PN server
                LokiPushNotificationManager.performOperation(context, ClosedGroupOperation.Unsubscribe, groupPublicKey, userPublicKey)
            } else {
                // Generate and distribute a new encryption key pair if needed
                val wasAnyUserRemoved = removedMembers.isNotEmpty()
                val isCurrentUserAdmin = admins.contains(userPublicKey)
                if (wasAnyUserRemoved && isCurrentUserAdmin) {
                    generateAndSendNewEncryptionKeyPair(context, groupPublicKey, members.minus(newMembers))
                }
                // Send closed group update messages to any new members individually
                for (member in newMembers) {
                    @Suppress("NAME_SHADOWING")
                    val closedGroupUpdateKind = ClosedGroupUpdateMessageSendJobV2.Kind.New(Hex.fromStringCondensed(groupPublicKey), name, encryptionKeyPair, membersAsData, adminsAsData)
                    @Suppress("NAME_SHADOWING")
                    val job = ClosedGroupUpdateMessageSendJobV2(member, closedGroupUpdateKind)
                    ApplicationContext.getInstance(context).jobManager.add(job)
                }
            }
            // Update the group
            groupDB.updateTitle(groupID, name)
            if (!isUserLeaving) {
                // The call below sets isActive to true, so if the user is leaving we have to use groupDB.remove(...) instead
                groupDB.updateMembers(groupID, members.map { Address.fromSerialized(it) })
            }
            // Notify the user
            val infoType = if (isUserLeaving) SignalServiceProtos.GroupContext.Type.QUIT else SignalServiceProtos.GroupContext.Type.UPDATE
            val threadID = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(Recipient.from(context, Address.fromSerialized(groupID), false))
            insertOutgoingInfoMessage(context, groupID, infoType, name, members, admins, threadID)
            deferred.resolve(Unit)
        }.start()
        return deferred.promise
    }

    fun generateAndSendNewEncryptionKeyPair(context: Context, groupPublicKey: String, targetMembers: Collection<String>) {
        // Prepare
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
        val groupDB = DatabaseFactory.getGroupDatabase(context)
        val groupID = doubleEncodeGroupID(groupPublicKey)
        val group = groupDB.getGroup(groupID).orNull()
        if (group == null) {
            Log.d("Loki", "Can't update nonexistent closed group.")
            return
        }
        if (!group.admins.map { it.toPhoneString() }.contains(userPublicKey)) {
            Log.d("Loki", "Can't distribute new encryption key pair as non-admin.")
            return
        }
        // Generate the new encryption key pair
        val newKeyPair = Curve.generateKeyPair()
        // Distribute it
        val proto = SignalServiceProtos.ClosedGroupUpdateV2.KeyPair.newBuilder()
        proto.publicKey = ByteString.copyFrom(newKeyPair.publicKey.serialize().removing05PrefixIfNeeded())
        proto.privateKey = ByteString.copyFrom(newKeyPair.privateKey.serialize())
        val plaintext = proto.build().toByteArray()
        val wrappers = targetMembers.mapNotNull { publicKey ->
            val ciphertext = SessionProtocolImpl(context).encrypt(plaintext, publicKey)
            ClosedGroupUpdateMessageSendJobV2.KeyPairWrapper(publicKey, ciphertext)
        }
        val job = ClosedGroupUpdateMessageSendJobV2(groupPublicKey, ClosedGroupUpdateMessageSendJobV2.Kind.EncryptionKeyPair(wrappers))
        job.setContext(context)
        job.onRun() // Run the job immediately
        // Store it * after * having sent out the message to the group
        apiDB.addClosedGroupEncryptionKeyPair(newKeyPair, groupPublicKey)
    }

    @JvmStatic
    public fun handleMessage(context: Context, closedGroupUpdate: SignalServiceProtos.ClosedGroupUpdateV2, sentTimestamp: Long, groupPublicKey: String, senderPublicKey: String) {
        if (!isValid(closedGroupUpdate)) { return; }
        when (closedGroupUpdate.type) {
            SignalServiceProtos.ClosedGroupUpdateV2.Type.NEW -> handleNewClosedGroup(context, closedGroupUpdate, senderPublicKey)
            SignalServiceProtos.ClosedGroupUpdateV2.Type.UPDATE -> handleClosedGroupUpdate(context, closedGroupUpdate, sentTimestamp, groupPublicKey, senderPublicKey)
            SignalServiceProtos.ClosedGroupUpdateV2.Type.ENCRYPTION_KEY_PAIR -> handleGroupEncryptionKeyPair(context, closedGroupUpdate, groupPublicKey, senderPublicKey)
            else -> {
                // Do nothing
            }
        }
    }

    private fun isValid(closedGroupUpdate: SignalServiceProtos.ClosedGroupUpdateV2): Boolean {
        when (closedGroupUpdate.type) {
            SignalServiceProtos.ClosedGroupUpdateV2.Type.NEW -> {
                return !closedGroupUpdate.publicKey.isEmpty && !closedGroupUpdate.name.isNullOrEmpty() && !(closedGroupUpdate.encryptionKeyPair.privateKey ?: ByteString.copyFrom(ByteArray(0))).isEmpty
                        && !(closedGroupUpdate.encryptionKeyPair.publicKey ?: ByteString.copyFrom(ByteArray(0))).isEmpty && closedGroupUpdate.membersCount > 0 && closedGroupUpdate.adminsCount > 0
            }
            SignalServiceProtos.ClosedGroupUpdateV2.Type.UPDATE -> {
                return !closedGroupUpdate.name.isNullOrEmpty()
            }
            SignalServiceProtos.ClosedGroupUpdateV2.Type.ENCRYPTION_KEY_PAIR -> return true
            else -> return false
        }
    }

    public fun handleNewClosedGroup(context: Context, closedGroupUpdate: SignalServiceProtos.ClosedGroupUpdateV2, senderPublicKey: String) {
        // Prepare
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
        // Unwrap the message
        val groupPublicKey = closedGroupUpdate.publicKey.toByteArray().toHexString()
        val name = closedGroupUpdate.name
        val encryptionKeyPairAsProto = closedGroupUpdate.encryptionKeyPair
        val members = closedGroupUpdate.membersList.map { it.toByteArray().toHexString() }
        val admins = closedGroupUpdate.adminsList.map { it.toByteArray().toHexString() }
        // Create the group
        val groupID = doubleEncodeGroupID(groupPublicKey)
        val groupDB = DatabaseFactory.getGroupDatabase(context)
        if (groupDB.getGroup(groupID).orNull() != null) {
            // Update the group
            groupDB.updateTitle(groupID, name)
            groupDB.updateMembers(groupID, members.map { Address.fromSerialized(it) })
        } else {
            groupDB.create(groupID, name, LinkedList(members.map { Address.fromSerialized(it) }),
                    null, null, LinkedList(admins.map { Address.fromSerialized(it) }))
        }
        DatabaseFactory.getRecipientDatabase(context).setProfileSharing(Recipient.from(context, Address.fromSerialized(groupID), false), true)
        // Add the group to the user's set of public keys to poll for
        apiDB.addClosedGroupPublicKey(groupPublicKey)
        // Store the encryption key pair
        val encryptionKeyPair = ECKeyPair(DjbECPublicKey(encryptionKeyPairAsProto.publicKey.toByteArray().removing05PrefixIfNeeded()), DjbECPrivateKey(encryptionKeyPairAsProto.privateKey.toByteArray()))
        apiDB.addClosedGroupEncryptionKeyPair(encryptionKeyPair, groupPublicKey)
        // Notify the user
        insertIncomingInfoMessage(context, senderPublicKey, groupID, SignalServiceProtos.GroupContext.Type.UPDATE, SignalServiceGroup.Type.UPDATE, name, members, admins)
        // Notify the PN server
        LokiPushNotificationManager.performOperation(context, ClosedGroupOperation.Subscribe, groupPublicKey, userPublicKey)
    }

    public fun handleClosedGroupUpdate(context: Context, closedGroupUpdate: SignalServiceProtos.ClosedGroupUpdateV2, sentTimestamp: Long, groupPublicKey: String, senderPublicKey: String) {
        // Prepare
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
        // Unwrap the message
        val name = closedGroupUpdate.name
        val members = closedGroupUpdate.membersList.map { it.toByteArray().toHexString() }
        val groupDB = DatabaseFactory.getGroupDatabase(context)
        val groupID = doubleEncodeGroupID(groupPublicKey)
        val group = groupDB.getGroup(groupID).orNull()
        if (group == null) {
            Log.d("Loki", "Ignoring closed group info message for nonexistent group.")
            return
        }
        val oldMembers = group.members.map { it.serialize() }
        val newMembers = members.toSet().minus(oldMembers)
        // Check that the message isn't from before the group was created
        if (group.createdAt > sentTimestamp) {
            Log.d("Loki", "Ignoring closed group update from before thread was created.")
            return
        }
        // Check that the sender is a member of the group (before the update)
        if (!oldMembers.contains(senderPublicKey)) {
            Log.d("Loki", "Ignoring closed group info message from non-member.")
            return
        }
        // Check that the admin wasn't removed unless the group was destroyed entirely
        if (!members.contains(group.admins.first().toPhoneString()) && members.isNotEmpty()) {
            Log.d("Loki", "Ignoring invalid closed group update message.")
            return
        }
        // Remove the group from the user's set of public keys to poll for if the current user was removed
        val wasCurrentUserRemoved = !members.contains(userPublicKey)
        if (wasCurrentUserRemoved) {
            apiDB.removeClosedGroupPublicKey(groupPublicKey)
            // Remove the key pairs
            apiDB.removeAllClosedGroupEncryptionKeyPairs(groupPublicKey)
            // Mark the group as inactive
            groupDB.setActive(groupID, false)
            groupDB.removeMember(groupID, Address.fromSerialized(userPublicKey))
            // Notify the PN server
            LokiPushNotificationManager.performOperation(context, ClosedGroupOperation.Unsubscribe, groupPublicKey, userPublicKey)
        }
        // Generate and distribute a new encryption key pair if needed
        val wasAnyUserRemoved = (members.toSet().intersect(oldMembers) != oldMembers.toSet())
        val isCurrentUserAdmin = group.admins.map { it.toPhoneString() }.contains(userPublicKey)
        if (wasAnyUserRemoved && isCurrentUserAdmin) {
            generateAndSendNewEncryptionKeyPair(context, groupPublicKey, members.minus(newMembers))
        }
        // Update the group
        groupDB.updateTitle(groupID, name)
        if (!wasCurrentUserRemoved) {
            // The call below sets isActive to true, so if the user is leaving we have to use groupDB.remove(...) instead
            groupDB.updateMembers(groupID, members.map { Address.fromSerialized(it) })
        }
        // Notify the user
        val wasSenderRemoved = !members.contains(senderPublicKey)
        val type0 = if (wasSenderRemoved) SignalServiceProtos.GroupContext.Type.QUIT else SignalServiceProtos.GroupContext.Type.UPDATE
        val type1 = if (wasSenderRemoved) SignalServiceGroup.Type.QUIT else SignalServiceGroup.Type.UPDATE
        insertIncomingInfoMessage(context, senderPublicKey, groupID, type0, type1, name, members, group.admins.map { it.toPhoneString() })
    }

    private fun handleGroupEncryptionKeyPair(context: Context, closedGroupUpdate: SignalServiceProtos.ClosedGroupUpdateV2, groupPublicKey: String, senderPublicKey: String) {
        // Prepare
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
        val userKeyPair = apiDB.getUserX25519KeyPair()
        // Unwrap the message
        val groupDB = DatabaseFactory.getGroupDatabase(context)
        val groupID = doubleEncodeGroupID(groupPublicKey)
        val group = groupDB.getGroup(groupID).orNull()
        if (group == null) {
            Log.d("Loki", "Ignoring closed group encryption key pair message for nonexistent group.")
            return
        }
        if (!group.admins.map { it.toPhoneString() }.contains(senderPublicKey)) {
            Log.d("Loki", "Ignoring closed group encryption key pair from non-admin.")
            return
        }
        // Find our wrapper and decrypt it if possible
        val wrapper = closedGroupUpdate.wrappersList.firstOrNull { it.publicKey.toByteArray().toHexString() == userPublicKey } ?: return
        val encryptedKeyPair = wrapper.encryptedKeyPair.toByteArray()
        val plaintext = SessionProtocolImpl(context).decrypt(encryptedKeyPair, userKeyPair).first
        // Parse it
        val proto = SignalServiceProtos.ClosedGroupUpdateV2.KeyPair.parseFrom(plaintext)
        val keyPair = ECKeyPair(DjbECPublicKey(proto.publicKey.toByteArray().removing05PrefixIfNeeded()), DjbECPrivateKey(proto.privateKey.toByteArray()))
        // Store it
        apiDB.addClosedGroupEncryptionKeyPair(keyPair, groupPublicKey)
        Log.d("Loki", "Received a new closed group encryption key pair")
    }

    private fun insertIncomingInfoMessage(context: Context, senderPublicKey: String, groupID: String, type0: SignalServiceProtos.GroupContext.Type, type1: SignalServiceGroup.Type,
                                          name: String, members: Collection<String>, admins: Collection<String>) {
        val groupContextBuilder = SignalServiceProtos.GroupContext.newBuilder()
                .setId(ByteString.copyFrom(GroupUtil.getDecodedId(groupID)))
                .setType(type0)
                .setName(name)
                .addAllMembers(members)
                .addAllAdmins(admins)
        val group = SignalServiceGroup(type1, GroupUtil.getDecodedId(groupID), SignalServiceGroup.GroupType.SIGNAL, name, members.toList(), null, admins.toList())
        val m = IncomingTextMessage(Address.fromSerialized(senderPublicKey), 1, System.currentTimeMillis(), "", Optional.of(group), 0, true)
        val infoMessage = IncomingGroupMessage(m, groupContextBuilder.build(), "")
        val smsDB = DatabaseFactory.getSmsDatabase(context)
        smsDB.insertMessageInbox(infoMessage)
    }

    private fun insertOutgoingInfoMessage(context: Context, groupID: String, type: SignalServiceProtos.GroupContext.Type, name: String,
                                          members: Collection<String>, admins: Collection<String>, threadID: Long) {
        val recipient = Recipient.from(context, Address.fromSerialized(groupID), false)
        val groupContextBuilder = SignalServiceProtos.GroupContext.newBuilder()
                .setId(ByteString.copyFrom(GroupUtil.getDecodedId(groupID)))
                .setType(type)
                .setName(name)
                .addAllMembers(members)
                .addAllAdmins(admins)
        val infoMessage = OutgoingGroupMediaMessage(recipient, groupContextBuilder.build(), null, System.currentTimeMillis(), 0, null, listOf(), listOf())
        val mmsDB = DatabaseFactory.getMmsDatabase(context)
        val infoMessageID = mmsDB.insertMessageOutbox(infoMessage, threadID, false, null)
        mmsDB.markAsSent(infoMessageID, true)
    }

    // NOTE: Signal group ID handling is weird. The ID is double encoded in the database, but not in a `GroupContext`.

    @JvmStatic
    @Throws(IOException::class)
    public fun doubleEncodeGroupID(groupPublicKey: String): String {
        return GroupUtil.getEncodedId(GroupUtil.getEncodedId(Hex.fromStringCondensed(groupPublicKey), false).toByteArray(), false)
    }

    @JvmStatic
    @Throws(IOException::class)
    public fun doubleDecodeGroupID(groupID: String): ByteArray {
        return GroupUtil.getDecodedId(GroupUtil.getDecodedStringId(groupID))
    }
}