/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PseudoLanguageTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun `short strings grow the most, accented`() {
    assertEquals("Çåñçéĺçå çåñçéĺçå çå", PseudoLanguage.EXPANSIVE.translate("Cancel"))
  }

  @Test
  fun `long strings grow less`() {
    val english = "Messages and calls are end-to-end encrypted and nobody else can read or listen to them"

    assertEquals(135, PseudoLanguage.EXPANSIVE.translate(english).length)
  }

  @Test
  fun `expansive grows words a little and adds more of them`() {
    assertEquals("Ĝöĝ ţöţ îţî ĝöĝ ţöţ îţî ĝö", PseudoLanguage.EXPANSIVE.translate("Go to it"))
  }

  @Test
  fun `compound joins words up to the longest word length`() {
    assertEquals("Ĝöĝţöţîţîĝöĝţöţîţîĝö", PseudoLanguage.COMPOUND.translate("Go to it"))
  }

  @Test
  fun `compound counts words joined by an apostrophe or hyphen as one word`() {
    listOf("Use your device\\'s biometrics or a security key", "Media auto-download").forEach { english ->
      val translated = PseudoLanguage.COMPOUND.translate(english)

      assertTrue(translated, translated.split(' ').all { it.replace("\\", "").length <= PseudoLanguage.MAX_WORD_LENGTH })
    }
  }

  @Test
  fun `no word grows past the longest word length`() {
    val words = PseudoLanguage.COMPOUND.translate("Notifications").split(' ')

    assertEquals(PseudoLanguage.MAX_WORD_LENGTH, words.first().length)
    assertTrue(words.all { it.length <= PseudoLanguage.MAX_WORD_LENGTH })
  }

  @Test
  fun `format specifiers, escapes and urls are left alone`() {
    val translated = PseudoLanguage.COMPOUND.translate("""Delete %1${'$'}s and %2${'$'}d others?\nIt\'s 100%% at https://signal.org/donate \u2026""")

    listOf("%1\$s", " %2\$d ", "\\n", "\\'", "100%%", "https://signal.org/donate", "\\u2026").forEach {
      assertTrue("Expected $it in $translated", translated.contains(it))
    }
  }

  @Test
  fun `text without letters is unchanged`() {
    assertEquals("%1\$s: %2\$d", PseudoLanguage.EXPANSIVE.translate("%1\$s: %2\$d"))
  }

  @Test
  fun `strings xml keeps only translatable strings, plurals and arrays`() {
    val translated = translateXml(
      """
      <string name="a">Cancel</string>
      <string name="b" translatable="false">Signal</string>
      <dimen name="c">4dp</dimen>
      <plurals name="d"><item quantity="one">%d minute</item><item quantity="other">%d minutes</item></plurals>
      <string-array name="e"><item>Never</item></string-array>
      """
    )

    assertTrue(translated, translated.contains("""<string name="a">Çåñçéĺçå çåñçéĺçå çå</string>"""))
    assertFalse(translated.contains("Signal"))
    assertFalse(translated.contains("dimen"))
    assertTrue(translated, translated.contains("""<item quantity="one">%d ɱîñûţéɱî ɱîñûţéɱî ɱîñû</item>"""))
    assertTrue(translated, translated.contains("<item>Ñéṽéŕñé ñéṽéŕñé ñ</item>"))
  }

  @Test
  fun `markup is kept around the text it covered`() {
    val translated = translateXml("""<string name="a">Tap <b>here</b> now</string>""")

    assertTrue(translated, translated.contains("""<string name="a">Ţåþţ <b>ĥéŕéĥ</b> ñöŵñ"""))
  }

  @Test
  fun `xliff placeholders and references are not translated`() {
    val translated = translateXml(
      """
      <string name="a">Hi <xliff:g id="name">Alice</xliff:g></string>
      <string name="b">@string/a</string>
      """
    )

    assertTrue(translated, translated.contains("""<xliff:g id="name">Alice</xliff:g>"""))
    assertTrue(translated, translated.contains("""<string name="b">@string/a</string>"""))
  }

  @Test
  fun `html inside cdata keeps its tags`() {
    val translated = translateXml("""<string name="a"><![CDATA[Read the <a href="https://signal.org">terms</a>]]></string>""")

    assertTrue(translated, translated.contains("""<a href="https://signal.org">ţéŕɱšţ"""))
    assertTrue(translated, translated.contains("</a>"))
  }

  private fun translateXml(strings: String): String {
    val file = temporaryFolder.newFile("strings.xml")
    file.writeText("""<?xml version="1.0" encoding="utf-8"?><resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">$strings</resources>""")
    return PseudoLanguage.EXPANSIVE.translateStringsXml(file)
  }
}
