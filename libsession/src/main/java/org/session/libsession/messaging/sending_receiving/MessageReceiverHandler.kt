package org.session.libsession.messaging.sending_receiving

import android.text.TextUtils
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.jobs.AttachmentDownloadJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.*
import org.session.libsession.messaging.messages.visible.Attachment
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.sending_receiving.attachments.PointerAttachment
import org.session.libsession.messaging.sending_receiving.linkpreview.LinkPreview
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationAPI
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.threads.GroupRecord
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.libsignal.ecc.DjbECPrivateKey
import org.session.libsignal.libsignal.ecc.DjbECPublicKey
import org.session.libsignal.libsignal.ecc.ECKeyPair
import org.session.libsignal.utilities.logging.Log
import org.session.libsignal.libsignal.util.guava.Optional
import org.session.libsignal.service.api.messages.SignalServiceGroup
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
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
        is ClosedGroupControlMessage -> handleClosedGroupControlMessage(message)
        is ExpirationTimerUpdate -> handleExpirationTimerUpdate(message, proto)
        is ConfigurationMessage -> handleConfigurationMessage(message)
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

private fun MessageReceiver.handleConfigurationMessage(message: ConfigurationMessage) {
    val context = MessagingConfiguration.shared.context
    val storage = MessagingConfiguration.shared.storage
    if (TextSecurePreferences.getConfigurationMessageSynced(context)) return
    if (message.sender != storage.getUserPublicKey()) return
    val allClosedGroupPublicKeys = storage.getAllClosedGroupPublicKeys()
    for (closeGroup in message.closedGroups) {
        if (allClosedGroupPublicKeys.contains(closeGroup.publicKey)) continue
        handleNewClosedGroup(message.sender!!, closeGroup.publicKey, closeGroup.name, closeGroup.encryptionKeyPair, closeGroup.members, closeGroup.admins, message.sentTimestamp!!)
    }
    val allOpenGroups = storage.getAllOpenGroups().map { it.value.server }
    for (openGroup in message.openGroups) {
        if (allOpenGroups.contains(openGroup)) continue
        storage.addOpenGroup(openGroup, 1)
    }
    // TODO: in future handle the latest in config messages
    TextSecurePreferences.setConfigurationMessageSynced(context, true)
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
    val attachmentIDs = storage.persistAttachments(message.id ?: 0, attachments)
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
    val threadID = storage.getOrCreateThreadIdFor(message.syncTarget ?: message.sender!!, message.groupPublicKey, openGroupID)
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

private fun MessageReceiver.handleClosedGroupControlMessage(message: ClosedGroupControlMessage) {
    when (message.kind!!) {
        is ClosedGroupControlMessage.Kind.New -> handleNewClosedGroup(message)
        is ClosedGroupControlMessage.Kind.Update -> handleClosedGroupUpdated(message)
        is ClosedGroupControlMessage.Kind.EncryptionKeyPair -> handleClosedGroupEncryptionKeyPair(message)
        is ClosedGroupControlMessage.Kind.NameChange -> handleClosedGroupNameChanged(message)
        is ClosedGroupControlMessage.Kind.MembersAdded -> handleClosedGroupMembersAdded(message)
        is ClosedGroupControlMessage.Kind.MembersRemoved -> handleClosedGroupMembersRemoved(message)
        ClosedGroupControlMessage.Kind.MemberLeft -> handleClosedGroupMemberLeft(message)
        ClosedGroupControlMessage.Kind.EncryptionKeyPairRequest -> handleClosedGroupEncryptionKeyPairRequest(message)
    }
}

private fun MessageReceiver.handleNewClosedGroup(message: ClosedGroupControlMessage) {
    val kind = message.kind!! as? ClosedGroupControlMessage.Kind.New ?: return
    val groupPublicKey = kind.publicKey.toByteArray().toHexString()
    val members = kind.members.map { it.toByteArray().toHexString() }
    val admins = kind.admins.map { it.toByteArray().toHexString() }
    handleNewClosedGroup(message.sender!!, groupPublicKey, kind.name, kind.encryptionKeyPair, members, admins, message.sentTimestamp!!)
}

// Parameter @sender:String is just for inserting incoming info message
private fun handleNewClosedGroup(sender: String, groupPublicKey: String, name: String, encryptionKeyPair: ECKeyPair, members: List<String>, admins: List<String>, formationTimestamp: Long) {
    val context = MessagingConfiguration.shared.context
    val storage = MessagingConfiguration.shared.storage
    // Create the group
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    if (storage.getGroup(groupID) != null) {
        // Update the group
        storage.updateTitle(groupID, name)
        storage.updateMembers(groupID, members.map { Address.fromSerialized(it) })
    } else {
        storage.createGroup(groupID, name, LinkedList(members.map { Address.fromSerialized(it) }),
                            null, null, LinkedList(admins.map { Address.fromSerialized(it) }), formationTimestamp)
        // Notify the user
        storage.insertIncomingInfoMessage(context, sender, groupID, SignalServiceProtos.GroupContext.Type.UPDATE, SignalServiceGroup.Type.UPDATE, name, members, admins)
    }
    storage.setProfileSharing(Address.fromSerialized(groupID), true)
    // Add the group to the user's set of public keys to poll for
    storage.addClosedGroupPublicKey(groupPublicKey)
    // Store the encryption key pair
    storage.addClosedGroupEncryptionKeyPair(encryptionKeyPair, groupPublicKey)
    // Notify the PN server
    PushNotificationAPI.performOperation(PushNotificationAPI.ClosedGroupOperation.Subscribe, groupPublicKey, storage.getUserPublicKey()!!)
}

private fun MessageReceiver.handleClosedGroupUpdated(message: ClosedGroupControlMessage) {
    // Prepare
    val context = MessagingConfiguration.shared.context
    val storage = MessagingConfiguration.shared.storage
    val senderPublicKey = message.sender ?: return
    val kind = message.kind!! as? ClosedGroupControlMessage.Kind.Update ?: return
    val groupPublicKey = message.groupPublicKey ?: return
    val userPublicKey = storage.getUserPublicKey()!!
    // Unwrap the message
    val name = kind.name
    val members = kind.members.map { it.toByteArray().toHexString() }
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Ignoring closed group info message for nonexistent group.")
        return
    }
    val oldMembers = group.members.map { it.serialize() }
    // Check common group update logic
    if (!isValidGroupUpdate(group, message.sentTimestamp!!, senderPublicKey)) {
        return
    }
    // Check that the admin wasn't removed unless the group was destroyed entirely
    if (!members.contains(group.admins.first().toString()) && members.isNotEmpty()) {
        android.util.Log.d("Loki", "Ignoring invalid closed group update message.")
        return
    }
    // Remove the group from the user's set of public keys to poll for if the current user was removed
    val wasCurrentUserRemoved = !members.contains(userPublicKey)
    if (wasCurrentUserRemoved) {
        disableLocalGroupAndUnsubscribe(groupPublicKey, groupID, userPublicKey)
    }
    // Generate and distribute a new encryption key pair if needed
    val wasAnyUserRemoved = (members.toSet().intersect(oldMembers) != oldMembers.toSet())
    val isCurrentUserAdmin = group.admins.map { it.toString() }.contains(userPublicKey)
    if (wasAnyUserRemoved && isCurrentUserAdmin) {
        MessageSender.generateAndSendNewEncryptionKeyPair(groupPublicKey, members)
    }
    // Update the group
    storage.updateTitle(groupID, name)
    if (!wasCurrentUserRemoved) {
        // The call below sets isActive to true, so if the user is leaving we have to use groupDB.remove(...) instead
        storage.updateMembers(groupID, members.map { Address.fromSerialized(it) })
    }
    // Notify the user
    val wasSenderRemoved = !members.contains(senderPublicKey)
    val type0 = if (wasSenderRemoved) SignalServiceProtos.GroupContext.Type.QUIT else SignalServiceProtos.GroupContext.Type.UPDATE
    val type1 = if (wasSenderRemoved) SignalServiceGroup.Type.QUIT else SignalServiceGroup.Type.UPDATE
    storage.insertIncomingInfoMessage(context, senderPublicKey, groupID, type0, type1, name, members, group.admins.map { it.toString() })
}

