package org.thoughtcrime.securesms.database

import android.content.Context
import android.net.Uri
import com.google.protobuf.ByteString
import org.session.libsession.messaging.StorageProtocol
import org.session.libsession.messaging.jobs.AttachmentUploadJob
import org.session.libsession.messaging.jobs.Job
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageSendJob
import org.session.libsession.messaging.messages.visible.Attachment
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.opengroups.OpenGroup
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId
import org.session.libsession.messaging.sending_receiving.attachments.PointerAttachment
import org.session.libsession.messaging.sending_receiving.linkpreview.LinkPreview
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.threads.GroupRecord
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.libsignal.ecc.ECKeyPair
import org.session.libsignal.libsignal.util.KeyHelper
import org.session.libsignal.libsignal.util.guava.Optional
import org.session.libsignal.service.api.messages.SignalServiceAttachment
import org.session.libsignal.service.api.messages.SignalServiceAttachmentPointer
import org.session.libsignal.service.api.messages.SignalServiceGroup
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.api.opengroups.PublicChat
import org.session.libsignal.utilities.logging.Log
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.loki.database.LokiThreadDatabase
import org.thoughtcrime.securesms.loki.protocol.SessionMetaProtocol
import org.thoughtcrime.securesms.loki.utilities.OpenGroupUtilities
import org.thoughtcrime.securesms.loki.utilities.get
import org.thoughtcrime.securesms.loki.utilities.getString
import org.thoughtcrime.securesms.mms.IncomingMediaMessage
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.mms.PartAuthority
import org.session.libsession.messaging.messages.signal.IncomingGroupMessage
import org.session.libsession.messaging.messages.signal.IncomingTextMessage
import org.session.libsession.messaging.messages.signal.OutgoingTextMessage
import org.session.libsession.utilities.preferences.ProfileKeyUtil

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

    override fun getProfileKeyForRecipient(recipientPublicKey: String): ByteArray? {
        val address = Address.fromSerialized(recipientPublicKey)
        val recipient = Recipient.from(context, address, false)
        return recipient.profileKey
    }

    override fun getOrGenerateRegistrationID(): Int {
        var registrationID = TextSecurePreferences.getLocalRegistrationId(context)
        if (registrationID == null) {
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

    override fun persist(message: VisibleMessage, quotes: QuoteModel?, linkPreview: List<LinkPreview?>, groupPublicKey: String?, openGroupID: String?): Long? {
        var messageID: Long? = null
        val senderAddress = Address.fromSerialized(message.sender!!)
        val senderRecipient = Recipient.from(context, senderAddress, false)
        var group: Optional<SignalServiceGroup> = Optional.absent()
        if (openGroupID != null) {
            group = Optional.of(SignalServiceGroup(openGroupID.toByteArray(), SignalServiceGroup.GroupType.PUBLIC_CHAT))
        } else if (groupPublicKey != null) {
            group = Optional.of(SignalServiceGroup(groupPublicKey.toByteArray(), SignalServiceGroup.GroupType.SIGNAL))
        }
        if (message.isMediaMessage()) {
            val quote: Optional<QuoteModel> = if (quotes != null) Optional.of(quotes) else Optional.absent()
            val linkPreviews: Optional<List<LinkPreview>> = if (linkPreview.isEmpty()) Optional.absent() else Optional.of(linkPreview.mapNotNull { it!! })
            val mmsDatabase = DatabaseFactory.getMmsDatabase(context)
            mmsDatabase.beginTransaction()
            val insertResult = if (message.sender == getUserPublicKey()) {
                val targetAddress = if (message.syncTarget != null) {
                    Address.fromSerialized(message.syncTarget!!)
                } else {
                    if (group.isPresent) {
                        Address.fromSerialized(GroupUtil.getEncodedId(group.get()))
                    } else {
                        Log.d("Loki", "Cannot handle message from self.")
                        return null
                    }
                }
                val attachments = message.attachmentIDs.mapNotNull {
                    DatabaseFactory.getAttachmentProvider(context).getSignalAttachmentPointer(it)
                }.mapNotNull {
                    PointerAttachment.forPointer(Optional.of(it)).orNull()
                }
                val mediaMessage = OutgoingMediaMessage.from(message, Recipient.from(context, targetAddress, false), attachments, quote.orNull(), linkPreviews.orNull().firstOrNull())
                mmsDatabase.insertSecureDecryptedMessageOutbox(mediaMessage, message.threadID ?: -1, message.sentTimestamp!!)
            } else {
                // It seems like we have replaced SignalServiceAttachment with SessionServiceAttachment
                val attachments: Optional<List<SignalServiceAttachment>> = Optional.of(message.attachmentIDs.mapNotNull {
                    DatabaseFactory.getAttachmentProvider(context).getSignalAttachmentPointer(it)
                })
                val mediaMessage = IncomingMediaMessage.from(message, senderAddress, senderRecipient.expireMessages * 1000L, group, attachments, quote, linkPreviews)
                if (group.isPresent) {
                    mmsDatabase.insertSecureDecryptedMessageInbox(mediaMessage, message.threadID ?: -1, message.sentTimestamp!!)
                } else {
                    mmsDatabase.insertSecureDecryptedMessageInbox(mediaMessage, message.threadID ?: -1)
                }
            }
            if (insertResult.isPresent) {
                mmsDatabase.setTransactionSuccessful()
                messageID = insertResult.get().messageId
            }
            mmsDatabase.endTransaction()
        } else {
            val smsDatabase = DatabaseFactory.getSmsDatabase(context)
            val insertResult = if (message.sender == getUserPublicKey()) {
                val targetAddress = if (message.syncTarget != null) {
                    Address.fromSerialized(message.syncTarget!!)
                } else {
                    if (group.isPresent) {
                        Address.fromSerialized(GroupUtil.getEncodedId(group.get()))
                    } else {
                        Log.d("Loki", "Cannot handle message from self.")
                        return null
                    }
                }
                val textMessage = OutgoingTextMessage.from(message, Recipient.from(context, targetAddress, false))
                smsDatabase.insertMessageOutbox(message.threadID ?: -1, textMessage, message.sentTimestamp!!)
            } else {
                val textMessage = IncomingTextMessage.from(message, senderAddress, group, senderRecipient.expireMessages * 1000L)
                if (group.isPresent) {
                    smsDatabase.insertMessageInbox(textMessage, message.sentTimestamp!!)
                } else {
                    smsDatabase.insertMessageInbox(textMessage)
                }
            }
            if (insertResult.isPresent) {
                messageID = insertResult.get().messageId
            }
        }
        return messageID
    }

    // JOBS
    override fun persistJob(job: Job) {
        DatabaseFactory.getSessionJobDatabase(context).persistJob(job)
    }

    override fun markJobAsSucceeded(job: Job) {
        DatabaseFactory.getSessionJobDatabase(context).markJobAsSucceeded(job)
    }

    override fun markJobAsFailed(job: Job) {
        DatabaseFactory.getSessionJobDatabase(context).markJobAsFailed(job)
    }

    override fun getAllPendingJobs(type: String): List<Job> {
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
        job.delegate = JobQueue.shared
        job.execute()
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

    override fun getOpenGroup(threadID: String): OpenGroup? {
        if (threadID.toInt() < 0) { return null }
        val database = databaseHelper.readableDatabase
        return database.get(LokiThreadDatabase.publicChatTable, "${LokiThreadDatabase.threadID} = ?", arrayOf(threadID)) { cursor ->
            val publicChatAsJSON = cursor.getString(LokiThreadDatabase.publicChat)
            OpenGroup.fromJSON(publicChatAsJSON)
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

    override fun getOpenGroupDisplayName(publicKey: String, channel: Long, server: String): String? {
        val groupID = "$server.$channel"
        return DatabaseFactory.getLokiUserDatabase(context).getServerDisplayName(groupID, publicKey)
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

    override fun getLastDeletionServerID(group: Long, server: String): Long? {
        return DatabaseFactory.getLokiAPIDatabase(context).getLastDeletionServerID(group, server)
    }

    override fun setLastDeletionServerID(group: Long, server: String, newValue: Long) {
        DatabaseFactory.getLokiAPIDatabase(context).setLastDeletionServerID(group, server, newValue)
    }

    override fun removeLastDeletionServerID(group: Long, server: String) {
        DatabaseFactory.getLokiAPIDatabase(context).removeLastDeletionServerID(group, server)
    }

    override fun isMessageDuplicated(timestamp: Long, sender: String): Boolean {
        val database = DatabaseFactory.getMmsSmsDatabase(context)
        return if (sender.isEmpty()) {
            database.getMessageForTimestamp(timestamp) != null
        } else {
            database.getMessageFor(timestamp, sender) != null
        }
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

//    override fun removeReceivedMessageTimestamps(timestamps: Set<Long>) {
//        TODO("Not yet implemented")
//    }

    override fun getMessageIdInDatabase(timestamp: Long, author: String): Long? {
        val database = DatabaseFactory.getMmsSmsDatabase(context)
        val address = Address.fromSerialized(author)
        return database.getMessageFor(timestamp, address)?.getId()
    }

    override fun setOpenGroupServerMessageID(messageID: Long, serverID: Long) {
        DatabaseFactory.getLokiMessageDatabase(context).setServerID(messageID, serverID)
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
    }

    override fun getGroup(groupID: String): GroupRecord? {
        val group = DatabaseFactory.getGroupDatabase(context).getGroup(groupID)
        return if (group.isPresent) { group.get() } else null
    }

    override fun createGroup(groupId: String, title: String?, members: List<Address>, avatar: SignalServiceAttachmentPointer?, relay: String?, admins: List<Address>, formationTimestamp: Long) {
        DatabaseFactory.getGroupDatabase(context).create(groupId, title, members, avatar, relay, admins, formationTimestamp)
    }

    override fun setActive(groupID: String, value: Boolean) {
        DatabaseFactory.getGroupDatabase(context).setActive(groupID, value)
    }

    override fun removeMember(groupID: String, member: Address) {
        DatabaseFactory.getGroupDatabase(context).removeMember(groupID, member)
    }

    override fun updateMembers(groupID: String, members: List<Address>) {
        DatabaseFactory.getGroupDatabase(context).updateMembers(groupID, members)
    }

    override fun insertIncomingInfoMessage(context: Context, senderPublicKey: String, groupID: String, type0: SignalServiceProtos.GroupContext.Type, type1: SignalServiceGroup.Type, name: String, members: Collection<String>, admins: Collection<String>) {
        val groupContextBuilder = SignalServiceProtos.GroupContext.newBuilder()
                .setId(ByteString.copyFrom(GroupUtil.getDecodedGroupIDAsData(groupID)))
                .setType(type0)
                .setName(name)
                .addAllMembers(members)
                .addAllAdmins(admins)
        val group = SignalServiceGroup(type1, GroupUtil.getDecodedGroupIDAsData(groupID), SignalServiceGroup.GroupType.SIGNAL, name, members.toList(), null, admins.toList())
        val m = IncomingTextMessage(Address.fromSerialized(senderPublicKey), 1, System.currentTimeMillis(), "", Optional.of(group), 0, true)
        val infoMessage = IncomingGroupMessage(m, groupContextBuilder.build(), "")
        val smsDB = DatabaseFactory.getSmsDatabase(context)
        smsDB.insertMessageInbox(infoMessage)
    }

    override fun insertOutgoingInfoMessage(context: Context, groupID: String, type: SignalServiceProtos.GroupContext.Type, name: String, members: Collection<String>, admins: Collection<String>, threadID: Long) {
        val recipient = Recipient.from(context, Address.fromSerialized(groupID), false)
        val groupContextBuilder = SignalServiceProtos.GroupContext.newBuilder()
                .setId(ByteString.copyFrom(GroupUtil.getDecodedGroupIDAsData(groupID)))
                .setType(type)
                .setName(name)
                .addAllMembers(members)
                .addAllAdmins(admins)
        val infoMessage = OutgoingGroupMediaMessage(recipient, groupContextBuilder.build(), null, 0, null, listOf(), listOf())
        val mmsDB = DatabaseFactory.getMmsDatabase(context)
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

    override fun getAllOpenGroups(): Map<Long, PublicChat> {
        return DatabaseFactory.getLokiThreadDatabase(context).getAllPublicChats()
    }

    override fun addOpenGroup(server: String, channel: Long) {
        OpenGroupUtilities.addGroup(context, server, channel)
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
            val recipient = Recipient.from(context, Address.fromSerialized(openGroupID), false)
            return database.getOrCreateThreadIdFor(recipient)
        } else if (!groupPublicKey.isNullOrEmpty()) {
            val recipient = Recipient.from(context, Address.fromSerialized(groupPublicKey), false)
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

    override fun getAttachmentDataUri(attachmentId: AttachmentId): Uri {
        return PartAuthority.getAttachmentDataUri(attachmentId)
    }

    override fun getAttachmentThumbnailUri(attachmentId: AttachmentId): Uri {
        return PartAuthority.getAttachmentThumbnailUri(attachmentId)
    }
}