package org.thoughtcrime.securesms.database

import android.content.Context
import android.net.Uri
import okhttp3.HttpUrl
import org.session.libsession.messaging.StorageProtocol
import org.session.libsession.messaging.jobs.AttachmentUploadJob
import org.session.libsession.messaging.jobs.Job
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageSendJob
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.messaging.messages.signal.*
import org.session.libsession.messaging.messages.signal.IncomingTextMessage
import org.session.libsession.messaging.messages.visible.Attachment
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.open_groups.OpenGroupV2
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.threads.Address.Companion.fromSerialized
import org.session.libsession.messaging.threads.GroupRecord
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsession.messaging.utilities.UpdateMessageData
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.IdentityKeyUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.preferences.ProfileKeyUtil
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.utilities.KeyHelper
import org.session.libsignal.utilities.guava.Optional
import org.session.libsignal.messages.SignalServiceAttachmentPointer
import org.session.libsignal.messages.SignalServiceGroup
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.thoughtcrime.securesms.loki.database.LokiThreadDatabase
import org.thoughtcrime.securesms.loki.protocol.SessionMetaProtocol
import org.thoughtcrime.securesms.loki.utilities.OpenGroupUtilities
import org.thoughtcrime.securesms.loki.utilities.get
import org.thoughtcrime.securesms.loki.utilities.getString
import org.thoughtcrime.securesms.mms.PartAuthority

class Storage(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), StorageProtocol {
    override fun getUserPublicKey(): String? {
        return TextSecurePreferences.getLocalNumber(context)
    }

    override fun getUserKeyPair(): Pair<String, ByteArray>? {
        val userPublicKey = TextSecurePreferences.getLocalNumber(context) ?: return null
        val userPrivateKey = IdentityKeyUtil.getIdentityKeyPair(context).privateKey.serialize()
        return Pair(userPublicKey, userPrivateKey)
    }

    override fun getUserX25519KeyPair(): ECKeyPair {
        return DatabaseFactory.getLokiAPIDatabase(context).getUserX25519KeyPair()
    }

    override fun getUserDisplayName(): String? {
        return TextSecurePreferences.getProfileName(context)
    }

    override fun getUserProfileKey(): ByteArray? {
        return ProfileKeyUtil.getProfileKey(context)
    }

    override fun getUserProfilePictureURL(): String? {
        return TextSecurePreferences.getProfilePictureURL(context)
    }

    override fun setUserProfilePictureUrl(newProfilePicture: String) {
        val ourRecipient = Address.fromSerialized(getUserPublicKey()!!).let {
            Recipient.from(context, it, false)
        }
        TextSecurePreferences.setProfilePictureURL(context, newProfilePicture)
        RetrieveProfileAvatarJob(ourRecipient, newProfilePicture)
        ApplicationContext.getInstance(context).jobManager.add(RetrieveProfileAvatarJob(ourRecipient, newProfilePicture))
    }

    override fun getProfileKeyForRecipient(recipientPublicKey: String): ByteArray? {
        val address = Address.fromSerialized(recipientPublicKey)
        val recipient = Recipient.from(context, address, false)
        return recipient.profileKey
    }

    override fun getDisplayNameForRecipient(recipientPublicKey: String): String? {
        val database = DatabaseFactory.getLokiUserDatabase(context)
        return database.getDisplayName(recipientPublicKey)
    }

    override fun setProfileKeyForRecipient(recipientPublicKey: String, profileKey: ByteArray) {
        val address = Address.fromSerialized(recipientPublicKey)
        val recipient = Recipient.from(context, address, false)
        DatabaseFactory.getRecipientDatabase(context).setProfileKey(recipient, profileKey)
    }

    override fun getOrGenerateRegistrationID(): Int {
        var registrationID = TextSecurePreferences.getLocalRegistrationId(context)
        if (registrationID == 0) {
            registrationID = KeyHelper.generateRegistrationId(false)
            TextSecurePreferences.setLocalRegistrationId(context, registrationID)
        }
        return registrationID
    }

    override fun persistAttachments(messageId: Long, attachments: List<Attachment>): List<Long> {
        val database = DatabaseFactory.getAttachmentDatabase(context)
        val databaseAttachments = attachments.mapNotNull { it.toSignalAttachment() }
        return database.insertAttachments(messageId, databaseAttachments)
    }

