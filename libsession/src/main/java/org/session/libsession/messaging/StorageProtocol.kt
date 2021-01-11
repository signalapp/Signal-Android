package org.session.libsession.messaging


import android.net.Uri
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
import org.session.libsession.messaging.threads.recipients.Recipient.RecipientSettings
import org.session.libsignal.libsignal.ecc.ECKeyPair
import org.session.libsignal.libsignal.ecc.ECPrivateKey
import org.session.libsignal.service.api.messages.SignalServiceAttachmentPointer

interface StorageProtocol {

    // General
    fun getUserPublicKey(): String?
    fun getUserKeyPair(): ECKeyPair?
    fun getUserDisplayName(): String?
    fun getUserProfileKey(): ByteArray?
    fun getUserProfilePictureURL(): String?

    fun getProfileKeyForRecipient(recipientPublicKey: String): ByteArray?

    // Signal Protocol

    fun getOrGenerateRegistrationID(): Int

    // Shared Sender Keys
    fun getClosedGroupPrivateKey(publicKey: String): ECPrivateKey?
    fun isClosedGroup(publicKey: String): Boolean

    // Jobs
    fun persist(job: Job)
    fun markJobAsSucceeded(job: Job)
    fun markJobAsFailed(job: Job)
    fun getAllPendingJobs(type: String): List<Job>
    fun getAttachmentUploadJob(attachmentID: Long): AttachmentUploadJob?
    fun getMessageSendJob(messageSendJobID: String): MessageSendJob?
    fun resumeMessageSendJobIfNeeded(messageSendJobID: String)
    fun isJobCanceled(job: Job): Boolean

    // Authorization
    fun getAuthToken(server: String): String?
    fun setAuthToken(server: String, newValue: String?)
    fun removeAuthToken(server: String)

    // Open Groups
    fun getOpenGroup(threadID: String): OpenGroup?
    fun getThreadID(openGroupID: String): String?

    // Open Group Public Keys
    fun getOpenGroupPublicKey(server: String): String?
    fun setOpenGroupPublicKey(server: String, newValue: String)

    // Open Group User Info
    fun setOpenGroupDisplayName(publicKey: String, channel: Long, server: String, displayName: String)
    fun getOpenGroupDisplayName(publicKey: String, channel: Long, server: String): String?

    // Last Message Server ID
    fun getLastMessageServerID(group: Long, server: String): Long?
    fun setLastMessageServerID(group: Long, server: String, newValue: Long)
    fun removeLastMessageServerID(group: Long, server: String)

    // Last Deletion Server ID
    fun getLastDeletionServerID(group: Long, server: String): Long?
    fun setLastDeletionServerID(group: Long, server: String, newValue: Long)
    fun removeLastDeletionServerID(group: Long, server: String)

    // Open Group Metadata
    fun setUserCount(group: Long, server: String, newValue: Int)
    fun setOpenGroupProfilePictureURL(group: Long, server: String, newValue: String)
    fun getOpenGroupProfilePictureURL(group: Long, server: String): String?
    fun updateTitle(groupID: String, newValue: String)
    fun updateProfilePicture(groupID: String, newValue: ByteArray)

    // Message Handling
    fun getReceivedMessageTimestamps(): Set<Long>
    fun addReceivedMessageTimestamp(timestamp: Long)
    // Returns the IDs of the saved attachments.
    fun persist(attachments: List<Attachment>): List<Long>
    fun insertMessageOutbox(message: Message)
    fun insertMessageInbox(message: Message)
    fun setErrorMessage(message: Message, error: Exception)

    // Closed Groups
    fun getGroup(groupID: String): GroupRecord?
    fun createGroup(groupId: String, title: String?, members: List<Address>, avatar: SignalServiceAttachmentPointer?, relay: String?, admins: List<Address>)
    fun setActive(groupID: String, value: Boolean)
    fun removeMember(groupID: String, member: Address)
    fun updateMembers(groupID: String, members: List<Address>)

    // Settings
    fun setProfileSharing(address: Address, value: Boolean)

    // Thread
    fun getOrCreateThreadIdFor(address: Address): Long
    fun getOrCreateThreadIdFor(publicKey: String, groupPublicKey: String?, openGroupID: String?): Long?
    fun getThreadIdFor(address: Address): Long?

    // Session Request
    fun getSessionRequestSentTimestamp(publicKey: String): Long?
    fun setSessionRequestSentTimestamp(publicKey: String, newValue: Long)
    fun getSessionRequestProcessedTimestamp(publicKey: String): Long?
    fun setSessionRequestProcessedTimestamp(publicKey: String, newValue: Long)

    // Loki User
    fun getDisplayName(publicKey: String): String?
    fun getServerDisplayName(serverID: String, publicKey: String): String?
    fun getProfilePictureURL(publicKey: String): String?

    // Recipient
    fun getRecipientSettings(address: Address): RecipientSettings?

    // PartAuthority
    fun getAttachmentDataUri(attachmentId: AttachmentId): Uri
    fun getAttachmentThumbnailUri(attachmentId: AttachmentId): Uri

    // Message Handling
    /// Returns the ID of the `TSIncomingMessage` that was constructed.
    fun persist(message: VisibleMessage, quotes: QuoteModel?, linkPreview: List<LinkPreview?>, groupPublicKey: String?, openGroupID: String?): Long?
}