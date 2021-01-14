package org.thoughtcrime.securesms.database

import android.content.Context
import android.net.Uri
import com.google.protobuf.ByteString
import org.session.libsession.messaging.StorageProtocol
import org.session.libsession.messaging.jobs.AttachmentUploadJob
import org.session.libsession.messaging.jobs.Job
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
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.libsignal.ecc.ECKeyPair
import org.session.libsignal.libsignal.util.KeyHelper
import org.session.libsignal.libsignal.util.guava.Optional
import org.session.libsignal.service.api.messages.SignalServiceAttachmentPointer
import org.session.libsignal.service.api.messages.SignalServiceGroup
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.loki.database.LokiAPIDatabase
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.sms.IncomingGroupMessage
import org.thoughtcrime.securesms.sms.IncomingTextMessage
import org.thoughtcrime.securesms.util.GroupUtil

class Storage(val context: Context): StorageProtocol {
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

    override fun persist(job: Job) {
        TODO("Not yet implemented")
    }

    override fun persist(attachments: List<Attachment>): List<Long> {
        TODO("Not yet implemented")
    }

    override fun persist(message: VisibleMessage, quotes: QuoteModel?, linkPreview: List<LinkPreview?>, groupPublicKey: String?, openGroupID: String?): Long? {
        TODO("Not yet implemented")
    }

    override fun markJobAsSucceeded(job: Job) {
        TODO("Not yet implemented")
    }

    override fun markJobAsFailed(job: Job) {
        TODO("Not yet implemented")
    }

    override fun getAllPendingJobs(type: String): List<Job> {
        TODO("Not yet implemented")
    }

    override fun getAttachmentUploadJob(attachmentID: Long): AttachmentUploadJob? {
        TODO("Not yet implemented")
    }

    override fun getMessageSendJob(messageSendJobID: String): MessageSendJob? {
        TODO("Not yet implemented")
    }

    override fun resumeMessageSendJobIfNeeded(messageSendJobID: String) {
        TODO("Not yet implemented")
    }

    override fun isJobCanceled(job: Job): Boolean {
        TODO("Not yet implemented")
    }

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
        TODO("Not yet implemented")
    }

    override fun getThreadID(openGroupID: String): String? {
        TODO("Not yet implemented")
    }

    override fun getOpenGroupPublicKey(server: String): String? {
        TODO("Not yet implemented")
    }

    override fun setOpenGroupPublicKey(server: String, newValue: String) {
        TODO("Not yet implemented")
    }

    override fun setOpenGroupDisplayName(publicKey: String, channel: Long, server: String, displayName: String) {
        TODO("Not yet implemented")
    }

    override fun getOpenGroupDisplayName(publicKey: String, channel: Long, server: String): String? {
        TODO("Not yet implemented")
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

    override fun insertMessageOutbox(message: Message) {
        TODO("Not yet implemented")
    }

    override fun insertMessageInbox(message: Message) {
        TODO("Not yet implemented")
    }

    override fun setErrorMessage(message: Message, error: Exception) {
        TODO("Not yet implemented")
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

    override fun insertOutgoingInfoMessage(context: Context, groupID: String, type: SignalServiceProtos.GroupContext.Type, name: String, members: Collection<String>, admins: Collection<String>, threadID: Long) {
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

    override fun isClosedGroup(publicKey: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun getClosedGroupEncryptionKeyPairs(groupPublicKey: String): MutableList<ECKeyPair> {
        TODO("Not yet implemented")
    }

    override fun getLatestClosedGroupEncryptionKeyPair(groupPublicKey: String): ECKeyPair {
        TODO("Not yet implemented")
    }

    override fun setProfileSharing(address: Address, value: Boolean) {
        val recipient = Recipient.from(context, address, false)
        DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipient, value)
    }

    override fun getOrCreateThreadIdFor(address: Address): Long {
        val recipient = Recipient.from(context, address, false)
        return DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(recipient)
    }

    override fun getOrCreateThreadIdFor(publicKey: String, groupPublicKey: String?, openGroupID: String?): Long? {
        TODO("Not yet implemented")
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