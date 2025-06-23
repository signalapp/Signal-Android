package org.thoughtcrime.securesms.storage

import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testutil.EmptyLogger
import org.whispersystems.signalservice.api.storage.SignalNotificationProfileRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.storage.protos.NotificationProfile
import org.whispersystems.signalservice.internal.storage.protos.Recipient
import java.util.UUID

/**
 * Tests for [NotificationProfileRecordProcessor]
 */
class NotificationProfileRecordProcessorTest {
  companion object {
    val STORAGE_ID: StorageId = StorageId.forNotificationProfile(byteArrayOf(1, 2, 3, 4))

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(EmptyLogger())
    }
  }

  private val testSubject = NotificationProfileRecordProcessor()

  @Test
  fun `Given a valid proto with a known name and id, assert valid`() {
    // GIVEN
    val proto = NotificationProfile.Builder().apply {
      id = UuidUtil.toByteArray(UUID.randomUUID()).toByteString()
      name = "name"
    }.build()
    val record = SignalNotificationProfileRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `Given a valid proto with a deleted timestamp, known name and id, assert valid`() {
    // GIVEN
    val proto = NotificationProfile.Builder().apply {
      id = UuidUtil.toByteArray(UUID.randomUUID()).toByteString()
      name = "name"
      deletedAtTimestampMs = 1000L
    }.build()
    val record = SignalNotificationProfileRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `Given an invalid proto with no id, assert invalid`() {
    // GIVEN
    val proto = NotificationProfile.Builder().apply {
      id = "Bad".toByteArray().toByteString()
      name = "Profile"
      deletedAtTimestampMs = 0L
    }.build()
    val record = SignalNotificationProfileRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given an invalid proto with no name, assert invalid`() {
    // GIVEN
    val proto = NotificationProfile.Builder().apply {
      id = UuidUtil.toByteArray(UUID.randomUUID()).toByteString()
      name = ""
      deletedAtTimestampMs = 0L
    }.build()
    val record = SignalNotificationProfileRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given an invalid proto with a member that does not have a service id, assert invalid`() {
    // GIVEN
    val proto = NotificationProfile.Builder().apply {
      id = UuidUtil.toByteArray(UUID.randomUUID()).toByteString()
      name = "Profile"
      allowedMembers = listOf(Recipient(contact = Recipient.Contact(serviceId = "bad")))
    }.build()
    val record = SignalNotificationProfileRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }
}
