package org.thoughtcrime.securesms.messages

import android.content.Context
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.core.util.toOptional
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.message.DecryptionErrorMessage
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.database.model.MessageLogEntry
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.PendingRetryReceiptModel
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.BadGroupIdException
import org.thoughtcrime.securesms.groups.GroupChangeBusyException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.groups.GroupNotAMemberException
import org.thoughtcrime.securesms.groups.v2.processing.GroupUpdateResult
import org.thoughtcrime.securesms.groups.v2.processing.GroupUpdateResult.UpdateStatus
import org.thoughtcrime.securesms.jobs.AutomaticSessionResetJob
import org.thoughtcrime.securesms.jobs.NullMessageSendJob
import org.thoughtcrime.securesms.jobs.ResendMessageJob
import org.thoughtcrime.securesms.jobs.SenderKeyDistributionSendJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.groupMasterKey
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.hasDisallowedAnnouncementOnlyContent
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.hasGroupContext
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.hasSignedGroupChange
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.hasStarted
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isExpirationUpdate
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isMediaMessage
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isValid
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.signedGroupChange
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.toDecryptionErrorMessage
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.EarlyMessageCacheEntry
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.CallMessage
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.push.GroupContextV2
import org.whispersystems.signalservice.internal.push.TypingMessage
import java.io.IOException
import java.util.Optional

open class MessageContentProcessor(private val context: Context) {

  enum class Gv2PreProcessResult {
    IGNORE,
    GROUP_UPDATE,
    GROUP_UP_TO_DATE
  }

