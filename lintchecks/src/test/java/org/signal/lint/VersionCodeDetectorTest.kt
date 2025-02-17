package org.signal.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Scanner

class VersionCodeDetectorTest {
  @Test
  fun version_code_constant_referenced_in_code() {
    TestLintTask.lint()
      .files(
        java(
          """
          package foo;
          import android.os.Build;
          public class Example {
            public void versionCodeMention() {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                continue;
              }
            }
          }
          """.trimIndent()
        )
      )
      .issues(VersionCodeDetector.VERSION_CODE_USAGE)
      .run()
      .expect(
        """
        src/foo/Example.java:5: Warning: Using 'VERSION_CODES' reference instead of the numeric value 21 [VersionCodeUsage]
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with 21:
        @@ -5 +5
        -     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        +     if (Build.VERSION.SDK_INT >= 21) {
        """.trimIndent()
      )
  }

  @Test
  fun numeric_value_referenced_in_code() {
    TestLintTask.lint()
      .files(
        java(
        """
        package foo;
        import android.os.Build;
        public class Example {
          public void versionCodeMention() {
            if (Build.VERSION.SDK_INT >= 22) {
              continue;
            }
          }
        }
        """.trimIndent()
        )
      )
      .issues(VersionCodeDetector.VERSION_CODE_USAGE)
      .run()
      .expectClean()
  }

  @Test
  fun non_version_code_constant_referenced_in_code() {
    TestLintTask.lint()
      .files(
        java(
          """
          package foo;
          import android.os.Build;
          public class Example {
            private final static int LOLLIPOP = 21;
            public void versionCodeMention() {
              if (Build.VERSION.SDK_INT >= LOLLIPOP) {
                continue;
              }
            }
          }
          """.trimIndent()
        )
      )
      .issues(VersionCodeDetector.VERSION_CODE_USAGE)
      .run()
      .expectClean()
  }

  @Test
  fun version_code_constant_referenced_in_TargetApi_attribute_and_inner_class_import() {
    TestLintTask.lint()
      .files(
        java(
          """
          package foo;
          import android.os.Build.VERSION_CODES;
          import android.annotation.TargetApi;
          public class Example {
            @TargetApi(VERSION_CODES.N)
            public void versionCodeMention() {
            }
          }
          """.trimIndent()
        )
      )
      .issues(VersionCodeDetector.VERSION_CODE_USAGE)
      .run()
      .expect(
        """
            src/foo/Example.java:5: Warning: Using 'VERSION_CODES' reference instead of the numeric value 24 [VersionCodeUsage]
              @TargetApi(VERSION_CODES.N)
                         ~~~~~~~~~~~~~~~
            0 errors, 1 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with 24:
        @@ -5 +5
        -   @TargetApi(VERSION_CODES.N)
        +   @TargetApi(24)
        """.trimIndent()
      )
  }

  @Test
  fun version_code_constant_referenced_in_RequiresApi_attribute_with_named_parameter() {
    TestLintTask.lint()
      .files(
        requiresApiStub,
        java(
          """
          package foo;
          import android.os.Build;
          import android.annotation.RequiresApi;
          public class Example {
            @RequiresApi(app = Build.VERSION_CODES.M)
            public void versionCodeMention() {
            }
          }
          """.trimIndent()
        )
      )
      .issues(VersionCodeDetector.VERSION_CODE_USAGE)
      .run()
      .expect(
        """
        src/foo/Example.java:5: Warning: Using 'VERSION_CODES' reference instead of the numeric value 23 [VersionCodeUsage]
          @RequiresApi(app = Build.VERSION_CODES.M)
                             ~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with 23:
        @@ -5 +5
        -   @RequiresApi(app = Build.VERSION_CODES.M)
        +   @RequiresApi(app = 23)
        """.trimIndent()
      )
  }

  companion object {
    private val requiresApiStub = kotlin(readResourceAsString("RequiresApiStub.kt"))

    private fun readResourceAsString(@Suppress("SameParameterValue") resourceName: String): String {
      val inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName)
      assertNotNull(inputStream)
      val scanner = Scanner(inputStream!!).useDelimiter("\\A")
      assertTrue(scanner.hasNext())
      return scanner.next()
    }
  }
}
