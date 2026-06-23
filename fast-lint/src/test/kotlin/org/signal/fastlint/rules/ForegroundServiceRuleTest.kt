package org.signal.fastlint.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.fastlint.ids
import org.signal.fastlint.lintJava
import org.signal.fastlint.lintKotlin

class ForegroundServiceRuleTest {

  @Test
  fun `context instance call is flagged`() {
    val findings = lintKotlin(
      """
      import android.content.Context
      class A { fun f(context: Context) { context.startForegroundService(null) } }
      """.trimIndent()
    )
    assertEquals(listOf("StartForegroundServiceUsage"), findings.ids())
  }

  @Test
  fun `java ContextCompat call is flagged`() {
    val findings = lintJava(
      """
      import androidx.core.content.ContextCompat;
      class A { void f(android.content.Context c, android.content.Intent i) { ContextCompat.startForegroundService(c, i); } }
      """.trimIndent()
    )
    assertEquals(listOf("StartForegroundServiceUsage"), findings.ids())
  }

  @Test
  fun `static wrapper call on a class is not flagged`() {
    val findings = lintKotlin("""class A { fun f() { FcmFetchManager.startForegroundService(null) } }""")
    assertTrue(findings.isEmpty())
  }

  @Test
  fun `call inside ForegroundServiceUtil is not flagged`() {
    val findings = lintKotlin(
      """
      import android.content.Context
      class A { fun f(context: Context) { context.startForegroundService(null) } }
      """.trimIndent(),
      fileName = "ForegroundServiceUtil.kt"
    )
    assertTrue(findings.isEmpty())
  }
}
