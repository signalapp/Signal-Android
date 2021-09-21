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
public final class AlertDialogBuilderDetectorTest {

  private static final TestFile appCompatAlertDialogStub = java(readResourceAsString("AppCompatAlertDialogStub.java"));

  @Test
  public void androidAlertDialogBuilderUsed_LogAlertDialogBuilderUsage_1_arg() {
    lint()
        .files(
            java("package foo;\n" +
                 "import android.app.AlertDialog;\n" +
                 "public class Example {\n" +
                 "  public void buildDialog() {\n" +
                 "    new AlertDialog.Builder(context).show();\n" +
                 "  }\n" +
                 "}")
        )
        .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
        .run()
        .expect("src/foo/Example.java:5: Warning: Using 'android.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder [AlertDialogBuilderUsage]\n" +
                "    new AlertDialog.Builder(context).show();\n" +
                "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings")
        .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.dialog.MaterialAlertDialogBuilder(context):\n" +
                        "@@ -5 +5\n" +
                        "-     new AlertDialog.Builder(context).show();\n" +
                        "+     new com.google.android.material.dialog.MaterialAlertDialogBuilder(context).show();");
  }

  @Test
  public void androidAlertDialogBuilderUsed_LogAlertDialogBuilderUsage_2_arg() {
    lint()
        .files(
            java("package foo;\n" +
                 "import android.app.AlertDialog;\n" +
                 "public class Example {\n" +
                 "  public void buildDialog() {\n" +
                 "    new AlertDialog.Builder(context, themeOverride).show();\n" +
                 "  }\n" +
                 "}")
        )
        .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
        .run()
        .expect("src/foo/Example.java:5: Warning: Using 'android.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder [AlertDialogBuilderUsage]\n" +
                "    new AlertDialog.Builder(context, themeOverride).show();\n" +
                "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings")
        .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, themeOverride):\n" +
                        "@@ -5 +5\n" +
                        "-     new AlertDialog.Builder(context, themeOverride).show();\n" +
                        "+     new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, themeOverride).show();");
  }

  @Test
  public void androidAlertDialogBuilderUsed_withAssignment_LogAlertDialogBuilderUsage_1_arg() {
    lint()
        .files(
            java("package foo;\n" +
                 "import android.app.AlertDialog;\n" +
                 "public class Example {\n" +
                 "  public void buildDialog() {\n" +
                 "    AlertDialog.Builder builder = new AlertDialog.Builder(context)\n" +
                 "                                         .show();\n" +
                 "  }\n" +
                 "}")
        )
        .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
        .run()
        .expect("src/foo/Example.java:5: Warning: Using 'android.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder [AlertDialogBuilderUsage]\n" +
                "    AlertDialog.Builder builder = new AlertDialog.Builder(context)\n" +
                "                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings")
        .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.dialog.MaterialAlertDialogBuilder(context):\n" +
                        "@@ -5 +5\n" +
                        "-     AlertDialog.Builder builder = new AlertDialog.Builder(context)\n" +
                        "+     AlertDialog.Builder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)");
  }

  @Test
  public void appcompatAlertDialogBuilderUsed_LogAlertDialogBuilderUsage_1_arg() {
    lint()
        .files(appCompatAlertDialogStub,
            java("package foo;\n" +
                 "import androidx.appcompat.app.AlertDialog;\n" +
                 "public class Example {\n" +
                 "  public void buildDialog() {\n" +
                 "    new AlertDialog.Builder(context).show();\n" +
                 "  }\n" +
                 "}")
        )
        .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
        .run()
        .expect("src/foo/Example.java:5: Warning: Using 'androidx.appcompat.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder [AlertDialogBuilderUsage]\n" +
                "    new AlertDialog.Builder(context).show();\n" +
                "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings")
        .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.dialog.MaterialAlertDialogBuilder(context):\n" +
                        "@@ -5 +5\n" +
                        "-     new AlertDialog.Builder(context).show();\n" +
                        "+     new com.google.android.material.dialog.MaterialAlertDialogBuilder(context).show();");
  }

  @Test
  public void appcompatAlertDialogBuilderUsed_LogAlertDialogBuilderUsage_2_arg() {
    lint()
        .files(appCompatAlertDialogStub,
            java("package foo;\n" +
                 "import androidx.appcompat.app.AlertDialog;\n" +
                 "public class Example {\n" +
                 "  public void buildDialog() {\n" +
                 "    new AlertDialog.Builder(context, themeOverride).show();\n" +
                 "  }\n" +
                 "}")
        )
        .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
        .run()
        .expect("src/foo/Example.java:5: Warning: Using 'androidx.appcompat.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder [AlertDialogBuilderUsage]\n" +
                "    new AlertDialog.Builder(context, themeOverride).show();\n" +
                "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings")
        .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, themeOverride):\n" +
                        "@@ -5 +5\n" +
                        "-     new AlertDialog.Builder(context, themeOverride).show();\n" +
                        "+     new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, themeOverride).show();");
  }

  @Test
  public void appcompatAlertDialogBuilderUsed_withAssignment_LogAlertDialogBuilderUsage_1_arg() {
    lint()
        .files(appCompatAlertDialogStub,
            java("package foo;\n" +
                 "import androidx.appcompat.app.AlertDialog;\n" +
                 "public class Example {\n" +
                 "  public void buildDialog() {\n" +
                 "    AlertDialog.Builder builder = new AlertDialog.Builder(context)\n" +
                 "                                         .show();\n" +
                 "  }\n" +
                 "}")
        )
        .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
        .run()
        .expect("src/foo/Example.java:5: Warning: Using 'androidx.appcompat.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder [AlertDialogBuilderUsage]\n" +
                "    AlertDialog.Builder builder = new AlertDialog.Builder(context)\n" +
                "                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings")
        .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.dialog.MaterialAlertDialogBuilder(context):\n" +
                        "@@ -5 +5\n" +
                        "-     AlertDialog.Builder builder = new AlertDialog.Builder(context)\n" +
                        "+     AlertDialog.Builder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)");
  }

  private static String readResourceAsString(String resourceName) {
    InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName);
    assertNotNull(inputStream);
    Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
    assertTrue(scanner.hasNext());
    return scanner.next();
  }
}
