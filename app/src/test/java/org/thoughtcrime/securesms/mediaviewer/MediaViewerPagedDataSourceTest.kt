/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaviewer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.logging.Log
import org.signal.paging.PagedDataSource
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.GalleryMediaFixtures
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MediaViewerPagedDataSourceTest {

  @get:Rule
  val recipients = RecipientTestRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  private val cancellationSignal = PagedDataSource.CancellationSignal { false }

  private val fixtures = GalleryMediaFixtures(recipients)

  @Test
  fun `given a thread of media, when I load pages in either direction, then they match the gallery order`() {
    val threadId = fixtures.createThread()
    val newestFirst = (0 until 40).map { AttachmentId(fixtures.insertAttachment(fixtures.insertMessage(threadId))) }.reversed()

    for (leftIsRecent in listOf(true, false)) {
      val expected = if (leftIsRecent) newestFirst else newestFirst.reversed()
      val source = createSource(threadId, leftIsRecent)
      val total = source.size()

      val paged = (0 until total step 12).flatMap { start ->
        source.load(start, minOf(12, total - start), total, cancellationSignal).map { it.attachmentId }
      }

      assertThat(total, name = "leftIsRecent=$leftIsRecent").isEqualTo(40)
      assertThat(paged, name = "leftIsRecent=$leftIsRecent").isEqualTo(expected)
    }
  }

  @Test
  fun `given an attachment, when I find its display index, then it matches its position in the loaded pages and primes the size`() {
    val threadId = fixtures.createThread()
    val ids = (0 until 30).map { AttachmentId(fixtures.insertAttachment(fixtures.insertMessage(threadId))) }

    for (leftIsRecent in listOf(true, false)) {
      for (anchor in listOf(ids.first(), ids[17], ids.last())) {
        val source = createSource(threadId, leftIsRecent)

        val displayIndex = source.findDisplayIndex(anchor)
        val total = source.size()
        val loaded = source.load(0, total, total, cancellationSignal).map { it.attachmentId }

        assertThat(total, name = "leftIsRecent=$leftIsRecent anchor=$anchor").isEqualTo(30)
        assertThat(loaded[displayIndex], name = "leftIsRecent=$leftIsRecent anchor=$anchor").isEqualTo(anchor)
      }
    }
  }

  @Test
  fun `given a non-viewable attachment, when I find its display index, then it is -1`() {
    val threadId = fixtures.createThread()
    fixtures.insertAttachment(fixtures.insertMessage(threadId))
    val nonViewable = fixtures.insertAttachment(fixtures.insertMessage(threadId), transferState = AttachmentTable.TRANSFER_PROGRESS_PENDING, dataFile = null)

    val source = createSource(threadId, leftIsRecent = true)

    assertThat(source.findDisplayIndex(AttachmentId(nonViewable))).isEqualTo(-1)
    assertThat(source.isStillViewable(AttachmentId(nonViewable))).isEqualTo(false)
    assertThat(source.size()).isEqualTo(1)
  }

  private fun createSource(threadId: Long, leftIsRecent: Boolean): MediaViewerPagedDataSource {
    return MediaViewerPagedDataSource(ApplicationProvider.getApplicationContext(), threadId, MediaTable.Sorting.Newest, leftIsRecent)
  }
}
