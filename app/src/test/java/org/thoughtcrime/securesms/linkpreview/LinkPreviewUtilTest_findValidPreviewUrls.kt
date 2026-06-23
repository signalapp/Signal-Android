package org.thoughtcrime.securesms.linkpreview

import android.app.Application
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class LinkPreviewUtilTest_findValidPreviewUrls {

  @Test
  fun no_links() {
    val links = LinkPreviewUtil.findValidPreviewUrls("No links")

    Assert.assertEquals(0, links.size().toLong())
    Assert.assertSame(LinkPreviewUtil.Links.EMPTY, links)
  }

  @Test
  fun contains_a_link() {
    val links = LinkPreviewUtil.findValidPreviewUrls("https://signal.org")

    Assert.assertEquals(1, links.size().toLong())

    Assert.assertTrue(links.containsUrl("https://signal.org"))
  }

  @Test
  fun does_not_contain_link() {
    val links = LinkPreviewUtil.findValidPreviewUrls("https://signal.org")

    Assert.assertEquals(1, links.size().toLong())

    Assert.assertFalse(links.containsUrl("https://signal.org/page"))
  }

  @Test
  fun contains_two_links() {
    val links = LinkPreviewUtil.findValidPreviewUrls("Links https://signal.org https://android.com")

    Assert.assertEquals(2, links.size().toLong())

    Assert.assertTrue(links.containsUrl("https://signal.org"))
    Assert.assertTrue(links.containsUrl("https://android.com"))
  }

  @Test
  fun link_trailing_slash_insensitivity() {
    val links = LinkPreviewUtil.findValidPreviewUrls("Links https://signal.org/ https://android.com")

    Assert.assertEquals(2, links.size().toLong())

    Assert.assertTrue(links.containsUrl("https://signal.org"))
    Assert.assertTrue(links.containsUrl("https://android.com"))
    Assert.assertTrue(links.containsUrl("https://signal.org/"))
    Assert.assertTrue(links.containsUrl("https://android.com/"))
  }

  @Test
  fun link_trailing_slash_insensitivity_where_last_url_has_trailing_slash() {
    val links = LinkPreviewUtil.findValidPreviewUrls("Links https://signal.org https://android.com/")

    Assert.assertEquals(2, links.size().toLong())

    Assert.assertTrue(links.containsUrl("https://signal.org"))
    Assert.assertTrue(links.containsUrl("https://android.com"))
    Assert.assertTrue(links.containsUrl("https://signal.org/"))
    Assert.assertTrue(links.containsUrl("https://android.com/"))
  }

  @Test
  fun multiple_trailing_slashes_are_not_stripped() {
    val links = LinkPreviewUtil.findValidPreviewUrls("Link https://android.com/")

    Assert.assertEquals(1, links.size().toLong())

    Assert.assertTrue(links.containsUrl("https://android.com"))
    Assert.assertTrue(links.containsUrl("https://android.com/"))
    Assert.assertFalse(links.containsUrl("https://android.com//"))
  }
}
