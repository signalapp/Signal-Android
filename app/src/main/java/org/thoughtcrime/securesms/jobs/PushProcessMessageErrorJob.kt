/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.ChangeNumberConstraint
import org.thoughtcrime.securesms.messages.ExceptionMetadata
import org.thoughtcrime.securesms.messages.MessageContentProcessor
import org.thoughtcrime.securesms.messages.MessageState
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Process messages that did not decrypt/validate successfully.
 */
class PushProcessMessageErrorJob private constructor(
  parameters: Parameters,
  private val messageState: MessageState,
  private val exceptionMetadata: ExceptionMetadata,
  private val timestamp: Long
) : BaseJob(parameters) {

  constructor(messageState: MessageState, exceptionMetadata: ExceptionMetadata, timestamp: Long) : this(
    parameters = createParameters(exceptionMetadata),
    messageState = messageState,
    exceptionMetadata = exceptionMetadata,
    timestamp = timestamp
  )

  override fun getFactoryKey(): String = KEY

  override fun shouldTrace(): Boolean = true

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putInt(KEY_MESSAGE_STATE, messageState.ordinal)
      .putLong(KEY_TIMESTAMP, timestamp)
      .putString(KEY_EXCEPTION_SENDER, exceptionMetadata.sender)
      .putInt(KEY_EXCEPTION_DEVICE, exceptionMetadata.senderDevice)
      .putString(KEY_EXCEPTION_GROUP_ID, exceptionMetadata.groupId?.toString())
      .serialize()
  }

  override fun onRun() {
    if (messageState == MessageState.DECRYPTED_OK || messageState == MessageState.NOOP) {
      Log.w(TAG, "Error job queued for valid or no-op decryption, generally this shouldn't happen. Bailing on state: $messageState")
      return
    }

    MessageContentProcessor.create(context).processException(messageState, exceptionMetadata, timestamp)
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  override fun onFailure() = Unit

  class Factory : Job.Factory<PushProcessMessageErrorJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): PushProcessMessageErrorJob {
      val data = JsonJobData.deserialize(serializedData)

      val state = MessageState.entries[data.getInt(KEY_MESSAGE_STATE)]
      check(state != MessageState.DECRYPTED_OK && state != MessageState.NOOP)

      val exceptionMetadata = ExceptionMetadata(
        sender = data.getString(KEY_EXCEPTION_SENDER),
        senderDevice = data.getInt(KEY_EXCEPTION_DEVICE),
        groupId = GroupId.parseNullableOrThrow(data.getStringOrDefault(KEY_EXCEPTION_GROUP_ID, null))
      )

      return PushProcessMessageErrorJob(parameters, state, exceptionMetadata, data.getLong(KEY_TIMESTAMP))
    }
  }

  companion object {
    const val KEY = "PushProcessMessageErrorV2Job"

    val TAG = Log.tag(PushProcessMessageErrorJob::class.java)

    private const val KEY_MESSAGE_STATE = "message_state"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_EXCEPTION_SENDER = "exception_sender"
    private const val KEY_EXCEPTION_DEVICE = "exception_device"
    private const val KEY_EXCEPTION_GROUP_ID = "exception_groupId"

    @WorkerThread
    private fun createParameters(exceptionMetadata: ExceptionMetadata): Parameters {
      val recipient: Recipient? = exceptionMetadata.groupId?.let { Recipient.externalPossiblyMigratedGroup(it) } ?: Recipient.external(exceptionMetadata.sender)

      if (recipient == null) {
        Log.w(TAG, "Unable to create Recipient for the requested identifier!")
      }

      return Parameters.Builder()
        .setMaxAttempts(Parameters.UNLIMITED)
        .addConstraint(ChangeNumberConstraint.KEY)
        .setQueue(recipient?.let { PushProcessMessageJob.getQueueName(it.id) })
        .build()
    }
  }
}
