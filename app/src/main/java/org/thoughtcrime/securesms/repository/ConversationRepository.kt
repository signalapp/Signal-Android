package org.thoughtcrime.securesms.repository

import org.session.libsession.database.MessageDataProvider
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.control.MessageRequestResponse
import org.session.libsession.messaging.messages.control.UnsendRequest
import org.session.libsession.messaging.messages.signal.OutgoingTextMessage
import org.session.libsession.messaging.messages.visible.OpenGroupInvitation
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.database.DraftDatabase
import org.thoughtcrime.securesms.database.LokiMessageDatabase
import org.thoughtcrime.securesms.database.LokiThreadDatabase
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.SessionJobDatabase
import org.thoughtcrime.securesms.database.SmsDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.ThreadRecord
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface ConversationRepository {
    fun isOxenHostedOpenGroup(threadId: Long): Boolean
    fun getRecipientForThreadId(threadId: Long): Recipient
    fun saveDraft(threadId: Long, text: String)
    fun getDraft(threadId: Long): String?
    fun inviteContacts(threadId: Long, contacts: List<Recipient>)
    fun unblock(recipient: Recipient)
    fun deleteLocally(recipient: Recipient, message: MessageRecord)
    fun setApproved(recipient: Recipient, isApproved: Boolean)

    suspend fun deleteForEveryone(
        threadId: Long,
        recipient: Recipient,
        message: MessageRecord
    ): ResultOf<Unit>

    fun buildUnsendRequest(recipient: Recipient, message: MessageRecord): UnsendRequest?

    suspend fun deleteMessageWithoutUnsendRequest(
        threadId: Long,
        messages: Set<MessageRecord>
    ): ResultOf<Unit>

    suspend fun banUser(threadId: Long, recipient: Recipient): ResultOf<Unit>

    suspend fun banAndDeleteAll(threadId: Long, recipient: Recipient): ResultOf<Unit>

    suspend fun deleteMessageRequest(thread: ThreadRecord): ResultOf<Unit>

    suspend fun clearAllMessageRequests(): ResultOf<Unit>

    suspend fun acceptMessageRequest(threadId: Long, recipient: Recipient): ResultOf<Unit>

    fun declineMessageRequest(threadId: Long, recipient: Recipient)

    fun hasReceived(threadId: Long): Boolean

}

