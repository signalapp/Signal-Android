package org.thoughtcrime.securesms.database

import android.content.Context
import android.net.Uri
import com.google.protobuf.ByteString
import org.session.libsession.messaging.StorageProtocol
import org.session.libsession.messaging.jobs.AttachmentUploadJob
import org.session.libsession.messaging.jobs.Job
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageSendJob
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.visible.Attachment
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.opengroups.OpenGroup
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId
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
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.loki.database.LokiThreadDatabase
import org.thoughtcrime.securesms.loki.utilities.get
import org.thoughtcrime.securesms.loki.utilities.getString
import org.thoughtcrime.securesms.mms.IncomingMediaMessage
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.sms.IncomingGroupMessage
import org.thoughtcrime.securesms.sms.IncomingTextMessage

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
        val profileKey = TextSecurePreferences.getProfileKey(context) ?: return null
        return profileKey.toByteArray()
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

    override fun persist(attachments: List<Attachment>): List<Long> {
        TODO("Not yet implemented")
    }

    override fun persist(message: VisibleMessage, quotes: QuoteModel?, linkPreview: List<LinkPreview?>, groupPublicKey: String?, openGroupID: String?): Long? {
        var messageID: Long? = null
        val address = Address.fromSerialized(message.sender!!)
        val recipient = Recipient.from(context, address, false)
        val body: Optional<String> = if (message.text != null) Optional.of(message.text) else Optional.absent()
        var group: Optional<SignalServiceGroup> = Optional.absent()
        if (openGroupID != null) {
            group = Optional.of(SignalServiceGroup(openGroupID.toByteArray(), SignalServiceGroup.GroupType.PUBLIC_CHAT))
        } else if (groupPublicKey != null) {
            group = Optional.of(SignalServiceGroup(groupPublicKey.toByteArray(), SignalServiceGroup.GroupType.SIGNAL))
        }
        if (message.isMediaMessage()) {
            val attachments: Optional<List<SignalServiceAttachment>> = Optional.absent() // TODO figure out how to get SignalServiceAttachment with attachmentID
            val quote: Optional<QuoteModel> = if (quotes != null) Optional.of(quotes) else Optional.absent()
            val linkPreviews: Optional<List<LinkPreview>> = if (linkPreview.isEmpty()) Optional.absent() else Optional.of(linkPreview.mapNotNull { it!! })
            val mediaMessage = IncomingMediaMessage(address, message.receivedTimestamp!!, -1, recipient.expireMessages * 1000L, false, false, body, group, attachments, quote, Optional.absent(), linkPreviews, Optional.absent())
            val mmsDatabase = DatabaseFactory.getMmsDatabase(context)
            mmsDatabase.beginTransaction()
            val insertResult: Optional<MessagingDatabase.InsertResult>
            if (group.isPresent) {
                insertResult = mmsDatabase.insertSecureDecryptedMessageInbox(mediaMessage, message.threadID ?: -1, message.sentTimestamp!!);
            } else {
                insertResult = mmsDatabase.insertSecureDecryptedMessageInbox(mediaMessage, message.threadID ?: -1)
            }
            if (insertResult.isPresent) {
                mmsDatabase.setTransactionSuccessful()
                messageID = insertResult.get().messageId
            }
            mmsDatabase.endTransaction()
        } else {
            val textMessage = IncomingTextMessage(address, 1, message.receivedTimestamp!!, body.get(), group, recipient.expireMessages * 1000L, false)
            val smsDatabase = DatabaseFactory.getSmsDatabase(context)
            val insertResult = smsDatabase.insertMessageInbox(textMessage)
            if (insertResult.isPresent) {
                messageID = insertResult.get().messageId
            }
        }
        return messageID
    }

    // JOBS

    override fun persist(job: Job) {
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
        TODO("Not yet implemented")
    }

    override fun addReceivedMessageTimestamp(timestamp: Long) {
        TODO("Not yet implemented")
    }

    override fun getMessageIdInDatabase(timestamp: Long, author: String): Long? {
        val database = DatabaseFactory.getMmsSmsDatabase(context)
        val address = Address.fromSerialized(author)
        return database.getMessageFor(timestamp, address)?.getId()
    }

    override fun setOpenGroupServerMessageID(messageID: Long, serverID: Long) {
        DatabaseFactory.getLokiMessageDatabase(context).setServerID(messageID, serverID)
    }

    override fun markAsSent(messageID: Long) {
        val database = DatabaseFactory.getMmsSmsDatabase(context)
        val messageRecord = database.getMessageFor(messageID)!!
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseFactory.getMmsDatabase(context)
            mmsDatabase.markAsSent(messageRecord.getId(), true)
        } else {
            val smsDatabase = DatabaseFactory.getSmsDatabase(context)
            smsDatabase.markAsSent(messageRecord.getId(), true)
        }
    }

    override fun markUnidentified(messageID: Long) {
        val database = DatabaseFactory.getMmsSmsDatabase(context)
        val messageRecord = database.getMessageFor(messageID)!!
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseFactory.getMmsDatabase(context)
            mmsDatabase.markUnidentified(messageRecord.getId(), true)
        } else {
            val smsDatabase = DatabaseFactory.getSmsDatabase(context)
            smsDatabase.markUnidentified(messageRecord.getId(), true)
        }
    }

    override fun setErrorMessage(messageID: Long, error: Exception) {
        val database = DatabaseFactory.getMmsSmsDatabase(context)
        val messageRecord = database.getMessageFor(messageID) ?: return
        if (messageRecord.isMms) {
            val mmsDatabase = DatabaseFactory.getMmsDatabase(context)
            mmsDatabase.markAsSentFailed(messageID)
        } else {
            val smsDatabase = DatabaseFactory.getSmsDatabase(context)
            smsDatabase.markAsSentFailed(messageID)
        }
    }

    override fun getGroup(groupID: String): GroupRecord? {
        val group = DatabaseFactory.getGroupDatabase(context).getGroup(groupID)
        return if (group.isPresent) { group.get() } else null
    }

    override fun createGroup(groupId: String, title: String?, members: List<Address>, avatar: SignalServiceAttachmentPointer?, relay: String?, admins: List<Address>) {
        DatabaseFactory.getGroupDatabase(context).create(groupId, title, members, avatar, relay, admins)
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
        val infoMessage = OutgoingGroupMediaMessage(recipient, groupContextBuilder.build(), null, System.currentTimeMillis(), 0, null, listOf(), listOf())
        val mmsDB = DatabaseFactory.getMmsDatabase(context)
        val infoMessageID = mmsDB.insertMessageOutbox(infoMessage, threadID, false, null)
        mmsDB.markAsSent(infoMessageID, true)
    }

    override fun isClosedGroup(publicKey: String): Boolean {
        val isSSKBasedClosedGroup = DatabaseFactory.getSSKDatabase(context).isSSKBasedClosedGroup(publicKey)
        val address = Address.fromSerialized(publicKey)
        return address.isClosedGroup || isSSKBasedClosedGroup
    }

    override fun getClosedGroupEncryptionKeyPairs(groupPublicKey: String): MutableList<ECKeyPair> {
        return DatabaseFactory.getLokiAPIDatabase(context).getClosedGroupEncryptionKeyPairs(groupPublicKey).toMutableList()
    }

    override fun getLatestClosedGroupEncryptionKeyPair(groupPublicKey: String): ECKeyPair? {
        return DatabaseFactory.getLokiAPIDatabase(context).getLatestClosedGroupEncryptionKeyPair(groupPublicKey)
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