private fun MessageReceiver.handleClosedGroupEncryptionKeyPair(message: ClosedGroupControlMessage) {
    // Prepare
    val storage = MessagingConfiguration.shared.storage
    val senderPublicKey = message.sender ?: return
    val kind = message.kind!! as? ClosedGroupControlMessage.Kind.EncryptionKeyPair ?: return
    val groupPublicKey = kind.publicKey?.toByteArray()?.toHexString() ?: message.groupPublicKey ?: return
    val userPublicKey = storage.getUserPublicKey()!!
    val userKeyPair = storage.getUserX25519KeyPair()
    // Unwrap the message
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Ignoring closed group info message for nonexistent group.")
        return
    }
    if (!group.members.map { it.toString() }.contains(senderPublicKey)) {
        android.util.Log.d("Loki", "Ignoring closed group encryption key pair from non-member.")
        return
    }
    // Find our wrapper and decrypt it if possible
    val wrapper = kind.wrappers.firstOrNull { it.publicKey!!.toByteArray().toHexString() == userPublicKey } ?: return
    val encryptedKeyPair = wrapper.encryptedKeyPair!!.toByteArray()
    val plaintext = MessageReceiverDecryption.decryptWithSessionProtocol(encryptedKeyPair, userKeyPair).first
    // Parse it
    val proto = SignalServiceProtos.KeyPair.parseFrom(plaintext)
    val keyPair = ECKeyPair(DjbECPublicKey(proto.publicKey.toByteArray().removing05PrefixIfNeeded()), DjbECPrivateKey(proto.privateKey.toByteArray()))
    // Store it if needed
    val closedGroupEncryptionKeyPairs = storage.getClosedGroupEncryptionKeyPairs(groupPublicKey)
    if (closedGroupEncryptionKeyPairs.contains(keyPair)) {
        Log.d("Loki", "Ignoring duplicate closed group encryption key pair.")
        return
    }
    storage.addClosedGroupEncryptionKeyPair(keyPair, groupPublicKey)
    Log.d("Loki", "Received a new closed group encryption key pair")
}

