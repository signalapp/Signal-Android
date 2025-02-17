package org.signal.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Scanner

class SignalLogDetectorTest {
  @Test
  fun androidLogUsed_LogNotSignal_2_args() {
    TestLintTask.lint()
      .files(
        java(
          """
          package foo;
          import android.util.Log;
          public class Example {
            public void log() {
              Log.d("TAG", "msg");
            }
          }
          """.trimIndent()
        )
      )
      .issues(SignalLogDetector.LOG_NOT_SIGNAL)
      .run()
      .expect(
        """
        src/foo/Example.java:5: Error: Using 'android.util.Log' instead of a Signal Logger [LogNotSignal]
            Log.d("TAG", "msg");
            ~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with org.signal.core.util.logging.Log.d("TAG", "msg"):
        @@ -5 +5
        -     Log.d("TAG", "msg");
        +     org.signal.core.util.logging.Log.d("TAG", "msg");
        """.trimIndent()
      )
  }

  @Test
  fun androidLogUsed_LogNotSignal_3_args() {
    TestLintTask.lint()
      .files(
        java(
          """
          package foo;
          import android.util.Log;
          public class Example {
            public void log() {
              Log.w("TAG", "msg", new Exception());
            }
          }
          """.trimIndent()
        )
      )
      .issues(SignalLogDetector.LOG_NOT_SIGNAL)
      .run()
      .expect(
      """
        src/foo/Example.java:5: Error: Using 'android.util.Log' instead of a Signal Logger [LogNotSignal]
            Log.w("TAG", "msg", new Exception());
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
            Fix for src/foo/Example.java line 5: Replace with org.signal.core.util.logging.Log.w("TAG", "msg", new Exception()):
            @@ -5 +5
            -     Log.w("TAG", "msg", new Exception());
            +     org.signal.core.util.logging.Log.w("TAG", "msg", new Exception());
            """.trimIndent()
      )
  }

  @Test
  fun signalServiceLogUsed_LogNotApp_2_args() {
    TestLintTask.lint()
      .files(
        serviceLogStub,
        java(
          """
          package foo;
          import org.signal.libsignal.protocol.logging.Log;
          public class Example {
            public void log() {
              Log.d("TAG", "msg");
            }
          }
          """.trimIndent()
        )
      )
      .issues(SignalLogDetector.LOG_NOT_APP)
      .run()
      .expect(
        """
        src/foo/Example.java:5: Error: Using Signal server logger instead of app level Logger [LogNotAppSignal]
            Log.d("TAG", "msg");
            ~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with org.signal.core.util.logging.Log.d("TAG", "msg"):
        @@ -5 +5
        -     Log.d("TAG", "msg");
        +     org.signal.core.util.logging.Log.d("TAG", "msg");
        """.trimIndent()
      )
  }

  @Test
  fun signalServiceLogUsed_LogNotApp_3_args() {
    TestLintTask.lint()
      .files(
        serviceLogStub,
        java(
          """
          package foo;
          import org.signal.libsignal.protocol.logging.Log;
          public class Example {
            public void log() {
              Log.w("TAG", "msg", new Exception());
            }
          }
          """.trimIndent()
        )
      )
      .issues(SignalLogDetector.LOG_NOT_APP)
      .run()
      .expect(
        """
        src/foo/Example.java:5: Error: Using Signal server logger instead of app level Logger [LogNotAppSignal]
            Log.w("TAG", "msg", new Exception());
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with org.signal.core.util.logging.Log.w("TAG", "msg", new Exception()):
        @@ -5 +5
        -     Log.w("TAG", "msg", new Exception());
        +     org.signal.core.util.logging.Log.w("TAG", "msg", new Exception());
        """.trimIndent()
      )
  }

  @Test
  fun log_uses_tag_constant() {
    TestLintTask.lint()
      .files(
        appLogStub,
        java(
          """
          package foo;
          import org.signal.core.util.logging.Log;
          public class Example {
            private static final String TAG = Log.tag(Example.class);
            public void log() {
              Log.d(TAG, "msg");
            }
          }
          """.trimIndent()
        )
      )
      .issues(SignalLogDetector.INLINE_TAG)
      .run()
      .expectClean()
  }

