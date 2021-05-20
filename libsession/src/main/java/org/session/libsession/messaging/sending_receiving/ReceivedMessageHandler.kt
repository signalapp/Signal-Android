package org.session.libsession.messaging.sending_receiving

import android.text.TextUtils
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.AttachmentDownloadJob
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.control.*
import org.session.libsession.messaging.messages.visible.Attachment
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.sending_receiving.attachments.PointerAttachment
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationAPI
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupRecord
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.ProfileKeyUtil
import org.session.libsignal.crypto.ecc.DjbECPrivateKey
import org.session.libsignal.crypto.ecc.DjbECPublicKey
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.utilities.guava.Optional
import org.session.libsignal.messages.SignalServiceGroup
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.utilities.removing05PrefixIfNeeded
import org.session.libsignal.utilities.toHexString
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import java.security.MessageDigest
import java.util.*
import kotlin.collections.ArrayList

internal fun MessageReceiver.isBlocked(publicKey: String): Boolean {
    val context = MessagingModuleConfiguration.shared.context
    val recipient = Recipient.from(context, Address.fromSerialized(publicKey), false)
    return recipient.isBlocked
}

fun MessageReceiver.handle(message: Message, proto: SignalServiceProtos.Content, openGroupID: String?) {
    when (message) {
        is ReadReceipt -> handleReadReceipt(message)
        is TypingIndicator -> handleTypingIndicator(message)
        is ClosedGroupControlMessage -> handleClosedGroupControlMessage(message)
        is ExpirationTimerUpdate -> handleExpirationTimerUpdate(message)
        is DataExtractionNotification -> handleDataExtractionNotification(message)
        is ConfigurationMessage -> handleConfigurationMessage(message)
        is VisibleMessage -> handleVisibleMessage(message, proto, openGroupID)
    }
}

// region Control Messages
private fun MessageReceiver.handleReadReceipt(message: ReadReceipt) {
    val context = MessagingModuleConfiguration.shared.context
    SSKEnvironment.shared.readReceiptManager.processReadReceipts(context, message.sender!!, message.timestamps!!, message.receivedTimestamp!!)
}

private fun MessageReceiver.handleTypingIndicator(message: TypingIndicator) {
    when (message.kind!!) {
        TypingIndicator.Kind.STARTED -> showTypingIndicatorIfNeeded(message.sender!!)
        TypingIndicator.Kind.STOPPED -> hideTypingIndicatorIfNeeded(message.sender!!)
    }
}

fun MessageReceiver.showTypingIndicatorIfNeeded(senderPublicKey: String) {
    val context = MessagingModuleConfiguration.shared.context
    val address = Address.fromSerialized(senderPublicKey)
    val threadID = MessagingModuleConfiguration.shared.storage.getThreadIdFor(address) ?: return
    SSKEnvironment.shared.typingIndicators.didReceiveTypingStartedMessage(context, threadID, address, 1)
}

fun MessageReceiver.hideTypingIndicatorIfNeeded(senderPublicKey: String) {
    val context = MessagingModuleConfiguration.shared.context
    val address = Address.fromSerialized(senderPublicKey)
    val threadID = MessagingModuleConfiguration.shared.storage.getThreadIdFor(address) ?: return
    SSKEnvironment.shared.typingIndicators.didReceiveTypingStoppedMessage(context, threadID, address, 1, false)
}

fun MessageReceiver.cancelTypingIndicatorsIfNeeded(senderPublicKey: String) {
    val context = MessagingModuleConfiguration.shared.context
    val address = Address.fromSerialized(senderPublicKey)
    val threadID = MessagingModuleConfiguration.shared.storage.getThreadIdFor(address) ?: return
    SSKEnvironment.shared.typingIndicators.didReceiveIncomingMessage(context, threadID, address, 1)
}

private fun MessageReceiver.handleExpirationTimerUpdate(message: ExpirationTimerUpdate) {
    if (message.duration!! > 0) {
        SSKEnvironment.shared.messageExpirationManager.setExpirationTimer(message)
    } else {
        SSKEnvironment.shared.messageExpirationManager.disableExpirationTimer(message)
    }
}