class DefaultConversationRepository @Inject constructor(
    private val textSecurePreferences: TextSecurePreferences,
    private val messageDataProvider: MessageDataProvider,
    private val threadDb: ThreadDatabase,
    private val draftDb: DraftDatabase,
    private val lokiThreadDb: LokiThreadDatabase,
    private val smsDb: SmsDatabase,
    private val mmsDb: MmsDatabase,
    private val mmsSmsDb: MmsSmsDatabase,
    private val recipientDb: RecipientDatabase,
    private val lokiMessageDb: LokiMessageDatabase,
    private val sessionJobDb: SessionJobDatabase
) : ConversationRepository {

    override fun isOxenHostedOpenGroup(threadId: Long): Boolean {
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId)
        return openGroup?.room == "session" || openGroup?.room == "oxen"
                || openGroup?.room == "lokinet" || openGroup?.room == "crypto"
    }

    override fun getRecipientForThreadId(threadId: Long): Recipient {
        return threadDb.getRecipientForThreadId(threadId)!!
    }

    override fun saveDraft(threadId: Long, text: String) {
        if (text.isEmpty()) return
        val drafts = DraftDatabase.Drafts()
        drafts.add(DraftDatabase.Draft(DraftDatabase.Draft.TEXT, text))
        draftDb.insertDrafts(threadId, drafts)
    }

    override fun getDraft(threadId: Long): String? {
        val drafts = draftDb.getDrafts(threadId)
        draftDb.clearDrafts(threadId)
        return drafts.find { it.type == DraftDatabase.Draft.TEXT }?.value
    }

    override fun inviteContacts(threadId: Long, contacts: List<Recipient>) {
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId) ?: return
        for (contact in contacts) {
            val message = VisibleMessage()
            message.sentTimestamp = System.currentTimeMillis()
            val openGroupInvitation = OpenGroupInvitation()
            openGroupInvitation.name = openGroup.name
            openGroupInvitation.url = openGroup.joinURL
            message.openGroupInvitation = openGroupInvitation
            val outgoingTextMessage = OutgoingTextMessage.fromOpenGroupInvitation(
                openGroupInvitation,
                contact,
                message.sentTimestamp
            )
            smsDb.insertMessageOutbox(-1, outgoingTextMessage, message.sentTimestamp!!)
            MessageSender.send(message, contact.address)
        }
    }

    override fun unblock(recipient: Recipient) {
        recipientDb.setBlocked(recipient, false)
    }

    override fun deleteLocally(recipient: Recipient, message: MessageRecord) {
        buildUnsendRequest(recipient, message)?.let { unsendRequest ->
            textSecurePreferences.getLocalNumber()?.let {
                MessageSender.send(unsendRequest, Address.fromSerialized(it))
            }
        }
        messageDataProvider.deleteMessage(message.id, !message.isMms)
    }

    override fun setApproved(recipient: Recipient, isApproved: Boolean) {
        recipientDb.setApproved(recipient, isApproved)
    }

    override suspend fun deleteForEveryone(
        threadId: Long,
        recipient: Recipient,
        message: MessageRecord
    ): ResultOf<Unit> = suspendCoroutine { continuation ->
        buildUnsendRequest(recipient, message)?.let { unsendRequest ->
            MessageSender.send(unsendRequest, recipient.address)
        }
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId)
        if (openGroup != null) {
            lokiMessageDb.getServerID(message.id, !message.isMms)?.let { messageServerID ->
                OpenGroupAPIV2.deleteMessage(messageServerID, openGroup.room, openGroup.server)
                    .success {
                        messageDataProvider.deleteMessage(message.id, !message.isMms)
                        continuation.resume(ResultOf.Success(Unit))
                    }.fail { error ->
                        continuation.resumeWithException(error)
                    }
            }
        } else {
            messageDataProvider.deleteMessage(message.id, !message.isMms)
            messageDataProvider.getServerHashForMessage(message.id)?.let { serverHash ->
                var publicKey = recipient.address.serialize()
                if (recipient.isClosedGroupRecipient) {
                    publicKey = GroupUtil.doubleDecodeGroupID(publicKey).toHexString()
                }
                SnodeAPI.deleteMessage(publicKey, listOf(serverHash))
                    .success {
                        continuation.resume(ResultOf.Success(Unit))
                    }.fail { error ->
                        continuation.resumeWithException(error)
                    }
            }
        }
    }

    override fun buildUnsendRequest(recipient: Recipient, message: MessageRecord): UnsendRequest? {
        if (recipient.isOpenGroupRecipient) return null
        messageDataProvider.getServerHashForMessage(message.id) ?: return null
        val unsendRequest = UnsendRequest()
        if (message.isOutgoing) {
            unsendRequest.author = textSecurePreferences.getLocalNumber()
        } else {
            unsendRequest.author = message.individualRecipient.address.contactIdentifier()
        }
        unsendRequest.timestamp = message.timestamp

        return unsendRequest
    }

    override suspend fun deleteMessageWithoutUnsendRequest(
        threadId: Long,
        messages: Set<MessageRecord>
    ): ResultOf<Unit> = suspendCoroutine { continuation ->
        val openGroup = lokiThreadDb.getOpenGroupChat(threadId)
        if (openGroup != null) {
            val messageServerIDs = mutableMapOf<Long, MessageRecord>()
            for (message in messages) {
                val messageServerID =
                    lokiMessageDb.getServerID(message.id, !message.isMms) ?: continue
                messageServerIDs[messageServerID] = message
            }
            for ((messageServerID, message) in messageServerIDs) {
                OpenGroupAPIV2.deleteMessage(messageServerID, openGroup.room, openGroup.server)
                    .success {
                        messageDataProvider.deleteMessage(message.id, !message.isMms)
                    }.fail { error ->
                        continuation.resumeWithException(error)
                    }
            }
        } else {
            for (message in messages) {
                if (message.isMms) {
                    mmsDb.deleteMessage(message.id)
                } else {
                    smsDb.deleteMessage(message.id)
                }
            }
        }
        continuation.resume(ResultOf.Success(Unit))
    }

    override suspend fun banUser(threadId: Long, recipient: Recipient): ResultOf<Unit> =
        suspendCoroutine { continuation ->
            val sessionID = recipient.address.toString()
            val openGroup = lokiThreadDb.getOpenGroupChat(threadId)!!
            OpenGroupAPIV2.ban(sessionID, openGroup.room, openGroup.server)
                .success {
                    continuation.resume(ResultOf.Success(Unit))
                }.fail { error ->
                    continuation.resumeWithException(error)
                }
        }

    override suspend fun banAndDeleteAll(threadId: Long, recipient: Recipient): ResultOf<Unit> =
        suspendCoroutine { continuation ->
            val sessionID = recipient.address.toString()
            val openGroup = lokiThreadDb.getOpenGroupChat(threadId)!!
            OpenGroupAPIV2.banAndDeleteAll(sessionID, openGroup.room, openGroup.server)
                .success {
                    continuation.resume(ResultOf.Success(Unit))
                }.fail { error ->
                    continuation.resumeWithException(error)
                }
        }

    override suspend fun deleteMessageRequest(thread: ThreadRecord): ResultOf<Unit> {
        sessionJobDb.cancelPendingMessageSendJobs(thread.threadId)
        recipientDb.setBlocked(thread.recipient, true)
        return ResultOf.Success(Unit)
    }

    override suspend fun clearAllMessageRequests(): ResultOf<Unit> {
        threadDb.readerFor(threadDb.unapprovedConversationList).use { reader ->
            while (reader.next != null) {
                deleteMessageRequest(reader.current)
            }
        }
        return ResultOf.Success(Unit)
    }

    override suspend fun acceptMessageRequest(threadId: Long, recipient: Recipient): ResultOf<Unit> = suspendCoroutine { continuation ->
        recipientDb.setApproved(recipient, true)
        val message = MessageRequestResponse(true)
        MessageSender.send(message, Destination.from(recipient.address))
            .success {
                threadDb.setHasSent(threadId, true)
                continuation.resume(ResultOf.Success(Unit))
            }.fail { error ->
                continuation.resumeWithException(error)
            }
    }

    override fun declineMessageRequest(threadId: Long, recipient: Recipient) {
        recipientDb.setBlocked(recipient, true)
    }

    override fun hasReceived(threadId: Long): Boolean {
        val cursor = mmsSmsDb.getConversation(threadId, true)
        mmsSmsDb.readerFor(cursor).use { reader ->
            while (reader.next != null) {
                if (!reader.current.isOutgoing) {
                    return true
                }
            }
        }
        return false
    }

}