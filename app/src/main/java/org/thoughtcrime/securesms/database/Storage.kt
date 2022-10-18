package org.thoughtcrime.securesms.database

import android.content.Context
import android.net.Uri
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.BlindedIdMapping
import org.session.libsession.messaging.calls.CallMessageType
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.jobs.AttachmentUploadJob
import org.session.libsession.messaging.jobs.GroupAvatarDownloadJob
import org.session.libsession.messaging.jobs.Job
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageReceiveJob
import org.session.libsession.messaging.jobs.MessageSendJob
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.messaging.messages.control.MessageRequestResponse
import org.session.libsession.messaging.messages.signal.IncomingEncryptedMessage
import org.session.libsession.messaging.messages.signal.IncomingGroupMessage
import org.session.libsession.messaging.messages.signal.IncomingMediaMessage
import org.session.libsession.messaging.messages.signal.IncomingTextMessage
import org.session.libsession.messaging.messages.signal.OutgoingGroupMediaMessage
import org.session.libsession.messaging.messages.signal.OutgoingMediaMessage
import org.session.libsession.messaging.messages.signal.OutgoingTextMessage
import org.session.libsession.messaging.messages.visible.Attachment
import org.session.libsession.messaging.messages.visible.Reaction
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.open_groups.GroupMember
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel
import org.session.libsession.messaging.utilities.SessionId
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.messaging.utilities.UpdateMessageData
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.GroupRecord
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.ProfileKeyUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.messages.SignalServiceAttachmentPointer
import org.session.libsignal.messages.SignalServiceGroup
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.KeyHelper
import org.session.libsignal.utilities.guava.Optional
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.SessionMetaProtocol

class Storage(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), StorageProtocol {
    
    override fun getUserPublicKey(): String? {
        return TextSecurePreferences.getLocalNumber(context)
    }

