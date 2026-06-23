package org.signal.fastlint.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.fastlint.ids
import org.signal.fastlint.lintJava
import org.signal.fastlint.lintKotlin

class AlertDialogRuleTest {

  @Test
  fun `kotlin appcompat AlertDialog Builder is flagged`() {
    val findings = lintKotlin(
      """
      import android.content.Context
      import androidx.appcompat.app.AlertDialog
      class A { fun f(c: Context) { AlertDialog.Builder(c) } }
      """.trimIndent()
    )
    assertEquals(listOf("AlertDialogBuilderUsage"), findings.ids())
  }

  @Test
  fun `java appcompat AlertDialog Builder is flagged`() {
    val findings = lintJava(
      """
      import android.content.Context;
      import androidx.appcompat.app.AlertDialog;
      class A { void f(Context c) { new AlertDialog.Builder(c); } }
      """.trimIndent()
    )
    assertEquals(listOf("AlertDialogBuilderUsage"), findings.ids())
  }

  @Test
  fun `MaterialAlertDialogBuilder is not flagged`() {
    val findings = lintKotlin(
      """
      import android.content.Context
      import com.google.android.material.dialog.MaterialAlertDialogBuilder
      class A { fun f(c: Context) { MaterialAlertDialogBuilder(c) } }
      """.trimIndent()
    )
    assertTrue(findings.isEmpty())
  }
}
