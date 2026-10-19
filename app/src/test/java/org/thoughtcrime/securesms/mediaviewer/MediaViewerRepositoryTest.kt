/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaviewer

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.database.AttachmentId
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MediaTable.Sorting
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.TestSms
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.thoughtcrime.securesms.util.RemoteConfig
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MediaViewerRepositoryTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val recipients = RecipientTestRule()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  private val repository = MediaViewerRepository
  private val context: Context = ApplicationProvider.getApplicationContext()

  private var nextReceivedTimestamp = 1_000_000L

  @Before
  fun setUp() {
    every { RemoteConfig.regularDeleteThreshold } returns 1.days.inWholeSeconds
    every { RemoteConfig.adminDeleteThreshold } returns 1.days.inWholeSeconds
  }

  @Test
  fun `getInitialItem agrees with the viewable gallery for each transfer state`() = runTest(testDispatcher) {
    val (threadId, contact) = createThread()
    val messageId = insertMessage(threadId)

    val cases = listOf(
      Triple("done", true, insertAttachment(messageId, transferState = AttachmentTable.TRANSFER_PROGRESS_DONE)),
      Triple("started", true, insertAttachment(messageId, transferState = AttachmentTable.TRANSFER_PROGRESS_STARTED)),
      Triple("pending without thumbnail", false, insertAttachment(messageId, transferState = AttachmentTable.TRANSFER_PROGRESS_PENDING, dataFile = null)),
      Triple("failed without thumbnail", false, insertAttachment(messageId, transferState = AttachmentTable.TRANSFER_PROGRESS_FAILED, dataFile = null)),
      Triple("permanent failure without thumbnail", false, insertAttachment(messageId, transferState = AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE, dataFile = null)),
      Triple("offloaded without thumbnail", false, insertAttachment(messageId, transferState = AttachmentTable.TRANSFER_RESTORE_OFFLOADED, dataFile = null)),
      Triple(
        "pending with thumbnail",
        true,
        insertAttachment(
          messageId = messageId,
          transferState = AttachmentTable.TRANSFER_PROGRESS_PENDING,
          dataFile = null,
          thumbnailFile = "/thumbnail/file",
          thumbnailRestoreState = AttachmentTable.ThumbnailRestoreState.FINISHED
        )
      ),
      Triple(
        "needs restore with thumbnail",
        true,
        insertAttachment(
          messageId = messageId,
          transferState = AttachmentTable.TRANSFER_NEEDS_RESTORE,
          dataFile = null,
          thumbnailFile = "/thumbnail/file",
          thumbnailRestoreState = AttachmentTable.ThumbnailRestoreState.FINISHED
        )
      ),
      Triple(
        "needs restore with thumbnail restore in progress",
        false,
        insertAttachment(
          messageId = messageId,
          transferState = AttachmentTable.TRANSFER_NEEDS_RESTORE,
          dataFile = null,
          thumbnailRestoreState = AttachmentTable.ThumbnailRestoreState.IN_PROGRESS
        )
      )
    )

    val args = createArgs(threadId, contact, Sorting.Newest, leftIsRecent = false)
    for ((name, expectedViewable, attachmentId) in cases) {
      val inGallery = SignalDatabase.media.getViewableGalleryMediaPositionAndCount(threadId, Sorting.Newest, attachmentId) != null
      val item = repository.getInitialItem(context, AttachmentId(attachmentId), args)

      assertThat(inGallery, name = "$name in viewable gallery").isEqualTo(expectedViewable)
      assertThat(item != null, name = "$name has initial item").isEqualTo(inGallery)
    }
  }

  @Test
  fun `getDeleteValidity disallows delete for everyone when the message has more than one attachment`() = runTest(testDispatcher) {
    val contact = recipients.createRecipient("Media Receiver")
    val messageId = recipients.insertOutgoingMessage(contact)
    val attachmentId = insertAttachment(messageId)
    insertAttachment(messageId)

    val validity = repository.getDeleteValidity(AttachmentId(attachmentId))

    assertThat(validity.canDeleteForEveryone).isFalse()
    assertThat(validity.isNoteToSelf).isFalse()
  }

  @Test
  fun `getDeleteValidity flags note to self and disallows delete for everyone`() = runTest(testDispatcher) {
    val messageId = recipients.insertOutgoingMessage(recipients.self)
    val attachmentId = insertAttachment(messageId)

    val validity = repository.getDeleteValidity(AttachmentId(attachmentId))

    assertThat(validity.isNoteToSelf).isTrue()
    assertThat(validity.canDeleteForEveryone).isFalse()
  }

  @Test
  fun `getDeleteValidity allows delete for everyone for a recent single attachment outgoing message`() = runTest(testDispatcher) {
    val contact = recipients.createRecipient("Media Receiver")
    val messageId = recipients.insertOutgoingMessage(contact)
    val attachmentId = insertAttachment(messageId)

    val validity = repository.getDeleteValidity(AttachmentId(attachmentId))

    assertThat(validity.canDeleteForEveryone).isTrue()
    assertThat(validity.isNoteToSelf).isFalse()
  }

  @Test
  fun `getDeleteValidity is all false for a missing attachment`() = runTest(testDispatcher) {
    val validity = repository.getDeleteValidity(AttachmentId(9_999))

    assertThat(validity.canDeleteForEveryone).isFalse()
    assertThat(validity.isNoteToSelf).isFalse()
  }

  private fun createArgs(threadId: Long, contact: RecipientId, sorting: Sorting, leftIsRecent: Boolean): MediaIntentFactory.MediaPreviewArgs {
    return MediaIntentFactory.MediaPreviewArgs(
      threadId = threadId,
      date = 0,
      messageId = 0,
      fromRecipientId = contact,
      threadRecipientId = contact,
      outgoing = false,
      initialMediaUri = Uri.EMPTY,
      initialMediaDataUri = null,
      initialMediaType = "image/jpeg",
      initialMediaSize = 100,
      leftIsRecent = leftIsRecent,
      sorting = sorting,
      isVideoGif = false,
      skipSharedElementTransition = false
    )
  }

  private fun createThread(): Pair<Long, RecipientId> {
    val contact = recipients.createRecipient("Media Sender")
    return SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(contact)) to contact
  }

  private fun insertMessage(threadId: Long): Long {
    return TestSms.insert(recipients.signalDatabase.writeableDatabase, threadId = threadId, receivedTimestampMillis = nextReceivedTimestamp++)
  }

  private fun insertAttachment(
    messageId: Long,
    transferState: Int = AttachmentTable.TRANSFER_PROGRESS_DONE,
    dataFile: String? = "/data/file",
    thumbnailFile: String? = null,
    thumbnailRestoreState: AttachmentTable.ThumbnailRestoreState = AttachmentTable.ThumbnailRestoreState.NONE
  ): Long {
    return SignalDatabase.writableDatabase.insert(
      AttachmentTable.TABLE_NAME,
      null,
      ContentValues().apply {
        put(AttachmentTable.MESSAGE_ID, messageId)
        put(AttachmentTable.CONTENT_TYPE, "image/jpeg")
        put(AttachmentTable.TRANSFER_STATE, transferState)
        put(AttachmentTable.DATA_SIZE, 100)
        put(AttachmentTable.THUMBNAIL_RESTORE_STATE, thumbnailRestoreState.value)
        dataFile?.let { put(AttachmentTable.DATA_FILE, it) }
        thumbnailFile?.let { put(AttachmentTable.THUMBNAIL_FILE, it) }
      }
    )
  }
}