private fun MessageReceiver.handleClosedGroupNameChanged(message: ClosedGroupControlMessage) {
    val context = MessagingConfiguration.shared.context
    val storage = MessagingConfiguration.shared.storage
    val senderPublicKey = message.sender ?: return
    val kind = message.kind!! as? ClosedGroupControlMessage.Kind.NameChange ?: return
    val groupPublicKey = message.groupPublicKey ?: return
    // Check that the sender is a member of the group (before the update)
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Ignoring closed group info message for nonexistent group.")
        return
    }
    // Check common group update logic
    if (!isValidGroupUpdate(group, message.sentTimestamp!!, senderPublicKey)) {
        return
    }
    val members = group.members.map { it.serialize() }
    val admins = group.admins.map { it.serialize() }
    val name = kind.name
    storage.updateTitle(groupID, name)

    storage.insertIncomingInfoMessage(context, senderPublicKey, groupID, SignalServiceProtos.GroupContext.Type.UPDATE, SignalServiceGroup.Type.UPDATE, name, members, admins)
}

private fun MessageReceiver.handleClosedGroupMembersAdded(message: ClosedGroupControlMessage) {
    val context = MessagingConfiguration.shared.context
    val storage = MessagingConfiguration.shared.storage
    val senderPublicKey = message.sender ?: return
    val kind = message.kind!! as? ClosedGroupControlMessage.Kind.MembersAdded ?: return
    val groupPublicKey = message.groupPublicKey ?: return
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Ignoring closed group info message for nonexistent group.")
        return
    }
    if (!isValidGroupUpdate(group, message.sentTimestamp!!, senderPublicKey)) {
        return
    }
    val name = group.title
    // Check common group update logic
    val members = group.members.map { it.serialize() }
    val admins = group.admins.map { it.serialize() }

    val updateMembers = kind.members.map { it.toByteArray().toHexString() }
    val newMembers = members + updateMembers
    storage.updateMembers(groupID, newMembers.map { Address.fromSerialized(it) })
    // Send the latest encryption key pair to the added members if the current user is the admin of the group
    val isCurrentUserAdmin = admins.contains(storage.getUserPublicKey()!!)
    if (isCurrentUserAdmin) {
        for (member in updateMembers) {
            MessageSender.sendLatestEncryptionKeyPair(member, groupPublicKey)
        }
    }
    storage.insertIncomingInfoMessage(context, senderPublicKey, groupID, SignalServiceProtos.GroupContext.Type.UPDATE, SignalServiceGroup.Type.UPDATE, name, members, admins)
}

private fun MessageReceiver.handleClosedGroupMembersRemoved(message: ClosedGroupControlMessage) {
    val context = MessagingConfiguration.shared.context
    val storage = MessagingConfiguration.shared.storage
    val userPublicKey = storage.getUserPublicKey()!!
    val senderPublicKey = message.sender ?: return
    val kind = message.kind!! as? ClosedGroupControlMessage.Kind.MembersRemoved ?: return
    val groupPublicKey = message.groupPublicKey ?: return
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Ignoring closed group info message for nonexistent group.")
        return
    }
    val name = group.title
    // Check common group update logic
    val members = group.members.map { it.serialize() }
    val admins = group.admins.map { it.toString() }

    // Users that are part of this remove update
    val updateMembers = kind.members.map { it.toByteArray().toHexString() }

    if (!isValidGroupUpdate(group, message.sentTimestamp!!, senderPublicKey)) { return }
    // If admin leaves the group is disbanded
    val didAdminLeave = admins.any { it in updateMembers }
    // newMembers to save is old members minus removed members
    val newMembers = members - updateMembers
    // user should be posting MEMBERS_LEFT so this should not be encountered
    val senderLeft = senderPublicKey in updateMembers
    if (senderLeft) {
        android.util.Log.d("Loki", "Received a MEMBERS_REMOVED instead of a MEMBERS_LEFT from sender $senderPublicKey")
    }
    val wasCurrentUserRemoved = userPublicKey in updateMembers

    // admin should send a MEMBERS_LEFT message but handled here in case
    if (didAdminLeave || wasCurrentUserRemoved) {
        disableLocalGroupAndUnsubscribe(groupPublicKey, groupID, userPublicKey)
    } else {
        val isCurrentUserAdmin = admins.contains(userPublicKey)
        storage.updateMembers(groupID, newMembers.map { Address.fromSerialized(it) })
        if (isCurrentUserAdmin) {
            MessageSender.generateAndSendNewEncryptionKeyPair(groupPublicKey, newMembers)
        }
    }
    val (contextType, signalType) =
            if (senderLeft) SignalServiceProtos.GroupContext.Type.QUIT to SignalServiceGroup.Type.QUIT
            else SignalServiceProtos.GroupContext.Type.UPDATE to SignalServiceGroup.Type.UPDATE

    storage.insertIncomingInfoMessage(context, senderPublicKey, groupID, contextType, signalType, name, members, admins)
}

