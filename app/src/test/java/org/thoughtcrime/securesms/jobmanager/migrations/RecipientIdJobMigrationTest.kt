package org.thoughtcrime.securesms.jobmanager.migrations

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobMigration.JobData
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.migrations.RecipientIdJobMigration.NewSerializableSyncMessageId
import org.thoughtcrime.securesms.jobmanager.migrations.RecipientIdJobMigration.OldSerializableSyncMessageId
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob
import org.thoughtcrime.securesms.jobs.IndividualSendJob
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceReadUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceVerifiedUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceViewOnceOpenJob
import org.thoughtcrime.securesms.jobs.PushGroupSendJob
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.thoughtcrime.securesms.jobs.SendDeliveryReceiptJob
import org.thoughtcrime.securesms.recipients.LiveRecipient
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.JsonUtils

class RecipientIdJobMigrationTest {
  @Before
  fun setup() {
    mockkObject(Recipient)
    every { Recipient.live(any()) } returns mockk<LiveRecipient>(relaxed = true)
  }

  @After
  fun cleanup() {
    unmockkAll()
  }

  @Test
  fun migrate_multiDeviceContactUpdateJob() {
    val testData = JobData(
      factoryKey = "MultiDeviceContactUpdateJob",
      queueKey = "MultiDeviceContactUpdateJob",
      maxAttempts = -1,
      lifespan = -1,
      data = JsonJobData.Builder().putBoolean("force_sync", false).putString("address", "+16101234567").serialize()
    )
    mockRecipientResolve("+16101234567", 1)

    val subject = RecipientIdJobMigration(mockk<Application>())
    val converted = subject.migrate(testData)
    val data = JsonJobData.deserialize(converted.data)

    assertEquals("MultiDeviceContactUpdateJob", converted.factoryKey)
    assertEquals("MultiDeviceContactUpdateJob", converted.queueKey)
    assertFalse(data.getBoolean("force_sync"))
    assertFalse(data.hasString("address"))
    assertEquals("1", data.getString("recipient"))

    MultiDeviceContactUpdateJob.Factory().create(Job.Parameters.Builder().build(), converted.data)
  }

  @Test
  fun migrate_multiDeviceViewOnceOpenJob() {
    val oldId = OldSerializableSyncMessageId("+16101234567", 1)
    val testData = JobData(
      factoryKey = "MultiDeviceRevealUpdateJob",
      queueKey = null,
      maxAttempts = -1,
      lifespan = -1,
      data = JsonJobData.Builder().putString("message_id", JsonUtils.toJson(oldId)).serialize()
    )
    mockRecipientResolve("+16101234567", 1)

    val subject = RecipientIdJobMigration(mockk<Application>())
    val converted = subject.migrate(testData)
    val data = JsonJobData.deserialize(converted.data)

    assertEquals("MultiDeviceRevealUpdateJob", converted.factoryKey)
    assertNull(converted.queueKey)
    assertEquals(JsonUtils.toJson(NewSerializableSyncMessageId("1", 1)), data.getString("message_id"))

    MultiDeviceViewOnceOpenJob.Factory().create(Job.Parameters.Builder().build(), converted.data)
  }

  @Test
  fun migrate_sendDeliveryReceiptJob() {
    val testData = JobData(
      factoryKey = "SendDeliveryReceiptJob",
      queueKey = null,
      maxAttempts = -1,
      lifespan = -1,
      data = JsonJobData.Builder().putString("address", "+16101234567")
        .putLong("message_id", 1)
        .putLong("timestamp", 2)
        .serialize()
    )
    mockRecipientResolve("+16101234567", 1)

    val subject = RecipientIdJobMigration(mockk<Application>())
    val converted = subject.migrate(testData)
    val data = JsonJobData.deserialize(converted.data)

    assertEquals("SendDeliveryReceiptJob", converted.factoryKey)
    assertNull(converted.queueKey)
    assertEquals("1", data.getString("recipient"))
    assertEquals(1, data.getLong("message_id"))
    assertEquals(2, data.getLong("timestamp"))

    SendDeliveryReceiptJob.Factory().create(Job.Parameters.Builder().build(), converted.data)
  }

