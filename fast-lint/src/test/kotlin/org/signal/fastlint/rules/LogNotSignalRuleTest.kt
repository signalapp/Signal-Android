package org.signal.fastlint.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.fastlint.ids
import org.signal.fastlint.lintJava
import org.signal.fastlint.lintKotlin

class LogNotSignalRuleTest {

  @Test
  fun `kotlin android Log is flagged`() {
    val findings = lintKotlin(
      """
      import android.util.Log
      class A { fun f() { Log.d("tag", "m") } }
      """.trimIndent()
    )
    assertEquals(listOf("LogNotSignal"), findings.ids())
  }

  @Test
  fun `java android Log is flagged`() {
    val findings = lintJava(
      """
      import android.util.Log;
      class A { void f() { Log.d("tag", "m"); } }
      """.trimIndent()
    )
    assertEquals(listOf("LogNotSignal"), findings.ids())
  }

  @Test
  fun `libsignal server logger is flagged`() {
    val findings = lintKotlin(
      """
      import org.signal.libsignal.protocol.logging.Log
      class A { fun f() { Log.w("tag", "m") } }
      """.trimIndent()
    )
    assertEquals(listOf("LogNotSignal"), findings.ids())
  }

  @Test
  fun `an unrecognized third-party logger is also flagged`() {
    val findings = lintKotlin(
      """
      import timber.log.Timber
      class A { fun f() { Timber.e("m") } }
      """.trimIndent()
    )
    assertEquals(listOf("LogNotSignal"), findings.ids())
  }

  @Test
  fun `app logger is not flagged`() {
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
  fun `a chain off the app logger such as Log internal is not flagged`() {
    val findings = lintKotlin(
      """
      import org.signal.core.util.logging.Log
      class A {
        val tag = "A"
        fun f() { Log.internal().d(tag, "m") }
      }
      """.trimIndent()
    )
    assertFalse("LogNotSignal" in findings.ids())
  }

  @Test
  fun `SensitiveLog is not flagged`() {
    val findings = lintKotlin(
      """
      import org.signal.registration.util.SensitiveLog
      class A {
        val tag = "A"
        fun f() { SensitiveLog.d(tag, "m") }
      }
      """.trimIndent()
    )
    assertFalse("LogNotSignal" in findings.ids())
  }

  @Test
  fun `calls within the logging package are not flagged`() {
    val findings = lintKotlin(
      """
      package org.signal.core.util.logging
      class CompoundLogger {
        fun f(logger: Any) { logger.d("t", "m") }
      }
      """.trimIndent()
    )
    assertTrue(findings.isEmpty())
  }

  @Test
  fun `SensitiveLog's own delegating implementation is not flagged`() {
    val findings = lintKotlin(
      """
      package org.signal.registration.util
      class SensitiveLog(private val logger: Any) {
        fun d(tag: String, message: String) { this.logger.d(tag, message) }
      }
      """.trimIndent(),
      fileName = "SensitiveLog.kt"
    )
    assertTrue(findings.isEmpty())
  }

  @Test
  fun `SuppressLint silences the rule`() {
    val findings = lintKotlin(
      """
      import android.annotation.SuppressLint
      import android.util.Log
      @SuppressLint("LogNotSignal")
      class A { fun f() { Log.d("tag", "m") } }
      """.trimIndent()
    )
    assertTrue(findings.isEmpty())
  }
}
