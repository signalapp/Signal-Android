package org.thoughtcrime.securesms.l10n

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isGreaterThan
import org.junit.Test
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.Path
import kotlin.io.path.inputStream

class SupportArticleTest {
  /**
   * Tests that support articles found in strings.xml:
   * <p>
   * - Do not have a locale mentioned in the URL.
   * - Only have an article number, i.e. no trailing text.
   * - Are https.
   * - Are marked as translatable="false".
   */
  @Test
  fun ensure_format_and_translatable_state_of_all_support_article_urls() {
    val errors = mutableListOf<String>()
    var seen = 0

    val strings = DocumentBuilderFactory.newInstance()
      .newDocumentBuilder()
      .parse(MAIN_STRINGS_PATH.inputStream())
      .getElementsByTagName("string")

    for (i in 0 until strings.length) {
      val stringNode = strings.item(i)
      val string = stringNode.textContent
      val stringName = stringName(stringNode)

      if (SUPPORT_ARTICLE.matches(string)) {
        seen++

        if (!CORRECT_SUPPORT_ARTICLE.matches(string)) {
          errors.add("Article URL format is not correct [$stringName] URL: $string")
        }
        if (isTranslatable(stringNode)) {
          errors.add("Article string is translatable [$stringName], add translatable=\"false\"")
        }
      }
    }

    assertThat(seen).isGreaterThan(0)
    assertThat(errors).isEmpty()
  }

  private fun isTranslatable(item: Node): Boolean {
    val translatableAttribute = item.attributes.getNamedItem("translatable")
    return translatableAttribute == null || translatableAttribute.textContent != "false"
  }

  private fun stringName(item: Node): String {
    return item.attributes.getNamedItem("name").textContent
  }

  companion object {
    private val MAIN_STRINGS_PATH = Path("src/main/res/values/strings.xml")
    private val SUPPORT_ARTICLE = Regex(".*://support.signal.org/.*articles/.*")
    private val CORRECT_SUPPORT_ARTICLE = Regex("https://support.signal.org/hc/articles/\\d+(#[a-z_]+)?")
  }
}
