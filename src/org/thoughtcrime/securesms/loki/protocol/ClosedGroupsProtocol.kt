package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import android.util.Log
import nl.komponents.kovenant.Promise
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.utilities.recipient
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.sms.OutgoingTextMessage
import org.thoughtcrime.securesms.util.GroupUtil
import org.thoughtcrime.securesms.util.Hex
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.signalservice.loki.protocol.closedgroups.ClosedGroupSenderKey
import org.whispersystems.signalservice.loki.protocol.closedgroups.SharedSenderKeysImplementation
import org.whispersystems.signalservice.loki.utilities.hexEncodedPrivateKey
import org.whispersystems.signalservice.loki.utilities.hexEncodedPublicKey
import java.util.*

object ClosedGroupsProtocol {

    public fun createClosedGroup(context: Context, name: String, members: Collection<String>): Promise<Unit, Exception> {
        // Prepare
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        // Generate a key pair for the group
        val groupKeyPair = Curve.generateKeyPair()
        val groupPublicKey = groupKeyPair.hexEncodedPublicKey // Includes the "05" prefix
        val membersAsData = members.map { Hex.fromStringCondensed(it) }
        // Create ratchets for all members
        val senderKeys: List<ClosedGroupSenderKey> = members.map { publicKey ->
            val ratchet = SharedSenderKeysImplementation.shared.generateRatchet(groupPublicKey, publicKey)
            ClosedGroupSenderKey(Hex.fromStringCondensed(ratchet.chainKey), ratchet.keyIndex, Hex.fromStringCondensed(publicKey))
        }
        // Create the group
        val groupID = GroupUtil.getEncodedId(Hex.fromStringCondensed(groupPublicKey), false)
        val admins = setOf( Address.fromSerialized(userPublicKey) )
        DatabaseFactory.getGroupDatabase(context).create(groupID, name, LinkedList<Address>(members.map { Address.fromSerialized(it) }),
            null, null, LinkedList<Address>(admins))
        DatabaseFactory.getRecipientDatabase(context).setProfileSharing(Recipient.from(context, Address.fromSerialized(groupID), false), true)
        // Establish sessions if needed
        establishSessionsWithMembersIfNeeded(context, members)
        // Send a closed group update message to all members using established channels
        val adminsAsData = admins.map { Hex.fromStringCondensed(it.serialize()) }
        val closedGroupUpdateKind = ClosedGroupUpdateMessageSendJob.Kind.New(Hex.fromStringCondensed(groupPublicKey), name, groupKeyPair.privateKey.serialize(),
            senderKeys, membersAsData, adminsAsData)
        for (member in members) {
            val job = ClosedGroupUpdateMessageSendJob(member, closedGroupUpdateKind)
            ApplicationContext.getInstance(context).jobManager.add(job)
        }
        // TODO: Wait for the messages to finish sending
        // Add the group to the user's set of public keys to poll for
        DatabaseFactory.getSSKDatabase(context).setClosedGroupPrivateKey(groupPublicKey, groupKeyPair.hexEncodedPrivateKey)
        // Notify the user
        // TODO: Implement
        // Return
        return Promise.of(Unit)
    }

    public fun addMembers(context: Context, newMembers: Collection<String>, groupPublicKey: String) {
        // Prepare
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val groupDB = DatabaseFactory.getGroupDatabase(context)
        val groupID = GroupUtil.getEncodedId(Hex.fromStringCondensed(groupPublicKey), false)
        val group = groupDB.getGroup(groupID).orNull()
        if (group == null) {
            Log.d("Loki", "Can't add users to nonexistent closed group.")
            return
        }
        val name = group.title
        val admins = group.admins.map { Hex.fromStringCondensed(it.serialize()) }
        val privateKey = DatabaseFactory.getSSKDatabase(context).getClosedGroupPrivateKey(groupPublicKey)
        if (privateKey == null) {
            Log.d("Loki", "Couldn't get private key for closed group.")
            return
        }
        // Add the members to the member list
        val members = group.members.map { it.serialize() }.toMutableSet()
        members.addAll(newMembers)
        val membersAsData = members.map { Hex.fromStringCondensed(it) }
        // Generate ratchets for the new members
        val senderKeys: List<ClosedGroupSenderKey> = newMembers.map { publicKey ->
            val ratchet = SharedSenderKeysImplementation.shared.generateRatchet(groupPublicKey, publicKey)
            ClosedGroupSenderKey(Hex.fromStringCondensed(ratchet.chainKey), ratchet.keyIndex, Hex.fromStringCondensed(publicKey))
        }
        // Send a closed group update message to the existing members with the new members' ratchets (this message is aimed at the group)
        val closedGroupUpdateKind = ClosedGroupUpdateMessageSendJob.Kind.Info(Hex.fromStringCondensed(groupPublicKey), name,
            senderKeys, membersAsData, admins)
        val job = ClosedGroupUpdateMessageSendJob(groupPublicKey, closedGroupUpdateKind)
        ApplicationContext.getInstance(context).jobManager.add(job)
        // Establish sessions if needed
        establishSessionsWithMembersIfNeeded(context, newMembers)
        // Send closed group update messages to the new members using established channels
        for (member in members) {
            @Suppress("NAME_SHADOWING")
            val closedGroupUpdateKind = ClosedGroupUpdateMessageSendJob.Kind.Info(Hex.fromStringCondensed(groupPublicKey), name,
                senderKeys, membersAsData, admins)
            @Suppress("NAME_SHADOWING")
            val job = ClosedGroupUpdateMessageSendJob(member, closedGroupUpdateKind)
            ApplicationContext.getInstance(context).jobManager.add(job)
        }
        // Update the group
        groupDB.updateMembers(groupID, members.map { Address.fromSerialized(it) })
        // Notify the user
        // TODO: Implement
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
        if (GroupUtil.isRSSFeed(groupID)) { return listOf() }
        if (GroupUtil.isOpenGroup(groupID)) {
            return listOf( Address.fromSerialized(groupID) )
        } else {
            // TODO: Shared sender keys
            return DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupID, false).map { it.address }
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
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        val message = GroupUtil.createGroupLeaveMessage(context, recipient).orNull()
        if (threadID < 0 || message == null) { return false }
        MessageSender.send(context, message, threadID, false, null)
        /*
        val masterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
        val publicKeyToRemove = masterPublicKey ?: TextSecurePreferences.getLocalNumber(context)
         */
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val groupDatabase = DatabaseFactory.getGroupDatabase(context)
        val groupID = recipient.address.toGroupString()
        groupDatabase.setActive(groupID, false)
        groupDatabase.remove(groupID, Address.fromSerialized(userPublicKey))
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
}