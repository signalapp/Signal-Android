package org.session.libsession.messaging.sending_receiving

import android.text.TextUtils
import com.annimon.stream.Collectors
import com.annimon.stream.Stream
import com.annimon.stream.function.Function
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.jobs.AttachmentDownloadJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.ClosedGroupUpdate
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.messages.control.ReadReceipt
import org.session.libsession.messaging.messages.control.TypingIndicator
import org.session.libsession.messaging.messages.visible.Attachment
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.sending_receiving.attachments.PointerAttachment
import org.session.libsession.messaging.sending_receiving.linkpreview.LinkPreview
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationAPI
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.libsignal.util.Hex
import org.session.libsignal.libsignal.util.guava.Optional
import org.session.libsignal.service.api.messages.SignalServiceGroup
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupRatchet
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupRatchetCollectionType
import org.session.libsignal.service.loki.protocol.closedgroups.ClosedGroupSenderKey
import org.session.libsignal.service.loki.protocol.closedgroups.SharedSenderKeysImplementation
import org.session.libsignal.service.loki.utilities.toHexString
import java.security.MessageDigest
import java.util.*
import kotlin.collections.ArrayList

internal fun MessageReceiver.isBlock(publicKey: String): Boolean {
    val context = MessagingConfiguration.shared.context
    val recipient = Recipient.from(context, Address.fromSerialized(publicKey), false)
    return recipient.isBlocked
}

fun MessageReceiver.handle(message: Message, proto: SignalServiceProtos.Content, openGroupID: String?) {
    when (message) {
        is ReadReceipt -> handleReadReceipt(message)
        is TypingIndicator -> handleTypingIndicator(message)
        is ClosedGroupUpdate -> handleClosedGroupUpdate(message)
        is ExpirationTimerUpdate -> handleExpirationTimerUpdate(message, proto)
        is VisibleMessage -> handleVisibleMessage(message, proto, openGroupID)
    }
}

private fun MessageReceiver.handleReadReceipt(message: ReadReceipt) {
    val context = MessagingConfiguration.shared.context
    SSKEnvironment.shared.readReceiptManager.processReadReceipts(context, message.sender!!, message.timestamps!!.asList(), message.receivedTimestamp!!)
}

private fun MessageReceiver.handleTypingIndicator(message: TypingIndicator) {
    when (message.kind!!) {
        TypingIndicator.Kind.STARTED -> showTypingIndicatorIfNeeded(message.sender!!)
        TypingIndicator.Kind.STOPPED -> hideTypingIndicatorIfNeeded(message.sender!!)
    }
}

fun MessageReceiver.showTypingIndicatorIfNeeded(senderPublicKey: String) {
    val context = MessagingConfiguration.shared.context
    val address = Address.fromSerialized(senderPublicKey)
    val threadID = MessagingConfiguration.shared.storage.getThreadIdFor(address) ?: return
    SSKEnvironment.shared.typingIndicators.didReceiveTypingStartedMessage(context, threadID, address, 1)
}

fun MessageReceiver.hideTypingIndicatorIfNeeded(senderPublicKey: String) {
    val context = MessagingConfiguration.shared.context
    val address = Address.fromSerialized(senderPublicKey)
    val threadID = MessagingConfiguration.shared.storage.getThreadIdFor(address) ?: return
    SSKEnvironment.shared.typingIndicators.didReceiveTypingStoppedMessage(context, threadID, address, 1, false)
}

fun MessageReceiver.cancelTypingIndicatorsIfNeeded(senderPublicKey: String) {
    val context = MessagingConfiguration.shared.context
    val address = Address.fromSerialized(senderPublicKey)
    val threadID = MessagingConfiguration.shared.storage.getThreadIdFor(address) ?: return
    SSKEnvironment.shared.typingIndicators.didReceiveIncomingMessage(context, threadID, address, 1)
}

private fun MessageReceiver.handleExpirationTimerUpdate(message: ExpirationTimerUpdate, proto: SignalServiceProtos.Content) {
    if (message.duration!! > 0) {
        setExpirationTimer(message, proto)
    } else {
        disableExpirationTimer(message, proto)
    }
}

