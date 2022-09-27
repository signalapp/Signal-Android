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
public final class ThreadIdDatabaseDetectorTest {

  private static final TestFile threadReferenceStub = java(readResourceAsString("ThreadIdDatabaseReferenceStub.java"));

  @Test
  public void threadIdDatabase_databaseHasThreadFieldButDoesNotImplementInterface_showError() {
    lint()
        .files(
            java("package foo;\n" +
                 "public class Example extends Database {\n" +
                 "  private static final String THREAD_ID = \"thread_id\";\n" +
                 "}")
        )
        .issues(ThreadIdDatabaseDetector.THREAD_ID_DATABASE_REFERENCE_ISSUE)
        .run()
        .expect("src/foo/Example.java:3: Error: If you reference a thread ID in your table, you must implement the ThreadIdDatabaseReference interface. [ThreadIdDatabaseReferenceUsage]\n" +
                "  private static final String THREAD_ID = \"thread_id\";\n" +
                "  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "1 errors, 0 warnings");
  }

  @Test
  public void threadIdDatabase_databaseHasThreadFieldAndImplementsInterface_noError() {
    lint()
        .files(
            threadReferenceStub,
            java("package foo;\n" +
                 "import org.thoughtcrime.securesms.database.ThreadIdDatabaseReference;\n" +
                 "public class Example extends Database implements ThreadIdDatabaseReference {\n" +
                 "  private static final String THREAD_ID = \"thread_id\";\n" +
                 "  @Override\n" +
                 "  public void remapThread(long fromId, long toId) {}\n" +
                 "}")
        )
        .issues(ThreadIdDatabaseDetector.THREAD_ID_DATABASE_REFERENCE_ISSUE)
        .run()
        .expectClean();
  }

  private static String readResourceAsString(String resourceName) {
    InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName);
    assertNotNull(inputStream);
    Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
    assertTrue(scanner.hasNext());
    return scanner.next();
  }
}