    override fun getUserX25519KeyPair(): ECKeyPair {
        return DatabaseComponent.get(context).lokiAPIDatabase().getUserX25519KeyPair()
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

    override fun setUserProfilePictureURL(newValue: String) {
        val ourRecipient = fromSerialized(getUserPublicKey()!!).let {
            Recipient.from(context, it, false)
        }
        TextSecurePreferences.setProfilePictureURL(context, newValue)
        RetrieveProfileAvatarJob(ourRecipient, newValue)
        ApplicationContext.getInstance(context).jobManager.add(RetrieveProfileAvatarJob(ourRecipient, newValue))
    }

    override fun getOrGenerateRegistrationID(): Int {
        var registrationID = TextSecurePreferences.getLocalRegistrationId(context)
        if (registrationID == 0) {
            registrationID = KeyHelper.generateRegistrationId(false)
            TextSecurePreferences.setLocalRegistrationId(context, registrationID)
        }
        return registrationID
    }

    override fun persistAttachments(messageID: Long, attachments: List<Attachment>): List<Long> {
        val database = DatabaseComponent.get(context).attachmentDatabase()
        val databaseAttachments = attachments.mapNotNull { it.toSignalAttachment() }
        return database.insertAttachments(messageID, databaseAttachments)
    }

    override fun getAttachmentsForMessage(messageID: Long): List<DatabaseAttachment> {
        val database = DatabaseComponent.get(context).attachmentDatabase()
        return database.getAttachmentsForMessage(messageID)
    }

    override fun markConversationAsRead(threadId: Long, updateLastSeen: Boolean) {
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        threadDb.setRead(threadId, updateLastSeen)
    }

    override fun incrementUnread(threadId: Long, amount: Int) {
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        threadDb.incrementUnread(threadId, amount)
    }

    override fun updateThread(threadId: Long, unarchive: Boolean) {
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        threadDb.update(threadId, unarchive)
    }

    override fun persist(message: VisibleMessage,
                         quotes: QuoteModel?,
                         linkPreview: List<LinkPreview?>,
                         groupPublicKey: String?,
                         openGroupID: String?,
                         attachments: List<Attachment>,
                         runIncrement: Boolean,
                         runThreadUpdate: Boolean): Long? {
        var messageID: Long? = null
        val senderAddress = fromSerialized(message.sender!!)
        val isUserSender = (message.sender!! == getUserPublicKey())
        val isUserBlindedSender = message.threadID?.takeIf { it >= 0 }?.let { getOpenGroup(it)?.publicKey }
            ?.let { SodiumUtilities.sessionId(getUserPublicKey()!!, message.sender!!, it) } ?: false
        val group: Optional<SignalServiceGroup> = when {
            openGroupID != null -> Optional.of(SignalServiceGroup(openGroupID.toByteArray(), SignalServiceGroup.GroupType.PUBLIC_CHAT))
            groupPublicKey != null -> {
                val doubleEncoded = GroupUtil.doubleEncodeGroupID(groupPublicKey)
                Optional.of(SignalServiceGroup(GroupUtil.getDecodedGroupIDAsData(doubleEncoded), SignalServiceGroup.GroupType.SIGNAL))
            }
            else -> Optional.absent()
        }
        val pointers = attachments.mapNotNull {
            it.toSignalAttachment()
        }
        val targetAddress = if ((isUserSender || isUserBlindedSender) && !message.syncTarget.isNullOrEmpty()) {
            fromSerialized(message.syncTarget!!)
        } else if (group.isPresent) {
            fromSerialized(GroupUtil.getEncodedId(group.get()))
        } else {
            senderAddress
        }
        val targetRecipient = Recipient.from(context, targetAddress, false)
        if (!targetRecipient.isGroupRecipient) {
            val recipientDb = DatabaseComponent.get(context).recipientDatabase()
            if (isUserSender || isUserBlindedSender) {
                recipientDb.setApproved(targetRecipient, true)
            } else {
                recipientDb.setApprovedMe(targetRecipient, true)
            }
        }
        if (message.isMediaMessage() || attachments.isNotEmpty()) {
            val quote: Optional<QuoteModel> = if (quotes != null) Optional.of(quotes) else Optional.absent()
            val linkPreviews: Optional<List<LinkPreview>> = if (linkPreview.isEmpty()) Optional.absent() else Optional.of(linkPreview.mapNotNull { it!! })
            val mmsDatabase = DatabaseComponent.get(context).mmsDatabase()
            val insertResult = if (isUserSender || isUserBlindedSender) {
                val mediaMessage = OutgoingMediaMessage.from(message, targetRecipient, pointers, quote.orNull(), linkPreviews.orNull()?.firstOrNull())
                mmsDatabase.insertSecureDecryptedMessageOutbox(mediaMessage, message.threadID ?: -1, message.sentTimestamp!!, runThreadUpdate)
            } else {
                // It seems like we have replaced SignalServiceAttachment with SessionServiceAttachment
                val signalServiceAttachments = attachments.mapNotNull {
                    it.toSignalPointer()
                }
                val mediaMessage = IncomingMediaMessage.from(message, senderAddress, targetRecipient.expireMessages * 1000L, group, signalServiceAttachments, quote, linkPreviews)
                mmsDatabase.insertSecureDecryptedMessageInbox(mediaMessage, message.threadID ?: -1, message.receivedTimestamp ?: 0, runIncrement, runThreadUpdate)
            }
            if (insertResult.isPresent) {
                messageID = insertResult.get().messageId
            }
        } else {
            val smsDatabase = DatabaseComponent.get(context).smsDatabase()
            val isOpenGroupInvitation = (message.openGroupInvitation != null)

            val insertResult = if (isUserSender || isUserBlindedSender) {
                val textMessage = if (isOpenGroupInvitation) OutgoingTextMessage.fromOpenGroupInvitation(message.openGroupInvitation, targetRecipient, message.sentTimestamp)
                else OutgoingTextMessage.from(message, targetRecipient)
                smsDatabase.insertMessageOutbox(message.threadID ?: -1, textMessage, message.sentTimestamp!!, runThreadUpdate)
            } else {
                val textMessage = if (isOpenGroupInvitation) IncomingTextMessage.fromOpenGroupInvitation(message.openGroupInvitation, senderAddress, message.sentTimestamp)
                else IncomingTextMessage.from(message, senderAddress, group, targetRecipient.expireMessages * 1000L)
                val encrypted = IncomingEncryptedMessage(textMessage, textMessage.messageBody)
                smsDatabase.insertMessageInbox(encrypted, message.receivedTimestamp ?: 0, runIncrement, runThreadUpdate)
            }
            insertResult.orNull()?.let { result ->
                messageID = result.messageId
            }
        }
        message.serverHash?.let { serverHash ->
            messageID?.let { id ->
                DatabaseComponent.get(context).lokiMessageDatabase().setMessageServerHash(id, serverHash)
            }
        }
        return messageID
    }

    override fun persistJob(job: Job) {
        DatabaseComponent.get(context).sessionJobDatabase().persistJob(job)
    }

    override fun markJobAsSucceeded(jobId: String) {
        DatabaseComponent.get(context).sessionJobDatabase().markJobAsSucceeded(jobId)
    }

    override fun markJobAsFailedPermanently(jobId: String) {
        DatabaseComponent.get(context).sessionJobDatabase().markJobAsFailedPermanently(jobId)
    }

    override fun getAllPendingJobs(type: String): Map<String, Job?> {
        return DatabaseComponent.get(context).sessionJobDatabase().getAllPendingJobs(type)
    }

    override fun getAttachmentUploadJob(attachmentID: Long): AttachmentUploadJob? {
        return DatabaseComponent.get(context).sessionJobDatabase().getAttachmentUploadJob(attachmentID)
    }

    override fun getMessageSendJob(messageSendJobID: String): MessageSendJob? {
        return DatabaseComponent.get(context).sessionJobDatabase().getMessageSendJob(messageSendJobID)
    }

    override fun getMessageReceiveJob(messageReceiveJobID: String): MessageReceiveJob? {
        return DatabaseComponent.get(context).sessionJobDatabase().getMessageReceiveJob(messageReceiveJobID)
    }

    override fun getGroupAvatarDownloadJob(server: String, room: String): GroupAvatarDownloadJob? {
        return DatabaseComponent.get(context).sessionJobDatabase().getGroupAvatarDownloadJob(server, room)
    }

    override fun resumeMessageSendJobIfNeeded(messageSendJobID: String) {
        val job = DatabaseComponent.get(context).sessionJobDatabase().getMessageSendJob(messageSendJobID) ?: return
        JobQueue.shared.resumePendingSendMessage(job)
    }

    override fun isJobCanceled(job: Job): Boolean {
        return DatabaseComponent.get(context).sessionJobDatabase().isJobCanceled(job)
    }

    override fun getAuthToken(room: String, server: String): String? {
        val id = "$server.$room"
        return DatabaseComponent.get(context).lokiAPIDatabase().getAuthToken(id)
    }

    override fun setAuthToken(room: String, server: String, newValue: String) {
        val id = "$server.$room"
        DatabaseComponent.get(context).lokiAPIDatabase().setAuthToken(id, newValue)
    }

    override fun removeAuthToken(room: String, server: String) {
        val id = "$server.$room"
        DatabaseComponent.get(context).lokiAPIDatabase().setAuthToken(id, null)
    }

    override fun getOpenGroup(threadId: Long): OpenGroup? {
        if (threadId.toInt() < 0) { return null }
        val database = databaseHelper.readableDatabase
        return database.get(LokiThreadDatabase.publicChatTable, "${LokiThreadDatabase.threadID} = ?", arrayOf( threadId.toString() )) { cursor ->
            val publicChatAsJson = cursor.getString(LokiThreadDatabase.publicChat)
            OpenGroup.fromJSON(publicChatAsJson)
        }
    }

    override fun getOpenGroupPublicKey(server: String): String? {
        return DatabaseComponent.get(context).lokiAPIDatabase().getOpenGroupPublicKey(server)
    }

    override fun setOpenGroupPublicKey(server: String, newValue: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().setOpenGroupPublicKey(server, newValue)
    }

    override fun getLastMessageServerID(room: String, server: String): Long? {
        return DatabaseComponent.get(context).lokiAPIDatabase().getLastMessageServerID(room, server)
    }

    override fun setLastMessageServerID(room: String, server: String, newValue: Long) {
        DatabaseComponent.get(context).lokiAPIDatabase().setLastMessageServerID(room, server, newValue)
    }

    override fun removeLastMessageServerID(room: String, server: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().removeLastMessageServerID(room, server)
    }

    override fun getLastDeletionServerID(room: String, server: String): Long? {
        return DatabaseComponent.get(context).lokiAPIDatabase().getLastDeletionServerID(room, server)
    }

    override fun setLastDeletionServerID(room: String, server: String, newValue: Long) {
        DatabaseComponent.get(context).lokiAPIDatabase().setLastDeletionServerID(room, server, newValue)
    }

    override fun removeLastDeletionServerID(room: String, server: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().removeLastDeletionServerID(room, server)
    }

    override fun setUserCount(room: String, server: String, newValue: Int) {
        DatabaseComponent.get(context).lokiAPIDatabase().setUserCount(room, server, newValue)
    }

    override fun setOpenGroupServerMessageID(messageID: Long, serverID: Long, threadID: Long, isSms: Boolean) {
        DatabaseComponent.get(context).lokiMessageDatabase().setServerID(messageID, serverID, isSms)
        DatabaseComponent.get(context).lokiMessageDatabase().setOriginalThreadID(messageID, serverID, threadID)
    }

    override fun getOpenGroup(room: String, server: String): OpenGroup? {
        return getAllOpenGroups().values.firstOrNull { it.server == server && it.room == room }
    }

    override fun setGroupMemberRoles(members: List<GroupMember>) {
        DatabaseComponent.get(context).groupMemberDatabase().setGroupMembers(members)
    }

    override fun isDuplicateMessage(timestamp: Long): Boolean {
        return getReceivedMessageTimestamps().contains(timestamp)
    }

    override fun updateTitle(groupID: String, newValue: String) {
        DatabaseComponent.get(context).groupDatabase().updateTitle(groupID, newValue)
    }

    override fun updateProfilePicture(groupID: String, newValue: ByteArray) {
        DatabaseComponent.get(context).groupDatabase().updateProfilePicture(groupID, newValue)
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
        val database = DatabaseComponent.get(context).mmsSmsDatabase()
        val address = fromSerialized(author)
        return database.getMessageFor(timestamp, address)?.getId()
    }

    override fun updateSentTimestamp(
        messageID: Long,
        isMms: Boolean,
        openGroupSentTimestamp: Long,
        threadId: Long
    ) {
        if (isMms) {
            val mmsDb = DatabaseComponent.get(context).mmsDatabase()
            mmsDb.updateSentTimestamp(messageID, openGroupSentTimestamp, threadId)
        } else {
            val smsDb = DatabaseComponent.get(context).smsDatabase()
            smsDb.updateSentTimestamp(messageID, openGroupSentTimestamp, threadId)
        }
    }

    override fun markAsSent(timestamp: Long, author: String) {
        val database = DatabaseComponent.get(context).mmsSmsDatabase()
        val messageRecord = database.getMessageFor(timestamp, author) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseComponent.get(context).mmsDatabase()
            mmsDatabase.markAsSent(messageRecord.getId(), true)
        } else {
            val smsDatabase = DatabaseComponent.get(context).smsDatabase()
            smsDatabase.markAsSent(messageRecord.getId(), true)
        }
    }

