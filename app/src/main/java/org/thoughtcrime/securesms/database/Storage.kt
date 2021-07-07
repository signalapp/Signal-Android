package org.thoughtcrime.securesms.database

import android.content.Context
import android.net.Uri
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.jobs.*
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.messaging.messages.signal.*
import org.session.libsession.messaging.messages.signal.IncomingTextMessage
import org.session.libsession.messaging.messages.visible.Attachment
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.open_groups.OpenGroupV2
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel
import org.session.libsession.messaging.utilities.UpdateMessageData
import org.session.libsession.utilities.*
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.messages.SignalServiceAttachmentPointer
import org.session.libsignal.messages.SignalServiceGroup
import org.session.libsignal.utilities.KeyHelper
import org.session.libsignal.utilities.guava.Optional
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.thoughtcrime.securesms.loki.api.OpenGroupManager
import org.thoughtcrime.securesms.loki.database.LokiThreadDatabase
import org.thoughtcrime.securesms.loki.protocol.SessionMetaProtocol
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

    override fun setUserProfilePictureURL(newValue: String) {
        val ourRecipient = Address.fromSerialized(getUserPublicKey()!!).let {
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
        val database = DatabaseFactory.getAttachmentDatabase(context)
        val databaseAttachments = attachments.mapNotNull { it.toSignalAttachment() }
        return database.insertAttachments(messageID, databaseAttachments)
    }

    override fun getAttachmentsForMessage(messageID: Long): List<DatabaseAttachment> {
        val database = DatabaseFactory.getAttachmentDatabase(context)
        return database.getAttachmentsForMessage(messageID)
    }

    override fun persist(message: VisibleMessage, quotes: QuoteModel?, linkPreview: List<LinkPreview?>, groupPublicKey: String?, openGroupID: String?, attachments: List<Attachment>): Long? {
        var messageID: Long? = null
        val senderAddress = Address.fromSerialized(message.sender!!)
        val isUserSender = (message.sender!! == getUserPublicKey())
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
                val mediaMessage = OutgoingMediaMessage.from(message, targetRecipient, pointers, quote.orNull(), linkPreviews.orNull()?.firstOrNull())
                mmsDatabase.insertSecureDecryptedMessageOutbox(mediaMessage, message.threadID ?: -1, message.sentTimestamp!!)
            } else {
                // It seems like we have replaced SignalServiceAttachment with SessionServiceAttachment
                val signalServiceAttachments = attachments.mapNotNull {
                    it.toSignalPointer()
                }
                val mediaMessage = IncomingMediaMessage.from(message, senderAddress, targetRecipient.expireMessages * 1000L, group, signalServiceAttachments, quote, linkPreviews)
                mmsDatabase.insertSecureDecryptedMessageInbox(mediaMessage, message.threadID ?: -1, message.receivedTimestamp ?: 0)
            }
            if (insertResult.isPresent) {
                messageID = insertResult.get().messageId
            }
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
        val threadID = message.threadID
        // open group trim thread job is scheduled after processing in OpenGroupPollerV2
        if (openGroupID.isNullOrEmpty() && threadID != null && threadID >= 0) {
            JobQueue.shared.add(TrimThreadJob(threadID))
        }
        return messageID
    }

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

    override fun getMessageReceiveJob(messageReceiveJobID: String): MessageReceiveJob? {
        return DatabaseFactory.getSessionJobDatabase(context).getMessageReceiveJob(messageReceiveJobID)
    }

    override fun resumeMessageSendJobIfNeeded(messageSendJobID: String) {
        val job = DatabaseFactory.getSessionJobDatabase(context).getMessageSendJob(messageSendJobID) ?: return
        JobQueue.shared.resumePendingSendMessage(job)
    }

    override fun isJobCanceled(job: Job): Boolean {
        return DatabaseFactory.getSessionJobDatabase(context).isJobCanceled(job)
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

    override fun getV2OpenGroup(threadId: Long): OpenGroupV2? {
        if (threadId.toInt() < 0) { return null }
        val database = databaseHelper.readableDatabase
        return database.get(LokiThreadDatabase.publicChatTable, "${LokiThreadDatabase.threadID} = ?", arrayOf( threadId.toString() )) { cursor ->
            val publicChatAsJson = cursor.getString(LokiThreadDatabase.publicChat)
            OpenGroupV2.fromJSON(publicChatAsJson)
        }
    }

    override fun getOpenGroupPublicKey(server: String): String? {
        return DatabaseFactory.getLokiAPIDatabase(context).getOpenGroupPublicKey(server)
    }

    override fun setOpenGroupPublicKey(server: String, newValue: String) {
        DatabaseFactory.getLokiAPIDatabase(context).setOpenGroupPublicKey(server, newValue)
    }

    override fun getLastMessageServerID(room: String, server: String): Long? {
        return DatabaseFactory.getLokiAPIDatabase(context).getLastMessageServerID(room, server)
    }

    override fun setLastMessageServerID(room: String, server: String, newValue: Long) {
        DatabaseFactory.getLokiAPIDatabase(context).setLastMessageServerID(room, server, newValue)
    }

    override fun removeLastMessageServerID(room: String, server: String) {
        DatabaseFactory.getLokiAPIDatabase(context).removeLastMessageServerID(room, server)
    }

    override fun getLastDeletionServerID(room: String, server: String): Long? {
        return DatabaseFactory.getLokiAPIDatabase(context).getLastDeletionServerID(room, server)
    }

    override fun setLastDeletionServerID(room: String, server: String, newValue: Long) {
        DatabaseFactory.getLokiAPIDatabase(context).setLastDeletionServerID(room, server, newValue)
    }

    override fun removeLastDeletionServerID(room: String, server: String) {
        DatabaseFactory.getLokiAPIDatabase(context).removeLastDeletionServerID(room, server)
    }

    override fun setUserCount(room: String, server: String, newValue: Int) {
        DatabaseFactory.getLokiAPIDatabase(context).setUserCount(room, server, newValue)
    }

    override fun setOpenGroupServerMessageID(messageID: Long, serverID: Long, threadID: Long, isSms: Boolean) {
        DatabaseFactory.getLokiMessageDatabase(context).setServerID(messageID, serverID, isSms)
        DatabaseFactory.getLokiMessageDatabase(context).setOriginalThreadID(messageID, serverID, threadID)
    }

    override fun isDuplicateMessage(timestamp: Long): Boolean {
        return getReceivedMessageTimestamps().contains(timestamp)
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

    override fun markAsSending(timestamp: Long, author: String) {
        val database = DatabaseFactory.getMmsSmsDatabase(context)
        val messageRecord = database.getMessageFor(timestamp, author) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseFactory.getMmsDatabase(context)
            mmsDatabase.markAsSending(messageRecord.getId())
        } else {
            val smsDatabase = DatabaseFactory.getSmsDatabase(context)
            smsDatabase.markAsSending(messageRecord.getId())
            messageRecord.isPending
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

    override fun getZombieMembers(groupID: String): Set<String> {
        return DatabaseFactory.getGroupDatabase(context).getGroupZombieMembers(groupID).map { it.address.serialize() }.toHashSet()
    }

    override fun removeMember(groupID: String, member: Address) {
        DatabaseFactory.getGroupDatabase(context).removeMember(groupID, member)
    }

    override fun updateMembers(groupID: String, members: List<Address>) {
        DatabaseFactory.getGroupDatabase(context).updateMembers(groupID, members)
    }

    override fun setZombieMembers(groupID: String, members: List<Address>) {
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

    override fun updateFormationTimestamp(groupID: String, formationTimestamp: Long) {
        DatabaseFactory.getGroupDatabase(context)
            .updateFormationTimestamp(groupID, formationTimestamp)
    }

    override fun setExpirationTimer(groupID: String, duration: Int) {
        val recipient = Recipient.from(context, fromSerialized(groupID), false)
        DatabaseFactory.getRecipientDatabase(context).setExpireMessages(recipient, duration);
    }

    override fun getAllV2OpenGroups(): Map<Long, OpenGroupV2> {
        return DatabaseFactory.getLokiThreadDatabase(context).getAllV2OpenGroups()
    }

    override fun getAllGroups(): List<GroupRecord> {
        return DatabaseFactory.getGroupDatabase(context).allGroups
    }

    override fun addOpenGroup(urlAsString: String) {
        OpenGroupManager.addOpenGroup(urlAsString, context)
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

    override fun getThreadId(publicKeyOrOpenGroupID: String): Long? {
        val address = Address.fromSerialized(publicKeyOrOpenGroupID)
        return getThreadId(address)
    }

    override fun getThreadId(address: Address): Long? {
        val recipient = Recipient.from(context, address, false)
        return getThreadId(recipient)
    }

    override fun getThreadId(recipient: Recipient): Long? {
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

    override fun getContactWithSessionID(sessionID: String): Contact? {
        return DatabaseFactory.getSessionContactDatabase(context).getContactWithSessionID(sessionID)
    }

    override fun getAllContacts(): Set<Contact> {
        return DatabaseFactory.getSessionContactDatabase(context).getAllContacts()
    }

    override fun setContact(contact: Contact) {
        DatabaseFactory.getSessionContactDatabase(context).setContact(contact)
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
            threadDatabase.notifyConversationListListeners()
        }
    }

    override fun getLastUpdated(threadID: Long): Long {
        val threadDB = DatabaseFactory.getThreadDatabase(context)
        return threadDB.getLastUpdated(threadID)
    }

    override fun trimThread(threadID: Long, threadLimit: Int) {
        val threadDB = DatabaseFactory.getThreadDatabase(context)
        threadDB.trimThread(threadID, threadLimit)
    }

    override fun getAttachmentDataUri(attachmentId: AttachmentId): Uri {
        return PartAuthority.getAttachmentDataUri(attachmentId)
    }

    override fun getAttachmentThumbnailUri(attachmentId: AttachmentId): Uri {
        return PartAuthority.getAttachmentThumbnailUri(attachmentId)
    }

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