fun MessageReceiver.setExpirationTimer(message: ExpirationTimerUpdate, proto: SignalServiceProtos.Content) {
    val id = message.id
    val duration = message.duration!!
    val senderPublicKey = message.sender!!
    SSKEnvironment.shared.messageExpirationManager.setExpirationTimer(id, duration, senderPublicKey, proto)
}

fun MessageReceiver.disableExpirationTimer(message: ExpirationTimerUpdate, proto: SignalServiceProtos.Content) {
    val id = message.id
    val senderPublicKey = message.sender!!
    SSKEnvironment.shared.messageExpirationManager.disableExpirationTimer(id, senderPublicKey, proto)
}

fun MessageReceiver.handleVisibleMessage(message: VisibleMessage, proto: SignalServiceProtos.Content, openGroupID: String?) {
    val storage = MessagingConfiguration.shared.storage
    val context = MessagingConfiguration.shared.context
    // Parse & persist attachments
    val attachments = proto.dataMessage.attachmentsList.mapNotNull { proto ->
        val attachment = Attachment.fromProto(proto)
        if (attachment == null || !attachment.isValid()) {
            return@mapNotNull null
        } else {
            return@mapNotNull attachment
        }
    }
    val attachmentIDs = storage.persist(attachments)
    message.attachmentIDs = attachmentIDs as ArrayList<Long>
    var attachmentsToDownload = attachmentIDs
    // Update profile if needed
    val newProfile = message.profile
    if (newProfile != null) {
        val profileManager = SSKEnvironment.shared.profileManager
        val recipient = Recipient.from(context, Address.fromSerialized(message.sender!!), false)
        val displayName = newProfile.displayName!!
        val userPublicKey = storage.getUserPublicKey()
        if (userPublicKey == message.sender) {
            // Update the user's local name if the message came from their master device
            TextSecurePreferences.setProfileName(context, displayName)
        }
        profileManager.setDisplayName(context, recipient, displayName)
        if (recipient.profileKey == null || !MessageDigest.isEqual(recipient.profileKey, newProfile.profileKey)) {
            profileManager.setProfileKey(context, recipient, newProfile.profileKey!!)
            profileManager.setUnidentifiedAccessMode(context, recipient, Recipient.UnidentifiedAccessMode.UNKNOWN)
            val url = newProfile.profilePictureURL.orEmpty()
            profileManager.setProfilePictureURL(context, recipient, url)
            if (userPublicKey == message.sender) {
                profileManager.updateOpenGroupProfilePicturesIfNeeded(context)
            }
        }
    }
    // Get or create thread
    val threadID = storage.getOrCreateThreadIdFor(message.sender!!, message.groupPublicKey, openGroupID)
    // Parse quote if needed
    var quoteModel: QuoteModel? = null
    if (message.quote != null && proto.dataMessage.hasQuote()) {
        val quote = proto.dataMessage.quote
        val author = Address.fromSerialized(quote.author)
        val messageID = MessagingConfiguration.shared.messageDataProvider.getMessageForQuote(quote.id, author)
        if (messageID != null) {
            val attachmentsWithLinkPreview = MessagingConfiguration.shared.messageDataProvider.getAttachmentsAndLinkPreviewFor(messageID)
            quoteModel = QuoteModel(quote.id, author, MessagingConfiguration.shared.messageDataProvider.getMessageBodyFor(messageID), false, attachmentsWithLinkPreview)
        } else {
            quoteModel = QuoteModel(quote.id, author, quote.text, true, PointerAttachment.forPointers(proto.dataMessage.quote.attachmentsList))
        }
    }
    // Parse link preview if needed
    val linkPreviews: MutableList<LinkPreview?> = mutableListOf()
    if (message.linkPreview != null && proto.dataMessage.previewCount > 0) {
        for (preview in proto.dataMessage.previewList) {
            val thumbnail = PointerAttachment.forPointer(preview.image)
            val url = Optional.fromNullable(preview.url)
            val title = Optional.fromNullable(preview.title)
            val hasContent = !TextUtils.isEmpty(title.or("")) || thumbnail.isPresent
            if (hasContent) {
                val linkPreview = LinkPreview(url.get(), title.or(""), thumbnail)
                linkPreviews.add(linkPreview)
            } else {
                Log.w("Loki", "Discarding an invalid link preview. hasContent: $hasContent")
            }
        }
    }
    // Parse stickers if needed
    // Persist the message
    val messageID = storage.persist(message, quoteModel, linkPreviews, message.groupPublicKey, openGroupID) ?: throw MessageReceiver.Error.NoThread
    message.threadID = threadID
    // Start attachment downloads if needed
    attachmentsToDownload.forEach { attachmentID ->
        val downloadJob = AttachmentDownloadJob(attachmentID, messageID)
        JobQueue.shared.add(downloadJob)
    }
    // Cancel any typing indicators if needed
    cancelTypingIndicatorsIfNeeded(message.sender!!)
    //Notify the user if needed
    SSKEnvironment.shared.notificationManager.updateNotification(context, threadID)
}

