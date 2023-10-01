package org.thoughtcrime.securesms.storage

import okio.ByteString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testutil.EmptyLogger
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.storage.SignalStoryDistributionListRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.storage.protos.StoryDistributionListRecord
import java.util.UUID

class StoryDistributionListRecordProcessorTest {

  companion object {
    val STORAGE_ID: StorageId = StorageId.forStoryDistributionList(byteArrayOf(1, 2, 3, 4))

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(EmptyLogger())
    }
  }

  private val testSubject = StoryDistributionListRecordProcessor()

  @Test
  fun `Given a proto without an identifier, when I isInvalid, then I expect true`() {
    // GIVEN
    val proto = StoryDistributionListRecord()
    val record = SignalStoryDistributionListRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given a proto with an identifier that is not a UUID, when I isInvalid, then I expect true`() {
    // GIVEN
    val proto = StoryDistributionListRecord()
      .newBuilder()
      .identifier(ByteString.of(*"Greetings, fellow UUIDs".encodeToByteArray()))
      .build()

    val record = SignalStoryDistributionListRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given a proto without a name or deletion timestamp, when I isInvalid, then I expect true`() {
    // GIVEN
    val proto = StoryDistributionListRecord()
      .newBuilder()
      .identifier(ByteString.of(*UuidUtil.toByteArray(UUID.randomUUID())))
      .build()

    val record = SignalStoryDistributionListRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given a proto with a deletion timestamp, when I isInvalid, then I expect false`() {
    // GIVEN
    val proto = StoryDistributionListRecord()
      .newBuilder()
      .identifier(ByteString.of(*UuidUtil.toByteArray(UUID.randomUUID())))
      .deletedAtTimestamp(1)
      .build()

    val record = SignalStoryDistributionListRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `Given a proto that is MyStory with a deletion timestamp, when I isInvalid, then I expect true`() {
    // GIVEN
    val proto = StoryDistributionListRecord()
      .newBuilder()
      .identifier(ByteString.of(*UuidUtil.toByteArray(DistributionId.MY_STORY.asUuid())))
      .deletedAtTimestamp(1)
      .build()

    val record = SignalStoryDistributionListRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given a validated proto that is MyStory, when I isInvalid with another MyStory, then I expect true`() {
    // GIVEN
    val proto = StoryDistributionListRecord()
      .newBuilder()
      .identifier(ByteString.of(*UuidUtil.toByteArray(DistributionId.MY_STORY.asUuid())))
      .deletedAtTimestamp(1)
      .build()

    val record = SignalStoryDistributionListRecord(STORAGE_ID, proto)
    testSubject.isInvalid(record)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given a proto with a visible name, when I isInvalid, then I expect false`() {
    // GIVEN
    val proto = StoryDistributionListRecord()
      .newBuilder()
      .identifier(ByteString.of(*UuidUtil.toByteArray(UUID.randomUUID())))
      .name("A visible name")
      .build()

    val record = SignalStoryDistributionListRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `Given a proto without a name, when I isInvalid, then I expect false`() {
    // GIVEN
    val proto = StoryDistributionListRecord()
      .newBuilder()
      .identifier(ByteString.of(*UuidUtil.toByteArray(UUID.randomUUID())))
      .build()

    val record = SignalStoryDistributionListRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `Given a proto without a visible name, when I isInvalid, then I expect true`() {
    // GIVEN
    val proto = StoryDistributionListRecord()
      .newBuilder()
      .identifier(ByteString.of(*UuidUtil.toByteArray(UUID.randomUUID())))
      .name("     ")
      .build()

    val record = SignalStoryDistributionListRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertTrue(result)
  }
}
