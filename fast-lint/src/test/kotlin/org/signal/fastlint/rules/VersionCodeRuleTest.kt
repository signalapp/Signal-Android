package org.signal.fastlint.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.fastlint.ids
import org.signal.fastlint.lintJava
import org.signal.fastlint.lintKotlin

class VersionCodeRuleTest {

  @Test
  fun `kotlin VERSION_CODES reference is flagged`() {
    val findings = lintKotlin(
      """
      import android.os.Build
      class A { fun f() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O }
      """.trimIndent()
    )
    assertEquals(listOf("VersionCodeUsage"), findings.ids())
  }

  @Test
  fun `java VERSION_CODES reference is flagged`() {
    val findings = lintJava(
      """
      import android.os.Build;
      class A { boolean f() { return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O; } }
      """.trimIndent()
    )
    assertEquals(listOf("VersionCodeUsage"), findings.ids())
  }

  @Test
  fun `VERSION_CODES inside a string literal is not flagged`() {
    val findings = lintKotlin("""class A { val s = "see VERSION_CODES.O for details" }""")
    assertTrue(findings.isEmpty())
  }
}
