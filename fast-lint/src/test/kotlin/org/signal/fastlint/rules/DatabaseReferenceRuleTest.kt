package org.signal.fastlint.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.fastlint.ids
import org.signal.fastlint.lintJava
import org.signal.fastlint.lintKotlin

class DatabaseReferenceRuleTest {

  @Test
  fun `java database with recipient column is flagged`() {
    val findings = lintJava(
      """
      class MyTable extends Database {
        private static final String RECIPIENT_ID = "recipient_id";
      }
      """.trimIndent()
    )
    assertEquals(listOf("RecipientIdDatabaseReferenceUsage"), findings.ids())
  }

  @Test
  fun `java database with thread column is flagged`() {
    val findings = lintJava(
      """
      class MyTable extends Database {
        private static final String THREAD_ID = "thread_id";
      }
      """.trimIndent()
    )
    assertEquals(listOf("ThreadIdDatabaseReferenceUsage"), findings.ids())
  }

  @Test
  fun `database implementing the interface is not flagged`() {
    val findings = lintJava(
      """
      class MyTable extends Database implements RecipientIdDatabaseReference {
        private static final String RECIPIENT_ID = "recipient_id";
      }
      """.trimIndent()
    )
    assertTrue(findings.isEmpty())
  }

  @Test
  fun `non-database class is not flagged`() {
    val findings = lintJava(
      """
      class Foo {
        private static final String RECIPIENT_ID = "recipient_id";
      }
      """.trimIndent()
    )
    assertTrue(findings.isEmpty())
  }

  @Test
  fun `kotlin database with recipient column is flagged`() {
    val findings = lintKotlin(
      """
      class MyTable : Database() {
        val recipientId: String = ""
      }
      """.trimIndent()
    )
    assertEquals(listOf("RecipientIdDatabaseReferenceUsage"), findings.ids())
  }
}
