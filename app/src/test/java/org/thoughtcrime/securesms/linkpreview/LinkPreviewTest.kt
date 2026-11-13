package org.thoughtcrime.securesms.linkpreview

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Optional

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class LinkPreviewTest {

  @Test
  fun `encoded entities in title and description are unescaped`() {
    val preview = LinkPreview("https://signal.org", "Signal &amp; friends", "It&#39;s private", 0, Optional.empty())

    assertEquals("Signal & friends", preview.title)
    assertEquals("It's private", preview.description)
  }

  @Test
  fun `markup in title and description is stripped`() {
    val preview = LinkPreview("https://signal.org", "<font color=\"-\">Title</font>", "<b>Desc</b>ription", 0, Optional.empty())

    assertEquals("Title", preview.title)
    assertEquals("Description", preview.description)
  }

  @Test
  fun `stray angle brackets are kept as text`() {
    val preview = LinkPreview("https://signal.org", "1 < 2 > 0", "", 0, Optional.empty())

    assertEquals("1 < 2 > 0", preview.title)
  }

  @Test
  fun `many unclosed tags are stripped`() {
    val preview = LinkPreview("https://signal.org", "<b>".repeat(100) + "News", "", 0, Optional.empty())

    assertEquals("News", preview.title)
  }
}
