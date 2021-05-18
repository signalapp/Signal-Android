package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import android.util.Log
import com.google.protobuf.ByteString
import org.session.libsession.messaging.sending_receiving.*
import org.session.libsignal.crypto.ecc.DjbECPrivateKey
import org.session.libsignal.crypto.ecc.DjbECPublicKey
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.messages.SignalServiceGroup
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.protos.SignalServiceProtos.DataMessage
import org.session.libsignal.utilities.removing05PrefixIfNeeded
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.loki.api.LokiPushNotificationManager
import org.thoughtcrime.securesms.loki.api.LokiPushNotificationManager.ClosedGroupOperation
import org.thoughtcrime.securesms.loki.database.LokiAPIDatabase

import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupRecord
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences

import java.util.*

object ClosedGroupsProtocolV2 {

    @JvmStatic
    fun handleMessage(context: Context, closedGroupUpdate: DataMessage.ClosedGroupControlMessage, sentTimestamp: Long, groupPublicKey: String, senderPublicKey: String) {
        if (!isValid(context, closedGroupUpdate, senderPublicKey, sentTimestamp)) { return }
        when (closedGroupUpdate.type) {
            DataMessage.ClosedGroupControlMessage.Type.NEW -> handleNewClosedGroup(context, closedGroupUpdate, senderPublicKey, sentTimestamp)
            DataMessage.ClosedGroupControlMessage.Type.MEMBERS_REMOVED -> handleClosedGroupMembersRemoved(context, closedGroupUpdate, sentTimestamp, groupPublicKey, senderPublicKey)
            DataMessage.ClosedGroupControlMessage.Type.MEMBERS_ADDED -> handleClosedGroupMembersAdded(context, closedGroupUpdate, sentTimestamp, groupPublicKey, senderPublicKey)
            DataMessage.ClosedGroupControlMessage.Type.NAME_CHANGE -> handleClosedGroupNameChange(context, closedGroupUpdate, sentTimestamp, groupPublicKey, senderPublicKey)
            DataMessage.ClosedGroupControlMessage.Type.MEMBER_LEFT -> handleClosedGroupMemberLeft(context, sentTimestamp, groupPublicKey, senderPublicKey)
            DataMessage.ClosedGroupControlMessage.Type.ENCRYPTION_KEY_PAIR -> handleGroupEncryptionKeyPair(context, closedGroupUpdate, groupPublicKey, senderPublicKey)
            else -> {
                Log.d("Loki","Can't handle closed group update of unknown type: ${closedGroupUpdate.type}")
            }
        }
    }

    private fun isValid(context: Context, closedGroupUpdate: DataMessage.ClosedGroupControlMessage, senderPublicKey: String, sentTimestamp: Long): Boolean {
        val record = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(sentTimestamp, senderPublicKey)
        if (record != null) return false

        return when (closedGroupUpdate.type) {
            DataMessage.ClosedGroupControlMessage.Type.NEW -> {
                (!closedGroupUpdate.publicKey.isEmpty && !closedGroupUpdate.name.isNullOrEmpty() && !(closedGroupUpdate.encryptionKeyPair.privateKey ?: ByteString.copyFrom(ByteArray(0))).isEmpty
                        && !(closedGroupUpdate.encryptionKeyPair.publicKey ?: ByteString.copyFrom(ByteArray(0))).isEmpty && closedGroupUpdate.membersCount > 0 && closedGroupUpdate.adminsCount > 0)
            }
            DataMessage.ClosedGroupControlMessage.Type.MEMBERS_ADDED,
            DataMessage.ClosedGroupControlMessage.Type.MEMBERS_REMOVED -> {
                closedGroupUpdate.membersCount > 0
            }
            DataMessage.ClosedGroupControlMessage.Type.MEMBER_LEFT -> {
                senderPublicKey.isNotEmpty()
            }
            DataMessage.ClosedGroupControlMessage.Type.NAME_CHANGE -> {
                !closedGroupUpdate.name.isNullOrEmpty()
            }
            DataMessage.ClosedGroupControlMessage.Type.ENCRYPTION_KEY_PAIR -> true
            else -> false
        }
    }

