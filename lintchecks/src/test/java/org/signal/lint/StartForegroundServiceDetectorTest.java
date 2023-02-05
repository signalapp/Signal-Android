package org.signal.lint;

import com.android.tools.lint.checks.infrastructure.TestFile;

import org.junit.Test;

import java.io.InputStream;
import java.util.Scanner;

import static com.android.tools.lint.checks.infrastructure.TestFiles.java;
import static com.android.tools.lint.checks.infrastructure.TestFiles.kotlin;
import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("UnstableApiUsage")
public final class StartForegroundServiceDetectorTest {

  private static final TestFile contextCompatStub = java(readResourceAsString("ContextCompatStub.java"));
  private static final TestFile contextStub       = java(readResourceAsString("ContextStub.java"));

  @Test
  public void contextCompatUsed() {
    lint()
      .files(
        contextCompatStub,
        java("package foo;\n" +
               "import androidx.core.content.ContextCompat;\n" +
               "public class Example {\n" +
               "  public void start() {\n" +
               "    ContextCompat.startForegroundService(context, new Intent());\n" +
               "  }\n" +
               "}")
      )
      .allowMissingSdk()
      .issues(StartForegroundServiceDetector.START_FOREGROUND_SERVICE_ISSUE)
      .run()
      .expect("src/foo/Example.java:5: Error: Using 'ContextCompat.startForegroundService' instead of a ForegroundServiceUtil [StartForegroundServiceUsage]\n" +
                "    ContextCompat.startForegroundService(context, new Intent());\n" +
                "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "1 errors, 0 warnings");
  }

  @Test
  public void contextUsed() {
    lint()
        .files(
            contextStub,
            java("package foo;\n" +
                 "import android.content.Context;\n" +
                 "public class Example {\n" +
                 "  Context context;\n" +
                 "  public void start() {\n" +
                 "    context.startForegroundService(new Intent());\n" +
                 "  }\n" +
                 "}")
        )
        .allowMissingSdk()
        .issues(StartForegroundServiceDetector.START_FOREGROUND_SERVICE_ISSUE)
        .run()
        .expect("src/foo/Example.java:6: Error: Using 'Context.startForegroundService' instead of a ForegroundServiceUtil [StartForegroundServiceUsage]\n" +
                "    context.startForegroundService(new Intent());\n" +
                "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "1 errors, 0 warnings");
  }


  private static String readResourceAsString(String resourceName) {
    InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName);
    assertNotNull(inputStream);
    Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
    assertTrue(scanner.hasNext());
    return scanner.next();
  }
}