private fun MessageReceiver.handleClosedGroupUpdate(message: ClosedGroupUpdate) {
    when (message.kind!!) {
        is ClosedGroupUpdate.Kind.New -> handleNewGroup(message)
        is ClosedGroupUpdate.Kind.Info -> handleGroupUpdate(message)
        is ClosedGroupUpdate.Kind.SenderKeyRequest -> handleSenderKeyRequest(message)
        is ClosedGroupUpdate.Kind.SenderKey -> handleSenderKey(message)
    }
}

private fun MessageReceiver.handleNewGroup(message: ClosedGroupUpdate) {
    val context = MessagingConfiguration.shared.context
    val storage = MessagingConfiguration.shared.storage
    val sskDatabase = MessagingConfiguration.shared.sskDatabase
    if (message.kind !is ClosedGroupUpdate.Kind.New) { return }
    val kind = message.kind!! as ClosedGroupUpdate.Kind.New
    val groupPublicKey = kind.groupPublicKey.toHexString()
    val name = kind.name
    val groupPrivateKey = kind.groupPrivateKey
    val senderKeys = kind.senderKeys
    val members = kind.members.map { it.toHexString() }
    val admins = kind.admins.map { it.toHexString() }
    // Persist the ratchets
    senderKeys.forEach { senderKey ->
        if (!members.contains(senderKey.publicKey.toHexString())) { return@forEach }
        val ratchet = ClosedGroupRatchet(senderKey.chainKey.toHexString(), senderKey.keyIndex, listOf())
        sskDatabase.setClosedGroupRatchet(groupPublicKey, senderKey.publicKey.toHexString(), ratchet, ClosedGroupRatchetCollectionType.Current)
    }
    // Sort out any discrepancies between the provided sender keys and what's required
    val missingSenderKeys = members.toSet().subtract(senderKeys.map { Hex.toStringCondensed(it.publicKey) })
    val userPublicKey = storage.getUserPublicKey()!!
    if (missingSenderKeys.contains(userPublicKey)) {
        val userRatchet = SharedSenderKeysImplementation.shared.generateRatchet(groupPublicKey, userPublicKey)
        val userSenderKey = ClosedGroupSenderKey(Hex.fromStringCondensed(userRatchet.chainKey), userRatchet.keyIndex, Hex.fromStringCondensed(userPublicKey))
        members.forEach { member ->
            if (member == userPublicKey) return@forEach
            val closedGroupUpdateKind = ClosedGroupUpdate.Kind.SenderKey(groupPublicKey.toByteArray(), userSenderKey)
            val closedGroupUpdate = ClosedGroupUpdate()
            closedGroupUpdate.kind = closedGroupUpdateKind
            MessageSender.send(closedGroupUpdate, Destination.ClosedGroup(groupPublicKey))
        }
    }
    missingSenderKeys.minus(userPublicKey).forEach { publicKey ->
        MessageSender.requestSenderKey(groupPublicKey, publicKey)
    }
    // Create the group
    val groupID = GroupUtil.getEncodedClosedGroupID(groupPublicKey)
    if (storage.getGroup(groupID) != null) {
        // Update the group
        storage.updateTitle(groupID, name)
        storage.updateMembers(groupID, members.map { Address.fromSerialized(it) })
    } else {
        storage.createGroup(groupID, name, LinkedList(members.map { Address.fromSerialized(it) }),
                null, null, LinkedList(admins.map { Address.fromSerialized(it) }))
    }
    storage.setProfileSharing(Address.fromSerialized(groupID), true)
    // Add the group to the user's set of public keys to poll for
    sskDatabase.setClosedGroupPrivateKey(groupPublicKey, groupPrivateKey.toHexString())
    // Notify the PN server
    PushNotificationAPI.performOperation(PushNotificationAPI.ClosedGroupOperation.Subscribe, groupPublicKey, userPublicKey)
    // Notify the user
    storage.insertIncomingInfoMessage(context, message.sender!!, groupID, SignalServiceProtos.GroupContext.Type.UPDATE, SignalServiceGroup.Type.UPDATE, name, members, admins)
}