private fun MessageReceiver.handleDataExtractionNotification(message: DataExtractionNotification) {
    // We don't handle data extraction messages for groups (they shouldn't be sent, but just in case we filter them here too)
    if (message.groupPublicKey != null) return
    val storage = MessagingModuleConfiguration.shared.storage
    val senderPublicKey = message.sender!!
    val notification: DataExtractionNotificationInfoMessage = when(message.kind) {
        is DataExtractionNotification.Kind.Screenshot -> DataExtractionNotificationInfoMessage(DataExtractionNotificationInfoMessage.Kind.SCREENSHOT)
        is DataExtractionNotification.Kind.MediaSaved -> DataExtractionNotificationInfoMessage(DataExtractionNotificationInfoMessage.Kind.MEDIA_SAVED)
        else -> return
    }
    storage.insertDataExtractionNotificationMessage(senderPublicKey, notification, message.sentTimestamp!!)
}

private fun handleConfigurationMessage(message: ConfigurationMessage) {
    val context = MessagingModuleConfiguration.shared.context
    val storage = MessagingModuleConfiguration.shared.storage
    if (TextSecurePreferences.getConfigurationMessageSynced(context)
        && !TextSecurePreferences.shouldUpdateProfile(context, message.sentTimestamp!!)) return
    val userPublicKey = storage.getUserPublicKey()
    if (userPublicKey == null || message.sender != storage.getUserPublicKey()) return
    TextSecurePreferences.setConfigurationMessageSynced(context, true)
    TextSecurePreferences.setLastProfileUpdateTime(context, message.sentTimestamp!!)
    val allClosedGroupPublicKeys = storage.getAllClosedGroupPublicKeys()
    for (closedGroup in message.closedGroups) {
        if (allClosedGroupPublicKeys.contains(closedGroup.publicKey)) continue
        handleNewClosedGroup(message.sender!!, message.sentTimestamp!!, closedGroup.publicKey, closedGroup.name,
            closedGroup.encryptionKeyPair!!, closedGroup.members, closedGroup.admins, message.sentTimestamp!!)
    }
    val allOpenGroups = storage.getAllOpenGroups().map { it.value.server }
    val allV2OpenGroups = storage.getAllV2OpenGroups().map { it.value.joinURL }
    for (openGroup in message.openGroups) {
        if (allOpenGroups.contains(openGroup) || allV2OpenGroups.contains(openGroup)) continue
        storage.addOpenGroup(openGroup, 1)
    }
    if (message.displayName.isNotEmpty()) {
        TextSecurePreferences.setProfileName(context, message.displayName)
        storage.setDisplayName(userPublicKey, message.displayName)
    }
    if (message.profileKey.isNotEmpty() && !message.profilePicture.isNullOrEmpty()
        && TextSecurePreferences.getProfilePictureURL(context) != message.profilePicture) {
        val profileKey = Base64.encodeBytes(message.profileKey)
        ProfileKeyUtil.setEncodedProfileKey(context, profileKey)
        storage.setProfileKeyForRecipient(userPublicKey, message.profileKey)
        storage.setUserProfilePictureUrl(message.profilePicture!!)
    }
    storage.addContacts(message.contacts)
}
//endregion

