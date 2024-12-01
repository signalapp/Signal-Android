package org.thoughtcrime.securesms.jobmanager.migrations

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.jobmanager.JobMigration.JobData
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobs.SendReadReceiptJob
import org.thoughtcrime.securesms.recipients.RecipientId

class SendReadReceiptsJobMigrationTest {
  private val mockDatabase = mockk<MessageTable>()
  private val testSubject = SendReadReceiptsJobMigration(mockDatabase)

  @Test
  fun givenSendReadReceiptJobDataWithoutThreadIdAndThreadIdFound_whenIMigrate_thenIInsertThreadId() {
    // GIVEN
    val job = SendReadReceiptJob(1, RecipientId.from(2), ArrayList(), ArrayList())
    val jobData = JobData(
      factoryKey = job.factoryKey,
      queueKey = "asdf",
      maxAttempts = -1,
      lifespan = -1,
      data = JsonJobData.Builder()
        .putString("recipient", RecipientId.from(2).serialize())
        .putLongArray("message_ids", longArrayOf(1, 2, 3, 4, 5))
        .putLong("timestamp", 292837649).serialize()
    )
    every { mockDatabase.getThreadIdForMessage(any()) } returns 1234L

    // WHEN
    val result = testSubject.migrate(jobData)
    val data = JsonJobData.deserialize(result.data)

    // THEN
    assertEquals(1234L, data.getLong("thread"))
    assertEquals(RecipientId.from(2).serialize(), data.getString("recipient"))
    assertTrue(data.hasLongArray("message_ids"))
    assertTrue(data.hasLong("timestamp"))
  }

  @Test
  fun givenSendReadReceiptJobDataWithoutThreadIdAndThreadIdNotFound_whenIMigrate_thenIGetAFailingJob() {
    // GIVEN
    val job = SendReadReceiptJob(1, RecipientId.from(2), ArrayList(), ArrayList())
    val jobData = JobData(
      factoryKey = job.factoryKey,
      queueKey = "asdf",
      maxAttempts = -1,
      lifespan = -1,
      data = JsonJobData.Builder()
        .putString("recipient", RecipientId.from(2).serialize())
        .putLongArray("message_ids", longArrayOf())
        .putLong("timestamp", 292837649).serialize()
    )
    every { mockDatabase.getThreadIdForMessage(any()) } returns -1L

    // WHEN
    val result = testSubject.migrate(jobData)

    // THEN
    assertEquals("FailingJob", result.factoryKey)
  }

  @Test
  fun givenSendReadReceiptJobDataWithThreadId_whenIMigrate_thenIDoNotReplace() {
    // GIVEN
    val job = SendReadReceiptJob(1, RecipientId.from(2), ArrayList(), ArrayList())
    val jobData = JobData(job.factoryKey, "asdf", -1, -1, job.serialize())

    // WHEN
    val result = testSubject.migrate(jobData)

    // THEN
    assertEquals(jobData, result)
  }

  @Test
  fun givenSomeOtherJobDataWithThreadId_whenIMigrate_thenIDoNotReplace() {
    // GIVEN
    val jobData = JobData("SomeOtherJob", "asdf", -1, -1, JsonJobData.Builder().putLong("thread", 1).serialize())

    // WHEN
    val result = testSubject.migrate(jobData)

    // THEN
    assertEquals(jobData, result)
  }

  @Test
  fun givenSomeOtherJobDataWithoutThreadId_whenIMigrate_thenIDoNotReplace() {
    // GIVEN
    val jobData = JobData("SomeOtherJob", "asdf", -1, -1, JsonJobData.Builder().serialize())

    // WHEN
    val result = testSubject.migrate(jobData)

    // THEN
    assertEquals(jobData, result)
  }
}
