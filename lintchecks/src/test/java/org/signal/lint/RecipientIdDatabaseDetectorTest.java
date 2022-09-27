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
public final class RecipientIdDatabaseDetectorTest {

  private static final TestFile recipientReferenceStub = java(readResourceAsString("RecipientIdDatabaseReferenceStub.java"));

  @Test
  public void recipientIdDatabase_databaseHasRecipientFieldButDoesNotImplementInterface_showError() {
    lint()
        .files(
            java("package foo;\n" +
                 "public class Example extends Database {\n" +
                 "  private static final String RECIPIENT_ID = \"recipient_id\";\n" +
                 "}")
        )
        .issues(RecipientIdDatabaseDetector.RECIPIENT_ID_DATABASE_REFERENCE_ISSUE)
        .run()
        .expect("src/foo/Example.java:3: Error: If you reference a RecipientId in your table, you must implement the RecipientIdDatabaseReference interface. [RecipientIdDatabaseReferenceUsage]\n" +
                "  private static final String RECIPIENT_ID = \"recipient_id\";\n" +
                "  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "1 errors, 0 warnings");
  }

  @Test
  public void recipientIdDatabase_databaseHasRecipientFieldAndImplementsInterface_noError() {
    lint()
        .files(
            recipientReferenceStub,
            java("package foo;\n" +
                 "import org.thoughtcrime.securesms.database.RecipientIdDatabaseReference;\n" +
                 "public class Example extends Database implements RecipientIdDatabaseReference {\n" +
                 "  private static final String RECIPIENT_ID = \"recipient_id\";\n" +
                 "  @Override\n" +
                 "  public void remapRecipient(RecipientId fromId, RecipientId toId) {}\n" +
                 "}")
        )
        .issues(RecipientIdDatabaseDetector.RECIPIENT_ID_DATABASE_REFERENCE_ISSUE)
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
