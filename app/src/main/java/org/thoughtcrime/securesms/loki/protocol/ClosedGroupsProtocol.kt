package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import androidx.annotation.WorkerThread
import com.google.protobuf.ByteString
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.service.internal.push.SignalServiceProtos.GroupContext
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupRatchetCollectionType
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupSenderKey
import org.session.libsignal.service.loki.protocol.closedgroups.SharedSenderKeysImplementation
import org.session.libsignal.service.loki.utilities.toHexString
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.session.libsignal.utilities.logging.Log
import org.thoughtcrime.securesms.loki.api.LokiPushNotificationManager
import org.thoughtcrime.securesms.loki.api.LokiPushNotificationManager.ClosedGroupOperation
import org.thoughtcrime.securesms.loki.utilities.recipient
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage
import org.thoughtcrime.securesms.sms.MessageSender
import org.session.libsignal.utilities.Hex
import java.io.IOException
import java.util.*

object ClosedGroupsProtocol {

    sealed class Error(val description: String) : Exception() {
        object NoThread : Error("Couldn't find a thread associated with the given group public key")
        object NoPrivateKey : Error("Couldn't find a private key associated with the given group public key.")
        object InvalidUpdate : Error("Invalid group update.")
    }

    @JvmStatic
    fun leave(context: Context, groupPublicKey: String) {
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
        val groupDB = DatabaseFactory.getGroupDatabase(context)
        val groupID = doubleEncodeGroupID(groupPublicKey)
        val group = groupDB.getGroup(groupID).orNull()
        if (group == null) {
            Log.d("Loki", "Can't leave nonexistent closed group.")
            return
        }
        val name = group.title
        val oldMembers = group.members.map { it.serialize() }.toSet()
        val newMembers = oldMembers.minus(userPublicKey)
        return update(context, groupPublicKey, newMembers, name).get()
    }

