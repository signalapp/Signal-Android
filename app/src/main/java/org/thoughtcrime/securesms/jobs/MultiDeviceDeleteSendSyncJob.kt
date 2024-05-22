/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.DeleteSyncJobData
import org.thoughtcrime.securesms.jobs.protos.DeleteSyncJobData.AddressableMessage
import org.thoughtcrime.securesms.jobs.protos.DeleteSyncJobData.ThreadDelete
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.pad
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.Recipient.Companion.self
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.SyncMessage
import org.whispersystems.signalservice.internal.push.SyncMessage.DeleteForMe
import java.io.IOException
import java.util.Optional
import kotlin.time.Duration.Companion.days

/**
 * Send delete for me sync messages for the various type of delete syncs.
 */
class MultiDeviceDeleteSendSyncJob private constructor(
  private var data: DeleteSyncJobData,
  parameters: Parameters = Parameters.Builder()
    .addConstraint(NetworkConstraint.KEY)
    .setMaxAttempts(Parameters.UNLIMITED)
    .setLifespan(1.days.inWholeMilliseconds)
    .build()
) : Job(parameters) {

  companion object {
    const val KEY = "MultiDeviceDeleteSendSyncJob"
    private val TAG = Log.tag(MultiDeviceDeleteSendSyncJob::class.java)

    private const val CHUNK_SIZE = 500
    private const val THREAD_CHUNK_SIZE = CHUNK_SIZE / 5

    @WorkerThread
    @JvmStatic
    fun enqueueMessageDeletes(messageRecords: Set<MessageRecord>) {
      if (!TextSecurePreferences.isMultiDevice(AppDependencies.application)) {
        return
      }

      if (!FeatureFlags.deleteSyncEnabled()) {
        Log.i(TAG, "Delete sync support not enabled.")
        return
      }

      messageRecords.chunked(CHUNK_SIZE).forEach { chunk ->
        AppDependencies.jobManager.add(createMessageDeletes(chunk))
      }
    }

    @WorkerThread
    fun enqueueThreadDeletes(threads: List<Pair<Long, Set<MessageRecord>>>, isFullDelete: Boolean) {
      if (!TextSecurePreferences.isMultiDevice(AppDependencies.application)) {
        return
      }

      if (!FeatureFlags.deleteSyncEnabled()) {
        Log.i(TAG, "Delete sync support not enabled.")
        return
      }

      threads.chunked(THREAD_CHUNK_SIZE).forEach { chunk ->
        AppDependencies.jobManager.add(createThreadDeletes(chunk, isFullDelete))
      }
    }

    @WorkerThread
    @VisibleForTesting
    fun createMessageDeletes(messageRecords: Collection<MessageRecord>): MultiDeviceDeleteSendSyncJob {
      val deletes = messageRecords.mapNotNull { message ->
        val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(message.threadId)
        if (threadRecipient == null) {
          Log.w(TAG, "Unable to find thread recipient for message: ${message.id} thread: ${message.threadId}")
          null
        } else if (threadRecipient.isReleaseNotes) {
          Log.w(TAG, "Syncing release channel deletes are not currently supported")
          null
        } else {
          AddressableMessage(
            threadRecipientId = threadRecipient.id.toLong(),
            sentTimestamp = message.dateSent,
            authorRecipientId = message.fromRecipient.id.toLong()
          )
        }
      }

      return MultiDeviceDeleteSendSyncJob(messages = deletes)
    }

    @WorkerThread
    @VisibleForTesting
    fun createThreadDeletes(threads: List<Pair<Long, Set<MessageRecord>>>, isFullDelete: Boolean): MultiDeviceDeleteSendSyncJob {
      val threadDeletes: List<ThreadDelete> = threads.mapNotNull { (threadId, messages) ->
        val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(threadId)
        if (threadRecipient == null) {
          Log.w(TAG, "Unable to find thread recipient for thread: $threadId")
          null
        } else if (threadRecipient.isReleaseNotes) {
          Log.w(TAG, "Syncing release channel delete is not currently supported")
          null
        } else {
          ThreadDelete(
            threadRecipientId = threadRecipient.id.toLong(),
            isFullDelete = isFullDelete,
            messages = messages.map {
              AddressableMessage(
                sentTimestamp = it.dateSent,
                authorRecipientId = it.fromRecipient.id.toLong()
              )
            }
          )
        }
      }

      return MultiDeviceDeleteSendSyncJob(
        threads = threadDeletes.filter { it.messages.isNotEmpty() },
        localOnlyThreads = threadDeletes.filter { it.messages.isEmpty() }
      )
    }
  }

  @VisibleForTesting
  constructor(
    messages: List<AddressableMessage> = emptyList(),
    threads: List<ThreadDelete> = emptyList(),
    localOnlyThreads: List<ThreadDelete> = emptyList()
  ) : this(
    DeleteSyncJobData(
      messageDeletes = messages,
      threadDeletes = threads,
      localOnlyThreadDeletes = localOnlyThreads
    )
  )

  override fun serialize(): ByteArray = data.encode()

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    if (!self().isRegistered) {
      Log.w(TAG, "Not registered")
      return Result.failure()
    }

    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.w(TAG, "Not multi-device")
      return Result.failure()
    }

    if (data.messageDeletes.isNotEmpty()) {
      val success = syncDelete(
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

      if (!success) {
        return Result.retry(defaultBackoff())
      }
    }

    if (data.threadDeletes.isNotEmpty()) {
      val success = syncDelete(
        DeleteForMe(
          conversationDeletes = data.threadDeletes.mapNotNull {
            val conversation = Recipient.resolved(RecipientId.from(it.threadRecipientId)).toDeleteSyncConversationId()
            if (conversation != null) {
              DeleteForMe.ConversationDelete(
                conversation = conversation,
                mostRecentMessages = it.messages.mapNotNull { m -> m.toDeleteSyncMessage() },
                isFullDelete = it.isFullDelete
              )
            } else {
              Log.w(TAG, "Unable to resolve ${it.threadRecipientId} to conversation id")
              null
            }
          }
        )
      )

      if (!success) {
        return Result.retry(defaultBackoff())
      }
    }

    if (data.localOnlyThreadDeletes.isNotEmpty()) {
      val success = syncDelete(
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

      if (!success) {
        return Result.retry(defaultBackoff())
      }
    }

    return Result.success()
  }

  override fun onFailure() = Unit

  private fun syncDelete(deleteForMe: DeleteForMe): Boolean {
    if (deleteForMe.conversationDeletes.isEmpty() && deleteForMe.messageDeletes.isEmpty() && deleteForMe.localOnlyConversationDeletes.isEmpty()) {
      Log.i(TAG, "No valid deletes, nothing to send, skipping")
      return true
    }

    val syncMessageContent = deleteForMeContent(deleteForMe)

    return try {
      AppDependencies.signalServiceMessageSender.sendSyncMessage(syncMessageContent, true, Optional.empty()).isSuccess
    } catch (e: IOException) {
      Log.w(TAG, "Unable to send message delete sync", e)
      false
    } catch (e: UntrustedIdentityException) {
      Log.w(TAG, "Unable to send message delete sync", e)
      false
    }
  }

  private fun deleteForMeContent(deleteForMe: DeleteForMe): Content {
    val syncMessage = SyncMessage.Builder()
      .pad()
      .deleteForMe(deleteForMe)

    return Content(syncMessage = syncMessage.build())
  }

  private fun Recipient.toDeleteSyncConversationId(): DeleteForMe.ConversationIdentifier? {
    return when {
      isGroup -> DeleteForMe.ConversationIdentifier(threadGroupId = requireGroupId().decodedId.toByteString())
      hasAci -> DeleteForMe.ConversationIdentifier(threadAci = requireAci().toString())
      hasE164 -> DeleteForMe.ConversationIdentifier(threadE164 = requireE164())
      else -> null
    }
  }

  private fun AddressableMessage.toDeleteSyncMessage(): DeleteForMe.AddressableMessage? {
    val author: Recipient = Recipient.resolved(RecipientId.from(authorRecipientId))
    val authorAci: String? = author.aci.orNull()?.toString()
    val authorE164: String? = if (authorAci == null) {
      author.e164.orNull()
    } else {
      null
    }

    return if (authorAci == null && authorE164 == null) {
      Log.w(TAG, "Unable to send sync message without aci and e164 recipient: ${author.id}")
      null
    } else {
      DeleteForMe.AddressableMessage(
        authorAci = authorAci,
        authorE164 = authorE164,
        sentTimestamp = sentTimestamp
      )
    }
  }

  class Factory : Job.Factory<MultiDeviceDeleteSendSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceDeleteSendSyncJob {
      return MultiDeviceDeleteSendSyncJob(DeleteSyncJobData.ADAPTER.decode(serializedData!!), parameters)
    }
  }
}
