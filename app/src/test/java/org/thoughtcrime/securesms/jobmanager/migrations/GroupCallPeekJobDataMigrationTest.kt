package org.thoughtcrime.securesms.jobmanager.migrations

import org.junit.Test
import org.thoughtcrime.securesms.assertIs
import org.thoughtcrime.securesms.jobmanager.JobMigration
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobs.protos.GroupCallPeekJobData
import org.thoughtcrime.securesms.recipients.Recipient

class GroupCallPeekJobDataMigrationTest {

  private val testSubject = GroupCallPeekJobDataMigration()

  @Test
  fun `given an un-migrated GroupCallPeekJob, when I migrate, then I expect updated data`() {
    val groupRecipientId = 1L
    val jobData = createJobData(groupRecipientId = groupRecipientId)
    val result = testSubject.migrate(jobData)

    val data = GroupCallPeekJobData.ADAPTER.decode(result.data!!)

    data.groupRecipientId assertIs groupRecipientId
    data.senderRecipientId assertIs Recipient.UNKNOWN.id.toLong()
    data.serverTimestamp assertIs 0L
  }

  @Test
  fun `given an un-migrated GroupCallPeekWorkerJob, when I migrate, then I expect updated data`() {
    val groupRecipientId = 1L
    val jobData = createJobData(factoryKey = "GroupCallPeekWorkerJob", groupRecipientId = groupRecipientId)
    val result = testSubject.migrate(jobData)

    val data = GroupCallPeekJobData.ADAPTER.decode(result.data!!)

    data.groupRecipientId assertIs groupRecipientId
    data.senderRecipientId assertIs Recipient.UNKNOWN.id.toLong()
    data.serverTimestamp assertIs 0L
  }

  @Test
  fun `given an un-migrated ASDF, when I migrate, then I expect unchanged job data`() {
    val groupRecipientId = 1L
    val jobData = createJobData(factoryKey = "ASDF", groupRecipientId = groupRecipientId)
    val result = testSubject.migrate(jobData)

    result assertIs jobData
  }

  @Test
  fun `given an un-migrated job without data, when I migrate, then I expect unchanged job data`() {
    val groupRecipientId = 1L
    val jobData = createJobData(groupRecipientId = groupRecipientId, data = null)
    val result = testSubject.migrate(jobData)

    result assertIs jobData
  }

  @Test
  fun `given an un-migrated job with incorrect data, when I migrate, then I expect unchanged job data`() {
    val groupRecipientId = 1L
    val jobData = createJobData(groupRecipientId = groupRecipientId, data = JsonJobData.Builder().putString("asdf", groupRecipientId.toString()).serialize())
    val result = testSubject.migrate(jobData)

    result assertIs jobData
  }

  @Test
  fun `given an un-migrated job with un-deserializable data, when I migrate, then I expect unchanged job data`() {
    val groupRecipientId = 1L
    val jobData = createJobData(groupRecipientId = groupRecipientId, data = GroupCallPeekJobData().encode())
    val result = testSubject.migrate(jobData)

    result assertIs jobData
  }

  private fun createJobData(
    factoryKey: String = "GroupCallPeekJob",
    groupRecipientId: Long,
    data: ByteArray? = JsonJobData.Builder().putString("group_recipient_id", groupRecipientId.toString()).serialize()
  ): JobMigration.JobData {
    return JobMigration.JobData(
      factoryKey = factoryKey,
      queueKey = null,
      maxAttempts = 0,
      lifespan = 0,
      data = data
    )
  }
}