  @Test
  fun migrate_multiDeviceVerifiedUpdateJob() {
    val testData = JobData(
      factoryKey = "MultiDeviceVerifiedUpdateJob",
      queueKey = "__MULTI_DEVICE_VERIFIED_UPDATE__",
      maxAttempts = -1,
      lifespan = -1,
      data = JsonJobData.Builder().putString("destination", "+16101234567")
        .putString("identity_key", "abcd")
        .putInt("verified_status", 1)
        .putLong("timestamp", 123)
        .serialize()
    )
    mockRecipientResolve("+16101234567", 1)

    val subject = RecipientIdJobMigration(mockk<Application>())
    val converted = subject.migrate(testData)
    val data = JsonJobData.deserialize(converted.data)

    assertEquals("MultiDeviceVerifiedUpdateJob", converted.factoryKey)
    assertEquals("__MULTI_DEVICE_VERIFIED_UPDATE__", converted.queueKey)
    assertEquals("abcd", data.getString("identity_key"))
    assertEquals(1, data.getInt("verified_status"))
    assertEquals(123, data.getLong("timestamp"))
    assertEquals("1", data.getString("destination"))

    MultiDeviceVerifiedUpdateJob.Factory().create(Job.Parameters.Builder().build(), converted.data)
  }

  @Test
  fun migrate_pushGroupSendJob_null() {
    val testData = JobData(
      factoryKey = "PushGroupSendJob",
      queueKey = "someGroupId",
      maxAttempts = -1,
      lifespan = -1,
      data = JsonJobData.Builder().putString("filter_address", null)
        .putLong("message_id", 123)
        .serialize()
    )
    mockRecipientResolve("someGroupId", 5)

    val subject = RecipientIdJobMigration(mockk<Application>())
    val converted = subject.migrate(testData)
    val data = JsonJobData.deserialize(converted.data)

    assertEquals("PushGroupSendJob", converted.factoryKey)
    assertEquals(RecipientId.from(5).toQueueKey(), converted.queueKey)
    assertNull(data.getString("filter_recipient"))
    assertFalse(data.hasString("filter_address"))

    PushGroupSendJob.Factory().create(Job.Parameters.Builder().build(), converted.data)
  }

  @Test
  fun migrate_pushGroupSendJob_nonNull() {
    val testData = JobData(
      factoryKey = "PushGroupSendJob",
      queueKey = "someGroupId",
      maxAttempts = -1,
      lifespan = -1,
      data = JsonJobData.Builder().putString("filter_address", "+16101234567")
        .putLong("message_id", 123)
        .serialize()
    )
    mockRecipientResolve("+16101234567", 1)
    mockRecipientResolve("someGroupId", 5)

    val subject = RecipientIdJobMigration(mockk<Application>())
    val converted = subject.migrate(testData)
    val data = JsonJobData.deserialize(converted.data)

    assertEquals("PushGroupSendJob", converted.factoryKey)
    assertEquals(RecipientId.from(5).toQueueKey(), converted.queueKey)
    assertEquals("1", data.getString("filter_recipient"))
    assertFalse(data.hasString("filter_address"))

    PushGroupSendJob.Factory().create(Job.Parameters.Builder().build(), converted.data)
  }

  @Test
  fun migrate_directoryRefreshJob_null() {
    val testData = JobData(
      factoryKey = "DirectoryRefreshJob",
      queueKey = "DirectoryRefreshJob",
      maxAttempts = -1,
      lifespan = -1,
      data = JsonJobData.Builder()
        .putString("address", null)
        .putBoolean("notify_of_new_users", true)
        .serialize()
    )

    val subject = RecipientIdJobMigration(mockk<Application>())
    val converted = subject.migrate(testData)
    val data = JsonJobData.deserialize(converted.data)

    assertEquals("DirectoryRefreshJob", converted.factoryKey)
    assertEquals("DirectoryRefreshJob", converted.queueKey)
    assertNull(data.getString("recipient"))
    assertTrue(data.getBoolean("notify_of_new_users"))
    assertFalse(data.hasString("address"))

    DirectoryRefreshJob.Factory().create(Job.Parameters.Builder().build(), converted.data)
  }

  @Test
  fun migrate_directoryRefreshJob_nonNull() {
    val testData = JobData(
      factoryKey = "DirectoryRefreshJob",
      queueKey = "DirectoryRefreshJob",
      maxAttempts = -1,
      lifespan = -1,
      data = JsonJobData.Builder()
        .putString("address", "+16101234567")
        .putBoolean("notify_of_new_users", true)
        .serialize()
    )
    mockRecipientResolve("+16101234567", 1)

    val subject = RecipientIdJobMigration(mockk<Application>())
    val converted = subject.migrate(testData)
    val data = JsonJobData.deserialize(converted.data)

    assertEquals("DirectoryRefreshJob", converted.factoryKey)
    assertEquals("DirectoryRefreshJob", converted.queueKey)
    assertTrue(data.getBoolean("notify_of_new_users"))
    assertEquals("1", data.getString("recipient"))
    assertFalse(data.hasString("address"))

    DirectoryRefreshJob.Factory().create(Job.Parameters.Builder().build(), converted.data)
  }

