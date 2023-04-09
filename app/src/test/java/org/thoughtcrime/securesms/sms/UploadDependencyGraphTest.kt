package org.thoughtcrime.securesms.sms

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobManager
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobs.AttachmentCompressionJob
import org.thoughtcrime.securesms.jobs.AttachmentCopyJob
import org.thoughtcrime.securesms.jobs.AttachmentUploadJob
import org.thoughtcrime.securesms.jobs.ResumableUploadSpecJob
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.mms.SentMediaQuality
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.UriAttachmentBuilder
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.MediaUtil
import java.util.concurrent.atomic.AtomicLong

/**
 * Requires Robolectric due to usage of Uri
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UploadDependencyGraphTest {

  private val jobManager: JobManager = mock()

  private var uniqueLong = AtomicLong(0)

  @Before
  fun setUp() {
    whenever(jobManager.startChain(any<Job>())).then {
      JobManager.Chain(jobManager, listOf(it.getArgument(0)))
    }
  }

  @Test
  fun `Given a list of Uri attachments and a list of Messages, when I get the dependencyMap, then I expect a times m results`() {
    // GIVEN
    val uriAttachments = (1..5).map { UriAttachmentBuilder.build(id = uniqueLong.getAndIncrement(), contentType = MediaUtil.IMAGE_JPEG) }
    val messages = (1..5).createMessages(uriAttachments)
    val testSubject = UploadDependencyGraph.create(messages, jobManager) { getAttachmentForPreUpload(uniqueLong.getAndIncrement(), it) }

    // WHEN
    val result = testSubject.dependencyMap

    // THEN
    assertEquals(5, result.size)
    result.values.forEach { assertEquals(5, it.size) }
  }

  @Test
  fun `Given a list of Uri attachments and a list of Messages, when I consumeDeferredQueue, then I expect one upload chain and one copy job for each attachment`() {
    // GIVEN
    val uriAttachments = (1..5).map { UriAttachmentBuilder.build(id = uniqueLong.getAndIncrement(), contentType = MediaUtil.IMAGE_JPEG) }
    val messages = (1..5).createMessages(uriAttachments)
    val testSubject = UploadDependencyGraph.create(messages, jobManager) { getAttachmentForPreUpload(uniqueLong.getAndIncrement(), it) }

    // WHEN
    val deferredQueue = testSubject.consumeDeferredQueue()

    // THEN
    deferredQueue.forEach { assertValidJobChain(it, 4) }
  }

  @Test
  fun `Given a list of Uri attachments with same id but different transforms and a list of Messages, when I consumeDeferredQueue, then I expect one upload chain for each attachment and 5 copy jobs`() {
    // GIVEN
    val uriAttachments = (1..5).map {
      val increment = uniqueLong.getAndIncrement()
      UriAttachmentBuilder.build(
        id = 10,
        contentType = MediaUtil.IMAGE_JPEG,
        transformProperties = AttachmentTable.TransformProperties(false, true, increment, increment + 1, SentMediaQuality.STANDARD.code)
      )
    }

    val messages = (1..5).createMessages(uriAttachments)
    val testSubject = UploadDependencyGraph.create(messages, jobManager) { getAttachmentForPreUpload(uniqueLong.getAndIncrement(), it) }

    // WHEN
    val deferredQueue = testSubject.consumeDeferredQueue()

    // THEN
    deferredQueue.forEach { assertValidJobChain(it, 4) }
  }

  @Test
  fun `Given a single Uri attachment with same id and a list of Messages, when I consumeDeferredQueue, then I expect one upload chain and one copy job`() {
    // GIVEN
    val uriAttachments = (1..5).map {
      UriAttachmentBuilder.build(
        id = 10,
        contentType = MediaUtil.IMAGE_JPEG
      )
    }

    val messages = (1..5).createMessages(uriAttachments)
    val testSubject = UploadDependencyGraph.create(messages, jobManager) { getAttachmentForPreUpload(uniqueLong.getAndIncrement(), it) }

    // WHEN
    val deferredQueue = testSubject.consumeDeferredQueue()

    // THEN
    assertEquals(1, deferredQueue.size)
    deferredQueue.forEach { assertValidJobChain(it, 4) }
  }

  @Test
  fun `Given three Uri attachments with same id and two share transform properties and a list of Messages, when I executeDeferredQueue, then I expect two chains`() {
    // GIVEN
    val uriAttachments = (1..3).map {
      UriAttachmentBuilder.build(
        id = 10,
        contentType = MediaUtil.IMAGE_JPEG,
        transformProperties = if (it != 1) AttachmentTable.TransformProperties(false, true, 1, 2, SentMediaQuality.STANDARD.code) else null
      )
    }

    val messages = (1..8).createMessages(uriAttachments)
    val testSubject = UploadDependencyGraph.create(messages, jobManager) { getAttachmentForPreUpload(uniqueLong.getAndIncrement(), it) }

    // WHEN
    val deferredQueue = testSubject.consumeDeferredQueue()

    // THEN
    assertEquals(2, deferredQueue.size)
    deferredQueue.forEach { assertValidJobChain(it, 7) }
  }

  @Test
  fun `Given a list of Database attachments and a list of Messages, when I get the dependency map, then I expect a times m results`() {
    // GIVEN
    val databaseAttachments = (1..5).map {
      val id = uniqueLong.getAndIncrement()
      val uriAttachment = UriAttachmentBuilder.build(id = uniqueLong.getAndIncrement(), contentType = MediaUtil.IMAGE_JPEG)
      getAttachmentForPreUpload(id, uriAttachment)
    }

    val messages = (1..5).createMessages(databaseAttachments)
    val testSubject = UploadDependencyGraph.create(messages, jobManager) { getAttachmentForPreUpload(uniqueLong.getAndIncrement(), it) }

    // WHEN
    val result = testSubject.dependencyMap

    // THEN
    assertEquals(5, result.size)
    result.values.forEach { assertEquals(5, it.size) }
  }

  @Test
  fun `Given a list of messages with unique ids, when I consumeDeferredQueue, then I expect no copy jobs`() {
    // GIVEN
    val attachment1 = UriAttachmentBuilder.build(uniqueLong.getAndIncrement(), contentType = MediaUtil.IMAGE_JPEG)
    val attachment2 = UriAttachmentBuilder.build(uniqueLong.getAndIncrement(), contentType = MediaUtil.IMAGE_JPEG)
    val message1 = OutgoingMessage(recipient = Recipient.UNKNOWN, sentTimeMillis = System.currentTimeMillis(), attachments = listOf(attachment1))
    val message2 = OutgoingMessage(recipient = Recipient.UNKNOWN, sentTimeMillis = System.currentTimeMillis() + 1, attachments = listOf(attachment2))
    val testSubject = UploadDependencyGraph.create(listOf(message1, message2), jobManager) { getAttachmentForPreUpload(uniqueLong.getAndIncrement(), it) }

    // WHEN
    val result = testSubject.consumeDeferredQueue()

    // THEN
    assertEquals(2, result.size)
    result.forEach {
      assertValidJobChain(it, 0)
    }
  }

  @Test
  fun `Given a list of attachments of the same uri with different transform props, when I consumeDeferredQueue, then I expect two chains`() {
    val uriAttachments = (0..1).map {
      UriAttachmentBuilder.build(
        1L,
        contentType = MediaUtil.IMAGE_JPEG,
        transformProperties = AttachmentTable.TransformProperties.forVideoTrim(it.toLong(), it.toLong() + 1)
      )
    }

    val message = OutgoingMessage(recipient = Recipient.UNKNOWN, sentTimeMillis = System.currentTimeMillis(), attachments = uriAttachments)
    val testSubject = UploadDependencyGraph.create(listOf(message), jobManager) { getAttachmentForPreUpload(uniqueLong.getAndIncrement(), it) }
    val result = testSubject.consumeDeferredQueue()

    assertEquals(2, result.size)
    result.forEach {
      assertValidJobChain(it, 0)
    }
  }

  private fun assertValidJobChain(chain: JobManager.Chain, expectedCopyDestinationCount: Int) {
    val steps: List<List<Job>> = chain.jobListChain

    assertTrue(steps.all { it.size == 1 })
    assertTrue(steps[0][0] is AttachmentCompressionJob)
    assertTrue(steps[1][0] is ResumableUploadSpecJob)
    assertTrue(steps[2][0] is AttachmentUploadJob)

    if (expectedCopyDestinationCount > 0) {
      assertTrue(steps[3][0] is AttachmentCopyJob)

      val uploadData = JsonJobData.deserialize(steps[2][0].serialize())
      val copyData = JsonJobData.deserialize(steps[3][0].serialize())

      val uploadAttachmentId = AttachmentId(uploadData.getLong("row_id"), uploadData.getLong("unique_id"))
      val copySourceAttachmentId = JsonUtils.fromJson(copyData.getString("source_id"), AttachmentId::class.java)

      assertEquals(uploadAttachmentId, copySourceAttachmentId)

      val copyDestinations = copyData.getStringArray("destination_ids")
      assertEquals(expectedCopyDestinationCount, copyDestinations.size)
    } else {
      assertEquals(3, steps.size)
    }
  }

  private fun getAttachmentForPreUpload(id: Long, attachment: Attachment): DatabaseAttachment {
    return DatabaseAttachment(
      AttachmentId(id, id),
      AttachmentTable.PREUPLOAD_MESSAGE_ID,
      false,
      false,
      attachment.contentType,
      AttachmentTable.TRANSFER_PROGRESS_PENDING,
      attachment.size,
      attachment.fileName,
      attachment.cdnNumber,
      attachment.location,
      attachment.key,
      attachment.relay,
      attachment.digest,
      attachment.fastPreflightId,
      attachment.isVoiceNote,
      attachment.isBorderless,
      attachment.isVideoGif,
      attachment.width,
      attachment.height,
      attachment.isQuote,
      attachment.caption,
      attachment.sticker,
      attachment.blurHash,
      attachment.audioHash,
      attachment.transformProperties,
      0,
      attachment.uploadTimestamp
    )
  }

  private fun Iterable<Int>.createMessages(uriAttachments: List<Attachment>): List<OutgoingMessage> {
    return mapIndexed { index, _ ->
      OutgoingMessage(
        recipient = Recipient.UNKNOWN,
        sentTimeMillis = System.currentTimeMillis() + index,
        attachments = uriAttachments,
        isSecure = true
      )
    }
  }
}