    override fun getAttachmentsForMessage(messageId: Long): List<DatabaseAttachment> {
        val database = DatabaseFactory.getAttachmentDatabase(context)
        return database.getAttachmentsForMessage(messageId)
    }

    override fun persist(message: VisibleMessage, quotes: QuoteModel?, linkPreview: List<LinkPreview?>, groupPublicKey: String?, openGroupID: String?, attachments: List<Attachment>): Long? {
        var messageID: Long? = null
        val senderAddress = Address.fromSerialized(message.sender!!)
        val isUserSender = message.sender!! == getUserPublicKey()
        val group: Optional<SignalServiceGroup> = when {
            openGroupID != null -> Optional.of(SignalServiceGroup(openGroupID.toByteArray(), SignalServiceGroup.GroupType.PUBLIC_CHAT))
            groupPublicKey != null -> {
                val doubleEncoded = GroupUtil.doubleEncodeGroupID(groupPublicKey)
                Optional.of(SignalServiceGroup(GroupUtil.getDecodedGroupIDAsData(doubleEncoded), SignalServiceGroup.GroupType.SIGNAL))
            }
            else -> Optional.absent()
        }
        val pointerAttachments = attachments.mapNotNull {
            it.toSignalAttachment()
        }
        val targetAddress = if (isUserSender && !message.syncTarget.isNullOrEmpty()) {
            Address.fromSerialized(message.syncTarget!!)
        } else if (group.isPresent) {
            Address.fromSerialized(GroupUtil.getEncodedId(group.get()))
        } else {
            senderAddress
        }
        val targetRecipient = Recipient.from(context, targetAddress, false)

        if (message.isMediaMessage() || attachments.isNotEmpty()) {
            val quote: Optional<QuoteModel> = if (quotes != null) Optional.of(quotes) else Optional.absent()
            val linkPreviews: Optional<List<LinkPreview>> = if (linkPreview.isEmpty()) Optional.absent() else Optional.of(linkPreview.mapNotNull { it!! })
            val mmsDatabase = DatabaseFactory.getMmsDatabase(context)
            val insertResult = if (message.sender == getUserPublicKey()) {
                val mediaMessage = OutgoingMediaMessage.from(message, targetRecipient, pointerAttachments, quote.orNull(), linkPreviews.orNull()?.firstOrNull())
                mmsDatabase.beginTransaction()
                mmsDatabase.insertSecureDecryptedMessageOutbox(mediaMessage, message.threadID ?: -1, message.sentTimestamp!!)
            } else {
                // It seems like we have replaced SignalServiceAttachment with SessionServiceAttachment
                val signalServiceAttachments = attachments.mapNotNull {
                    it.toSignalPointer()
                }
                val mediaMessage = IncomingMediaMessage.from(message, senderAddress, targetRecipient.expireMessages * 1000L, group, signalServiceAttachments, quote, linkPreviews)
                mmsDatabase.beginTransaction()
                mmsDatabase.insertSecureDecryptedMessageInbox(mediaMessage, message.threadID ?: -1, message.receivedTimestamp ?: 0)
            }
            if (insertResult.isPresent) {
                mmsDatabase.setTransactionSuccessful()
                messageID = insertResult.get().messageId
            }
            mmsDatabase.endTransaction()
        } else {
            val smsDatabase = DatabaseFactory.getSmsDatabase(context)
            val isOpenGroupInvitation = (message.openGroupInvitation != null)

            val insertResult = if (message.sender == getUserPublicKey()) {
                val textMessage = if (isOpenGroupInvitation) OutgoingTextMessage.fromOpenGroupInvitation(message.openGroupInvitation, targetRecipient, message.sentTimestamp)
                else OutgoingTextMessage.from(message, targetRecipient)
                smsDatabase.insertMessageOutbox(message.threadID ?: -1, textMessage, message.sentTimestamp!!)
            } else {
                val textMessage = if (isOpenGroupInvitation) IncomingTextMessage.fromOpenGroupInvitation(message.openGroupInvitation, senderAddress, message.sentTimestamp)
                else IncomingTextMessage.from(message, senderAddress, group, targetRecipient.expireMessages * 1000L)
                val encrypted = IncomingEncryptedMessage(textMessage, textMessage.messageBody)
                smsDatabase.insertMessageInbox(encrypted, message.receivedTimestamp ?: 0)
            }
            insertResult.orNull()?.let { result ->
                messageID = result.messageId
            }
        }
        return messageID
    }

