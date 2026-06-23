package org.signal.fastlint.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.fastlint.ids
import org.signal.fastlint.lintValues

class StringResourceEscapingRuleTest {

  @Test
  fun `unescaped apostrophe is flagged`() {
    val findings = lintValues("""<resources><string name="x">Don't</string></resources>""")
    assertEquals(listOf("StringResourceEscaping"), findings.ids())
  }

  @Test
  fun `escaped apostrophe is not flagged`() {
    val findings = lintValues("""<resources><string name="x">Don\'t</string></resources>""")
    assertTrue(findings.isEmpty())
  }

  @Test
  fun `apostrophe inside a double-quoted span is not flagged`() {
    val findings = lintValues("""<resources><string name="x">"Don't"</string></resources>""")
    assertTrue(findings.isEmpty())
  }

  @Test
  fun `unbalanced double quote is flagged`() {
    val findings = lintValues("""<resources><string name="x">5" tall</string></resources>""")
    assertEquals(listOf("StringResourceEscaping"), findings.ids())
  }

  @Test
  fun `escaped double quote is not flagged`() {
    val findings = lintValues("""<resources><string name="x">5\" tall</string></resources>""")
    assertTrue(findings.isEmpty())
  }

  @Test
  fun `balanced double quotes are not flagged`() {
    val findings = lintValues("""<resources><string name="x">"hello world"</string></resources>""")
    assertTrue(findings.isEmpty())
  }

  @Test
  fun `value starting with a literal at-sign is flagged`() {
    val findings = lintValues("""<resources><string name="x">@everyone</string></resources>""")
    assertEquals(listOf("StringResourceEscaping"), findings.ids())
  }

  @Test
  fun `escaped leading at-sign is not flagged`() {
    val findings = lintValues("""<resources><string name="x">\@everyone</string></resources>""")
    assertTrue(findings.isEmpty())
  }

  @Test
  fun `a resource reference value is not flagged`() {
    val findings = lintValues("""<resources><string name="x">@string/foo</string></resources>""")
    assertTrue(findings.isEmpty())
  }

  @Test
  fun `at-null is not flagged`() {
    val findings = lintValues("""<resources><string name="x">@null</string></resources>""")
    assertTrue(findings.isEmpty())
  }

  @Test
  fun `value starting with a literal question mark is flagged`() {
    val findings = lintValues("""<resources><string name="x">???</string></resources>""")
    assertEquals(listOf("StringResourceEscaping"), findings.ids())
  }

  @Test
  fun `a theme attribute reference is not flagged`() {
    val findings = lintValues("""<resources><string name="x">?attr/colorPrimary</string></resources>""")
    assertTrue(findings.isEmpty())
  }

  @Test
  fun `tools ignore suppresses the rule`() {
    val findings = lintValues(
      """<resources xmlns:tools="http://schemas.android.com/tools"><string name="x" tools:ignore="StringResourceEscaping">Don't</string></resources>"""
    )
    assertTrue(findings.isEmpty())
  }
}
