package org.signal.lint;

import com.android.tools.lint.checks.infrastructure.TestFile;

import org.junit.Test;

import java.io.InputStream;
import java.util.Scanner;

import static com.android.tools.lint.checks.infrastructure.TestFiles.java;
import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("UnstableApiUsage")
public final class LogDetectorTest {

  private static final TestFile serviceLogStub = java(readResourceAsString("ServiceLogStub.java"));
  private static final TestFile appLogStub     = java(readResourceAsString("AppLogStub.java"));

  @Test
  public void androidLogUsed_LogNotSignal_2_args() {
    lint()
      .files(
        java("package foo;\n" +
               "import android.util.Log;\n" +
               "public class Example {\n" +
               "  public void log() {\n" +
               "    Log.d(\"TAG\", \"msg\");\n" +
               "  }\n" +
               "}")
      )
      .issues(SignalLogDetector.LOG_NOT_SIGNAL)
      .run()
      .expect("src/foo/Example.java:5: Error: Using 'android.util.Log' instead of a Signal Logger [LogNotSignal]\n" +
                "    Log.d(\"TAG\", \"msg\");\n" +
                "    ~~~~~~~~~~~~~~~~~~~\n" +
                "1 errors, 0 warnings")
      .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with org.signal.core.util.logging.Log.d(\"TAG\", \"msg\"):\n" +
                        "@@ -5 +5\n" +
                        "-     Log.d(\"TAG\", \"msg\");\n" +
                        "+     org.signal.core.util.logging.Log.d(\"TAG\", \"msg\");");
  }

  @Test
  public void androidLogUsed_LogNotSignal_3_args() {
    lint()
      .files(
        java("package foo;\n" +
               "import android.util.Log;\n" +
               "public class Example {\n" +
               "  public void log() {\n" +
               "    Log.w(\"TAG\", \"msg\", new Exception());\n" +
               "  }\n" +
               "}")
      )
      .issues(SignalLogDetector.LOG_NOT_SIGNAL)
      .run()
      .expect("src/foo/Example.java:5: Error: Using 'android.util.Log' instead of a Signal Logger [LogNotSignal]\n" +
                "    Log.w(\"TAG\", \"msg\", new Exception());\n" +
                "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "1 errors, 0 warnings")
      .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with org.signal.core.util.logging.Log.w(\"TAG\", \"msg\", new Exception()):\n" +
                        "@@ -5 +5\n" +
                        "-     Log.w(\"TAG\", \"msg\", new Exception());\n" +
                        "+     org.signal.core.util.logging.Log.w(\"TAG\", \"msg\", new Exception());");
  }

  @Test
  public void signalServiceLogUsed_LogNotApp_2_args() {
    lint()
      .files(serviceLogStub,
        java("package foo;\n" +
               "import org.whispersystems.libsignal.logging.Log;\n" +
               "public class Example {\n" +
               "  public void log() {\n" +
               "    Log.d(\"TAG\", \"msg\");\n" +
               "  }\n" +
               "}")
      )
      .issues(SignalLogDetector.LOG_NOT_APP)
      .run()
      .expect("src/foo/Example.java:5: Error: Using Signal server logger instead of app level Logger [LogNotAppSignal]\n" +
                "    Log.d(\"TAG\", \"msg\");\n" +
                "    ~~~~~~~~~~~~~~~~~~~\n" +
                "1 errors, 0 warnings")
      .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with org.signal.core.util.logging.Log.d(\"TAG\", \"msg\"):\n" +
                        "@@ -5 +5\n" +
                        "-     Log.d(\"TAG\", \"msg\");\n" +
                        "+     org.signal.core.util.logging.Log.d(\"TAG\", \"msg\");");
  }

  @Test
  public void signalServiceLogUsed_LogNotApp_3_args() {
    lint()
      .files(serviceLogStub,
        java("package foo;\n" +
               "import org.whispersystems.libsignal.logging.Log;\n" +
               "public class Example {\n" +
               "  public void log() {\n" +
               "    Log.w(\"TAG\", \"msg\", new Exception());\n" +
               "  }\n" +
               "}")
      )
      .issues(SignalLogDetector.LOG_NOT_APP)
      .run()
      .expect("src/foo/Example.java:5: Error: Using Signal server logger instead of app level Logger [LogNotAppSignal]\n" +
                "    Log.w(\"TAG\", \"msg\", new Exception());\n" +
                "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "1 errors, 0 warnings")
      .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with org.signal.core.util.logging.Log.w(\"TAG\", \"msg\", new Exception()):\n" +
                        "@@ -5 +5\n" +
                        "-     Log.w(\"TAG\", \"msg\", new Exception());\n" +
                        "+     org.signal.core.util.logging.Log.w(\"TAG\", \"msg\", new Exception());");
  }

  @Test
  public void log_uses_tag_constant() {
    lint()
      .files(appLogStub,
        java("package foo;\n" +
               "import org.signal.core.util.logging.Log;\n" +
               "public class Example {\n" +
               "  private static final String TAG = Log.tag(Example.class);\n" +
               "  public void log() {\n" +
               "    Log.d(TAG, \"msg\");\n" +
               "  }\n" +
               "}")
      )
      .issues(SignalLogDetector.INLINE_TAG)
      .run()
      .expectClean();
  }

  @Test
  public void log_uses_inline_tag() {
    lint()
      .files(appLogStub,
        java("package foo;\n" +
               "import org.signal.core.util.logging.Log;\n" +
               "public class Example {\n" +
               "  public void log() {\n" +
               "    Log.d(\"TAG\", \"msg\");\n" +
               "  }\n" +
               "}")
      )
      .issues(SignalLogDetector.INLINE_TAG)
      .run()
      .expect("src/foo/Example.java:5: Error: Not using a tag constant [LogTagInlined]\n" +
                "    Log.d(\"TAG\", \"msg\");\n" +
                "    ~~~~~~~~~~~~~~~~~~~\n" +
                "1 errors, 0 warnings")
      .expectFixDiffs("");
  }

  private static String readResourceAsString(String resourceName) {
    InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName);
    assertNotNull(inputStream);
    Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
    assertTrue(scanner.hasNext());
    return scanner.next();
  }
}
