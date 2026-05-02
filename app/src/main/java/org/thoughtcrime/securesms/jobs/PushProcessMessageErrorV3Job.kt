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
import org.thoughtcrime.securesms.jobs.PushProcessMessageJob.Companion.getQueueName
import org.thoughtcrime.securesms.messages.ExceptionMetadata
import org.thoughtcrime.securesms.messages.MessageContentProcessor
import org.thoughtcrime.securesms.messages.MessageDecryptor
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Process messages that did not decrypt/validate successfully.
 */
class PushProcessMessageErrorV3Job private constructor(
  parameters: Parameters,
  private val resultClass: String?,
  private val exceptionMetadata: ExceptionMetadata,
  private val timestamp: Long
) : BaseJob(parameters) {

  constructor(result: MessageDecryptor.Result.Error) : this(
    createParameters(result.errorMetadata.toExceptionMetadata()),
    result::class.qualifiedName,
    result.errorMetadata.toExceptionMetadata(),
    result.envelope.clientTimestamp!!
  )

  override fun getFactoryKey(): String = KEY

  override fun shouldTrace(): Boolean = true

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_RESULT_CLASS, resultClass)
      .putLong(KEY_TIMESTAMP, timestamp)
      .putString(KEY_EXCEPTION_SENDER, exceptionMetadata.sender)
      .putInt(KEY_EXCEPTION_DEVICE, exceptionMetadata.senderDevice)
      .putString(KEY_EXCEPTION_GROUP_ID, exceptionMetadata.groupId?.toString())
      .serialize()
  }

  override fun onRun() {
    MessageContentProcessor.create(context).processExceptionV2(resultClass, exceptionMetadata, timestamp)
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  override fun onFailure() = Unit

  class Factory : Job.Factory<PushProcessMessageErrorV3Job?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): PushProcessMessageErrorV3Job {
      val data = JsonJobData.deserialize(serializedData)

      val resultClass = data.getString(KEY_RESULT_CLASS)

      val exceptionMetadata = ExceptionMetadata(
        sender = data.getString(KEY_EXCEPTION_SENDER),
        senderDevice = data.getInt(KEY_EXCEPTION_DEVICE),
        groupId = GroupId.parseNullableOrThrow(data.getStringOrDefault(KEY_EXCEPTION_GROUP_ID, null))
      )

      return PushProcessMessageErrorV3Job(parameters, resultClass, exceptionMetadata, data.getLong(KEY_TIMESTAMP))
    }
  }

  companion object {
    const val KEY = "PushProcessMessageErrorV3Job"

    val TAG = Log.tag(PushProcessMessageErrorV3Job::class.java)

    private const val KEY_RESULT_CLASS = "result_class"
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
        .setQueue(recipient?.let { getQueueName(it.id) })
        .build()
    }
  }
}