// region Visible Messages
fun MessageReceiver.handleVisibleMessage(message: VisibleMessage, proto: SignalServiceProtos.Content, openGroupID: String?) {
    val storage = MessagingModuleConfiguration.shared.storage
    val context = MessagingModuleConfiguration.shared.context
    val userPublicKey = storage.getUserPublicKey()
    // Get or create thread
    // FIXME: In case this is an open group this actually * doesn't * create the thread if it doesn't yet
    //        exist. This is intentional, but it's very non-obvious.
    val threadID = storage.getOrCreateThreadIdFor(message.syncTarget
        ?: message.sender!!, message.groupPublicKey, openGroupID)
    if (threadID < 0) {
        // Thread doesn't exist; should only be reached in a case where we are processing open group messages for a no longer existent thread
        throw MessageReceiver.Error.NoThread
    }
    val openGroup = threadID.let {
        storage.getOpenGroup(it.toString())
    }
    // Update profile if needed
    val profile = message.profile
    if (profile != null && userPublicKey != message.sender && openGroup == null) { // Don't do this in V1 open groups
        val profileManager = SSKEnvironment.shared.profileManager
        val recipient = Recipient.from(context, Address.fromSerialized(message.sender!!), false)
        val displayName = profile.displayName!!
        if (displayName.isNotEmpty()) {
            profileManager.setDisplayName(context, recipient, displayName)
        }
        if (profile.profileKey?.isNotEmpty() == true && profile.profilePictureURL?.isNotEmpty() == true
            && (recipient.profileKey == null || !MessageDigest.isEqual(recipient.profileKey, profile.profileKey))) {
            profileManager.setProfileKey(context, recipient, profile.profileKey!!)
            profileManager.setUnidentifiedAccessMode(context, recipient, Recipient.UnidentifiedAccessMode.UNKNOWN)
            profileManager.setProfilePictureURL(context, recipient, profile.profilePictureURL!!)
        }
    }
    // Parse quote if needed
    var quoteModel: QuoteModel? = null
    if (message.quote != null && proto.dataMessage.hasQuote()) {
        val quote = proto.dataMessage.quote
        val author = Address.fromSerialized(quote.author)
        val messageDataProvider = MessagingModuleConfiguration.shared.messageDataProvider
        val messageInfo = messageDataProvider.getMessageForQuote(quote.id, author)
        if (messageInfo != null) {
            val attachments = if (messageInfo.second) messageDataProvider.getAttachmentsAndLinkPreviewFor(messageInfo.first) else ArrayList()
            quoteModel = QuoteModel(quote.id, author, messageDataProvider.getMessageBodyFor(quote.id, quote.author), false, attachments)
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
    // Parse attachments if needed
    val attachments = proto.dataMessage.attachmentsList.mapNotNull { proto ->
        val attachment = Attachment.fromProto(proto)
        if (!attachment.isValid()) {
            return@mapNotNull null
        } else {
            return@mapNotNull attachment
        }
    }
    // Persist the message
    message.threadID = threadID
    val messageID = storage.persist(message, quoteModel, linkPreviews, message.groupPublicKey, openGroupID, attachments) ?: throw MessageReceiver.Error.DuplicateMessage
    // Parse & persist attachments
    // Start attachment downloads if needed
    storage.getAttachmentsForMessage(messageID).forEach { attachment ->
        attachment.attachmentId?.let { id ->
            val downloadJob = AttachmentDownloadJob(id.rowId, messageID)
            JobQueue.shared.add(downloadJob)
        }
    }
    val openGroupServerID = message.openGroupServerMessageID
    if (openGroupServerID != null) {
        val isSms = !(message.isMediaMessage() || attachments.isNotEmpty())
        storage.setOpenGroupServerMessageID(messageID, openGroupServerID, threadID, isSms)
    }
    // Cancel any typing indicators if needed
    cancelTypingIndicatorsIfNeeded(message.sender!!)
    //Notify the user if needed
    SSKEnvironment.shared.notificationManager.updateNotification(context, threadID)
}
//endregion

// region Closed Groups
private fun MessageReceiver.handleClosedGroupControlMessage(message: ClosedGroupControlMessage) {
    when (message.kind!!) {
        is ClosedGroupControlMessage.Kind.New -> handleNewClosedGroup(message)
        is ClosedGroupControlMessage.Kind.EncryptionKeyPair -> handleClosedGroupEncryptionKeyPair(message)
        is ClosedGroupControlMessage.Kind.NameChange -> handleClosedGroupNameChanged(message)
        is ClosedGroupControlMessage.Kind.MembersAdded -> handleClosedGroupMembersAdded(message)
        is ClosedGroupControlMessage.Kind.MembersRemoved -> handleClosedGroupMembersRemoved(message)
        is ClosedGroupControlMessage.Kind.MemberLeft -> handleClosedGroupMemberLeft(message)
    }
}

private fun MessageReceiver.handleNewClosedGroup(message: ClosedGroupControlMessage) {
    val kind = message.kind!! as? ClosedGroupControlMessage.Kind.New ?: return
    val groupPublicKey = kind.publicKey.toByteArray().toHexString()
    val members = kind.members.map { it.toByteArray().toHexString() }
    val admins = kind.admins.map { it.toByteArray().toHexString() }
    handleNewClosedGroup(message.sender!!, message.sentTimestamp!!, groupPublicKey, kind.name, kind.encryptionKeyPair!!, members, admins, message.sentTimestamp!!)
}

private fun handleNewClosedGroup(sender: String, sentTimestamp: Long, groupPublicKey: String, name: String, encryptionKeyPair: ECKeyPair, members: List<String>, admins: List<String>, formationTimestamp: Long) {
    val context = MessagingModuleConfiguration.shared.context
    val storage = MessagingModuleConfiguration.shared.storage
    // Create the group
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    if (storage.getGroup(groupID) != null) {
        // Update the group
        storage.updateTitle(groupID, name)
        storage.updateMembers(groupID, members.map { Address.fromSerialized(it) })
    } else {
        storage.createGroup(groupID, name, LinkedList(members.map { Address.fromSerialized(it) }),
            null, null, LinkedList(admins.map { Address.fromSerialized(it) }), formationTimestamp)
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        // Notify the user
        if (userPublicKey == sender) {
            val threadID = storage.getOrCreateThreadIdFor(Address.fromSerialized(groupID))
            storage.insertOutgoingInfoMessage(context, groupID, SignalServiceGroup.Type.CREATION, name, members, admins, threadID, sentTimestamp)
        } else {
            storage.insertIncomingInfoMessage(context, sender, groupID, SignalServiceGroup.Type.CREATION, name, members, admins, sentTimestamp)
        }
    }
    storage.setProfileSharing(Address.fromSerialized(groupID), true)
    // Add the group to the user's set of public keys to poll for
    storage.addClosedGroupPublicKey(groupPublicKey)
    // Store the encryption key pair
    storage.addClosedGroupEncryptionKeyPair(encryptionKeyPair, groupPublicKey)
    // Notify the PN server
    PushNotificationAPI.performOperation(PushNotificationAPI.ClosedGroupOperation.Subscribe, groupPublicKey, storage.getUserPublicKey()!!)
}

private fun MessageReceiver.handleClosedGroupEncryptionKeyPair(message: ClosedGroupControlMessage) {
    // Prepare
    val storage = MessagingModuleConfiguration.shared.storage
    val senderPublicKey = message.sender ?: return
    val kind = message.kind!! as? ClosedGroupControlMessage.Kind.EncryptionKeyPair ?: return
    var groupPublicKey = kind.publicKey?.toByteArray()?.toHexString()
    if (groupPublicKey.isNullOrEmpty()) groupPublicKey = message.groupPublicKey ?: return
    val userPublicKey = storage.getUserPublicKey()!!
    val userKeyPair = storage.getUserX25519KeyPair()
    // Unwrap the message
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Ignoring closed group encryption key pair for nonexistent group.")
        return
    }
    if (!group.isActive) {
        Log.d("Loki", "Ignoring closed group encryption key pair for inactive group.")
        return
    }
    if (!group.admins.map { it.toString() }.contains(senderPublicKey)) {
        Log.d("Loki", "Ignoring closed group encryption key pair from non-admin.")
        return
    }
    // Find our wrapper and decrypt it if possible
    val wrapper = kind.wrappers.firstOrNull { it.publicKey!! == userPublicKey } ?: return
    val encryptedKeyPair = wrapper.encryptedKeyPair!!.toByteArray()
    val plaintext = MessageDecrypter.decrypt(encryptedKeyPair, userKeyPair).first
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
    Log.d("Loki", "Received a new closed group encryption key pair.")
}

private fun MessageReceiver.handleClosedGroupNameChanged(message: ClosedGroupControlMessage) {
    val context = MessagingModuleConfiguration.shared.context
    val storage = MessagingModuleConfiguration.shared.storage
    val userPublicKey = TextSecurePreferences.getLocalNumber(context)
    val senderPublicKey = message.sender ?: return
    val kind = message.kind!! as? ClosedGroupControlMessage.Kind.NameChange ?: return
    val groupPublicKey = message.groupPublicKey ?: return
    // Check that the sender is a member of the group (before the update)
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Ignoring closed group update for nonexistent group.")
        return
    }
    if (!group.isActive) {
        Log.d("Loki", "Ignoring closed group update for inactive group.")
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
    // Notify the user
    if (userPublicKey == senderPublicKey) {
        val threadID = storage.getOrCreateThreadIdFor(Address.fromSerialized(groupID))
        storage.insertOutgoingInfoMessage(context, groupID, SignalServiceGroup.Type.NAME_CHANGE, name, members, admins, threadID, message.sentTimestamp!!)
    } else {
        storage.insertIncomingInfoMessage(context, senderPublicKey, groupID, SignalServiceGroup.Type.NAME_CHANGE, name, members, admins, message.sentTimestamp!!)
    }
}

private fun MessageReceiver.handleClosedGroupMembersAdded(message: ClosedGroupControlMessage) {
    val context = MessagingModuleConfiguration.shared.context
    val storage = MessagingModuleConfiguration.shared.storage
    val userPublicKey = storage.getUserPublicKey()!!
    val senderPublicKey = message.sender ?: return
    val kind = message.kind!! as? ClosedGroupControlMessage.Kind.MembersAdded ?: return
    val groupPublicKey = message.groupPublicKey ?: return
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Ignoring closed group update for nonexistent group.")
        return
    }
    if (!group.isActive) {
        Log.d("Loki", "Ignoring closed group update for inactive group.")
        return
    }
    if (!isValidGroupUpdate(group, message.sentTimestamp!!, senderPublicKey)) { return }
    val name = group.title
    // Check common group update logic
    val members = group.members.map { it.serialize() }
    val admins = group.admins.map { it.serialize() }

    val updateMembers = kind.members.map { it.toByteArray().toHexString() }
    val newMembers = members + updateMembers
    storage.updateMembers(groupID, newMembers.map { Address.fromSerialized(it) })
    // Notify the user
    if (userPublicKey == senderPublicKey) {
        val threadID = storage.getOrCreateThreadIdFor(Address.fromSerialized(groupID))
        storage.insertOutgoingInfoMessage(context, groupID, SignalServiceGroup.Type.MEMBER_ADDED, name, updateMembers, admins, threadID, message.sentTimestamp!!)
    } else {
        storage.insertIncomingInfoMessage(context, senderPublicKey, groupID, SignalServiceGroup.Type.MEMBER_ADDED, name, updateMembers, admins, message.sentTimestamp!!)
    }
    if (userPublicKey in admins) {
        // Send the latest encryption key pair to the added members if the current user is the admin of the group
        //
        // This fixes a race condition where:
        // • A member removes another member.
        // • A member adds someone to the group and sends them the latest group key pair.
        // • The admin is offline during all of this.
        // • When the admin comes back online they see the member removed message and generate + distribute a new key pair,
        //   but they don't know about the added member yet.
        // • Now they see the member added message.
        //
        // Without the code below, the added member(s) would never get the key pair that was generated by the admin when they saw
        // the member removed message.
        val encryptionKeyPair = pendingKeyPairs[groupPublicKey]?.orNull()
            ?: storage.getLatestClosedGroupEncryptionKeyPair(groupPublicKey)
        if (encryptionKeyPair == null) {
            android.util.Log.d("Loki", "Couldn't get encryption key pair for closed group.")
        } else {
            for (user in updateMembers) {
                MessageSender.sendEncryptionKeyPair(groupPublicKey, encryptionKeyPair, setOf(user), targetUser = user, force = false)
            }
        }
    }
}

