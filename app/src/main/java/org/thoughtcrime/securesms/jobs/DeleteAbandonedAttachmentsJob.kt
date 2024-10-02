/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.attachments
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import kotlin.time.Duration.Companion.days

/**
 * Deletes attachment files that are no longer referenced in the database.
 */
class DeleteAbandonedAttachmentsJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(DeleteAbandonedAttachmentsJob::class)
    const val KEY = "DeleteAbandonedAttachmentsJob"

    fun enqueue() {
      AppDependencies.jobManager.add(DeleteAbandonedAttachmentsJob())
    }
  }

  constructor() : this(
    parameters = Parameters.Builder()
      .setMaxInstancesForFactory(2)
      .setLifespan(1.days.inWholeMilliseconds)
      .build()
  )

  override fun serialize(): ByteArray? = null
  override fun getFactoryKey(): String = KEY
  override fun onFailure() = Unit

  override fun run(): Result {
    val deletes = attachments.deleteAbandonedAttachmentFiles()
    Log.i(TAG, "Deleted $deletes abandoned attachments.")
    return Result.success()
  }

  class Factory : Job.Factory<DeleteAbandonedAttachmentsJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): DeleteAbandonedAttachmentsJob {
      return DeleteAbandonedAttachmentsJob(parameters)
    }
  }
}