private fun MessageReceiver.handleClosedGroupMemberLeft(message: ClosedGroupControlMessage) {
    val context = MessagingConfiguration.shared.context
    val storage = MessagingConfiguration.shared.storage
    val senderPublicKey = message.sender ?: return
    val userPublicKey = storage.getUserPublicKey()!!
    if (message.kind!! !is ClosedGroupControlMessage.Kind.MemberLeft) return
    val groupPublicKey = message.groupPublicKey ?: return
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Ignoring closed group info message for nonexistent group.")
        return
    }
    val name = group.title
    // Check common group update logic
    val members = group.members.map { it.serialize() }
    val admins = group.admins.map { it.toString() }
    if (!isValidGroupUpdate(group, message.sentTimestamp!!, senderPublicKey)) {
        return
    }
    // If admin leaves the group is disbanded
    val didAdminLeave = admins.contains(senderPublicKey)
    val updatedMemberList = members - senderPublicKey

    if (didAdminLeave) {
        disableLocalGroupAndUnsubscribe(groupPublicKey, groupID, userPublicKey)
    } else {
        val isCurrentUserAdmin = admins.contains(userPublicKey)
        storage.updateMembers(groupID, updatedMemberList.map { Address.fromSerialized(it) })
        if (isCurrentUserAdmin) {
            MessageSender.generateAndSendNewEncryptionKeyPair(groupPublicKey, updatedMemberList)
        }
    }
    storage.insertIncomingInfoMessage(context, senderPublicKey, groupID, SignalServiceProtos.GroupContext.Type.QUIT, SignalServiceGroup.Type.QUIT, name, members, admins)
}

private fun MessageReceiver.handleClosedGroupEncryptionKeyPairRequest(message: ClosedGroupControlMessage) {
    val storage = MessagingConfiguration.shared.storage
    val senderPublicKey = message.sender ?: return
    val userPublicKey = storage.getUserPublicKey()!!
    if (message.kind!! !is ClosedGroupControlMessage.Kind.EncryptionKeyPairRequest) return
    if (senderPublicKey == userPublicKey) {
        Log.d("Loki", "Ignoring invalid closed group update.")
        return
    }
    val groupPublicKey = message.groupPublicKey ?: return
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Ignoring closed group info message for nonexistent group.")
        return
    }
    if (!isValidGroupUpdate(group, message.sentTimestamp!!, senderPublicKey)) { return }
    MessageSender.sendLatestEncryptionKeyPair(senderPublicKey, groupPublicKey)
}

private fun isValidGroupUpdate(group: GroupRecord,
                               sentTimestamp: Long,
                               senderPublicKey: String): Boolean  {
    val oldMembers = group.members.map { it.serialize() }
    // Check that the message isn't from before the group was created
    if (group.formationTimestamp > sentTimestamp) {
        android.util.Log.d("Loki", "Ignoring closed group update from before thread was created.")
        return false
    }
    // Check that the sender is a member of the group (before the update)
    if (senderPublicKey !in oldMembers) {
        android.util.Log.d("Loki", "Ignoring closed group info message from non-member.")
        return false
    }
    return true
}

fun MessageReceiver.disableLocalGroupAndUnsubscribe(groupPublicKey: String, groupID: String, userPublicKey: String) {
    val storage = MessagingConfiguration.shared.storage
    storage.removeClosedGroupPublicKey(groupPublicKey)
    // Remove the key pairs
    storage.removeAllClosedGroupEncryptionKeyPairs(groupPublicKey)
    // Mark the group as inactive
    storage.setActive(groupID, false)
    storage.removeMember(groupID, Address.fromSerialized(userPublicKey))
    // Notify the PN server
    PushNotificationAPI.performOperation(PushNotificationAPI.ClosedGroupOperation.Unsubscribe, groupPublicKey, userPublicKey)
}