    // JOBS
    override fun persistJob(job: Job) {
        DatabaseFactory.getSessionJobDatabase(context).persistJob(job)
    }

    override fun markJobAsSucceeded(jobId: String) {
        DatabaseFactory.getSessionJobDatabase(context).markJobAsSucceeded(jobId)
    }

    override fun markJobAsFailedPermanently(jobId: String) {
        DatabaseFactory.getSessionJobDatabase(context).markJobAsFailedPermanently(jobId)
    }

    override fun getAllPendingJobs(type: String): Map<String, Job?> {
        return DatabaseFactory.getSessionJobDatabase(context).getAllPendingJobs(type)
    }

    override fun getAttachmentUploadJob(attachmentID: Long): AttachmentUploadJob? {
        return DatabaseFactory.getSessionJobDatabase(context).getAttachmentUploadJob(attachmentID)
    }

    override fun getMessageSendJob(messageSendJobID: String): MessageSendJob? {
        return DatabaseFactory.getSessionJobDatabase(context).getMessageSendJob(messageSendJobID)
    }

    override fun resumeMessageSendJobIfNeeded(messageSendJobID: String) {
        val job = DatabaseFactory.getSessionJobDatabase(context).getMessageSendJob(messageSendJobID) ?: return
        JobQueue.shared.add(job)
    }

    override fun isJobCanceled(job: Job): Boolean {
        return DatabaseFactory.getSessionJobDatabase(context).isJobCanceled(job)
    }

    // Authorization

    override fun getAuthToken(server: String): String? {
        return DatabaseFactory.getLokiAPIDatabase(context).getAuthToken(server)
    }

    override fun setAuthToken(server: String, newValue: String?) {
        DatabaseFactory.getLokiAPIDatabase(context).setAuthToken(server, newValue)
    }

    override fun removeAuthToken(server: String) {
        DatabaseFactory.getLokiAPIDatabase(context).setAuthToken(server, null)
    }

    override fun getAuthToken(room: String, server: String): String? {
        val id = "$server.$room"
        return DatabaseFactory.getLokiAPIDatabase(context).getAuthToken(id)
    }

    override fun setAuthToken(room: String, server: String, newValue: String) {
        val id = "$server.$room"
        DatabaseFactory.getLokiAPIDatabase(context).setAuthToken(id, newValue)
    }

    override fun removeAuthToken(room: String, server: String) {
        val id = "$server.$room"
        DatabaseFactory.getLokiAPIDatabase(context).setAuthToken(id, null)
    }

    override fun getOpenGroup(threadID: String): OpenGroup? {
        if (threadID.toInt() < 0) { return null }
        val database = databaseHelper.readableDatabase
        return database.get(LokiThreadDatabase.publicChatTable, "${LokiThreadDatabase.threadID} = ?", arrayOf(threadID)) { cursor ->
            val publicChatAsJSON = cursor.getString(LokiThreadDatabase.publicChat)
            OpenGroup.fromJSON(publicChatAsJSON)
        }
    }

    override fun getV2OpenGroup(threadId: String): OpenGroupV2? {
        if (threadId.toInt() < 0) { return null }
        val database = databaseHelper.readableDatabase
        return database.get(LokiThreadDatabase.publicChatTable, "${LokiThreadDatabase.threadID} = ?", arrayOf(threadId)) { cursor ->
            val publicChatAsJson = cursor.getString(LokiThreadDatabase.publicChat)
            OpenGroupV2.fromJSON(publicChatAsJson)
        }
    }

    override fun getThreadID(openGroupID: String): String {
        val address = Address.fromSerialized(openGroupID)
        val recipient = Recipient.from(context, address, false)
        return DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(recipient).toString()
    }

