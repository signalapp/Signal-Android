package org.signal.fastlint.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.fastlint.ids
import org.signal.fastlint.lintJava
import org.signal.fastlint.lintKotlin

class LogTagInlinedRuleTest {

  @Test
  fun `app logger with inline string tag is flagged`() {
    val findings = lintKotlin(
      """
      import org.signal.core.util.logging.Log
      class A { fun f() { Log.d("literal", "m") } }
      """.trimIndent()
    )
    assertEquals(listOf("LogTagInlined"), findings.ids())
  }

  @Test
  fun `java app logger with inline string tag is flagged`() {
    val findings = lintJava(
      """
      import org.signal.core.util.logging.Log;
      class A { void f() { Log.d("literal", "m"); } }
      """.trimIndent()
    )
    assertEquals(listOf("LogTagInlined"), findings.ids())
  }

  @Test
  fun `app logger with constant tag is not flagged`() {
    val findings = lintKotlin(
      """
      import org.signal.core.util.logging.Log
      class A {
        val tag = "A"
        fun f() { Log.d(tag, "m") }
      }
      """.trimIndent()
    )
    assertTrue(findings.isEmpty())
  }

  @Test
  fun `non-signal logger with inline tag is not flagged as LogTagInlined`() {
    val findings = lintKotlin(
      """
      import android.util.Log
      class A { fun f() { Log.d("literal", "m") } }
      """.trimIndent()
    )
    assertEquals(listOf("LogNotSignal"), findings.ids())
  }

  @Test
  fun `SuppressLint silences the rule`() {
    val findings = lintKotlin(
      """
      import android.annotation.SuppressLint
      import org.signal.core.util.logging.Log
      @SuppressLint("LogTagInlined")
      class A { fun f() { Log.d("literal", "m") } }
      """.trimIndent()
    )
    assertTrue(findings.isEmpty())
  }
}