    fun update(context: Context, groupPublicKey: String, members: Collection<String>, name: String): Promise<Unit, Exception> {
        val deferred = deferred<Unit, Exception>()
        Thread {
            val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
            val sskDatabase = DatabaseFactory.getSSKDatabase(context)
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
            val groupPrivateKey = DatabaseFactory.getSSKDatabase(context).getClosedGroupPrivateKey(groupPublicKey)
            if (groupPrivateKey == null) {
                Log.d("Loki", "Couldn't get private key for closed group.")
                return@Thread deferred.reject(Error.NoPrivateKey)
            }
            val wasAnyUserRemoved = members.toSet().intersect(oldMembers) != oldMembers.toSet()
            val removedMembers = oldMembers.minus(members)
            val isUserLeaving = removedMembers.contains(userPublicKey)
            var newSenderKeys = listOf<ClosedGroupSenderKey>()
            if (wasAnyUserRemoved) {
                if (isUserLeaving && removedMembers.count() != 1) {
                    Log.d("Loki", "Can't remove self and others simultaneously.")
                    return@Thread deferred.reject(Error.InvalidUpdate)
                }
                // Establish sessions if needed
                establishSessionsWithMembersIfNeeded(context, members)
                // Send the update to the existing members using established channels (don't include new ratchets as everyone should regenerate new ratchets individually)
                for (member in oldMembers) {
                    @Suppress("NAME_SHADOWING")
                    val closedGroupUpdateKind = ClosedGroupUpdateMessageSendJob.Kind.Info(Hex.fromStringCondensed(groupPublicKey),
                            name, setOf(), membersAsData, adminsAsData)
                    @Suppress("NAME_SHADOWING")
                    val job = ClosedGroupUpdateMessageSendJob(member, closedGroupUpdateKind)
                    job.setContext(context)
                    job.onRun() // Run the job immediately
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
                    groupDB.setActive(groupID, false)
                    groupDB.removeMember(groupID, Address.fromSerialized(userPublicKey))
                    // Notify the PN server
                    LokiPushNotificationManager.performOperation(context, ClosedGroupOperation.Unsubscribe, groupPublicKey, userPublicKey)
                } else {
                    // Send closed group update messages to any new members using established channels
                    for (member in newMembers) {
                        @Suppress("NAME_SHADOWING")
                        val closedGroupUpdateKind = ClosedGroupUpdateMessageSendJob.Kind.New(Hex.fromStringCondensed(groupPublicKey), name,
                                Hex.fromStringCondensed(groupPrivateKey), listOf(), membersAsData, adminsAsData)
                        @Suppress("NAME_SHADOWING")
                        val job = ClosedGroupUpdateMessageSendJob(member, closedGroupUpdateKind)
                        ApplicationContext.getInstance(context).jobManager.add(job)
                    }
                    // Send out the user's new ratchet to all members (minus the removed ones) using established channels
                    val userRatchet = SharedSenderKeysImplementation.shared.generateRatchet(groupPublicKey, userPublicKey)
                    val userSenderKey = ClosedGroupSenderKey(Hex.fromStringCondensed(userRatchet.chainKey), userRatchet.keyIndex, Hex.fromStringCondensed(userPublicKey))
                    for (member in members) {
                        if (member == userPublicKey) { continue }
                        @Suppress("NAME_SHADOWING")
                        val closedGroupUpdateKind = ClosedGroupUpdateMessageSendJob.Kind.SenderKey(Hex.fromStringCondensed(groupPublicKey), userSenderKey)
                        @Suppress("NAME_SHADOWING")
                        val job = ClosedGroupUpdateMessageSendJob(member, closedGroupUpdateKind)
                        ApplicationContext.getInstance(context).jobManager.add(job)
                    }
                }
            } else if (newMembers.isNotEmpty()) {
                // Generate ratchets for any new members
                newSenderKeys = newMembers.map { publicKey ->
                    val ratchet = SharedSenderKeysImplementation.shared.generateRatchet(groupPublicKey, publicKey)
                    ClosedGroupSenderKey(Hex.fromStringCondensed(ratchet.chainKey), ratchet.keyIndex, Hex.fromStringCondensed(publicKey))
                }
                // Send a closed group update message to the existing members with the new members' ratchets (this message is aimed at the group)
                val closedGroupUpdateKind = ClosedGroupUpdateMessageSendJob.Kind.Info(Hex.fromStringCondensed(groupPublicKey), name,
                        newSenderKeys, membersAsData, adminsAsData)
                val job = ClosedGroupUpdateMessageSendJob(groupPublicKey, closedGroupUpdateKind)
                ApplicationContext.getInstance(context).jobManager.add(job)
                // Establish sessions if needed
                establishSessionsWithMembersIfNeeded(context, newMembers)
                // Send closed group update messages to the new members using established channels
                var allSenderKeys = sskDatabase.getAllClosedGroupSenderKeys(groupPublicKey, ClosedGroupRatchetCollectionType.Current)
                allSenderKeys = allSenderKeys.union(newSenderKeys)
                for (member in newMembers) {
                    @Suppress("NAME_SHADOWING")
                    val closedGroupUpdateKind = ClosedGroupUpdateMessageSendJob.Kind.New(Hex.fromStringCondensed(groupPublicKey), name,
                            Hex.fromStringCondensed(groupPrivateKey), allSenderKeys, membersAsData, adminsAsData)
                    @Suppress("NAME_SHADOWING")
                    val job = ClosedGroupUpdateMessageSendJob(member, closedGroupUpdateKind)
                    ApplicationContext.getInstance(context).jobManager.add(job)
                }
            } else {
                val allSenderKeys = sskDatabase.getAllClosedGroupSenderKeys(groupPublicKey, ClosedGroupRatchetCollectionType.Current)
                val closedGroupUpdateKind = ClosedGroupUpdateMessageSendJob.Kind.Info(Hex.fromStringCondensed(groupPublicKey), name,
                        allSenderKeys, membersAsData, adminsAsData)
                val job = ClosedGroupUpdateMessageSendJob(groupPublicKey, closedGroupUpdateKind)
                ApplicationContext.getInstance(context).jobManager.add(job)
            }
            // Update the group
            groupDB.updateTitle(groupID, name)
            if (!isUserLeaving) {
                // The call below sets isActive to true, so if the user is leaving we have to use groupDB.remove(...) instead
                groupDB.updateMembers(groupID, members.map { Address.fromSerialized(it) })
            }
            // Notify the user
            val infoType = if (isUserLeaving) GroupContext.Type.QUIT else GroupContext.Type.UPDATE
            val threadID = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(Recipient.from(context, Address.fromSerialized(groupID), false))
            insertOutgoingInfoMessage(context, groupID, infoType, name, members, admins, threadID)
            deferred.resolve(Unit)
        }.start()
        return deferred.promise
    }

    @JvmStatic
    fun requestSenderKey(context: Context, groupPublicKey: String, senderPublicKey: String) {
        Log.d("Loki", "Requesting sender key for group public key: $groupPublicKey, sender public key: $senderPublicKey.")
        // Establish session if needed
        ApplicationContext.getInstance(context).sendSessionRequestIfNeeded(senderPublicKey)
        // Send the request
        val closedGroupUpdateKind = ClosedGroupUpdateMessageSendJob.Kind.SenderKeyRequest(Hex.fromStringCondensed(groupPublicKey))
        val job = ClosedGroupUpdateMessageSendJob(senderPublicKey, closedGroupUpdateKind)
        ApplicationContext.getInstance(context).jobManager.add(job)
    }

