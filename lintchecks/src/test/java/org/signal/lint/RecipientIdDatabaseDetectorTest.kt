package org.signal.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Scanner

class RecipientIdDatabaseDetectorTest {
  @Test
  fun recipientIdDatabase_databaseHasRecipientFieldButDoesNotImplementInterface_showError() {
    TestLintTask.lint()
      .files(
        java(
          """
          package foo;
          public class Example extends Database {
            private static final String RECIPIENT_ID = "recipient_id";
          }
          """.trimIndent()
        )
      )
      .issues(RecipientIdDatabaseDetector.RECIPIENT_ID_DATABASE_REFERENCE_ISSUE)
      .run()
      .expect(
        """
          src/foo/Example.java:3: Error: If you reference a RecipientId in your table, you must implement the RecipientIdDatabaseReference interface. [RecipientIdDatabaseReferenceUsage]
            private static final String RECIPIENT_ID = "recipient_id";
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          1 errors, 0 warnings
          """.trimIndent()
      )
  }

  @Test
  fun recipientIdDatabase_databaseHasRecipientFieldAndImplementsInterface_noError() {
    TestLintTask.lint()
      .files(
        recipientReferenceStub,
        java(
          """
          package foo;
          import org.thoughtcrime.securesms.database.RecipientIdDatabaseReference;
          public class Example extends Database implements RecipientIdDatabaseReference {
            private static final String RECIPIENT_ID = "recipient_id";
            @Override
            public void remapRecipient(RecipientId fromId, RecipientId toId) {}
          }
          """.trimIndent()
        )
      )
      .issues(RecipientIdDatabaseDetector.RECIPIENT_ID_DATABASE_REFERENCE_ISSUE)
      .run()
      .expectClean()
  }

  companion object {
    private val recipientReferenceStub = kotlin(readResourceAsString("RecipientIdDatabaseReferenceStub.kt"))

    private fun readResourceAsString(@Suppress("SameParameterValue") resourceName: String): String {
      val inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName)
      assertNotNull(inputStream)
      val scanner = Scanner(inputStream!!).useDelimiter("\\A")
      assertTrue(scanner.hasNext())
      return scanner.next()
    }
  }
}
