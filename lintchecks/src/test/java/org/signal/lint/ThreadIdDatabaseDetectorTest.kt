package org.signal.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Scanner

class ThreadIdDatabaseDetectorTest {
  @Test
  fun threadIdDatabase_databaseHasThreadFieldButDoesNotImplementInterface_showError() {
    TestLintTask.lint()
      .files(
        java(
          """
          package foo;
          public class Example extends Database {
            private static final String THREAD_ID = "thread_id";
          }
          """.trimIndent()
        )
      )
      .issues(ThreadIdDatabaseDetector.THREAD_ID_DATABASE_REFERENCE_ISSUE)
      .run()
      .expect(
        """
        src/foo/Example.java:3: Error: If you reference a thread ID in your table, you must implement the ThreadIdDatabaseReference interface. [ThreadIdDatabaseReferenceUsage]
          private static final String THREAD_ID = "thread_id";
          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
  }

  @Test
  fun threadIdDatabase_databaseHasThreadFieldAndImplementsInterface_noError() {
    TestLintTask.lint()
      .files(
        threadReferenceStub,
        java(
          """
          package foo;
          import org.thoughtcrime.securesms.database.ThreadIdDatabaseReference;
          public class Example extends Database implements ThreadIdDatabaseReference {
            private static final String THREAD_ID = "thread_id";
            @Override
            public void remapThread(long fromId, long toId) {}
          }
          """.trimIndent()
        )
      )
      .issues(ThreadIdDatabaseDetector.THREAD_ID_DATABASE_REFERENCE_ISSUE)
      .run()
      .expectClean()
  }

  companion object {
    private val threadReferenceStub = kotlin(readResourceAsString("ThreadIdDatabaseReferenceStub.kt"))

    private fun readResourceAsString(@Suppress("SameParameterValue") resourceName: String): String {
      val inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName)
      assertNotNull(inputStream)
      val scanner = Scanner(inputStream!!).useDelimiter("\\A")
      assertTrue(scanner.hasNext())
      return scanner.next()
    }
  }
}
