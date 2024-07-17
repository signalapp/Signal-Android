/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobmanager.migrations

import org.thoughtcrime.securesms.jobmanager.JobMigration
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobs.protos.GroupCallPeekJobData
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Migrate jobs with just the recipient id to utilize the new data proto.
 */
class GroupCallPeekJobDataMigration : JobMigration(12) {

  companion object {
    private const val KEY_GROUP_RECIPIENT_ID: String = "group_recipient_id"
    private val GROUP_PEEK_JOB_KEYS = arrayOf("GroupCallPeekJob", "GroupCallPeekWorkerJob")
  }

  override fun migrate(jobData: JobData): JobData {
    if (jobData.factoryKey !in GROUP_PEEK_JOB_KEYS) {
      return jobData
    }

    val data = jobData.data ?: return jobData
    val jsonData = JsonJobData.deserializeOrNull(data) ?: return jobData
    val recipientId = jsonData.getStringOrDefault(KEY_GROUP_RECIPIENT_ID, null) ?: return jobData

    val jobProto = GroupCallPeekJobData(
      groupRecipientId = recipientId.toLong(),
      senderRecipientId = RecipientId.UNKNOWN.toLong(),
      serverTimestamp = 0L
    )

    return jobData.withData(jobProto.encode())
  }
}