    override fun markAsSending(timestamp: Long, author: String) {
        val database = DatabaseComponent.get(context).mmsSmsDatabase()
        val messageRecord = database.getMessageFor(timestamp, author) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseComponent.get(context).mmsDatabase()
            mmsDatabase.markAsSending(messageRecord.getId())
        } else {
            val smsDatabase = DatabaseComponent.get(context).smsDatabase()
            smsDatabase.markAsSending(messageRecord.getId())
            messageRecord.isPending
        }
    }

    override fun markUnidentified(timestamp: Long, author: String) {
        val database = DatabaseComponent.get(context).mmsSmsDatabase()
        val messageRecord = database.getMessageFor(timestamp, author) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseComponent.get(context).mmsDatabase()
            mmsDatabase.markUnidentified(messageRecord.getId(), true)
        } else {
            val smsDatabase = DatabaseComponent.get(context).smsDatabase()
            smsDatabase.markUnidentified(messageRecord.getId(), true)
        }
    }

    override fun setErrorMessage(timestamp: Long, author: String, error: Exception) {
        val database = DatabaseComponent.get(context).mmsSmsDatabase()
        val messageRecord = database.getMessageFor(timestamp, author) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseComponent.get(context).mmsDatabase()
            mmsDatabase.markAsSentFailed(messageRecord.getId())
        } else {
            val smsDatabase = DatabaseComponent.get(context).smsDatabase()
            smsDatabase.markAsSentFailed(messageRecord.getId())
        }
        if (error.localizedMessage != null) {
            val message: String
            if (error is OnionRequestAPI.HTTPRequestFailedAtDestinationException && error.statusCode == 429) {
                message = "429: Rate limited."
            } else {
                message = error.localizedMessage!!
            }
            DatabaseComponent.get(context).lokiMessageDatabase().setErrorMessage(messageRecord.getId(), message)
        } else {
            DatabaseComponent.get(context).lokiMessageDatabase().setErrorMessage(messageRecord.getId(), error.javaClass.simpleName)
        }
    }

    override fun setMessageServerHash(messageID: Long, serverHash: String) {
        DatabaseComponent.get(context).lokiMessageDatabase().setMessageServerHash(messageID, serverHash)
    }

    override fun getGroup(groupID: String): GroupRecord? {
        val group = DatabaseComponent.get(context).groupDatabase().getGroup(groupID)
        return if (group.isPresent) { group.get() } else null
    }

    override fun createGroup(groupId: String, title: String?, members: List<Address>, avatar: SignalServiceAttachmentPointer?, relay: String?, admins: List<Address>, formationTimestamp: Long) {
        DatabaseComponent.get(context).groupDatabase().create(groupId, title, members, avatar, relay, admins, formationTimestamp)
    }

    override fun isGroupActive(groupPublicKey: String): Boolean {
        return DatabaseComponent.get(context).groupDatabase().getGroup(GroupUtil.doubleEncodeGroupID(groupPublicKey)).orNull()?.isActive == true
    }

    override fun setActive(groupID: String, value: Boolean) {
        DatabaseComponent.get(context).groupDatabase().setActive(groupID, value)
    }

    override fun getZombieMembers(groupID: String): Set<String> {
        return DatabaseComponent.get(context).groupDatabase().getGroupZombieMembers(groupID).map { it.address.serialize() }.toHashSet()
    }

    override fun removeMember(groupID: String, member: Address) {
        DatabaseComponent.get(context).groupDatabase().removeMember(groupID, member)
    }

    override fun updateMembers(groupID: String, members: List<Address>) {
        DatabaseComponent.get(context).groupDatabase().updateMembers(groupID, members)
    }

    override fun setZombieMembers(groupID: String, members: List<Address>) {
        DatabaseComponent.get(context).groupDatabase().updateZombieMembers(groupID, members)
    }

    override fun insertIncomingInfoMessage(context: Context, senderPublicKey: String, groupID: String, type: SignalServiceGroup.Type, name: String, members: Collection<String>, admins: Collection<String>, sentTimestamp: Long) {
        val group = SignalServiceGroup(type, GroupUtil.getDecodedGroupIDAsData(groupID), SignalServiceGroup.GroupType.SIGNAL, name, members.toList(), null, admins.toList())
        val m = IncomingTextMessage(fromSerialized(senderPublicKey), 1, sentTimestamp, "", Optional.of(group), 0, true)
        val updateData = UpdateMessageData.buildGroupUpdate(type, name, members)?.toJSON()
        val infoMessage = IncomingGroupMessage(m, groupID, updateData, true)
        val smsDB = DatabaseComponent.get(context).smsDatabase()
        smsDB.insertMessageInbox(infoMessage, true, true)
    }

    override fun insertOutgoingInfoMessage(context: Context, groupID: String, type: SignalServiceGroup.Type, name: String, members: Collection<String>, admins: Collection<String>, threadID: Long, sentTimestamp: Long) {
        val userPublicKey = getUserPublicKey()
        val recipient = Recipient.from(context, fromSerialized(groupID), false)

        val updateData = UpdateMessageData.buildGroupUpdate(type, name, members)?.toJSON() ?: ""
        val infoMessage = OutgoingGroupMediaMessage(recipient, updateData, groupID, null, sentTimestamp, 0, true, null, listOf(), listOf())
        val mmsDB = DatabaseComponent.get(context).mmsDatabase()
        val mmsSmsDB = DatabaseComponent.get(context).mmsSmsDatabase()
        if (mmsSmsDB.getMessageFor(sentTimestamp, userPublicKey) != null) return
        val infoMessageID = mmsDB.insertMessageOutbox(infoMessage, threadID, false, null, runThreadUpdate = true)
        mmsDB.markAsSent(infoMessageID, true)
    }

    override fun isClosedGroup(publicKey: String): Boolean {
        val isClosedGroup = DatabaseComponent.get(context).lokiAPIDatabase().isClosedGroup(publicKey)
        val address = fromSerialized(publicKey)
        return address.isClosedGroup || isClosedGroup
    }

    override fun getClosedGroupEncryptionKeyPairs(groupPublicKey: String): MutableList<ECKeyPair> {
        return DatabaseComponent.get(context).lokiAPIDatabase().getClosedGroupEncryptionKeyPairs(groupPublicKey).toMutableList()
    }

    override fun getLatestClosedGroupEncryptionKeyPair(groupPublicKey: String): ECKeyPair? {
        return DatabaseComponent.get(context).lokiAPIDatabase().getLatestClosedGroupEncryptionKeyPair(groupPublicKey)
    }

    override fun getAllClosedGroupPublicKeys(): Set<String> {
        return DatabaseComponent.get(context).lokiAPIDatabase().getAllClosedGroupPublicKeys()
    }

    override fun getAllActiveClosedGroupPublicKeys(): Set<String> {
        return DatabaseComponent.get(context).lokiAPIDatabase().getAllClosedGroupPublicKeys().filter {
            getGroup(GroupUtil.doubleEncodeGroupID(it))?.isActive == true
        }.toSet()
    }

    override fun addClosedGroupPublicKey(groupPublicKey: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().addClosedGroupPublicKey(groupPublicKey)
    }

    override fun removeClosedGroupPublicKey(groupPublicKey: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().removeClosedGroupPublicKey(groupPublicKey)
    }

    override fun addClosedGroupEncryptionKeyPair(encryptionKeyPair: ECKeyPair, groupPublicKey: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().addClosedGroupEncryptionKeyPair(encryptionKeyPair, groupPublicKey)
    }

    override fun removeAllClosedGroupEncryptionKeyPairs(groupPublicKey: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().removeAllClosedGroupEncryptionKeyPairs(groupPublicKey)
    }

    override fun updateFormationTimestamp(groupID: String, formationTimestamp: Long) {
        DatabaseComponent.get(context).groupDatabase()
            .updateFormationTimestamp(groupID, formationTimestamp)
    }

    override fun updateTimestampUpdated(groupID: String, updatedTimestamp: Long) {
        DatabaseComponent.get(context).groupDatabase()
            .updateTimestampUpdated(groupID, updatedTimestamp)
    }

    override fun setExpirationTimer(groupID: String, duration: Int) {
        val recipient = Recipient.from(context, fromSerialized(groupID), false)
        DatabaseComponent.get(context).recipientDatabase().setExpireMessages(recipient, duration);
    }

    override fun setServerCapabilities(server: String, capabilities: List<String>) {
        return DatabaseComponent.get(context).lokiAPIDatabase().setServerCapabilities(server, capabilities)
    }

    override fun getServerCapabilities(server: String): List<String> {
        return DatabaseComponent.get(context).lokiAPIDatabase().getServerCapabilities(server)
    }

    override fun getAllOpenGroups(): Map<Long, OpenGroup> {
        return DatabaseComponent.get(context).lokiThreadDatabase().getAllOpenGroups()
    }

    override fun updateOpenGroup(openGroup: OpenGroup) {
        OpenGroupManager.updateOpenGroup(openGroup, context)
    }

    override fun getAllGroups(): List<GroupRecord> {
        return DatabaseComponent.get(context).groupDatabase().allGroups
    }

    override fun addOpenGroup(urlAsString: String) {
        OpenGroupManager.addOpenGroup(urlAsString, context)
    }

    override fun onOpenGroupAdded(server: String) {
        OpenGroupManager.restartPollerForServer(server.removeSuffix("/"))
    }

    override fun hasBackgroundGroupAddJob(groupJoinUrl: String): Boolean {
        val jobDb = DatabaseComponent.get(context).sessionJobDatabase()
        return jobDb.hasBackgroundGroupAddJob(groupJoinUrl)
    }

    override fun setProfileSharing(address: Address, value: Boolean) {
        val recipient = Recipient.from(context, address, false)
        DatabaseComponent.get(context).recipientDatabase().setProfileSharing(recipient, value)
    }

    override fun getOrCreateThreadIdFor(address: Address): Long {
        val recipient = Recipient.from(context, address, false)
        return DatabaseComponent.get(context).threadDatabase().getOrCreateThreadIdFor(recipient)
    }

    override fun getOrCreateThreadIdFor(publicKey: String, groupPublicKey: String?, openGroupID: String?): Long {
        val database = DatabaseComponent.get(context).threadDatabase()
        return if (!openGroupID.isNullOrEmpty()) {
            val recipient = Recipient.from(context, fromSerialized(GroupUtil.getEncodedOpenGroupID(openGroupID.toByteArray())), false)
            database.getThreadIdIfExistsFor(recipient)
        } else if (!groupPublicKey.isNullOrEmpty()) {
            val recipient = Recipient.from(context, fromSerialized(GroupUtil.doubleEncodeGroupID(groupPublicKey)), false)
            database.getOrCreateThreadIdFor(recipient)
        } else {
            val recipient = Recipient.from(context, fromSerialized(publicKey), false)
            database.getOrCreateThreadIdFor(recipient)
        }
    }

    override fun getThreadId(publicKeyOrOpenGroupID: String): Long? {
        val address = fromSerialized(publicKeyOrOpenGroupID)
        return getThreadId(address)
    }

    override fun getThreadId(address: Address): Long? {
        val recipient = Recipient.from(context, address, false)
        return getThreadId(recipient)
    }

    override fun getThreadId(recipient: Recipient): Long? {
        val threadID = DatabaseComponent.get(context).threadDatabase().getThreadIdIfExistsFor(recipient)
        return if (threadID < 0) null else threadID
    }

    override fun getThreadIdForMms(mmsId: Long): Long {
        val mmsDb = DatabaseComponent.get(context).mmsDatabase()
        val cursor = mmsDb.getMessage(mmsId)
        val reader = mmsDb.readerFor(cursor)
        val threadId = reader.next?.threadId
        cursor.close()
        return threadId ?: -1
    }

    override fun getContactWithSessionID(sessionID: String): Contact? {
        return DatabaseComponent.get(context).sessionContactDatabase().getContactWithSessionID(sessionID)
    }

    override fun getAllContacts(): Set<Contact> {
        return DatabaseComponent.get(context).sessionContactDatabase().getAllContacts()
    }

    override fun setContact(contact: Contact) {
        DatabaseComponent.get(context).sessionContactDatabase().setContact(contact)
    }

    override fun getRecipientForThread(threadId: Long): Recipient? {
        return DatabaseComponent.get(context).threadDatabase().getRecipientForThreadId(threadId)
    }

    override fun getRecipientSettings(address: Address): Recipient.RecipientSettings? {
        val recipientSettings = DatabaseComponent.get(context).recipientDatabase().getRecipientSettings(address)
        return if (recipientSettings.isPresent) { recipientSettings.get() } else null
    }

    override fun addContacts(contacts: List<ConfigurationMessage.Contact>) {
        val recipientDatabase = DatabaseComponent.get(context).recipientDatabase()
        val threadDatabase = DatabaseComponent.get(context).threadDatabase()
        val mappingDb = DatabaseComponent.get(context).blindedIdMappingDatabase()
        val moreContacts = contacts.filter { contact ->
            val id = SessionId(contact.publicKey)
            id.prefix != IdPrefix.BLINDED || mappingDb.getBlindedIdMapping(contact.publicKey).none { it.sessionId != null }
        }
        for (contact in moreContacts) {
            val address = fromSerialized(contact.publicKey)
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
            val threadId = threadDatabase.getOrCreateThreadIdFor(recipient)
            if (contact.didApproveMe == true) {
                recipientDatabase.setApprovedMe(recipient, true)
            }
            if (contact.isApproved == true) {
                recipientDatabase.setApproved(recipient, true)
                threadDatabase.setHasSent(threadId, true)
            }
            if (contact.isBlocked == true) {
                recipientDatabase.setBlocked(recipient, true)
                threadDatabase.deleteConversation(threadId)
            }
        }
        if (contacts.isNotEmpty()) {
            threadDatabase.notifyConversationListListeners()
        }
    }

    override fun getLastUpdated(threadID: Long): Long {
        val threadDB = DatabaseComponent.get(context).threadDatabase()
        return threadDB.getLastUpdated(threadID)
    }

    override fun trimThread(threadID: Long, threadLimit: Int) {
        val threadDB = DatabaseComponent.get(context).threadDatabase()
        threadDB.trimThread(threadID, threadLimit)
    }

    override fun trimThreadBefore(threadID: Long, timestamp: Long) {
        val threadDB = DatabaseComponent.get(context).threadDatabase()
        threadDB.trimThreadBefore(threadID, timestamp)
    }

    override fun getMessageCount(threadID: Long): Long {
        val mmsSmsDb = DatabaseComponent.get(context).mmsSmsDatabase()
        return mmsSmsDb.getConversationCount(threadID)
    }



    override fun getAttachmentDataUri(attachmentId: AttachmentId): Uri {
        return PartAuthority.getAttachmentDataUri(attachmentId)
    }

    override fun getAttachmentThumbnailUri(attachmentId: AttachmentId): Uri {
        return PartAuthority.getAttachmentThumbnailUri(attachmentId)
    }

    override fun insertDataExtractionNotificationMessage(senderPublicKey: String, message: DataExtractionNotificationInfoMessage, sentTimestamp: Long) {
        val database = DatabaseComponent.get(context).mmsDatabase()
        val address = fromSerialized(senderPublicKey)
        val recipient = Recipient.from(context, address, false)

        if (recipient.isBlocked) return

        val mediaMessage = IncomingMediaMessage(
            address,
            sentTimestamp,
            -1,
            0,
            false,
            false,
            false,
            Optional.absent(),
            Optional.absent(),
            Optional.absent(),
            Optional.absent(),
            Optional.absent(),
            Optional.absent(),
            Optional.of(message)
        )

        database.insertSecureDecryptedMessageInbox(mediaMessage, -1, runIncrement = true, runThreadUpdate = true)
    }

    override fun insertMessageRequestResponse(response: MessageRequestResponse) {
        val userPublicKey = getUserPublicKey()
        val senderPublicKey = response.sender!!
        val recipientPublicKey = response.recipient!!
        if (userPublicKey == null || (userPublicKey != recipientPublicKey && userPublicKey != senderPublicKey)) return
        val recipientDb = DatabaseComponent.get(context).recipientDatabase()
        val threadDB = DatabaseComponent.get(context).threadDatabase()
        if (userPublicKey == senderPublicKey) {
            val requestRecipient = Recipient.from(context, fromSerialized(recipientPublicKey), false)
            recipientDb.setApproved(requestRecipient, true)
            val threadId = threadDB.getOrCreateThreadIdFor(requestRecipient)
            threadDB.setHasSent(threadId, true)
        } else {
            val mmsDb = DatabaseComponent.get(context).mmsDatabase()
            val smsDb = DatabaseComponent.get(context).smsDatabase()
            val sender = Recipient.from(context, fromSerialized(senderPublicKey), false)
            val threadId = threadDB.getOrCreateThreadIdFor(sender)
            threadDB.setHasSent(threadId, true)
            val mappingDb = DatabaseComponent.get(context).blindedIdMappingDatabase()
            val mappings = mutableMapOf<String, BlindedIdMapping>()
            threadDB.readerFor(threadDB.conversationList).use { reader ->
                while (reader.next != null) {
                    val recipient = reader.current.recipient
                    val address = recipient.address.serialize()
                    val blindedId = when {
                        recipient.isGroupRecipient -> null
                        recipient.isOpenGroupInboxRecipient -> {
                            GroupUtil.getDecodedOpenGroupInbox(address)
                        }
                        else -> {
                            if (SessionId(address).prefix == IdPrefix.BLINDED) {
                                address
                            } else null
                        }
                    } ?: continue
                    mappingDb.getBlindedIdMapping(blindedId).firstOrNull()?.let {
                        mappings[address] = it
                    }
                }
            }
            for (mapping in mappings) {
                if (!SodiumUtilities.sessionId(senderPublicKey, mapping.value.blindedId, mapping.value.serverId)) {
                    continue
                }
                mappingDb.addBlindedIdMapping(mapping.value.copy(sessionId = senderPublicKey))

                val blindedThreadId = threadDB.getOrCreateThreadIdFor(Recipient.from(context, fromSerialized(mapping.key), false))
                mmsDb.updateThreadId(blindedThreadId, threadId)
                smsDb.updateThreadId(blindedThreadId, threadId)
                threadDB.deleteConversation(blindedThreadId)
            }
            recipientDb.setApproved(sender, true)
            recipientDb.setApprovedMe(sender, true)

            val message = IncomingMediaMessage(
                sender.address,
                response.sentTimestamp!!,
                -1,
                0,
                false,
                false,
                true,
                Optional.absent(),
                Optional.absent(),
                Optional.absent(),
                Optional.absent(),
                Optional.absent(),
                Optional.absent(),
                Optional.absent()
            )
            mmsDb.insertSecureDecryptedMessageInbox(message, threadId, runIncrement = true, runThreadUpdate = true)
        }
    }

    override fun setRecipientApproved(recipient: Recipient, approved: Boolean) {
        DatabaseComponent.get(context).recipientDatabase().setApproved(recipient, approved)
    }

    override fun setRecipientApprovedMe(recipient: Recipient, approvedMe: Boolean) {
        DatabaseComponent.get(context).recipientDatabase().setApprovedMe(recipient, approvedMe)
    }

    override fun insertCallMessage(senderPublicKey: String, callMessageType: CallMessageType, sentTimestamp: Long) {
        val database = DatabaseComponent.get(context).smsDatabase()
        val address = fromSerialized(senderPublicKey)
        val callMessage = IncomingTextMessage.fromCallInfo(callMessageType, address, Optional.absent(), sentTimestamp)
        database.insertCallMessage(callMessage)
    }

    override fun conversationHasOutgoing(userPublicKey: String): Boolean {
        val database = DatabaseComponent.get(context).threadDatabase()
        val threadId = database.getThreadIdIfExistsFor(userPublicKey)

        if (threadId == -1L) return false

        return database.getLastSeenAndHasSent(threadId).second() ?: false
    }

    override fun getLastInboxMessageId(server: String): Long? {
        return DatabaseComponent.get(context).lokiAPIDatabase().getLastInboxMessageId(server)
    }

    override fun setLastInboxMessageId(server: String, messageId: Long) {
        DatabaseComponent.get(context).lokiAPIDatabase().setLastInboxMessageId(server, messageId)
    }

    override fun removeLastInboxMessageId(server: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().removeLastInboxMessageId(server)
    }

    override fun getLastOutboxMessageId(server: String): Long? {
        return DatabaseComponent.get(context).lokiAPIDatabase().getLastOutboxMessageId(server)
    }

    override fun setLastOutboxMessageId(server: String, messageId: Long) {
        DatabaseComponent.get(context).lokiAPIDatabase().setLastOutboxMessageId(server, messageId)
    }

    override fun removeLastOutboxMessageId(server: String) {
        DatabaseComponent.get(context).lokiAPIDatabase().removeLastOutboxMessageId(server)
    }

    override fun getOrCreateBlindedIdMapping(
        blindedId: String,
        server: String,
        serverPublicKey: String,
        fromOutbox: Boolean
    ): BlindedIdMapping {
        val db = DatabaseComponent.get(context).blindedIdMappingDatabase()
        val mapping = db.getBlindedIdMapping(blindedId).firstOrNull() ?: BlindedIdMapping(blindedId, null, server, serverPublicKey)
        if (mapping.sessionId != null) {
            return mapping
        }
        val threadDb = DatabaseComponent.get(context).threadDatabase()
        threadDb.readerFor(threadDb.conversationList).use { reader ->
            while (reader.next != null) {
                val recipient = reader.current.recipient
                val sessionId = recipient.address.serialize()
                if (!recipient.isGroupRecipient && SodiumUtilities.sessionId(sessionId, blindedId, serverPublicKey)) {
                    val contactMapping = mapping.copy(sessionId = sessionId)
                    db.addBlindedIdMapping(contactMapping)
                    return contactMapping
                }
            }
        }
        db.getBlindedIdMappingsExceptFor(server).forEach {
            if (SodiumUtilities.sessionId(it.sessionId!!, blindedId, serverPublicKey)) {
                val otherMapping = mapping.copy(sessionId = it.sessionId)
                db.addBlindedIdMapping(otherMapping)
                return otherMapping
            }
        }
        db.addBlindedIdMapping(mapping)
        return mapping
    }

    override fun addReaction(reaction: Reaction, messageSender: String, notifyUnread: Boolean) {
        val timestamp = reaction.timestamp
        val localId = reaction.localId
        val isMms = reaction.isMms
        val messageId = if (localId != null && localId > 0 && isMms != null) {
            MessageId(localId, isMms)
        } else if (timestamp != null && timestamp > 0) {
            val messageRecord = DatabaseComponent.get(context).mmsSmsDatabase().getMessageForTimestamp(timestamp) ?: return
            MessageId(messageRecord.id, messageRecord.isMms)
        } else return
        DatabaseComponent.get(context).reactionDatabase().addReaction(
            messageId,
            ReactionRecord(
                messageId = messageId.id,
                isMms = messageId.mms,
                author = messageSender,
                emoji = reaction.emoji!!,
                serverId = reaction.serverId!!,
                count = reaction.count!!,
                sortId = reaction.index!!,
                dateSent = reaction.dateSent!!,
                dateReceived = reaction.dateReceived!!
            ),
            notifyUnread
        )
    }

    override fun removeReaction(emoji: String, messageTimestamp: Long, author: String, notifyUnread: Boolean) {
        val messageRecord = DatabaseComponent.get(context).mmsSmsDatabase().getMessageForTimestamp(messageTimestamp) ?: return
        val messageId = MessageId(messageRecord.id, messageRecord.isMms)
        DatabaseComponent.get(context).reactionDatabase().deleteReaction(emoji, messageId, author, notifyUnread)
    }

    override fun updateReactionIfNeeded(message: Message, sender: String, openGroupSentTimestamp: Long) {
        val database = DatabaseComponent.get(context).reactionDatabase()
        var reaction = database.getReactionFor(message.sentTimestamp!!, sender) ?: return
        if (openGroupSentTimestamp != -1L) {
            addReceivedMessageTimestamp(openGroupSentTimestamp)
            reaction = reaction.copy(dateSent = openGroupSentTimestamp)
        }
        message.serverHash?.let {
            reaction = reaction.copy(serverId = it)
        }
        message.openGroupServerMessageID?.let {
            reaction = reaction.copy(serverId = "$it")
        }
        database.updateReaction(reaction)
    }

    override fun deleteReactions(messageId: Long, mms: Boolean) {
        DatabaseComponent.get(context).reactionDatabase().deleteMessageReactions(MessageId(messageId, mms))
    }

    override fun unblock(toUnblock: List<Recipient>) {
        val recipientDb = DatabaseComponent.get(context).recipientDatabase()
        recipientDb.setBlocked(toUnblock, false)
    }

    override fun blockedContacts(): List<Recipient> {
        val recipientDb = DatabaseComponent.get(context).recipientDatabase()
        return recipientDb.blockedContacts
    }

}