    public fun handleNewClosedGroup(context: Context, closedGroupUpdate: DataMessage.ClosedGroupControlMessage, senderPublicKey: String, sentTimestamp: Long) {
        // Prepare
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
        val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
        // Unwrap the message
        val groupPublicKey = closedGroupUpdate.publicKey.toByteArray().toHexString()
        val name = closedGroupUpdate.name
        val encryptionKeyPairAsProto = closedGroupUpdate.encryptionKeyPair
        val members = closedGroupUpdate.membersList.map { it.toByteArray().toHexString() }
        val admins = closedGroupUpdate.adminsList.map { it.toByteArray().toHexString() }
        // Create the group
        val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
        val groupDB = DatabaseFactory.getGroupDatabase(context)
        val prevGroup = groupDB.getGroup(groupID).orNull()
        if (prevGroup != null) {
            // Update the group
            groupDB.updateTitle(groupID, name)
            groupDB.updateMembers(groupID, members.map { Address.fromSerialized(it) })
        } else {
            groupDB.create(groupID, name, LinkedList(members.map { Address.fromSerialized(it) }),
                    null, null, LinkedList(admins.map { Address.fromSerialized(it) }), sentTimestamp)
        }
        DatabaseFactory.getRecipientDatabase(context).setProfileSharing(Recipient.from(context, Address.fromSerialized(groupID), false), true)
        // Add the group to the user's set of public keys to poll for
        apiDB.addClosedGroupPublicKey(groupPublicKey)
        // Store the encryption key pair
        val encryptionKeyPair = ECKeyPair(DjbECPublicKey(encryptionKeyPairAsProto.publicKey.toByteArray().removing05PrefixIfNeeded()), DjbECPrivateKey(encryptionKeyPairAsProto.privateKey.toByteArray()))
        apiDB.addClosedGroupEncryptionKeyPair(encryptionKeyPair, groupPublicKey)
        // Notify the user (if we didn't make the group)
        if (userPublicKey != senderPublicKey) {
            DatabaseFactory.getStorage(context).insertIncomingInfoMessage(context, senderPublicKey, groupID, SignalServiceGroup.Type.UPDATE, name, members, admins, sentTimestamp)
        } else if (prevGroup == null) {
            // only notify if we created this group
            val threadID = DatabaseFactory.getLokiThreadDatabase(context).getThreadID(groupID)
            DatabaseFactory.getStorage(context).insertOutgoingInfoMessage(context, groupID, SignalServiceGroup.Type.UPDATE, name, members, admins, threadID, sentTimestamp)
        }
        // Notify the PN server
        LokiPushNotificationManager.performOperation(context, ClosedGroupOperation.Subscribe, groupPublicKey, userPublicKey)
    }

    fun handleClosedGroupMembersRemoved(context: Context, closedGroupUpdate: DataMessage.ClosedGroupControlMessage, sentTimestamp: Long, groupPublicKey: String, senderPublicKey: String) {
        val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
        val groupDB = DatabaseFactory.getGroupDatabase(context)
        val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
        val group = groupDB.getGroup(groupID).orNull()
        if (group == null || !group.isActive) {
            Log.d("Loki", "Ignoring closed group info message for nonexistent or inactive group.")
            return
        }
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
        val name = group.title
        // Check common group update logic
        val members = group.members.map { it.serialize() }
        val admins = group.admins.map { it.toString() }

        // Users that are part of this remove update
        val updateMembers = closedGroupUpdate.membersList.map { it.toByteArray().toHexString() }

        if (!isValidGroupUpdate(group, sentTimestamp, senderPublicKey)) {
            return
        }
        // If admin leaves the group is disbanded
        val didAdminLeave = admins.any { it in updateMembers }
        // newMembers to save is old members minus removed members
        val newMembers = members - updateMembers
        // user should be posting MEMBERS_LEFT so this should not be encountered
        val senderLeft = senderPublicKey in updateMembers
        if (senderLeft) {
            Log.d("Loki", "Received a MEMBERS_REMOVED instead of a MEMBERS_LEFT from sender $senderPublicKey")
        }
        val wasCurrentUserRemoved = userPublicKey in updateMembers

        // admin should send a MEMBERS_LEFT message but handled here in case
        if (didAdminLeave || wasCurrentUserRemoved) {
            disableLocalGroupAndUnsubscribe(context, apiDB, groupPublicKey, groupDB, groupID, userPublicKey)
        } else {
            val isCurrentUserAdmin = admins.contains(userPublicKey)
            groupDB.updateMembers(groupID, newMembers.map { Address.fromSerialized(it) })
            if (isCurrentUserAdmin) {
                MessageSender.generateAndSendNewEncryptionKeyPair(groupPublicKey, newMembers)
            }
        }
        val type =
                if (senderLeft) SignalServiceGroup.Type.QUIT
                else SignalServiceGroup.Type.UPDATE
        if (userPublicKey == senderPublicKey) {
            val threadID = DatabaseFactory.getLokiThreadDatabase(context).getThreadID(groupID)
            DatabaseFactory.getStorage(context).insertOutgoingInfoMessage(context, groupID, type, name, members, admins, threadID, sentTimestamp)
        } else {
            DatabaseFactory.getStorage(context).insertIncomingInfoMessage(context, senderPublicKey, groupID, type, name, members, admins, sentTimestamp)
        }
    }