/// Removes the given members from the group IF
/// • it wasn't the admin that was removed (that should happen through a `MEMBER_LEFT` message).
/// • the admin sent the message (only the admin can truly remove members).
/// If we're among the users that were removed, delete all encryption key pairs and the group public key, unsubscribe
/// from push notifications for this closed group, and remove the given members from the zombie list for this group.
private fun MessageReceiver.handleClosedGroupMembersRemoved(message: ClosedGroupControlMessage) {
    val context = MessagingModuleConfiguration.shared.context
    val storage = MessagingModuleConfiguration.shared.storage
    val userPublicKey = storage.getUserPublicKey()!!
    val senderPublicKey = message.sender ?: return
    val kind = message.kind!! as? ClosedGroupControlMessage.Kind.MembersRemoved ?: return
    val groupPublicKey = message.groupPublicKey ?: return
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Ignoring closed group update for nonexistent group.")
        return
    }
    if (!group.isActive) {
        Log.d("Loki", "Ignoring closed group update for inactive group.")
        return
    }
    val name = group.title
    // Check common group update logic
    val members = group.members.map { it.serialize() }
    val admins = group.admins.map { it.toString() }
    val removedMembers = kind.members.map { it.toByteArray().toHexString() }
    // Check that the admin wasn't removed
    if (removedMembers.contains(admins.first())) {
        Log.d("Loki", "Ignoring invalid closed group update.")
        return
    }
    // Check that the message was sent by the group admin
    if (!admins.contains(senderPublicKey)) {
        Log.d("Loki", "Ignoring invalid closed group update.")
        return
    }
    if (!isValidGroupUpdate(group, message.sentTimestamp!!, senderPublicKey)) { return }
    // If the admin leaves the group is disbanded
    val didAdminLeave = admins.any { it in removedMembers }
    val newMembers = members - removedMembers
    // A user should be posting a MEMBERS_LEFT in case they leave, so this shouldn't be encountered
    val senderLeft = senderPublicKey in removedMembers
    if (senderLeft) {
        Log.d("Loki", "Received a MEMBERS_REMOVED instead of a MEMBERS_LEFT from sender: $senderPublicKey.")
    }
    val wasCurrentUserRemoved = userPublicKey in removedMembers
    // Admin should send a MEMBERS_LEFT message but handled here just in case
    if (didAdminLeave || wasCurrentUserRemoved) {
        disableLocalGroupAndUnsubscribe(groupPublicKey, groupID, userPublicKey)
    } else {
        storage.updateMembers(groupID, newMembers.map { Address.fromSerialized(it) })
    }
    // Update zombie members
    val zombies = storage.getZombieMember(groupID)
    storage.updateZombieMembers(groupID, zombies.minus(removedMembers).map { Address.fromSerialized(it) })
    val type = if (senderLeft) SignalServiceGroup.Type.QUIT else SignalServiceGroup.Type.MEMBER_REMOVED
    // Notify the user
    // We don't display zombie members in the notification as users have already been notified when those members left
    val notificationMembers = removedMembers.minus(zombies)
    if (notificationMembers.isNotEmpty()) {
        // No notification to display when only zombies have been removed
        if (userPublicKey == senderPublicKey) {
            val threadID = storage.getOrCreateThreadIdFor(Address.fromSerialized(groupID))
            storage.insertOutgoingInfoMessage(context, groupID, type, name, notificationMembers, admins, threadID, message.sentTimestamp!!)
        } else {
            storage.insertIncomingInfoMessage(context, senderPublicKey, groupID, type, name, notificationMembers, admins, message.sentTimestamp!!)
        }
    }
}