private fun MessageReceiver.handleGroupUpdate(message: ClosedGroupUpdate) {
    val context = MessagingConfiguration.shared.context
    val storage = MessagingConfiguration.shared.storage
    val sskDatabase = MessagingConfiguration.shared.sskDatabase
    if (message.kind !is ClosedGroupUpdate.Kind.Info) { return }
    val kind = message.kind!! as ClosedGroupUpdate.Kind.Info
    val groupPublicKey = kind.groupPublicKey.toHexString()
    val name = kind.name
    val senderKeys = kind.senderKeys
    val members = kind.members.map { it.toHexString() }
    val admins = kind.admins.map { it.toHexString() }
    // Get the group
    val groupID = GroupUtil.getEncodedClosedGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: return Log.d("Loki", "Ignoring closed group info message for nonexistent group.")
    // Check that the sender is a member of the group (before the update)
    if (!group.members.contains(Address.fromSerialized(message.sender!!))) { return Log.d("Loki", "Ignoring closed group info message from non-member.") }
    // Store the ratchets for any new members (it's important that this happens before the code below)
    senderKeys.forEach { senderKey ->
        val ratchet = ClosedGroupRatchet(senderKey.chainKey.toHexString(), senderKey.keyIndex, listOf())
        sskDatabase.setClosedGroupRatchet(groupPublicKey, senderKey.publicKey.toHexString(), ratchet, ClosedGroupRatchetCollectionType.Current)
    }
    // Delete all ratchets and either:
    // • Send out the user's new ratchet using established channels if other members of the group left or were removed
    // • Remove the group from the user's set of public keys to poll for if the current user was among the members that were removed
    val oldMembers = group.members.map { it.serialize() }.toSet()
    val userPublicKey = storage.getUserPublicKey()!!
    val wasUserRemoved = !members.contains(userPublicKey)
    val wasSenderRemoved = !members.contains(message.sender!!)
    if (members.toSet().intersect(oldMembers) != oldMembers.toSet()) {
        val allOldRatchets = sskDatabase.getAllClosedGroupRatchets(groupPublicKey, ClosedGroupRatchetCollectionType.Current)
        for (pair in allOldRatchets) {
            val senderPublicKey = pair.first
            val ratchet = pair.second
            val collection = ClosedGroupRatchetCollectionType.Old
            sskDatabase.setClosedGroupRatchet(groupPublicKey, senderPublicKey, ratchet, collection)
        }
        sskDatabase.removeAllClosedGroupRatchets(groupPublicKey, ClosedGroupRatchetCollectionType.Current)
        if (wasUserRemoved) {
            sskDatabase.removeClosedGroupPrivateKey(groupPublicKey)
            storage.setActive(groupID, false)
            storage.removeMember(groupID, Address.fromSerialized(userPublicKey))
            // Notify the PN server
            PushNotificationAPI.performOperation(PushNotificationAPI.ClosedGroupOperation.Unsubscribe, groupPublicKey, userPublicKey)
        } else {
            val userRatchet = SharedSenderKeysImplementation.shared.generateRatchet(groupPublicKey, userPublicKey)
            val userSenderKey = ClosedGroupSenderKey(Hex.fromStringCondensed(userRatchet.chainKey), userRatchet.keyIndex, Hex.fromStringCondensed(userPublicKey))
            members.forEach { member ->
                if (member == userPublicKey) return@forEach
                val address = Address.fromSerialized(member)
                val closedGroupUpdateKind = ClosedGroupUpdate.Kind.SenderKey(Hex.fromStringCondensed(groupPublicKey), userSenderKey)
                val closedGroupUpdate = ClosedGroupUpdate()
                closedGroupUpdate.kind = closedGroupUpdateKind
                MessageSender.send(closedGroupUpdate, address)
            }
        }
    }
    // Update the group
    storage.updateTitle(groupID, name)
    storage.updateMembers(groupID, members.map { Address.fromSerialized(it) })
    // Notify the user if needed
    val type0 = if (wasSenderRemoved) SignalServiceProtos.GroupContext.Type.QUIT else SignalServiceProtos.GroupContext.Type.UPDATE
    val type1 = if (wasSenderRemoved) SignalServiceGroup.Type.QUIT else SignalServiceGroup.Type.UPDATE
    storage.insertIncomingInfoMessage(context, message.sender!!, groupID, type0, type1, name, members, admins)
}