  companion object {
    const val TAG = "MessageProcessorV2"

    @JvmStatic
    @JvmOverloads
    fun create(context: Context = AppDependencies.application): MessageContentProcessor {
      return MessageContentProcessor(context)
    }

    fun debug(message: String) {
      Log.d(TAG, message)
    }

    fun log(message: String) {
      Log.i(TAG, message)
    }

    fun log(timestamp: Long, message: String) {
      log(timestamp.toString(), message)
    }

    fun log(extra: String, message: String) {
      val extraLog = if (Util.isEmpty(extra)) "" else "[$extra] "
      Log.i(TAG, extraLog + message)
    }

    fun warn(message: String) {
      warn("", message, null)
    }

    fun warn(extra: String, message: String) {
      warn(extra, message, null)
    }

    fun warn(timestamp: Long, message: String) {
      warn(timestamp.toString(), message)
    }

    fun warn(timestamp: Long, message: String, t: Throwable?) {
      warn(timestamp.toString(), message, t)
    }

    fun warn(message: String, t: Throwable?) {
      warn("", message, t)
    }

    fun warn(extra: String, message: String, t: Throwable?) {
      val extraLog = if (Util.isEmpty(extra)) "" else "[$extra] "
      Log.w(TAG, extraLog + message, t)
    }

    fun formatSender(recipientId: RecipientId, serviceId: ServiceId, device: Int): String {
      return "$recipientId ($serviceId.$device)"
    }

    @Throws(BadGroupIdException::class)
    private fun getMessageDestination(content: Content, sender: Recipient): Recipient {
      return if (content.storyMessage != null && content.storyMessage!!.group.isValid) {
        getGroupRecipient(content.storyMessage?.group, sender)
      } else if (content.dataMessage.hasGroupContext) {
        getGroupRecipient(content.dataMessage?.groupV2, sender)
      } else if (content.editMessage?.dataMessage.hasGroupContext) {
        getGroupRecipient(content.editMessage?.dataMessage?.groupV2, sender)
      } else {
        sender
      }
    }

    private fun getGroupRecipient(groupContextV2: GroupContextV2?, senderRecipient: Recipient): Recipient {
      return if (groupContextV2 != null) {
        Recipient.externalPossiblyMigratedGroup(GroupId.v2(groupContextV2.groupMasterKey))
      } else {
        senderRecipient
      }
    }

    @Throws(BadGroupIdException::class)
    private fun shouldIgnore(content: Content, senderRecipient: Recipient, threadRecipient: Recipient): Boolean {
      if (content.dataMessage != null) {
        val message = content.dataMessage!!
        return if (threadRecipient.isGroup && threadRecipient.isBlocked) {
          true
        } else if (threadRecipient.isGroup) {
          if (threadRecipient.isUnknownGroup) {
            return senderRecipient.isBlocked
          }

          val isTextMessage = message.body != null
          val isMediaMessage = message.isMediaMessage
          val isExpireMessage = message.isExpirationUpdate
          val isGv2Update = message.hasSignedGroupChange
          val isContentMessage = !isGv2Update && !isExpireMessage && (isTextMessage || isMediaMessage)
          val isGroupActive = threadRecipient.isActiveGroup

          isContentMessage && !isGroupActive || senderRecipient.isBlocked && !isGv2Update
        } else {
          senderRecipient.isBlocked
        }
      } else if (content.callMessage != null) {
        return senderRecipient.isBlocked
      } else if (content.typingMessage != null) {
        if (senderRecipient.isBlocked) {
          return true
        }

        if (content.typingMessage!!.groupId != null) {
          val groupId: GroupId = GroupId.push(content.typingMessage!!.groupId!!)
          val groupRecipient = Recipient.externalPossiblyMigratedGroup(groupId)
          return if (groupRecipient.isBlocked || !groupRecipient.isActiveGroup) {
            true
          } else {
            val groupRecord = SignalDatabase.groups.getGroup(groupId)
            groupRecord.isPresent && groupRecord.get().isAnnouncementGroup && !groupRecord.get().admins.contains(senderRecipient)
          }
        }
      } else if (content.storyMessage != null) {
        return if (threadRecipient.isGroup && threadRecipient.isBlocked) {
          true
        } else {
          senderRecipient.isBlocked
        }
      }
      return false
    }

    @Throws(BadGroupIdException::class)
    private fun handlePendingRetry(pending: PendingRetryReceiptModel?, timestamp: Long, destination: Recipient): Long {
      var receivedTime = System.currentTimeMillis()

      if (pending != null) {
        warn(timestamp, "Incoming message matches a pending retry we were expecting.")

        val threadId = SignalDatabase.threads.getThreadIdFor(destination.id)
        if (threadId != null) {
          val lastSeen = SignalDatabase.threads.getConversationMetadata(threadId).lastSeen
          val visibleThread = AppDependencies.messageNotifier.visibleThread.map(ConversationId::threadId).orElse(-1L)

          if (threadId != visibleThread && lastSeen > 0 && lastSeen < pending.receivedTimestamp) {
            receivedTime = pending.receivedTimestamp
            warn(timestamp, "Thread has not been opened yet. Using received timestamp of $receivedTime")
          } else {
            warn(timestamp, "Thread was opened after receiving the original message. Using the current time for received time. (Last seen: " + lastSeen + ", ThreadVisible: " + (threadId == visibleThread) + ")")
          }
        } else {
          warn(timestamp, "Could not find a thread for the pending message. Using current time for received time.")
        }
      }

      return receivedTime
    }

    /**
     * @return True if the content should be ignored, otherwise false.
     */
    @Throws(IOException::class, GroupChangeBusyException::class)
    fun handleGv2PreProcessing(
      context: Context,
      timestamp: Long,
      content: Content,
      metadata: EnvelopeMetadata,
      groupId: GroupId.V2,
      groupV2: GroupContextV2,
      senderRecipient: Recipient,
      groupSecretParams: GroupSecretParams? = null,
      serverGuid: String? = null
    ): Gv2PreProcessResult {
      val preUpdateGroupRecord = SignalDatabase.groups.getGroup(groupId)
      val groupUpdateResult = updateGv2GroupFromServerOrP2PChange(context, timestamp, groupV2, preUpdateGroupRecord, groupSecretParams, serverGuid)
      if (groupUpdateResult == null) {
        log(timestamp, "Ignoring GV2 message for group we are not currently in $groupId")
        return Gv2PreProcessResult.IGNORE
      }

      val groupRecord = if (groupUpdateResult.updateStatus == UpdateStatus.GROUP_CONSISTENT_OR_AHEAD) {
        preUpdateGroupRecord
      } else {
        SignalDatabase.groups.getGroup(groupId)
      }

      if (groupRecord.isPresent && !groupRecord.get().members.contains(senderRecipient.id)) {
        log(timestamp, "Ignoring GV2 message from member not in group $groupId. Sender: ${formatSender(senderRecipient.id, metadata.sourceServiceId, metadata.sourceDeviceId)}")
        return Gv2PreProcessResult.IGNORE
      }

      if (groupRecord.isPresent && groupRecord.get().isAnnouncementGroup && !groupRecord.get().admins.contains(senderRecipient)) {
        if (content.dataMessage != null) {
          if (content.dataMessage!!.hasDisallowedAnnouncementOnlyContent) {
            Log.w(TAG, "Ignoring message from ${senderRecipient.id} because it has disallowed content, and they're not an admin in an announcement-only group.")
            return Gv2PreProcessResult.IGNORE
          }
        } else if (content.typingMessage != null) {
          Log.w(TAG, "Ignoring typing indicator from ${senderRecipient.id} because they're not an admin in an announcement-only group.")
          return Gv2PreProcessResult.IGNORE
        }
      }

      return when (groupUpdateResult.updateStatus) {
        UpdateStatus.GROUP_UPDATED -> Gv2PreProcessResult.GROUP_UPDATE
        UpdateStatus.GROUP_CONSISTENT_OR_AHEAD -> Gv2PreProcessResult.GROUP_UP_TO_DATE
      }
    }

    @Throws(IOException::class, GroupChangeBusyException::class)
    fun updateGv2GroupFromServerOrP2PChange(
      context: Context,
      timestamp: Long,
      groupV2: GroupContextV2,
      localRecord: Optional<GroupRecord>,
      groupSecretParams: GroupSecretParams? = null,
      serverGuid: String? = null
    ): GroupUpdateResult? {
      return try {
        val signedGroupChange: ByteArray? = if (groupV2.hasSignedGroupChange) groupV2.signedGroupChange else null
        val updatedTimestamp = if (signedGroupChange != null) timestamp else timestamp - 1
        if (groupV2.revision != null) {
          GroupManager.updateGroupFromServer(context, groupV2.groupMasterKey, localRecord, groupSecretParams, groupV2.revision!!, updatedTimestamp, signedGroupChange, serverGuid)
        } else {
          warn(timestamp, "Ignore group update message without a revision")
          null
        }
      } catch (e: GroupNotAMemberException) {
        warn(timestamp, "Ignoring message for a group we're not in")
        null
      }
    }

    private fun insertErrorMessage(context: Context, sender: Recipient, timestamp: Long, groupId: Optional<GroupId>, marker: (Long) -> Unit) {
      val textMessage = IncomingMessage(
        type = MessageType.NORMAL,
        from = sender.id,
        sentTimeMillis = timestamp,
        serverTimeMillis = -1,
        receivedTimeMillis = System.currentTimeMillis(),
        groupId = groupId.orNull()
      )

      SignalDatabase
        .messages
        .insertMessageInbox(textMessage)
        .ifPresent {
          marker(it.messageId)
          AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(it.threadId))
        }
    }
  }

  /**
   * Given the details about a message decryption, this will insert the proper message content into
   * the database.
   *
   * This is super-stateful, and it's recommended that this be run in a transaction so that no
   * intermediate results are persisted to the database if the app were to crash.
   *
   * @param processingEarlyContent pass `true` to specifically target at early content. Using this method will *not*
   * store or enqueue early content jobs if we detect this as being early, to avoid recursive scenarios.
   */
  @JvmOverloads
  open fun process(envelope: Envelope, content: Content, metadata: EnvelopeMetadata, serverDeliveredTimestamp: Long, processingEarlyContent: Boolean = false, localMetric: SignalLocalMetrics.MessageReceive? = null) {
    val senderRecipient = Recipient.externalPush(SignalServiceAddress(metadata.sourceServiceId, metadata.sourceE164))

    handleMessage(senderRecipient, envelope, content, metadata, serverDeliveredTimestamp, processingEarlyContent, localMetric)

    val earlyCacheEntries: List<EarlyMessageCacheEntry>? = AppDependencies
      .earlyMessageCache
      .retrieve(senderRecipient.id, envelope.timestamp!!)
      .orNull()

    if (!processingEarlyContent && earlyCacheEntries != null) {
      log(envelope.timestamp!!, "Found " + earlyCacheEntries.size + " dependent item(s) that were retrieved earlier. Processing.")
      for (entry in earlyCacheEntries) {
        handleMessage(senderRecipient, entry.envelope, entry.content, entry.metadata, entry.serverDeliveredTimestamp, processingEarlyContent = true, localMetric = null)
      }
    }
  }

  fun processException(messageState: MessageState, exceptionMetadata: ExceptionMetadata, timestamp: Long) {
    val sender = Recipient.external(context, exceptionMetadata.sender)

    if (sender.isBlocked) {
      warn("Ignoring exception content from blocked sender, message state: $messageState")
      return
    }

    when (messageState) {
      MessageState.DECRYPTION_ERROR -> {
        warn(timestamp, "Handling encryption error.")

        val threadRecipient = if (exceptionMetadata.groupId != null) Recipient.externalPossiblyMigratedGroup(exceptionMetadata.groupId) else sender
        val threadId: Long? = SignalDatabase.threads.getThreadIdFor(threadRecipient.id)

        if (threadId != null) {
          SignalDatabase
            .messages
            .insertBadDecryptMessage(
              recipientId = sender.id,
              senderDevice = exceptionMetadata.senderDevice,
              sentTimestamp = timestamp,
              receivedTimestamp = System.currentTimeMillis(),
              threadId = threadId
            )
        } else {
          warn(timestamp, "Could not find a thread for the target recipient. Skipping.")
        }
      }

      MessageState.INVALID_VERSION -> {
        warn(timestamp, "Handling invalid version.")
        insertErrorMessage(context, sender, timestamp, exceptionMetadata.groupId.toOptional()) { messageId ->
          SignalDatabase.messages.markAsInvalidVersionKeyExchange(messageId)
        }
      }

      MessageState.LEGACY_MESSAGE -> {
        warn(timestamp, "Handling legacy message.")
        insertErrorMessage(context, sender, timestamp, exceptionMetadata.groupId.toOptional()) { messageId ->
          SignalDatabase.messages.markAsLegacyVersion(messageId)
        }
      }

      MessageState.UNSUPPORTED_DATA_MESSAGE -> {
        warn(timestamp, "Handling unsupported data message.")
        insertErrorMessage(context, sender, timestamp, exceptionMetadata.groupId.toOptional()) { messageId ->
          SignalDatabase.messages.markAsUnsupportedProtocolVersion(messageId)
        }
      }

      MessageState.CORRUPT_MESSAGE,
      MessageState.NO_SESSION -> {
        warn(timestamp, "Discovered old enqueued bad encrypted message. Scheduling reset.")
        AppDependencies.jobManager.add(AutomaticSessionResetJob(sender.id, exceptionMetadata.senderDevice, timestamp))
      }

      MessageState.DUPLICATE_MESSAGE -> warn(timestamp, "Duplicate message. Dropping.")

      else -> throw AssertionError("Not handled $messageState. ($timestamp)")
    }
  }

  private fun handleMessage(
    senderRecipient: Recipient,
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    serverDeliveredTimestamp: Long,
    processingEarlyContent: Boolean,
    localMetric: SignalLocalMetrics.MessageReceive?
  ) {
    val threadRecipient = getMessageDestination(content, senderRecipient)

    if (shouldIgnore(content, senderRecipient, threadRecipient)) {
      log(envelope.timestamp!!, "Ignoring message.")
      return
    }

    val pending: PendingRetryReceiptModel? = AppDependencies.pendingRetryReceiptCache.get(senderRecipient.id, envelope.timestamp!!)
    val receivedTime: Long = handlePendingRetry(pending, envelope.timestamp!!, threadRecipient)

    log(envelope.timestamp!!, "Beginning message processing. Sender: " + formatSender(senderRecipient.id, metadata.sourceServiceId, metadata.sourceDeviceId))
    localMetric?.onPreProcessComplete()
    when {
      content.dataMessage != null -> {
        DataMessageProcessor.process(
          context,
          senderRecipient,
          threadRecipient,
          envelope,
          content,
          metadata,
          receivedTime,
          if (processingEarlyContent) null else EarlyMessageCacheEntry(envelope, content, metadata, serverDeliveredTimestamp),
          localMetric
        )
      }

      content.syncMessage != null -> {
        TextSecurePreferences.setMultiDevice(context, true)

        SyncMessageProcessor.process(
          context,
          senderRecipient,
          envelope,
          content,
          metadata,
          if (processingEarlyContent) null else EarlyMessageCacheEntry(envelope, content, metadata, serverDeliveredTimestamp)
        )
      }

      content.callMessage != null -> {
        log(envelope.timestamp!!, "Got call message...")

        val message: CallMessage = content.callMessage!!

        if (message.destinationDeviceId != null && message.destinationDeviceId != SignalStore.account.deviceId) {
          log(envelope.timestamp!!, "Ignoring call message that is not for this device! intended: ${message.destinationDeviceId}, this: ${SignalStore.account.deviceId}")
          return
        }

        CallMessageProcessor.process(senderRecipient, envelope, content, metadata, serverDeliveredTimestamp)
      }

      content.receiptMessage != null -> {
        ReceiptMessageProcessor.process(
          context,
          senderRecipient,
          envelope,
          content,
          metadata,
          if (processingEarlyContent) null else EarlyMessageCacheEntry(envelope, content, metadata, serverDeliveredTimestamp)
        )
      }

      content.typingMessage != null -> {
        handleTypingMessage(envelope, metadata, content.typingMessage!!, senderRecipient)
      }

      content.storyMessage != null -> {
        StoryMessageProcessor.process(
          envelope,
          content,
          metadata,
          senderRecipient,
          threadRecipient
        )
      }

      content.decryptionErrorMessage != null -> {
        handleRetryReceipt(envelope, metadata, content.decryptionErrorMessage!!.toDecryptionErrorMessage(metadata), senderRecipient)
      }

      content.editMessage != null -> {
        EditMessageProcessor.process(
          context,
          senderRecipient,
          threadRecipient,
          envelope,
          content,
          metadata,
          if (processingEarlyContent) null else EarlyMessageCacheEntry(envelope, content, metadata, serverDeliveredTimestamp)
        )
      }

      content.senderKeyDistributionMessage != null || content.pniSignatureMessage != null -> {
        // Already handled, here in order to prevent unrecognized message log
      }

      else -> {
        warn(envelope.timestamp!!, "Got unrecognized message!")
      }
    }

    if (pending != null) {
      warn(envelope.timestamp!!, "Pending retry was processed. Deleting.")
      AppDependencies.pendingRetryReceiptCache.delete(pending)
    }
  }

  @Throws(BadGroupIdException::class)
  private fun handleTypingMessage(
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    typingMessage: TypingMessage,
    senderRecipient: Recipient
  ) {
    if (!TextSecurePreferences.isTypingIndicatorsEnabled(context)) {
      return
    }

    val threadId: Long = if (typingMessage.groupId != null) {
      val groupId = GroupId.push(typingMessage.groupId!!)
      if (!SignalDatabase.groups.isCurrentMember(groupId, senderRecipient.id)) {
        warn(envelope.timestamp!!, "Seen typing indicator for non-member " + senderRecipient.id)
        return
      }

      val groupRecipient = Recipient.externalPossiblyMigratedGroup(groupId)
      SignalDatabase.threads.getOrCreateThreadIdFor(groupRecipient)
    } else {
      SignalDatabase.threads.getOrCreateThreadIdFor(senderRecipient)
    }

    if (threadId <= 0) {
      warn(envelope.timestamp!!, "Couldn't find a matching thread for a typing message.")
      return
    }

    if (typingMessage.hasStarted) {
      Log.d(TAG, "Typing started on thread $threadId")
      AppDependencies.typingStatusRepository.onTypingStarted(context, threadId, senderRecipient, metadata.sourceDeviceId)
    } else {
      Log.d(TAG, "Typing stopped on thread $threadId")
      AppDependencies.typingStatusRepository.onTypingStopped(threadId, senderRecipient, metadata.sourceDeviceId, false)
    }
  }

  private fun handleRetryReceipt(envelope: Envelope, metadata: EnvelopeMetadata, decryptionErrorMessage: DecryptionErrorMessage, senderRecipient: Recipient) {
    if (!RemoteConfig.retryReceipts) {
      warn(envelope.timestamp!!, "[RetryReceipt] Feature flag disabled, skipping retry receipt.")
      return
    }

    if (decryptionErrorMessage.deviceId != SignalStore.account.deviceId) {
      log(envelope.timestamp!!, "[RetryReceipt] Received a DecryptionErrorMessage targeting a linked device. Ignoring.")
      return
    }

    val sentTimestamp = decryptionErrorMessage.timestamp
    warn(envelope.timestamp!!, "[RetryReceipt] Received a retry receipt from ${formatSender(senderRecipient.id, metadata.sourceServiceId, metadata.sourceDeviceId)} for message with timestamp $sentTimestamp.")
    if (!senderRecipient.hasServiceId) {
      warn(envelope.timestamp!!, "[RetryReceipt] Requester ${senderRecipient.id} somehow has no UUID! timestamp: $sentTimestamp")
      return
    }

    val messageLogEntry = SignalDatabase.messageLog.getLogEntry(senderRecipient.id, metadata.sourceDeviceId, sentTimestamp)
    if (decryptionErrorMessage.ratchetKey.isPresent) {
      handleIndividualRetryReceipt(senderRecipient, messageLogEntry, envelope, metadata, decryptionErrorMessage)
    } else {
      handleSenderKeyRetryReceipt(senderRecipient, messageLogEntry, envelope, metadata, decryptionErrorMessage)
    }
  }

  private fun handleSenderKeyRetryReceipt(
    requester: Recipient,
    messageLogEntry: MessageLogEntry?,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    decryptionErrorMessage: DecryptionErrorMessage
  ) {
    val sentTimestamp = decryptionErrorMessage.timestamp
    val relatedMessage = findRetryReceiptRelatedMessage(messageLogEntry, sentTimestamp)

    if (relatedMessage == null) {
      warn(envelope.timestamp!!, "[RetryReceipt-SK] The related message could not be found! There shouldn't be any sender key resends where we can't find the related message. Skipping.")
      return
    }

    val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(relatedMessage.threadId)
    if (threadRecipient == null) {
      warn(envelope.timestamp!!, "[RetryReceipt-SK] Could not find a thread recipient! Skipping.")
      return
    }

    if (!threadRecipient.isPushV2Group && !threadRecipient.isDistributionList) {
      warn(envelope.timestamp!!, "[RetryReceipt-SK] Thread recipient is not a V2 group or distribution list! Skipping.")
      return
    }

    val distributionId: DistributionId?
    val groupId: GroupId.V2?

    if (threadRecipient.isGroup) {
      groupId = threadRecipient.requireGroupId().requireV2()
      distributionId = SignalDatabase.groups.getOrCreateDistributionId(groupId)
    } else {
      groupId = null
      distributionId = SignalDatabase.distributionLists.getDistributionId(threadRecipient.id)
    }

    if (distributionId == null) {
      Log.w(TAG, "[RetryReceipt-SK] Failed to find a distributionId! Skipping.")
      return
    }

    val requesterAddress = SignalProtocolAddress(requester.requireServiceId().toString(), metadata.sourceDeviceId)
    SignalDatabase.senderKeyShared.delete(distributionId, setOf(requesterAddress))

    if (messageLogEntry != null) {
      warn(envelope.timestamp!!, "[RetryReceipt-SK] Found MSL entry for ${requester.id} ($requesterAddress) with timestamp $sentTimestamp. Scheduling a resend.")
      AppDependencies.jobManager.add(
        ResendMessageJob(
          messageLogEntry.recipientId,
          messageLogEntry.dateSent,
          messageLogEntry.content,
          messageLogEntry.contentHint,
          messageLogEntry.urgent,
          groupId,
          distributionId
        )
      )
    } else {
      warn(envelope.timestamp!!, "[RetryReceipt-SK] Unable to find MSL entry for ${requester.id} ($requesterAddress) with timestamp $sentTimestamp for ${if (groupId != null) "group $groupId" else "distribution list"}. Scheduling a job to send them the SenderKeyDistributionMessage. Membership will be checked there.")
      AppDependencies.jobManager.add(SenderKeyDistributionSendJob(requester.id, threadRecipient.id))
    }
  }

  private fun handleIndividualRetryReceipt(requester: Recipient, messageLogEntry: MessageLogEntry?, envelope: Envelope, metadata: EnvelopeMetadata, decryptionErrorMessage: DecryptionErrorMessage) {
    var archivedSession = false

    if (ServiceId.parseOrNull(envelope.destinationServiceId) is ServiceId.PNI) {
      warn(envelope.timestamp!!, "[RetryReceipt-I] Destination is our PNI. Ignoring.")
      return
    }

    if (decryptionErrorMessage.ratchetKey.isPresent) {
      if (ratchetKeyMatches(requester, metadata.sourceDeviceId, decryptionErrorMessage.ratchetKey.get())) {
        warn(envelope.timestamp!!, "[RetryReceipt-I] Ratchet key matches. Archiving the session.")
        AppDependencies.protocolStore.aci().sessions().archiveSession(requester.requireServiceId(), metadata.sourceDeviceId)
        archivedSession = true
      } else {
        log(envelope.timestamp!!, "[RetryReceipt-I] Ratchet key does not match. Leaving the session as-is.")
      }
    } else {
      warn(envelope.timestamp!!, "[RetryReceipt-I] Missing ratchet key! Can't archive session.")
    }

    if (messageLogEntry != null) {
      warn(envelope.timestamp!!, "[RetryReceipt-I] Found an entry in the MSL. Resending.")
      AppDependencies.jobManager.add(
        ResendMessageJob(
          messageLogEntry.recipientId,
          messageLogEntry.dateSent,
          messageLogEntry.content,
          messageLogEntry.contentHint,
          messageLogEntry.urgent,
          null,
          null
        )
      )
    } else if (archivedSession) {
      warn(envelope.timestamp!!, "[RetryReceipt-I] Could not find an entry in the MSL, but we archived the session, so we're sending a null message to complete the reset.")
      AppDependencies.jobManager.add(NullMessageSendJob(requester.id))
    } else {
      warn(envelope.timestamp!!, "[RetryReceipt-I] Could not find an entry in the MSL. Skipping.")
    }
  }

  private fun findRetryReceiptRelatedMessage(messageLogEntry: MessageLogEntry?, sentTimestamp: Long): MessageRecord? {
    return if (messageLogEntry != null && messageLogEntry.hasRelatedMessage) {
      val id = messageLogEntry.relatedMessages[0].id
      SignalDatabase.messages.getMessageRecordOrNull(id)
    } else {
      SignalDatabase.messages.getMessageFor(sentTimestamp, Recipient.self().id)
    }
  }

  private fun ratchetKeyMatches(recipient: Recipient, deviceId: Int, ratchetKey: ECPublicKey): Boolean {
    val address = recipient.resolve().requireAci().toProtocolAddress(deviceId)
    val session = AppDependencies.protocolStore.aci().loadSession(address)
    return session.currentRatchetKeyMatches(ratchetKey)
  }
}
