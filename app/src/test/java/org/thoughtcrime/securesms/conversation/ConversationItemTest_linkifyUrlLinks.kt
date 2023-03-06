package org.thoughtcrime.securesms.conversation

import android.app.Application
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.util.UrlClickHandler

@Suppress("ClassName")
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = Application::class)
class ConversationItemTest_linkifyUrlLinks(private val input: String, private val expectedUrl: String) {

  @Test
  fun test1() {
    val spannableStringBuilder = SpannableStringBuilder(input)

    ConversationItem.linkifyUrlLinks(spannableStringBuilder, true, UrlHandler)

    val spans = spannableStringBuilder.getSpans(0, expectedUrl.length, URLSpan::class.java)
    assertEquals(2, spans.size)
    assertEquals(expectedUrl, spans.get(0).url)
  }

  private object UrlHandler : UrlClickHandler {
    override fun handleOnClick(url: String): Boolean = true
  }

  companion object {
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "Input: {0}, {1}")
    fun params() = listOf(
      arrayOf("https://www.google.com", "https://www.google.com"),
      arrayOf("https://www.google.com%d332", "https://www.google.com"),
//      arrayOf("https://www.instagram.com/tv/CfImYdngccQ/?igshid=YmMyMTA2M2Y= ", "https://www.instagram.com/tv/CfImYdngccQ/?igshid=YmMyMTA2M2Y="),
      arrayOf("https://www.instagram.com/tv/CfImYdngccQ/?igshid=YmMyMTA2M2Y=\n", "https://www.instagram.com/tv/CfImYdngccQ/?igshid=YmMyMTA2M2Y="),
//      arrayOf("https://fr.ulule.com/sapins-barbus-la-bd-/ ", "https://fr.ulule.com/sapins-barbus-la-bd-/"),
      arrayOf("https://fr.ulule.com/sapins-barbus-la-bd-/\n", "https://fr.ulule.com/sapins-barbus-la-bd-/"),
      arrayOf("https://de.m.wikipedia.org/wiki/Red_Dawn_(2012)", "https://de.m.wikipedia.org/wiki/Red_Dawn_(2012)")
//      arrayOf("https://de.m.wikipedia.org/wiki/Red_Dawn_(2012)\n\n\uD83E\uDD14\uD83D\uDE1C", "https://de.m.wikipedia.org/wiki/Red_Dawn_(2012)")
    )
  }
}