    fun handleClosedGroupMembersAdded(context: Context, closedGroupUpdate: DataMessage.ClosedGroupControlMessage, sentTimestamp: Long, groupPublicKey: String, senderPublicKey: String) {
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
        val groupDB = DatabaseFactory.getGroupDatabase(context)
        val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
        val group = groupDB.getGroup(groupID).orNull()
        if (group == null || !group.isActive) {
            Log.d("Loki", "Ignoring closed group info message for nonexistent or inactive group.")
            return
        }
        // Check common group update logic
        if (!isValidGroupUpdate(group, sentTimestamp, senderPublicKey)) {
            return
        }
        val name = group.title
        val members = group.members.map { it.serialize() }
        val admins = group.admins.map { it.serialize() }
        // Users that are part of this add update
        val updateMembers = closedGroupUpdate.membersList.map { it.toByteArray().toHexString() }
        // newMembers to save is old members plus members included in this update
        val newMembers = members + updateMembers
        groupDB.updateMembers(groupID, newMembers.map { Address.fromSerialized(it) })
        if (userPublicKey == senderPublicKey) {
            val threadID = DatabaseFactory.getLokiThreadDatabase(context).getThreadID(groupID)
            DatabaseFactory.getStorage(context).insertOutgoingInfoMessage(context, groupID, SignalServiceGroup.Type.UPDATE, name, members, admins, threadID, sentTimestamp)
        } else {
            DatabaseFactory.getStorage(context).insertIncomingInfoMessage(context, senderPublicKey, groupID, SignalServiceGroup.Type.UPDATE, name, members, admins, sentTimestamp)
        }
        if (userPublicKey in admins) {
            // send current encryption key to the latest added members
            val encryptionKeyPair = pendingKeyPairs[groupPublicKey]?.orNull()
                ?: apiDB.getLatestClosedGroupEncryptionKeyPair(groupPublicKey)
            if (encryptionKeyPair == null) {
                Log.d("Loki", "Couldn't get encryption key pair for closed group.")
            } else {
                for (user in updateMembers) {
                    MessageSender.sendEncryptionKeyPair(groupPublicKey, encryptionKeyPair, setOf(user), targetUser = user, force = false)
                }
            }
        }
    }

    fun handleClosedGroupNameChange(context: Context, closedGroupUpdate: DataMessage.ClosedGroupControlMessage, sentTimestamp: Long, groupPublicKey: String, senderPublicKey: String) {
        // Check that the sender is a member of the group (before the update)
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val groupDB = DatabaseFactory.getGroupDatabase(context)
        val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
        val group = groupDB.getGroup(groupID).orNull()
        if (group == null || !group.isActive) {
            Log.d("Loki", "Ignoring closed group info message for nonexistent or inactive group.")
            return
        }
        // Check common group update logic
        if (!isValidGroupUpdate(group, sentTimestamp, senderPublicKey)) {
            return
        }
        val members = group.members.map { it.serialize() }
        val admins = group.admins.map { it.serialize() }
        val name = closedGroupUpdate.name
        groupDB.updateTitle(groupID, name)
        // Notify the user
        if (userPublicKey == senderPublicKey) {
            val threadID = DatabaseFactory.getLokiThreadDatabase(context).getThreadID(groupID)
            DatabaseFactory.getStorage(context).insertOutgoingInfoMessage(context, groupID, SignalServiceGroup.Type.UPDATE, name, members, admins, threadID, sentTimestamp)
        } else {
            DatabaseFactory.getStorage(context).insertIncomingInfoMessage(context, senderPublicKey, groupID, SignalServiceGroup.Type.UPDATE, name, members, admins, sentTimestamp)
        }
    }