  @Test
  fun migrate_retrieveProfileAvatarJob() {
    val testData = JobData(
      factoryKey = "RetrieveProfileAvatarJob",
      queueKey = "RetrieveProfileAvatarJob+16101234567",
      maxAttempts = -1,
      lifespan = -1,
      data = JsonJobData.Builder()
        .putString("address", "+16101234567")
        .putString("profile_avatar", "abc")
        .serialize()
    )
    mockRecipientResolve("+16101234567", 1)

    val subject = RecipientIdJobMigration(mockk<Application>())
    val converted = subject.migrate(testData)
    val data = JsonJobData.deserialize(converted.data)

    assertEquals("RetrieveProfileAvatarJob", converted.factoryKey)
    assertEquals("RetrieveProfileAvatarJob::" + RecipientId.from(1).toQueueKey(), converted.queueKey)
    assertEquals("1", data.getString("recipient"))

    RetrieveProfileAvatarJob.Factory().create(Job.Parameters.Builder().build(), converted.data)
  }

  @Test
  fun migrate_multiDeviceReadUpdateJob_empty() {
    val testData = JobData(
      factoryKey = "MultiDeviceReadUpdateJob",
      queueKey = null,
      maxAttempts = -1,
      lifespan = -1,
      data = JsonJobData.Builder()
        .putStringArray("message_ids", arrayOfNulls(0))
        .serialize()
    )

    val subject = RecipientIdJobMigration(mockk<Application>())
    val converted = subject.migrate(testData)
    val data = JsonJobData.deserialize(converted.data)

    assertEquals("MultiDeviceReadUpdateJob", converted.factoryKey)
    assertNull(converted.queueKey)
    assertEquals(0, data.getStringArray("message_ids").size)

    MultiDeviceReadUpdateJob.Factory().create(Job.Parameters.Builder().build(), converted.data)
  }

  @Test
  fun migrate_multiDeviceReadUpdateJob_twoIds() {
    val id1 = OldSerializableSyncMessageId("+16101234567", 1)
    val id2 = OldSerializableSyncMessageId("+16101112222", 2)

    val testData = JobData(
      factoryKey = "MultiDeviceReadUpdateJob",
      queueKey = null,
      maxAttempts = -1,
      lifespan = -1,
      data = JsonJobData.Builder()
        .putStringArray("message_ids", arrayOf(JsonUtils.toJson(id1), JsonUtils.toJson(id2)))
        .serialize()
    )
    mockRecipientResolve("+16101234567", 1)
    mockRecipientResolve("+16101112222", 2)

    val subject = RecipientIdJobMigration(mockk<Application>())
    val converted = subject.migrate(testData)
    val data = JsonJobData.deserialize(converted.data)

    assertEquals("MultiDeviceReadUpdateJob", converted.factoryKey)
    assertNull(converted.queueKey)

    val updated = data.getStringArray("message_ids")
    assertEquals(2, updated.size)

    assertEquals(JsonUtils.toJson(NewSerializableSyncMessageId("1", 1)), updated[0])
    assertEquals(JsonUtils.toJson(NewSerializableSyncMessageId("2", 2)), updated[1])

    MultiDeviceReadUpdateJob.Factory().create(Job.Parameters.Builder().build(), converted.data)
  }

  @Test
  fun migrate_pushMediaSendJob() {
    val testData = JobData(
      factoryKey = "PushMediaSendJob",
      queueKey = "+16101234567",
      maxAttempts = -1,
      lifespan = -1,
      data = JsonJobData.Builder().putLong("message_id", 1).serialize()
    )
    mockRecipientResolve("+16101234567", 1)

    val subject = RecipientIdJobMigration(mockk<Application>())
    val converted = subject.migrate(testData)
    val data = JsonJobData.deserialize(converted.data)

    assertEquals("PushMediaSendJob", converted.factoryKey)
    assertEquals(RecipientId.from(1).toQueueKey(), converted.queueKey)
    assertEquals(1, data.getLong("message_id"))

    IndividualSendJob.Factory().create(Job.Parameters.Builder().build(), converted.data)
  }

  private fun mockRecipientResolve(address: String, recipientId: Long) {
    every { Recipient.external(any(), address) } returns mockRecipient(recipientId)
  }

  private fun mockRecipient(id: Long): Recipient {
    return mockk<Recipient> {
      every { this@mockk.id } returns RecipientId.from(id)
    }
  }
}
