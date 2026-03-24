package org.signal.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Scanner

class SystemOutPrintLnDetectorTest {

  @Test
  fun systemOutPrintlnUsed_Java() {
    TestLintTask.lint()
      .allowMissingSdk()
      .files(
        java(
          """
          package foo;
          public class Example {
            public void log() {
              System.out.println("Hello World");
            }
          }
          """.trimIndent()
        )
      )
      .issues(SystemOutPrintLnDetector.SYSTEM_OUT_PRINTLN_USAGE)
      .run()
      .expect(
        """
        src/foo/Example.java:4: Error: Using 'System.out.println' instead of Signal Logger [SystemOutPrintLnUsage]
            System.out.println("Hello World");
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 4: Replace with org.signal.core.util.logging.Log.d(TAG, "Hello World"):
        @@ -4 +4
        -     System.out.println("Hello World");
        +     org.signal.core.util.logging.Log.d(TAG, "Hello World");
        """.trimIndent()
      )
  }

  @Test
  fun systemOutPrintUsed_Java() {
    TestLintTask.lint()
      .allowMissingSdk()
      .files(
        java(
          """
          package foo;
          public class Example {
            public void log() {
              System.out.print("Hello");
            }
          }
          """.trimIndent()
        )
      )
      .issues(SystemOutPrintLnDetector.SYSTEM_OUT_PRINTLN_USAGE)
      .run()
      .expect(
        """
        src/foo/Example.java:4: Error: Using 'System.out.print' instead of Signal Logger [SystemOutPrintLnUsage]
            System.out.print("Hello");
            ~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 4: Replace with org.signal.core.util.logging.Log.d(TAG, "Hello"):
        @@ -4 +4
        -     System.out.print("Hello");
        +     org.signal.core.util.logging.Log.d(TAG, "Hello");
        """.trimIndent()
      )
  }

  @Test
  fun kotlinIOPrintlnUsed_Kotlin() {
    TestLintTask.lint()
      .allowMissingSdk()
      .files(
        kotlinIOStub,
        kotlin(
          """
          package foo
          import kotlin.io.println
          class Example {
            fun log() {
              println("Hello World")
            }
          }
          """.trimIndent()
        )
      )
      .issues(SystemOutPrintLnDetector.KOTLIN_IO_PRINTLN_USAGE)
      .run()
      .expect(
        """
        src/foo/Example.kt:5: Error: Using 'kotlin.io.println' instead of Signal Logger. [KotlinIOPrintLnUsage]
            println("Hello World")
            ~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.kt line 5: Replace with org.signal.core.util.logging.Log.d(TAG, "Hello World"):
        @@ -5 +5
        -     println("Hello World")
        +     org.signal.core.util.logging.Log.d(TAG, "Hello World")
        """.trimIndent()
      )
  }

  @Test
  fun kotlinIOPrintlnUsed_TopLevel_Kotlin() {
    TestLintTask.lint()
      .allowMissingSdk()
      .files(
        kotlinIOStub,
        kotlin(
          """
          package foo
          fun example() {
            println("Hello World")
          }
          """.trimIndent()
        )
      )
      .issues(SystemOutPrintLnDetector.KOTLIN_IO_PRINTLN_USAGE)
      .run()
      .expect(
        """
        src/foo/test.kt:3: Error: Using 'kotlin.io.println' instead of Signal Logger. [KotlinIOPrintLnUsage]
          println("Hello World")
          ~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/test.kt line 3: Replace with org.signal.core.util.logging.Log.d(TAG, "Hello World"):
        @@ -3 +3
        -   println("Hello World")
        +   org.signal.core.util.logging.Log.d(TAG, "Hello World")
        """.trimIndent()
      )
  }

  @Test
  fun systemOutPrintlnWithNoArgs_Java() {
    TestLintTask.lint()
      .allowMissingSdk()
      .files(
        java(
          """
          package foo;
          public class Example {
            public void log() {
              System.out.println();
            }
          }
          """.trimIndent()
        )
      )
      .issues(SystemOutPrintLnDetector.SYSTEM_OUT_PRINTLN_USAGE)
      .run()
      .expect(
        """
        src/foo/Example.java:4: Error: Using 'System.out.println' instead of Signal Logger [SystemOutPrintLnUsage]
            System.out.println();
            ~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 4: Replace with org.signal.core.util.logging.Log.d(TAG, ""):
        @@ -4 +4
        -     System.out.println();
        +     org.signal.core.util.logging.Log.d(TAG, "");
        """.trimIndent()
      )
  }

  @Test
  fun regularPrintStreamMethodsNotFlagged() {
    TestLintTask.lint()
      .allowMissingSdk()
      .files(
        java(
          """
          package foo;
          import java.io.PrintStream;
          import java.io.ByteArrayOutputStream;
          public class Example {
            public void log() {
              PrintStream ps = new PrintStream(new ByteArrayOutputStream());
              ps.println("This should not be flagged");
            }
          }
          """.trimIndent()
        )
      )
      .issues(
        SystemOutPrintLnDetector.SYSTEM_OUT_PRINTLN_USAGE,
        SystemOutPrintLnDetector.KOTLIN_IO_PRINTLN_USAGE
      )
      .run()
      .expectClean()
  }

  companion object {
    private val kotlinIOStub = kotlin(readResourceAsString("KotlinIOStub.kt"))

    private fun readResourceAsString(resourceName: String): String {
      val inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName)
      assertNotNull(inputStream)
      val scanner = Scanner(inputStream!!).useDelimiter("\\A")
      assertTrue(scanner.hasNext())
      return scanner.next()
    }
  }
}