    private fun handleClosedGroupMemberLeft(context: Context, sentTimestamp: Long, groupPublicKey: String, senderPublicKey: String) {
        // Check the user leaving isn't us, will already be handled
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
        val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
        val groupDB = DatabaseFactory.getGroupDatabase(context)
        val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
        val group = groupDB.getGroup(groupID).orNull()
        if (group == null || !group.isActive) {
            Log.d("Loki", "Ignoring closed group info message for nonexistent or inactive group.")
            return
        }
        val name = group.title
        // Check common group update logic
        val members = group.members.map { it.serialize() }
        val admins = group.admins.map { it.toString() }
        if (!isValidGroupUpdate(group, sentTimestamp, senderPublicKey)) {
            return
        }
        // If the admin leaves the group is disbanded
        val didAdminLeave = admins.contains(senderPublicKey)
        val updatedMemberList = members - senderPublicKey
        val userLeft = (userPublicKey == senderPublicKey)

        // if the admin left, we left, or we are the only remaining member: remove the group
        if (didAdminLeave || userLeft) {
            disableLocalGroupAndUnsubscribe(context, apiDB, groupPublicKey, groupDB, groupID, userPublicKey)
        } else {
            val isCurrentUserAdmin = admins.contains(userPublicKey)
            groupDB.updateMembers(groupID, updatedMemberList.map { Address.fromSerialized(it) })
            if (isCurrentUserAdmin) {
                MessageSender.generateAndSendNewEncryptionKeyPair(groupPublicKey, updatedMemberList)
            }
        }
        // Notify user
        if (userLeft) {
            val threadID = DatabaseFactory.getLokiThreadDatabase(context).getThreadID(groupID)
            DatabaseFactory.getStorage(context).insertOutgoingInfoMessage(context, groupID, SignalServiceGroup.Type.QUIT, name, members, admins, threadID, sentTimestamp)
        } else {
            DatabaseFactory.getStorage(context).insertIncomingInfoMessage(context, senderPublicKey, groupID, SignalServiceGroup.Type.QUIT, name, members, admins, sentTimestamp)
        }
    }

    private fun disableLocalGroupAndUnsubscribe(context: Context, apiDB: LokiAPIDatabase, groupPublicKey: String, groupDB: GroupDatabase, groupID: String, userPublicKey: String) {
        apiDB.removeClosedGroupPublicKey(groupPublicKey)
        // Remove the key pairs
        apiDB.removeAllClosedGroupEncryptionKeyPairs(groupPublicKey)
        // Mark the group as inactive
        groupDB.setActive(groupID, false)
        groupDB.removeMember(groupID, Address.fromSerialized(userPublicKey))
        // Notify the PN server
        LokiPushNotificationManager.performOperation(context, ClosedGroupOperation.Unsubscribe, groupPublicKey, userPublicKey)
    }

    private fun isValidGroupUpdate(group: GroupRecord,
                                   sentTimestamp: Long,
                                   senderPublicKey: String): Boolean {
        val oldMembers = group.members.map { it.serialize() }
        // Check that the message isn't from before the group was created
        // TODO: We should check that formationTimestamp is the sent timestamp of the closed group update that created the group
        if (group.formationTimestamp > sentTimestamp) {
            Log.d("Loki", "Ignoring closed group update from before thread was created.")
            return false
        }
        // Check that the sender is a member of the group (before the update)
        if (senderPublicKey !in oldMembers) {
            Log.d("Loki", "Ignoring closed group info message from non-member.")
            return false
        }
        return true
    }

