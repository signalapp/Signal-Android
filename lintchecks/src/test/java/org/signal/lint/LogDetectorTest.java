package org.signal.lint;

import org.junit.Test;

import static com.android.tools.lint.checks.infrastructure.TestFiles.java;
import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;

public final class LogDetectorTest {

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
      .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with org.thoughtcrime.securesms.logging.Log.d(\"TAG\", \"msg\"):\n" +
                        "@@ -5 +5\n" +
                        "-     Log.d(\"TAG\", \"msg\");\n" +
                        "+     org.thoughtcrime.securesms.logging.Log.d(\"TAG\", \"msg\");");
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
      .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with org.thoughtcrime.securesms.logging.Log.w(\"TAG\", \"msg\", new Exception()):\n" +
                        "@@ -5 +5\n" +
                        "-     Log.w(\"TAG\", \"msg\", new Exception());\n" +
                        "+     org.thoughtcrime.securesms.logging.Log.w(\"TAG\", \"msg\", new Exception());");
  }
}
