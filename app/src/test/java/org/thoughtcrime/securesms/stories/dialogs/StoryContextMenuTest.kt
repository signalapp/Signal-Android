package org.thoughtcrime.securesms.stories.dialogs

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.database.FakeMessageRecords
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mms.ImageSlide
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.util.Base64
import java.util.Optional

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StoryContextMenuTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val intentCaptor = argumentCaptor<Intent>()
  private val fragment: Fragment = mock {
    on { requireContext() } doReturn context
  }

  @Test
  fun `Given a story with an attachment, when I share, then I expect the correct stream, type, and flag`() {
    // GIVEN
    val attachmentId = AttachmentId(1, 2)
    val storyRecord = FakeMessageRecords.buildMediaMmsMessageRecord(
      storyType = StoryType.STORY_WITH_REPLIES,
      slideDeck = SlideDeck().apply {
        addSlide(
          ImageSlide(
            context,
            FakeMessageRecords.buildDatabaseAttachment(
              attachmentId = attachmentId
            )
          )
        )
      }
    )

    // WHEN
    StoryContextMenu.share(fragment, storyRecord)

    // THEN
    verify(fragment).startActivity(intentCaptor.capture())
    val chooserIntent: Intent = intentCaptor.firstValue
    val targetIntent: Intent = chooserIntent.getParcelableExtraCompat(Intent.EXTRA_INTENT, Intent::class.java)!!
    assertEquals(PartAuthority.getAttachmentPublicUri(PartAuthority.getAttachmentDataUri(attachmentId)), targetIntent.getParcelableExtraCompat(Intent.EXTRA_STREAM, Uri::class.java))
    assertEquals(MediaUtil.IMAGE_JPEG, targetIntent.type)
    assertTrue(Intent.FLAG_GRANT_READ_URI_PERMISSION and chooserIntent.flags == Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }

  @Test
  fun `Given a story with a text, when I share, then I expect the correct text`() {
    // GIVEN
    val expected = "Hello"
    val storyRecord = FakeMessageRecords.buildMediaMmsMessageRecord(
      storyType = StoryType.TEXT_STORY_WITH_REPLIES,
      body = Base64.encodeBytes(StoryTextPost.newBuilder().setBody(expected).build().toByteArray())
    )

    // WHEN
    StoryContextMenu.share(fragment, storyRecord)

    // THEN
    verify(fragment).startActivity(intentCaptor.capture())
    val chooserIntent: Intent = intentCaptor.firstValue
    val targetIntent: Intent = chooserIntent.getParcelableExtraCompat(Intent.EXTRA_INTENT, Intent::class.java)!!
    assertEquals(expected, targetIntent.getStringExtra(Intent.EXTRA_TEXT))
  }

  @Test
  fun `Given a story with a link, when I share, then I expect the correct text`() {
    // GIVEN
    val expected = "https://www.signal.org"
    val storyRecord = FakeMessageRecords.buildMediaMmsMessageRecord(
      storyType = StoryType.TEXT_STORY_WITH_REPLIES,
      body = Base64.encodeBytes(StoryTextPost.newBuilder().build().toByteArray()),
      linkPreviews = listOf(LinkPreview(expected, "", "", 0L, Optional.empty()))
    )

    // WHEN
    StoryContextMenu.share(fragment, storyRecord)

    // THEN
    verify(fragment).startActivity(intentCaptor.capture())
    val chooserIntent: Intent = intentCaptor.firstValue
    val targetIntent: Intent = chooserIntent.getParcelableExtraCompat(Intent.EXTRA_INTENT, Intent::class.java)!!
    assertEquals(expected, targetIntent.getStringExtra(Intent.EXTRA_TEXT))
  }

  @Test
  fun `Given a story with a text and a link, when I share, then I expect the correct text`() {
// GIVEN
    val url = "https://www.signal.org"
    val text = "hello"
    val expected = "$text $url"
    val storyRecord = FakeMessageRecords.buildMediaMmsMessageRecord(
      storyType = StoryType.TEXT_STORY_WITH_REPLIES,
      body = Base64.encodeBytes(StoryTextPost.newBuilder().setBody(text).build().toByteArray()),
      linkPreviews = listOf(LinkPreview(url, "", "", 0L, Optional.empty()))
    )

    // WHEN
    StoryContextMenu.share(fragment, storyRecord)

    // THEN
    verify(fragment).startActivity(intentCaptor.capture())
    val chooserIntent: Intent = intentCaptor.firstValue
    val targetIntent: Intent = chooserIntent.getParcelableExtraCompat(Intent.EXTRA_INTENT, Intent::class.java)!!
    assertEquals(expected, targetIntent.getStringExtra(Intent.EXTRA_TEXT))
  }
}