    private fun handleGroupEncryptionKeyPair(context: Context, closedGroupUpdate: DataMessage.ClosedGroupControlMessage, groupPublicKey: String, senderPublicKey: String) {
        // Prepare
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
        val userKeyPair = apiDB.getUserX25519KeyPair()
        // Unwrap the message
        val groupDB = DatabaseFactory.getGroupDatabase(context)
        val groupPublicKeyToUse = when {
            groupPublicKey.isNotEmpty() -> groupPublicKey
            !closedGroupUpdate.publicKey.isEmpty -> closedGroupUpdate.publicKey.toByteArray().toHexString()
            else -> ""
        }
        val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKeyToUse)
        val group = groupDB.getGroup(groupID).orNull()
        if (group == null) {
            Log.d("Loki", "Ignoring closed group encryption key pair message for nonexistent group.")
            return
        }
        if (!group.admins.map { it.toString() }.contains(senderPublicKey)) {
            Log.d("Loki", "Ignoring closed group encryption key pair from non-admin.")
            return
        }
        // Find our wrapper and decrypt it if possible
        val wrapper = closedGroupUpdate.wrappersList.firstOrNull { it.publicKey.toByteArray().toHexString() == userPublicKey } ?: return
        val encryptedKeyPair = wrapper.encryptedKeyPair.toByteArray()
        val plaintext = MessageDecrypter.decrypt(encryptedKeyPair, userKeyPair).first
        // Parse it
        val proto = SignalServiceProtos.KeyPair.parseFrom(plaintext)
        val keyPair = ECKeyPair(DjbECPublicKey(proto.publicKey.toByteArray().removing05PrefixIfNeeded()), DjbECPrivateKey(proto.privateKey.toByteArray()))
        // Store it
        apiDB.addClosedGroupEncryptionKeyPair(keyPair, groupPublicKeyToUse)
        Log.d("Loki", "Received a new closed group encryption key pair")
    }

    // region Deprecated
    private fun handleClosedGroupUpdate(context: Context, closedGroupUpdate: DataMessage.ClosedGroupControlMessage, sentTimestamp: Long, groupPublicKey: String, senderPublicKey: String) {
        // Prepare
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
        val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
        // Unwrap the message
        val name = closedGroupUpdate.name
        val members = closedGroupUpdate.membersList.map { it.toByteArray().toHexString() }
        val groupDB = DatabaseFactory.getGroupDatabase(context)
        val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
        val group = groupDB.getGroup(groupID).orNull()
        if (group == null || !group.isActive) {
            Log.d("Loki", "Ignoring closed group info message for nonexistent or inactive group.")
            return
        }
        val oldMembers = group.members.map { it.serialize() }
        // Check common group update logic
        if (!isValidGroupUpdate(group, sentTimestamp, senderPublicKey)) {
            return
        }
        // Check that the admin wasn't removed unless the group was destroyed entirely
        if (!members.contains(group.admins.first().toString()) && members.isNotEmpty()) {
            Log.d("Loki", "Ignoring invalid closed group update message.")
            return
        }
        // Remove the group from the user's set of public keys to poll for if the current user was removed
        val wasCurrentUserRemoved = !members.contains(userPublicKey)
        if (wasCurrentUserRemoved) {
            disableLocalGroupAndUnsubscribe(context, apiDB, groupPublicKey, groupDB, groupID, userPublicKey)
        }
        // Generate and distribute a new encryption key pair if needed
        val wasAnyUserRemoved = (members.toSet().intersect(oldMembers) != oldMembers.toSet())
        val isCurrentUserAdmin = group.admins.map { it.toString() }.contains(userPublicKey)
        if (wasAnyUserRemoved && isCurrentUserAdmin) {
            MessageSender.generateAndSendNewEncryptionKeyPair(groupPublicKey, members)
        }
        // Update the group
        groupDB.updateTitle(groupID, name)
        if (!wasCurrentUserRemoved) {
            // The call below sets isActive to true, so if the user is leaving we have to use groupDB.remove(...) instead
            groupDB.updateMembers(groupID, members.map { Address.fromSerialized(it) })
        }
        // Notify the user
        val wasSenderRemoved = !members.contains(senderPublicKey)
        val type = if (wasSenderRemoved) SignalServiceGroup.Type.QUIT else SignalServiceGroup.Type.UPDATE
        val admins = group.admins.map { it.toString() }
        if (userPublicKey == senderPublicKey) {
            val threadID = DatabaseFactory.getLokiThreadDatabase(context).getThreadID(groupID)
            DatabaseFactory.getStorage(context).insertOutgoingInfoMessage(context, groupID, type, name, members, admins, threadID, sentTimestamp)
        } else {
            DatabaseFactory.getStorage(context).insertIncomingInfoMessage(context, senderPublicKey, groupID, type, name, members, admins, sentTimestamp)
        }
    }
    // endregion
}