  @Test
  fun log_uses_tag_constant_kotlin() {
    TestLintTask.lint()
      .files(
        appLogStub,
        kotlin(
          """
          package foo
          import org.signal.core.util.logging.Log
          class Example {
            const val TAG: String = Log.tag(Example::class.java)
            fun log() {
              Log.d(TAG, "msg")
            }
          }
          """.trimIndent()
        )
      )
      .issues(SignalLogDetector.INLINE_TAG)
      .skipTestModes(TestMode.REORDER_ARGUMENTS)
      .run()
      .expectClean()
  }

  @Test
  fun log_uses_tag_companion_kotlin() {
    TestLintTask.lint()
      .files(
        appLogStub,
        kotlin(
          """
          package foo
          import org.signal.core.util.logging.Log
          class Example {
            companion object { val TAG: String = Log.tag(Example::class.java) }
            fun log() {
              Log.d(TAG, "msg")
            }
          }
          fun logOutsie() {
            Log.d(Example.TAG, "msg")
          }
          """.trimIndent()
        )
      )
      .issues(SignalLogDetector.INLINE_TAG)
      .skipTestModes(TestMode.REORDER_ARGUMENTS)
      .run()
      .expectClean()
  }

  @Test
  fun log_uses_inline_tag() {
    TestLintTask.lint()
      .files(
        appLogStub,
        java(
          """
          package foo;
          import org.signal.core.util.logging.Log;
          public class Example {
            public void log() {
              Log.d("TAG", "msg");
            }
          }
          """.trimIndent()
        )
      )
      .issues(SignalLogDetector.INLINE_TAG)
      .run()
      .expect(
        """
        src/foo/Example.java:5: Error: Not using a tag constant [LogTagInlined]
            Log.d("TAG", "msg");
            ~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
      .expectFixDiffs("")
  }

  @Test
  fun log_uses_inline_tag_kotlin() {
    TestLintTask.lint()
      .files(
        appLogStub,
        kotlin(
          """
          package foo
          import org.signal.core.util.logging.Log
          class Example {
            fun log() {
              Log.d("TAG", "msg")
            }
          }
          """.trimIndent()
        )
      )
      .issues(SignalLogDetector.INLINE_TAG)
      .run()
      .expect(
        """
        src/foo/Example.kt:5: Error: Not using a tag constant [LogTagInlined]
            Log.d("TAG", "msg")
            ~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
      .expectFixDiffs("")
  }

  @Test
  fun glideLogUsed_LogNotSignal_2_args() {
    TestLintTask.lint()
      .files(
        glideLogStub,
        java(
          """
          package foo;
          import org.signal.glide.Log;
          public class Example {
            public void log() {
              Log.d("TAG", "msg");
            }
          }
          """.trimIndent()
        )
      )
      .issues(SignalLogDetector.LOG_NOT_SIGNAL)
      .run()
      .expect(
        """
        src/foo/Example.java:5: Error: Using 'org.signal.glide.Log' instead of a Signal Logger [LogNotSignal]
            Log.d("TAG", "msg");
            ~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with org.signal.core.util.logging.Log.d("TAG", "msg"):
        @@ -5 +5
        -     Log.d("TAG", "msg");
        +     org.signal.core.util.logging.Log.d("TAG", "msg");
        """.trimIndent()
      )
  }

  companion object {
    private val serviceLogStub = kotlin(readResourceAsString("ServiceLogStub.kt"))
    private val appLogStub = kotlin(readResourceAsString("AppLogStub.kt"))
    private val glideLogStub = kotlin(readResourceAsString("GlideLogStub.kt"))

    private fun readResourceAsString(resourceName: String): String {
      val inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName)
      assertNotNull(inputStream)
      val scanner = Scanner(inputStream!!).useDelimiter("\\A")
      assertTrue(scanner.hasNext())
      return scanner.next()
    }
  }
}