/// If a regular member left:
/// • Mark them as a zombie (to be removed by the admin later).
/// If the admin left:
/// • Unsubscribe from PNs, delete the group public key, etc. as the group will be disbanded.
private fun MessageReceiver.handleClosedGroupMemberLeft(message: ClosedGroupControlMessage) {
    val context = MessagingModuleConfiguration.shared.context
    val storage = MessagingModuleConfiguration.shared.storage
    val senderPublicKey = message.sender ?: return
    val userPublicKey = storage.getUserPublicKey()!!
    if (message.kind!! !is ClosedGroupControlMessage.Kind.MemberLeft) return
    val groupPublicKey = message.groupPublicKey ?: return
    val groupID = GroupUtil.doubleEncodeGroupID(groupPublicKey)
    val group = storage.getGroup(groupID) ?: run {
        Log.d("Loki", "Ignoring closed group update for nonexistent group.")
        return
    }
    if (!group.isActive) {
        Log.d("Loki", "Ignoring closed group update for inactive group.")
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
    val userLeft = (userPublicKey == senderPublicKey)
    if (didAdminLeave || userLeft) {
        disableLocalGroupAndUnsubscribe(groupPublicKey, groupID, userPublicKey)
    } else {
        storage.updateMembers(groupID, updatedMemberList.map { Address.fromSerialized(it) })
        // Update zombie members
        val zombies = storage.getZombieMember(groupID)
        storage.updateZombieMembers(groupID, zombies.plus(senderPublicKey).map { Address.fromSerialized(it) })
    }
    // Notify the user
    if (userLeft) {
        val threadID = storage.getOrCreateThreadIdFor(Address.fromSerialized(groupID))
        storage.insertOutgoingInfoMessage(context, groupID, SignalServiceGroup.Type.QUIT, name, members, admins, threadID, message.sentTimestamp!!)
    } else {
        storage.insertIncomingInfoMessage(context, senderPublicKey, groupID, SignalServiceGroup.Type.QUIT, name, members, admins, message.sentTimestamp!!)
    }
}

private fun isValidGroupUpdate(group: GroupRecord, sentTimestamp: Long, senderPublicKey: String): Boolean  {
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
    val storage = MessagingModuleConfiguration.shared.storage
    storage.removeClosedGroupPublicKey(groupPublicKey)
    // Remove the key pairs
    storage.removeAllClosedGroupEncryptionKeyPairs(groupPublicKey)
    // Mark the group as inactive
    storage.setActive(groupID, false)
    storage.removeMember(groupID, Address.fromSerialized(userPublicKey))
    // Notify the PN server
    PushNotificationAPI.performOperation(PushNotificationAPI.ClosedGroupOperation.Unsubscribe, groupPublicKey, userPublicKey)
}
// endregion