private fun MessageReceiver.handleSenderKeyRequest(message: ClosedGroupUpdate) {
    if (message.kind !is ClosedGroupUpdate.Kind.SenderKeyRequest) { return }
    val kind = message.kind!! as ClosedGroupUpdate.Kind.SenderKeyRequest
    val storage = MessagingConfiguration.shared.storage
    val sskDatabase = MessagingConfiguration.shared.sskDatabase
    val userPublicKey = storage.getUserPublicKey()!!
    val groupPublicKey = kind.groupPublicKey.toHexString()
    val groupID = GroupUtil.getEncodedClosedGroupID(groupPublicKey)
    val group = storage.getGroup(groupID)
    if (group == null) {
        Log.d("Loki", "Ignoring closed group sender key request for nonexistent group.")
        return
    }
    // Check that the requesting user is a member of the group
    if (!group.members.map { it.serialize() }.contains(message.sender!!)) {
        Log.d("Loki", "Ignoring closed group sender key request from non-member.")
        return
    }
    // Respond to the request
    Log.d("Loki", "Responding to sender key request from: ${message.sender!!}.")
    val userRatchet = sskDatabase.getClosedGroupRatchet(groupPublicKey, userPublicKey, ClosedGroupRatchetCollectionType.Current)
            ?: SharedSenderKeysImplementation.shared.generateRatchet(groupPublicKey, userPublicKey)
    val userSenderKey = ClosedGroupSenderKey(Hex.fromStringCondensed(userRatchet.chainKey), userRatchet.keyIndex, Hex.fromStringCondensed(userPublicKey))
    val closedGroupUpdateKind = ClosedGroupUpdate.Kind.SenderKey(Hex.fromStringCondensed(groupPublicKey), userSenderKey)
    val closedGroupUpdate = ClosedGroupUpdate()
    closedGroupUpdate.kind = closedGroupUpdateKind
    MessageSender.send(closedGroupUpdate, Address.fromSerialized(groupID))
}

private fun MessageReceiver.handleSenderKey(message: ClosedGroupUpdate) {
    if (message.kind !is ClosedGroupUpdate.Kind.SenderKey) { return }
    val kind = message.kind!! as ClosedGroupUpdate.Kind.SenderKey
    val groupPublicKey = kind.groupPublicKey.toHexString()
    val senderKey = kind.senderKey
    if (senderKey.publicKey.toHexString() != message.sender!!) {
        Log.d("Loki", "Ignoring invalid closed group sender key.")
        return
    }
    Log.d("Loki", "Received a sender key from: ${message.sender!!}.")
    val ratchet = ClosedGroupRatchet(senderKey.chainKey.toHexString(), senderKey.keyIndex, listOf())
    MessagingConfiguration.shared.sskDatabase.setClosedGroupRatchet(groupPublicKey, senderKey.publicKey.toHexString(), ratchet, ClosedGroupRatchetCollectionType.Current)
}