    override fun getOpenGroupPublicKey(server: String): String? {
        return DatabaseFactory.getLokiAPIDatabase(context).getOpenGroupPublicKey(server)
    }

    override fun setOpenGroupPublicKey(server: String, newValue: String) {
        DatabaseFactory.getLokiAPIDatabase(context).setOpenGroupPublicKey(server, newValue)
    }

    override fun setOpenGroupDisplayName(publicKey: String, channel: Long, server: String, displayName: String) {
        val groupID = "$server.$channel"
        DatabaseFactory.getLokiUserDatabase(context).setServerDisplayName(groupID, publicKey, displayName)
    }

    override fun setOpenGroupDisplayName(publicKey: String, room: String, server: String, displayName: String) {
        val groupID = "$server.$room"
        DatabaseFactory.getLokiUserDatabase(context).setServerDisplayName(groupID, publicKey, displayName)
    }

    override fun getOpenGroupDisplayName(publicKey: String, channel: Long, server: String): String? {
        val groupID = "$server.$channel"
        return DatabaseFactory.getLokiUserDatabase(context).getServerDisplayName(groupID, publicKey)
    }

    override fun getOpenGroupDisplayName(publicKey: String, room: String, server: String): String? {
        val groupID = "$server.$room"
        return DatabaseFactory.getLokiUserDatabase(context).getServerDisplayName(groupID, publicKey)
    }

    override fun getLastMessageServerId(room: String, server: String): Long? {
        return DatabaseFactory.getLokiAPIDatabase(context).getLastMessageServerID(room, server)
    }

    override fun setLastMessageServerId(room: String, server: String, newValue: Long) {
        DatabaseFactory.getLokiAPIDatabase(context).setLastMessageServerID(room, server, newValue)
    }

    override fun removeLastMessageServerId(room: String, server: String) {
        DatabaseFactory.getLokiAPIDatabase(context).removeLastMessageServerID(room, server)
    }

    override fun getLastMessageServerID(group: Long, server: String): Long? {
        return DatabaseFactory.getLokiAPIDatabase(context).getLastMessageServerID(group, server)
    }

    override fun setLastMessageServerID(group: Long, server: String, newValue: Long) {
        DatabaseFactory.getLokiAPIDatabase(context).setLastMessageServerID(group, server, newValue)
    }

    override fun removeLastMessageServerID(group: Long, server: String) {
        DatabaseFactory.getLokiAPIDatabase(context).removeLastMessageServerID(group, server)
    }

    override fun getLastDeletionServerId(room: String, server: String): Long? {
        return DatabaseFactory.getLokiAPIDatabase(context).getLastDeletionServerID(room, server)
    }

    override fun setLastDeletionServerId(room: String, server: String, newValue: Long) {
        DatabaseFactory.getLokiAPIDatabase(context).setLastDeletionServerID(room, server, newValue)
    }

    override fun removeLastDeletionServerId(room: String, server: String) {
        DatabaseFactory.getLokiAPIDatabase(context).removeLastDeletionServerID(room, server)
    }

    override fun setUserCount(room: String, server: String, newValue: Int) {
        DatabaseFactory.getLokiAPIDatabase(context).setUserCount(room, server, newValue)
    }

    override fun getLastDeletionServerID(group: Long, server: String): Long? {
        return DatabaseFactory.getLokiAPIDatabase(context).getLastDeletionServerID(group, server)
    }

    override fun setLastDeletionServerID(group: Long, server: String, newValue: Long) {
        DatabaseFactory.getLokiAPIDatabase(context).setLastDeletionServerID(group, server, newValue)
    }

    override fun removeLastDeletionServerID(group: Long, server: String) {
        DatabaseFactory.getLokiAPIDatabase(context).removeLastDeletionServerID(group, server)
    }

    override fun isDuplicateMessage(timestamp: Long, sender: String): Boolean {
        return getReceivedMessageTimestamps().contains(timestamp)
    }

    override fun setUserCount(group: Long, server: String, newValue: Int) {
        DatabaseFactory.getLokiAPIDatabase(context).setUserCount(group, server, newValue)
    }

    override fun setOpenGroupProfilePictureURL(group: Long, server: String, newValue: String) {
        DatabaseFactory.getLokiAPIDatabase(context).setOpenGroupProfilePictureURL(group, server, newValue)
    }

