package org.signal.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Scanner

class StartForegroundServiceDetectorTest {
  @Test
  fun contextCompatUsed() {
    TestLintTask.lint()
      .files(
        contextCompatStub,
        java(
          """
          package foo;
          import androidx.core.content.ContextCompat;
          public class Example {
            public void start() {
              ContextCompat.startForegroundService(context, new Intent());
            }
          }
          """.trimIndent()
        )
      )
      .allowMissingSdk()
      .issues(StartForegroundServiceDetector.START_FOREGROUND_SERVICE_ISSUE)
      .run()
      .expect(
        """
        src/foo/Example.java:5: Error: Using 'ContextCompat.startForegroundService' instead of a ForegroundServiceUtil [StartForegroundServiceUsage]
            ContextCompat.startForegroundService(context, new Intent());
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
  }

  @Test
  fun contextUsed() {
    TestLintTask.lint()
      .files(
        contextStub,
        java(
          """
          package foo;
          import android.content.Context;
          public class Example {
            Context context;
            public void start() {
              context.startForegroundService(new Intent());
            }
          }
          """.trimIndent()
        )
      )
      .allowMissingSdk()
      .issues(StartForegroundServiceDetector.START_FOREGROUND_SERVICE_ISSUE)
      .run()
      .expect(
        """
        src/foo/Example.java:6: Error: Using 'Context.startForegroundService' instead of a ForegroundServiceUtil [StartForegroundServiceUsage]
            context.startForegroundService(new Intent());
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """.trimIndent()
      )
  }


  companion object {
    private val contextCompatStub = kotlin(readResourceAsString("ContextCompatStub.kt"))
    private val contextStub = kotlin(readResourceAsString("ContextStub.kt"))

    private fun readResourceAsString(resourceName: String): String {
      val inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName)
      assertNotNull(inputStream)
      val scanner = Scanner(inputStream!!).useDelimiter("\\A")
      assertTrue(scanner.hasNext())
      return scanner.next()
    }
  }
}
