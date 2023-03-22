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
public final class CardViewDetectorTest {

  private static final TestFile cardViewStub = java(readResourceAsString("CardViewStub.java"));

  @Test
  public void cardViewUsed_LogCardViewUsage_1_arg() {
    lint()
        .files(cardViewStub,
            java("package foo;\n" +
                 "import androidx.cardview.widget.CardView;\n" +
                 "public class Example {\n" +
                 "  public void buildDialog() {\n" +
                 "    new CardView(context);\n" +
                 "  }\n" +
                 "}")
        )
        .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
        .run()
        .expect("src/foo/Example.java:5: Warning: Using 'androidx.cardview.widget.CardView' instead of com.google.android.material.card.MaterialCardView [CardViewUsage]\n" +
                "    new CardView(context);\n" +
                "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings")
        .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.card.MaterialCardView(context):\n" +
                        "@@ -5 +5\n" +
                        "-     new CardView(context);\n" +
                        "+     new com.google.android.material.card.MaterialCardView(context);");
  }

  @Test
  public void cardViewUsed_LogCardViewUsage_2_arg() {
    lint()
        .files(cardViewStub,
            java("package foo;\n" +
                 "import androidx.cardview.widget.CardView;\n" +
                 "public class Example {\n" +
                 "  public void buildDialog() {\n" +
                 "    new CardView(context, attrs);\n" +
                 "  }\n" +
                 "}")
        )
        .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
        .run()
        .expect("src/foo/Example.java:5: Warning: Using 'androidx.cardview.widget.CardView' instead of com.google.android.material.card.MaterialCardView [CardViewUsage]\n" +
                "    new CardView(context, attrs);\n" +
                "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings")
        .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.card.MaterialCardView(context, attrs):\n" +
                        "@@ -5 +5\n" +
                        "-     new CardView(context, attrs);\n" +
                        "+     new com.google.android.material.card.MaterialCardView(context, attrs);");
  }

  @Test
  public void cardViewUsed_withAssignment_LogCardViewUsage_1_arg() {
    lint()
        .files(cardViewStub,
            java("package foo;\n" +
                 "import androidx.cardview.widget.CardView;\n" +
                 "public class Example {\n" +
                 "  public void buildDialog() {\n" +
                 "    CardView cardView = new CardView(context)\n" +
                 "                                         ;\n" +
                 "  }\n" +
                 "}")
        )
        .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
        .run()
        .expect("src/foo/Example.java:5: Warning: Using 'androidx.cardview.widget.CardView' instead of com.google.android.material.card.MaterialCardView [CardViewUsage]\n" +
                "    CardView cardView = new CardView(context)\n" +
                "                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings")
        .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.card.MaterialCardView(context):\n" +
                        "@@ -5 +5\n" +
                        "-     CardView cardView = new CardView(context)\n" +
                        "+     CardView cardView = new com.google.android.material.card.MaterialCardView(context)");
  }

  private static String readResourceAsString(String resourceName) {
    InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName);
    assertNotNull(inputStream);
    Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
    assertTrue(scanner.hasNext());
    return scanner.next();
  }
}