    override fun getOpenGroupProfilePictureURL(group: Long, server: String): String? {
        return DatabaseFactory.getLokiAPIDatabase(context).getOpenGroupProfilePictureURL(group, server)
    }

    override fun updateTitle(groupID: String, newValue: String) {
        DatabaseFactory.getGroupDatabase(context).updateTitle(groupID, newValue)
    }

    override fun updateProfilePicture(groupID: String, newValue: ByteArray) {
        DatabaseFactory.getGroupDatabase(context).updateProfilePicture(groupID, newValue)
    }

    override fun getReceivedMessageTimestamps(): Set<Long> {
        return SessionMetaProtocol.getTimestamps()
    }

    override fun addReceivedMessageTimestamp(timestamp: Long) {
        SessionMetaProtocol.addTimestamp(timestamp)
    }

    override fun removeReceivedMessageTimestamps(timestamps: Set<Long>) {
        SessionMetaProtocol.removeTimestamps(timestamps)
    }

    override fun getMessageIdInDatabase(timestamp: Long, author: String): Long? {
        val database = DatabaseFactory.getMmsSmsDatabase(context)
        val address = Address.fromSerialized(author)
        return database.getMessageFor(timestamp, address)?.getId()
    }

    override fun setOpenGroupServerMessageID(messageID: Long, serverID: Long, threadID: Long, isSms: Boolean) {
        DatabaseFactory.getLokiMessageDatabase(context).setServerID(messageID, serverID, isSms)
        DatabaseFactory.getLokiMessageDatabase(context).setOriginalThreadID(messageID, serverID, threadID)
    }

    override fun getQuoteServerID(quoteID: Long, publicKey: String): Long? {
        return DatabaseFactory.getLokiMessageDatabase(context).getQuoteServerID(quoteID, publicKey)
    }

