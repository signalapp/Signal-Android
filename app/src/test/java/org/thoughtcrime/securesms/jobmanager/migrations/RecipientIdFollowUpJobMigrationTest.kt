package org.thoughtcrime.securesms.jobmanager.migrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobMigration.JobData
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobs.FailingJob
import org.thoughtcrime.securesms.jobs.SendDeliveryReceiptJob

class RecipientIdFollowUpJobMigrationTest {
  @Test
  fun migrate_sendDeliveryReceiptJob_good() {
    val testData = JobData(
      "SendDeliveryReceiptJob",
      null,
      -1,
      -1,
      JsonJobData.Builder().putString("recipient", "1")
        .putLong("message_id", 1)
        .putLong("timestamp", 2)
        .serialize()
    )
    val subject = RecipientIdFollowUpJobMigration()
    val converted = subject.migrate(testData)

    assertEquals("SendDeliveryReceiptJob", converted.factoryKey)
    assertNull(converted.queueKey)

    val data = JsonJobData.deserialize(converted.data)
    assertEquals("1", data.getString("recipient"))
    assertEquals(1, data.getLong("message_id"))
    assertEquals(2, data.getLong("timestamp"))

    SendDeliveryReceiptJob.Factory().create(Job.Parameters.Builder().build(), converted.data)
  }

  @Test
  fun migrate_sendDeliveryReceiptJob_bad() {
    val testData = JobData(
      "SendDeliveryReceiptJob",
      null,
      -1,
      -1,
      JsonJobData.Builder().putString("recipient", "1")
        .serialize()
    )
    val subject = RecipientIdFollowUpJobMigration()
    val converted = subject.migrate(testData)

    assertEquals("FailingJob", converted.factoryKey)
    assertNull(converted.queueKey)

    FailingJob.Factory().create(Job.Parameters.Builder().build(), converted.data)
  }
}
