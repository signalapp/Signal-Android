package org.signal.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Scanner

class AlertDialogBuilderDetectorTest {
  @Test
  fun androidAlertDialogBuilderUsed_LogAlertDialogBuilderUsage_1_arg() {
    TestLintTask.lint()
      .files(
        java(
          """
          package foo;
          import android.app.AlertDialog;
          public class Example {
            public void buildDialog() {
              new AlertDialog.Builder(context).show();
            }
          }
          """.trimIndent()
        )
      )
      .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
      .run()
      .expect(
        """
        src/foo/Example.java:5: Warning: Using 'android.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder [AlertDialogBuilderUsage]
            new AlertDialog.Builder(context).show();
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.dialog.MaterialAlertDialogBuilder(context):
        @@ -5 +5
        -     new AlertDialog.Builder(context).show();
        +     new com.google.android.material.dialog.MaterialAlertDialogBuilder(context).show();
        """.trimIndent()
      )
  }

  @Test
  fun androidAlertDialogBuilderUsed_LogAlertDialogBuilderUsage_2_arg() {
    TestLintTask.lint()
      .files(
        java(
          """
          package foo;
          import android.app.AlertDialog;
          public class Example {
            public void buildDialog() {
              new AlertDialog.Builder(context, themeOverride).show();
            }
          }
          """.trimIndent()
        )
      )
      .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
      .run()
      .expect(
        """
        src/foo/Example.java:5: Warning: Using 'android.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder [AlertDialogBuilderUsage]
            new AlertDialog.Builder(context, themeOverride).show();
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, themeOverride):
        @@ -5 +5
        -     new AlertDialog.Builder(context, themeOverride).show();
        +     new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, themeOverride).show();
        """.trimIndent()
      )
  }

  @Test
  fun androidAlertDialogBuilderUsed_withAssignment_LogAlertDialogBuilderUsage_1_arg() {
    TestLintTask.lint()
      .files(
        java(
          """
          package foo;
          import android.app.AlertDialog;
          public class Example {
            public void buildDialog() {
              AlertDialog.Builder builder = new AlertDialog.Builder(context)
                                                   .show();
            }
          }
          """.trimIndent()
        )
      )
      .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
      .run()
      .expect(
        """
        src/foo/Example.java:5: Warning: Using 'android.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder [AlertDialogBuilderUsage]
            AlertDialog.Builder builder = new AlertDialog.Builder(context)
                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.dialog.MaterialAlertDialogBuilder(context):
        @@ -5 +5
        -     AlertDialog.Builder builder = new AlertDialog.Builder(context)
        +     AlertDialog.Builder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
        """.trimIndent()
      )
  }

  @Test
  fun appcompatAlertDialogBuilderUsed_LogAlertDialogBuilderUsage_1_arg() {
    TestLintTask.lint()
      .files(
        appCompatAlertDialogStub,
        java(
          """
          package foo;
          import androidx.appcompat.app.AlertDialog;
          public class Example {
            public void buildDialog() {
              new AlertDialog.Builder(context).show();
            }
          }
          """.trimIndent()
        )
      )
      .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
      .run()
      .expect(
      """
        src/foo/Example.java:5: Warning: Using 'androidx.appcompat.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder [AlertDialogBuilderUsage]
            new AlertDialog.Builder(context).show();
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.dialog.MaterialAlertDialogBuilder(context):
        @@ -5 +5
        -     new AlertDialog.Builder(context).show();
        +     new com.google.android.material.dialog.MaterialAlertDialogBuilder(context).show();
        """.trimIndent()
      )
  }

  @Test
  fun appcompatAlertDialogBuilderUsed_LogAlertDialogBuilderUsage_2_arg() {
    TestLintTask.lint()
      .files(
        appCompatAlertDialogStub,
        java(
        """
          package foo;
          import androidx.appcompat.app.AlertDialog;
          public class Example {
            public void buildDialog() {
              new AlertDialog.Builder(context, themeOverride).show();
            }
          }
          """.trimIndent()
        )
      )
      .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
      .run()
      .expect(
        """
        src/foo/Example.java:5: Warning: Using 'androidx.appcompat.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder [AlertDialogBuilderUsage]
            new AlertDialog.Builder(context, themeOverride).show();
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, themeOverride):
        @@ -5 +5
        -     new AlertDialog.Builder(context, themeOverride).show();
        +     new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, themeOverride).show();
        """.trimIndent()
      )
  }

  @Test
  fun appcompatAlertDialogBuilderUsed_withAssignment_LogAlertDialogBuilderUsage_1_arg() {
    TestLintTask.lint()
      .files(
        appCompatAlertDialogStub,
        java(
          """
          package foo;
          import androidx.appcompat.app.AlertDialog;
          public class Example {
            public void buildDialog() {
              AlertDialog.Builder builder = new AlertDialog.Builder(context)
                                                   .show();
            }
          }
          """.trimIndent()
        )
      )
      .issues(AlertDialogBuilderDetector.ALERT_DIALOG_BUILDER_USAGE)
      .run()
      .expect(
      """
        src/foo/Example.java:5: Warning: Using 'androidx.appcompat.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder [AlertDialogBuilderUsage]
            AlertDialog.Builder builder = new AlertDialog.Builder(context)
                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.dialog.MaterialAlertDialogBuilder(context):
        @@ -5 +5
        -     AlertDialog.Builder builder = new AlertDialog.Builder(context)
        +     AlertDialog.Builder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
        """.trimIndent()
      )
  }

  companion object {
    private val appCompatAlertDialogStub = kotlin(readResourceAsString("AppCompatAlertDialogStub.kt"))

    private fun readResourceAsString(@Suppress("SameParameterValue") resourceName: String): String {
      val inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName)
      assertNotNull(inputStream)
      val scanner = Scanner(inputStream!!).useDelimiter("\\A")
      assertTrue(scanner.hasNext())
      return scanner.next()
    }
  }
}