    override fun markAsSent(timestamp: Long, author: String) {
        val database = DatabaseFactory.getMmsSmsDatabase(context)
        val messageRecord = database.getMessageFor(timestamp, author) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseFactory.getMmsDatabase(context)
            mmsDatabase.markAsSent(messageRecord.getId(), true)
        } else {
            val smsDatabase = DatabaseFactory.getSmsDatabase(context)
            smsDatabase.markAsSent(messageRecord.getId(), true)
        }
    }

    override fun markUnidentified(timestamp: Long, author: String) {
        val database = DatabaseFactory.getMmsSmsDatabase(context)
        val messageRecord = database.getMessageFor(timestamp, author) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseFactory.getMmsDatabase(context)
            mmsDatabase.markUnidentified(messageRecord.getId(), true)
        } else {
            val smsDatabase = DatabaseFactory.getSmsDatabase(context)
            smsDatabase.markUnidentified(messageRecord.getId(), true)
        }
    }

    override fun setErrorMessage(timestamp: Long, author: String, error: Exception) {
        val database = DatabaseFactory.getMmsSmsDatabase(context)
        val messageRecord = database.getMessageFor(timestamp, author) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseFactory.getMmsDatabase(context)
            mmsDatabase.markAsSentFailed(messageRecord.getId())
        } else {
            val smsDatabase = DatabaseFactory.getSmsDatabase(context)
            smsDatabase.markAsSentFailed(messageRecord.getId())
        }
        if (error.localizedMessage != null) {
            DatabaseFactory.getLokiMessageDatabase(context).setErrorMessage(messageRecord.getId(), error.localizedMessage!!)
        } else {
            DatabaseFactory.getLokiMessageDatabase(context).setErrorMessage(messageRecord.getId(), error.javaClass.simpleName)
        }
    }

    override fun getGroup(groupID: String): GroupRecord? {
        val group = DatabaseFactory.getGroupDatabase(context).getGroup(groupID)
        return if (group.isPresent) { group.get() } else null
    }

    override fun createGroup(groupId: String, title: String?, members: List<Address>, avatar: SignalServiceAttachmentPointer?, relay: String?, admins: List<Address>, formationTimestamp: Long) {
        DatabaseFactory.getGroupDatabase(context).create(groupId, title, members, avatar, relay, admins, formationTimestamp)
    }

    override fun isGroupActive(groupPublicKey: String): Boolean {
        return DatabaseFactory.getGroupDatabase(context).getGroup(GroupUtil.doubleEncodeGroupID(groupPublicKey)).orNull()?.isActive == true
    }

    override fun setActive(groupID: String, value: Boolean) {
        DatabaseFactory.getGroupDatabase(context).setActive(groupID, value)
    }

    override fun getZombieMember(groupID: String): Set<String> {
        return DatabaseFactory.getGroupDatabase(context).getGroupZombieMembers(groupID).map { it.address.serialize() }.toHashSet()
    }

    override fun removeMember(groupID: String, member: Address) {
        DatabaseFactory.getGroupDatabase(context).removeMember(groupID, member)
    }

    override fun updateMembers(groupID: String, members: List<Address>) {
        DatabaseFactory.getGroupDatabase(context).updateMembers(groupID, members)
    }

    override fun updateZombieMembers(groupID: String, members: List<Address>) {
        DatabaseFactory.getGroupDatabase(context).updateZombieMembers(groupID, members)
    }

    override fun insertIncomingInfoMessage(context: Context, senderPublicKey: String, groupID: String, type: SignalServiceGroup.Type, name: String, members: Collection<String>, admins: Collection<String>, sentTimestamp: Long) {
        val group = SignalServiceGroup(type, GroupUtil.getDecodedGroupIDAsData(groupID), SignalServiceGroup.GroupType.SIGNAL, name, members.toList(), null, admins.toList())
        val m = IncomingTextMessage(Address.fromSerialized(senderPublicKey), 1, sentTimestamp, "", Optional.of(group), 0, true)
        val updateData = UpdateMessageData.buildGroupUpdate(type, name, members)?.toJSON()
        val infoMessage = IncomingGroupMessage(m, groupID, updateData, true)
        val smsDB = DatabaseFactory.getSmsDatabase(context)
        smsDB.insertMessageInbox(infoMessage)
    }

    override fun insertOutgoingInfoMessage(context: Context, groupID: String, type: SignalServiceGroup.Type, name: String, members: Collection<String>, admins: Collection<String>, threadID: Long, sentTimestamp: Long) {
        val userPublicKey = getUserPublicKey()
        val recipient = Recipient.from(context, Address.fromSerialized(groupID), false)

        val updateData = UpdateMessageData.buildGroupUpdate(type, name, members)?.toJSON() ?: ""
        val infoMessage = OutgoingGroupMediaMessage(recipient, updateData, groupID, null, sentTimestamp, 0, true, null, listOf(), listOf())
        val mmsDB = DatabaseFactory.getMmsDatabase(context)
        val mmsSmsDB = DatabaseFactory.getMmsSmsDatabase(context)
        if (mmsSmsDB.getMessageFor(sentTimestamp, userPublicKey) != null) return
        val infoMessageID = mmsDB.insertMessageOutbox(infoMessage, threadID, false, null)
        mmsDB.markAsSent(infoMessageID, true)
    }

    override fun isClosedGroup(publicKey: String): Boolean {
        val isClosedGroup = DatabaseFactory.getLokiAPIDatabase(context).isClosedGroup(publicKey)
        val address = Address.fromSerialized(publicKey)
        return address.isClosedGroup || isClosedGroup
    }

    override fun getClosedGroupEncryptionKeyPairs(groupPublicKey: String): MutableList<ECKeyPair> {
        return DatabaseFactory.getLokiAPIDatabase(context).getClosedGroupEncryptionKeyPairs(groupPublicKey).toMutableList()
    }

    override fun getLatestClosedGroupEncryptionKeyPair(groupPublicKey: String): ECKeyPair? {
        return DatabaseFactory.getLokiAPIDatabase(context).getLatestClosedGroupEncryptionKeyPair(groupPublicKey)
    }

    override fun getAllClosedGroupPublicKeys(): Set<String> {
        return DatabaseFactory.getLokiAPIDatabase(context).getAllClosedGroupPublicKeys()
    }

    override fun getAllActiveClosedGroupPublicKeys(): Set<String> {
        return DatabaseFactory.getLokiAPIDatabase(context).getAllClosedGroupPublicKeys().filter {
            getGroup(GroupUtil.doubleEncodeGroupID(it))?.isActive == true
        }.toSet()
    }

    override fun addClosedGroupPublicKey(groupPublicKey: String) {
        DatabaseFactory.getLokiAPIDatabase(context).addClosedGroupPublicKey(groupPublicKey)
    }

    override fun removeClosedGroupPublicKey(groupPublicKey: String) {
        DatabaseFactory.getLokiAPIDatabase(context).removeClosedGroupPublicKey(groupPublicKey)
    }

    override fun addClosedGroupEncryptionKeyPair(encryptionKeyPair: ECKeyPair, groupPublicKey: String) {
        DatabaseFactory.getLokiAPIDatabase(context).addClosedGroupEncryptionKeyPair(encryptionKeyPair, groupPublicKey)
    }

    override fun removeAllClosedGroupEncryptionKeyPairs(groupPublicKey: String) {
        DatabaseFactory.getLokiAPIDatabase(context).removeAllClosedGroupEncryptionKeyPairs(groupPublicKey)
    }

    override fun getAllOpenGroups(): Map<Long, OpenGroup> {
        return DatabaseFactory.getLokiThreadDatabase(context).getAllPublicChats().mapValues { (_,chat)->
            OpenGroup(chat.channel, chat.server, chat.displayName, chat.isDeletable)
        }
    }

    override fun getAllV2OpenGroups(): Map<Long, OpenGroupV2> {
        return DatabaseFactory.getLokiThreadDatabase(context).getAllV2OpenGroups()
    }

    override fun addOpenGroup(serverUrl: String, channel: Long) {
        val httpUrl = HttpUrl.parse(serverUrl) ?: return
        if (httpUrl.queryParameterNames().contains("public_key")) {
            // open group v2
            val server = HttpUrl.Builder().scheme(httpUrl.scheme()).host(httpUrl.host()).apply {
                if (httpUrl.port() != 80 || httpUrl.port() != 443) {
                    // non-standard port, add to server
                    this.port(httpUrl.port())
                }
            }.build()
            val room = httpUrl.pathSegments().firstOrNull() ?: return
            val publicKey = httpUrl.queryParameter("public_key") ?: return

            OpenGroupUtilities.addGroup(context, server.toString().removeSuffix("/"), room, publicKey)
        } else {
            OpenGroupUtilities.addGroup(context, serverUrl, channel)
        }
    }

    override fun getAllGroups(): List<GroupRecord> {
        return DatabaseFactory.getGroupDatabase(context).allGroups
    }

    override fun setProfileSharing(address: Address, value: Boolean) {
        val recipient = Recipient.from(context, address, false)
        DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipient, value)
    }

    override fun getOrCreateThreadIdFor(address: Address): Long {
        val recipient = Recipient.from(context, address, false)
        return DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(recipient)
    }

    override fun getOrCreateThreadIdFor(publicKey: String, groupPublicKey: String?, openGroupID: String?): Long {
        val database = DatabaseFactory.getThreadDatabase(context)
        if (!openGroupID.isNullOrEmpty()) {
            val recipient = Recipient.from(context, Address.fromSerialized(GroupUtil.getEncodedOpenGroupID(openGroupID.toByteArray())), false)
            return database.getThreadIdIfExistsFor(recipient)
        } else if (!groupPublicKey.isNullOrEmpty()) {
            val recipient = Recipient.from(context, Address.fromSerialized(GroupUtil.doubleEncodeGroupID(groupPublicKey)), false)
            return database.getOrCreateThreadIdFor(recipient)
        } else {
            val recipient = Recipient.from(context, Address.fromSerialized(publicKey), false)
            return database.getOrCreateThreadIdFor(recipient)
        }
    }

    override fun getThreadIdFor(address: Address): Long? {
        val recipient = Recipient.from(context, address, false)
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(recipient)
        return if (threadID < 0) null else threadID
    }

    override fun getThreadIdForMms(mmsId: Long): Long {
        val mmsDb = DatabaseFactory.getMmsDatabase(context)
        val cursor = mmsDb.getMessage(mmsId)
        val reader = mmsDb.readerFor(cursor)
        val threadId = reader.next.threadId
        cursor.close()
        return threadId
    }

    override fun getSessionRequestSentTimestamp(publicKey: String): Long? {
        return DatabaseFactory.getLokiAPIDatabase(context).getSessionRequestSentTimestamp(publicKey)
    }

    override fun setSessionRequestSentTimestamp(publicKey: String, newValue: Long) {
        DatabaseFactory.getLokiAPIDatabase(context).setSessionRequestSentTimestamp(publicKey, newValue)
    }

    override fun getSessionRequestProcessedTimestamp(publicKey: String): Long? {
        return DatabaseFactory.getLokiAPIDatabase(context).getSessionRequestProcessedTimestamp(publicKey)
    }

    override fun setSessionRequestProcessedTimestamp(publicKey: String, newValue: Long) {
        DatabaseFactory.getLokiAPIDatabase(context).setSessionRequestProcessedTimestamp(publicKey, newValue)
    }

    override fun getDisplayName(publicKey: String): String? {
        return DatabaseFactory.getLokiUserDatabase(context).getDisplayName(publicKey)
    }

    override fun setDisplayName(publicKey: String, newName: String) {
        DatabaseFactory.getLokiUserDatabase(context).setDisplayName(publicKey, newName)
    }

    override fun getServerDisplayName(serverID: String, publicKey: String): String? {
        return DatabaseFactory.getLokiUserDatabase(context).getServerDisplayName(serverID, publicKey)
    }

    override fun getProfilePictureURL(publicKey: String): String? {
        return DatabaseFactory.getLokiUserDatabase(context).getProfilePictureURL(publicKey)
    }

    override fun getRecipientSettings(address: Address): Recipient.RecipientSettings? {
        val recipientSettings = DatabaseFactory.getRecipientDatabase(context).getRecipientSettings(address)
        return if (recipientSettings.isPresent) { recipientSettings.get() } else null
    }

    override fun addContacts(contacts: List<ConfigurationMessage.Contact>) {
        val recipientDatabase = DatabaseFactory.getRecipientDatabase(context)
        val threadDatabase = DatabaseFactory.getThreadDatabase(context)
        for (contact in contacts) {
            val address = Address.fromSerialized(contact.publicKey)
            val recipient = Recipient.from(context, address, true)
            if (!contact.profilePicture.isNullOrEmpty()) {
                recipientDatabase.setProfileAvatar(recipient, contact.profilePicture)
            }
            if (contact.profileKey?.isNotEmpty() == true) {
                recipientDatabase.setProfileKey(recipient, contact.profileKey)
            }
            if (contact.name.isNotEmpty()) {
                recipientDatabase.setProfileName(recipient, contact.name)
            }
            recipientDatabase.setProfileSharing(recipient, true)
            recipientDatabase.setRegistered(recipient, Recipient.RegisteredState.REGISTERED)
            // create Thread if needed
            threadDatabase.getOrCreateThreadIdFor(recipient)
        }
        if (contacts.isNotEmpty()) {
            threadDatabase.notifyUpdatedFromConfig()
        }
    }

    override fun getAttachmentDataUri(attachmentId: AttachmentId): Uri {
        return PartAuthority.getAttachmentDataUri(attachmentId)
    }

    override fun getAttachmentThumbnailUri(attachmentId: AttachmentId): Uri {
        return PartAuthority.getAttachmentThumbnailUri(attachmentId)
    }

    // Data Extraction Notification
    override fun insertDataExtractionNotificationMessage(senderPublicKey: String, message: DataExtractionNotificationInfoMessage, sentTimestamp: Long) {
        val database = DatabaseFactory.getMmsDatabase(context)
        val address = fromSerialized(senderPublicKey)
        val recipient = Recipient.from(context, address, false)

        if (recipient.isBlocked) return

        val mediaMessage = IncomingMediaMessage(address, sentTimestamp, -1,
                0, false,
                false,
                Optional.absent(),
                Optional.absent(),
                Optional.absent(),
                Optional.absent(),
                Optional.absent(),
                Optional.absent(),
                Optional.of(message))

        database.insertSecureDecryptedMessageInbox(mediaMessage, -1)
    }
}