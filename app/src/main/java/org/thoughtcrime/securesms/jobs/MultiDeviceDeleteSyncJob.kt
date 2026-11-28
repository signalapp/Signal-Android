/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.Base64
import org.signal.core.util.UuidUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.jobs.protos.DeleteSyncJobData
import org.thoughtcrime.securesms.jobs.protos.DeleteSyncJobData.AttachmentDelete
import org.thoughtcrime.securesms.jobs.protos.DeleteSyncJobData.ThreadDelete
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.AddressableMessage
import org.whispersystems.signalservice.internal.push.ConversationIdentifier
import org.whispersystems.signalservice.internal.push.SyncMessage.DeleteForMe
import kotlin.time.Duration.Companion.days

/**
 * Send delete for me sync messages for the various type of delete syncs.
 */
class MultiDeviceDeleteSyncJob private constructor(
  private var data: DeleteSyncJobData,
  parameters: Parameters = Parameters.Builder()
    .addConstraint(NetworkConstraint.KEY)
    .addConstraint(SealedSenderConstraint.KEY)
    .setMaxAttempts(Parameters.UNLIMITED)
    .setLifespan(1.days.inWholeMilliseconds)
    .build()
) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "MultiDeviceDeleteSendSyncJob"
    private val TAG = Log.tag(MultiDeviceDeleteSyncJob::class.java)

    private const val CHUNK_SIZE = 500
    private const val THREAD_CHUNK_SIZE = CHUNK_SIZE / 5

    @WorkerThread
    @JvmStatic
    fun enqueueMessageDeletes(messageRecords: Set<MessageRecord>) {
      if (!SignalStore.account.isMultiDevice) {
        return
      }

      messageRecords.chunked(CHUNK_SIZE).forEach { chunk ->
        val deletes = createMessageDeletes(chunk)
        if (deletes.isNotEmpty()) {
          AppDependencies.jobManager.add(MultiDeviceDeleteSyncJob(messages = deletes))
        } else {
          Log.i(TAG, "No valid message deletes to sync")
        }
      }
    }

    @WorkerThread
    @JvmStatic
    fun enqueueAttachmentDelete(message: MessageRecord?, attachment: DatabaseAttachment) {
      if (!SignalStore.account.isMultiDevice) {
        return
      }

      val delete = createAttachmentDelete(message, attachment)
      if (delete != null) {
        AppDependencies.jobManager.add(MultiDeviceDeleteSyncJob(attachments = listOf(delete)))
      } else {
        Log.i(TAG, "No valid attachment deletes to sync attachment:${attachment.attachmentId}")
      }
    }

    @WorkerThread
    fun enqueueThreadDeletes(threads: List<ThreadTable.ThreadDeleteSyncInfo>, isFullDelete: Boolean) {
      if (!SignalStore.account.isMultiDevice) {
        return
      }

      threads.chunked(THREAD_CHUNK_SIZE).forEach { chunk ->
        val threadDeletes = createThreadDeletes(chunk, isFullDelete)
        if (threadDeletes.isNotEmpty()) {
          AppDependencies.jobManager.add(
            MultiDeviceDeleteSyncJob(
              threads = threadDeletes.filter { it.messages.isNotEmpty() },
              localOnlyThreads = threadDeletes.filter { it.messages.isEmpty() }
            )
          )
        } else {
          Log.i(TAG, "No valid thread deletes to sync")
        }
      }
    }

    @WorkerThread
    private fun createMessageDeletes(messageRecords: Collection<MessageRecord>): List<DeleteSyncJobData.AddressableMessage> {
      return messageRecords.mapNotNull { message ->
        val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(message.threadId)
        if (threadRecipient == null) {
          Log.w(TAG, "Unable to find thread recipient for message: ${message.id} thread: ${message.threadId}")
          null
        } else if (threadRecipient.isReleaseNotes) {
          Log.w(TAG, "Syncing release channel deletes are not currently supported")
          null
        } else if (threadRecipient.isDistributionList || !message.canDeleteSync()) {
          null
        } else {
          DeleteSyncJobData.AddressableMessage(
            threadRecipientId = threadRecipient.id.toLong(),
            sentTimestamp = message.dateSent,
            authorRecipientId = message.fromRecipient.id.toLong()
          )
        }
      }
    }

    @WorkerThread
    private fun createAttachmentDelete(message: MessageRecord?, attachment: DatabaseAttachment): AttachmentDelete? {
      if (message == null) {
        return null
      }

      val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(message.threadId)
      val addressableMessage = if (threadRecipient == null) {
        Log.w(TAG, "Unable to find thread recipient for message: ${message.id} thread: ${message.threadId} attachment: ${attachment.attachmentId}")
        null
      } else if (threadRecipient.isReleaseNotes) {
        Log.w(TAG, "Syncing release channel deletes are not currently supported")
        null
      } else if (threadRecipient.isDistributionList || !message.canDeleteSync()) {
        null
      } else {
        DeleteSyncJobData.AddressableMessage(
          threadRecipientId = threadRecipient.id.toLong(),
          sentTimestamp = message.dateSent,
          authorRecipientId = message.fromRecipient.id.toLong()
        )
      }

      if (addressableMessage == null) {
        return null
      }

      val delete = AttachmentDelete(
        targetMessage = addressableMessage,
        uuid = attachment.uuid?.let { UuidUtil.toByteString(it) },
        digest = attachment.remoteDigest?.toByteString(),
        plaintextHash = attachment.dataHash?.let { Base64.decodeOrNull(it)?.toByteString() }
      )

      return if (delete.uuid == null && delete.digest == null && delete.plaintextHash == null) {
        Log.w(TAG, "Unable to find uuid, digest, or plain text hash for attachment: ${attachment.attachmentId}")
        null
      } else {
        delete
      }
    }

    @WorkerThread
    private fun createThreadDeletes(threads: List<ThreadTable.ThreadDeleteSyncInfo>, isFullDelete: Boolean): List<ThreadDelete> {
      return threads.mapNotNull { (threadId, messages, nonExpiringMessages) ->
        val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(threadId)
        if (threadRecipient == null) {
          Log.w(TAG, "Unable to find thread recipient for thread: $threadId")
          null
        } else if (threadRecipient.isReleaseNotes) {
          Log.w(TAG, "Syncing release channel delete is not currently supported")
          null
        } else if (threadRecipient.isDistributionList) {
          null
        } else {
          ThreadDelete(
            threadRecipientId = threadRecipient.id.toLong(),
            isFullDelete = isFullDelete,
            messages = messages.map {
              DeleteSyncJobData.AddressableMessage(
                sentTimestamp = it.dateSent,
                authorRecipientId = it.fromRecipient.id.toLong()
              )
            },
            nonExpiringMessages = nonExpiringMessages.map {
              DeleteSyncJobData.AddressableMessage(
                sentTimestamp = it.dateSent,
                authorRecipientId = it.fromRecipient.id.toLong()
              )
            }
          )
        }
      }
    }
  }

  @VisibleForTesting
  constructor(
    messages: List<DeleteSyncJobData.AddressableMessage> = emptyList(),
    threads: List<ThreadDelete> = emptyList(),
    localOnlyThreads: List<ThreadDelete> = emptyList(),
    attachments: List<AttachmentDelete> = emptyList()
  ) : this(
    DeleteSyncJobData(
      messageDeletes = messages,
      threadDeletes = threads,
      localOnlyThreadDeletes = localOnlyThreads,
      attachmentDeletes = attachments
    )
  )

  override fun serialize(): ByteArray = data.encode()

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (!Recipient.self().isRegistered) {
      Log.w(TAG, "Not registered")
      return Result.failure()
    }

    if (!SignalStore.account.isMultiDevice) {
      Log.w(TAG, "Not multi-device")
      return Result.failure()
    }

    if (data.messageDeletes.isNotEmpty()) {
      val outcome = syncDelete(
        DeleteForMe(
          messageDeletes = data.messageDeletes.groupBy { it.threadRecipientId }.mapNotNull { (threadRecipientId, messages) ->
            val conversation = Recipient.resolved(RecipientId.from(threadRecipientId)).toDeleteSyncConversationId()
            if (conversation != null) {
              DeleteForMe.MessageDeletes(
                conversation = conversation,
                messages = messages.mapNotNull { it.toDeleteSyncMessage() }
              )
            } else {
              Log.w(TAG, "Unable to resolve $threadRecipientId to conversation id")
              null
            }
          }
        )
      )

      if (outcome is SyncDeleteResult.Failed) {
        return outcome.result
      }
    }

    if (data.threadDeletes.isNotEmpty()) {
      val outcome = syncDelete(
        DeleteForMe(
          conversationDeletes = data.threadDeletes.mapNotNull { threadDelete ->
            val conversation = Recipient.resolved(RecipientId.from(threadDelete.threadRecipientId)).toDeleteSyncConversationId()
            if (conversation != null) {
              DeleteForMe.ConversationDelete(
                conversation = conversation,
                mostRecentMessages = threadDelete.messages.mapNotNull { it.toDeleteSyncMessage() },
                isFullDelete = threadDelete.isFullDelete,
                mostRecentNonExpiringMessages = threadDelete.messages.mapNotNull { it.toDeleteSyncMessage() }
              )
            } else {
              Log.w(TAG, "Unable to resolve ${threadDelete.threadRecipientId} to conversation id")
              null
            }
          }
        )
      )

      if (outcome is SyncDeleteResult.Failed) {
        return outcome.result
      }
    }

    if (data.localOnlyThreadDeletes.isNotEmpty()) {
      val outcome = syncDelete(
        DeleteForMe(
          localOnlyConversationDeletes = data.localOnlyThreadDeletes.mapNotNull {
            val conversation = Recipient.resolved(RecipientId.from(it.threadRecipientId)).toDeleteSyncConversationId()
            if (conversation != null) {
              DeleteForMe.LocalOnlyConversationDelete(
                conversation = conversation
              )
            } else {
              Log.w(TAG, "Unable to resolve ${it.threadRecipientId} to conversation id")
              null
            }
          }
        )
      )

      if (outcome is SyncDeleteResult.Failed) {
        return outcome.result
      }
    }

    if (data.attachmentDeletes.isNotEmpty()) {
      val outcome = syncDelete(
        DeleteForMe(
          attachmentDeletes = data.attachmentDeletes.mapNotNull {
            val conversation = Recipient.resolved(RecipientId.from(it.targetMessage!!.threadRecipientId)).toDeleteSyncConversationId()
            val targetMessage = it.targetMessage.toDeleteSyncMessage()

            if (conversation != null && targetMessage != null) {
              DeleteForMe.AttachmentDelete(
                conversation = conversation,
                targetMessage = targetMessage,
                clientUuid = it.uuid,
                fallbackDigest = it.digest,
                fallbackPlaintextHash = it.plaintextHash
              )
            } else {
              Log.w(TAG, "Unable to resolve ${it.targetMessage.threadRecipientId} to conversation id or resolve target message data")
              null
            }
          }
        )
      )

      if (outcome is SyncDeleteResult.Failed) {
        return outcome.result
      }
    }

    return Result.success()
  }

  override fun onFailure() = Unit

  private suspend fun syncDelete(deleteForMe: DeleteForMe): SyncDeleteResult {
    if (deleteForMe.conversationDeletes.isEmpty() &&
      deleteForMe.messageDeletes.isEmpty() &&
      deleteForMe.localOnlyConversationDeletes.isEmpty() &&
      deleteForMe.attachmentDeletes.isEmpty()
    ) {
      Log.i(TAG, "No valid deletes, nothing to send, skipping")
      return SyncDeleteResult.NothingToSend
    }

    Log.d(TAG, "Sending delete sync messageDeletes=${deleteForMe.messageDeletes.size} conversationDeletes=${deleteForMe.conversationDeletes.size} localOnlyConversationDeletes=${deleteForMe.localOnlyConversationDeletes.size} attachmentDeletes=${deleteForMe.attachmentDeletes.size}")
    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncDeleteForMe(deleteForMe),
      urgent = true,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { SyncDeleteResult.Sent },
      ifLeft = { error ->
        val jobResult = error.toSyncJobResult(TAG, "message delete sync", defaultBackoff())
        SyncDeleteResult.Failed(jobResult)
      }
    )
  }

  private fun Recipient.toDeleteSyncConversationId(): ConversationIdentifier? {
    return when {
      isGroup -> ConversationIdentifier(threadGroupId = requireGroupId().decodedId.toByteString())
      hasAci -> ConversationIdentifier(threadServiceIdBinary = requireAci().toByteString())
      hasPni -> ConversationIdentifier(threadServiceIdBinary = requirePni().toByteString())
      hasE164 -> ConversationIdentifier(threadE164 = requireE164())
      else -> null
    }
  }

  private fun DeleteSyncJobData.AddressableMessage.toDeleteSyncMessage(): AddressableMessage? {
    val author: Recipient = Recipient.resolved(RecipientId.from(authorRecipientId))
    val authorServiceId: ByteString? = author.aci.orNull()?.toByteString() ?: author.pni.orNull()?.toByteString()

    val authorE164: String? = if (authorServiceId == null) {
      author.e164.orNull()
    } else {
      null
    }

    return if (authorServiceId == null && authorE164 == null) {
      Log.w(TAG, "Unable to send sync message without serviceId or e164 recipient: ${author.id}")
      null
    } else {
      AddressableMessage(
        authorServiceIdBinary = authorServiceId,
        authorE164 = authorE164,
        sentTimestamp = sentTimestamp
      )
    }
  }

  private sealed interface SyncDeleteResult {
    data object Sent : SyncDeleteResult
    data object NothingToSend : SyncDeleteResult
    data class Failed(val result: Result) : SyncDeleteResult
  }

  class Factory : Job.Factory<MultiDeviceDeleteSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceDeleteSyncJob {
      return MultiDeviceDeleteSyncJob(DeleteSyncJobData.ADAPTER.decode(serializedData!!), parameters)
    }
  }
}