    @JvmStatic
    fun shouldIgnoreContentMessage(context: Context, address: Address, groupID: String?, senderPublicKey: String): Boolean {
        if (!address.isClosedGroup || groupID == null) { return false }
        /*
        FileServerAPI.shared.getDeviceLinks(senderPublicKey).timeout(6000).get()
        val senderMasterPublicKey = MultiDeviceProtocol.shared.getMasterDevice(senderPublicKey)
        val publicKeyToCheckFor = senderMasterPublicKey ?: senderPublicKey
         */
        val members = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupID, true)
        return !members.contains(recipient(context, senderPublicKey))
    }

    @JvmStatic
    fun getMessageDestinations(context: Context, groupID: String): List<Address> {
        if (GroupUtil.isOpenGroup(groupID)) {
            return listOf(Address.fromSerialized(groupID))
        } else {
            var groupPublicKey: String? = null
            try {
                groupPublicKey = doubleDecodeGroupID(groupID).toHexString()
            } catch (exception: Exception) {
                // Do nothing
            }
            if (groupPublicKey != null && DatabaseFactory.getSSKDatabase(context).isSSKBasedClosedGroup(groupPublicKey)) {
                return listOf(Address.fromSerialized(groupPublicKey))
            } else {
                return DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupID, false).map { it.address }
            }
            /*
            return FileServerAPI.shared.getDeviceLinks(members.map { it.address.serialize() }.toSet()).map {
                val result = members.flatMap { member ->
                    MultiDeviceProtocol.shared.getAllLinkedDevices(member.address.serialize()).map { Address.fromSerialized(it) }
                }.toMutableSet()
                val userMasterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
                if (userMasterPublicKey != null && result.contains(Address.fromSerialized(userMasterPublicKey))) {
                    result.remove(Address.fromSerialized(userMasterPublicKey))
                }
                val userPublicKey = TextSecurePreferences.getLocalNumber(context)
                if (userPublicKey != null && result.contains(Address.fromSerialized(userPublicKey))) {
                    result.remove(Address.fromSerialized(userPublicKey))
                }
                result.toList()
            }
             */
        }
    }

    @JvmStatic
    fun leaveLegacyGroup(context: Context, recipient: Recipient): Boolean {
        if (!recipient.address.isClosedGroup) { return true }
        val threadID = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(recipient)
        val message = createGroupLeaveMessage(context, recipient)
        if (threadID < 0 || message == null) { return false }
        MessageSender.send(context, message, threadID, false, null)
        /*
        val masterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
        val publicKeyToRemove = masterPublicKey ?: TextSecurePreferences.getLocalNumber(context)
         */
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
        val groupDatabase = DatabaseFactory.getGroupDatabase(context)
        val groupID = recipient.address.toGroupString()
        groupDatabase.setActive(groupID, false)
        groupDatabase.removeMember(groupID, Address.fromSerialized(userPublicKey))
        return true
    }

    @JvmStatic
    fun establishSessionsWithMembersIfNeeded(context: Context, members: Collection<String>) {
        @Suppress("NAME_SHADOWING") val members = members.toMutableSet()
        /*
        val allDevices = members.flatMap { member ->
            MultiDeviceProtocol.shared.getAllLinkedDevices(member)
        }.toMutableSet()
        val userMasterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
        if (userMasterPublicKey != null && allDevices.contains(userMasterPublicKey)) {
            allDevices.remove(userMasterPublicKey)
        }
         */
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        if (userPublicKey != null && members.contains(userPublicKey)) {
            members.remove(userPublicKey)
        }
        for (member in members) {
            ApplicationContext.getInstance(context).sendSessionRequestIfNeeded(member)
        }
    }

    private fun insertOutgoingInfoMessage(context: Context, groupID: String, type: GroupContext.Type, name: String,
                                          members: Collection<String>, admins: Collection<String>, threadID: Long) {
        val recipient = Recipient.from(context, Address.fromSerialized(groupID), false)
        val groupContextBuilder = GroupContext.newBuilder()
            .setId(ByteString.copyFrom(GroupUtil.getDecodedGroupIDAsData(groupID)))
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
    fun doubleEncodeGroupID(groupPublicKey: String): String {
        return GroupUtil.getEncodedClosedGroupID(GroupUtil.getEncodedClosedGroupID(Hex.fromStringCondensed(groupPublicKey)).toByteArray())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun doubleDecodeGroupID(groupID: String): ByteArray {
        return GroupUtil.getDecodedGroupIDAsData(GroupUtil.getDecodedGroupID(groupID))
    }

    @WorkerThread
    fun createGroupLeaveMessage(context: Context, groupRecipient: Recipient): OutgoingGroupMediaMessage? {
        val encodedGroupId = groupRecipient.address.toGroupString()
        val groupDatabase = DatabaseFactory.getGroupDatabase(context)
        if (!groupDatabase.isActive(encodedGroupId)) {
            Log.w("Loki", "Group has already been left.")
            return null
        }
        val decodedGroupId: ByteString
        try {
            decodedGroupId = ByteString.copyFrom(GroupUtil.getDecodedGroupIDAsData(encodedGroupId))
        } catch (e: IOException) {
            Log.w("Loki", "Failed to decode group ID.", e)
            return null
        }
        val groupContext = GroupContext.newBuilder()
                .setId(decodedGroupId)
                .setType(GroupContext.Type.QUIT)
                .build()
        return OutgoingGroupMediaMessage(groupRecipient, groupContext, null, System.currentTimeMillis(), 0, null, emptyList(), emptyList())
    }
}