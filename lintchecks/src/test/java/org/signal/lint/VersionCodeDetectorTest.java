package org.signal.lint;

import org.junit.Test;

import static com.android.tools.lint.checks.infrastructure.TestFiles.java;
import static com.android.tools.lint.checks.infrastructure.TestLintTask.lint;

@SuppressWarnings("UnstableApiUsage")
public final class VersionCodeDetectorTest {

  @Test
  public void version_code_constant_referenced_in_code() {
    lint()
      .files(
        java("package foo;\n" +
             "import android.os.Build;\n" +
             "public class Example {\n" +
             "  public void versionCodeMention() {\n" +
             "    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {\n" +
             "    }\n" +
             "  }\n" +
             "}")
      )
      .issues(VersionCodeDetector.VERSION_CODE_USAGE)
      .run()
      .expect("src/foo/Example.java:5: Warning: Using 'VERSION_CODES' reference instead of the numeric value 21 [VersionCodeUsage]\n" +
              "    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {\n" +
              "                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
              "0 errors, 1 warnings")
      .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with 21:\n" +
                      "@@ -5 +5\n" +
                      "-     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {\n" +
                      "+     if (Build.VERSION.SDK_INT >= 21) {");
  }

  @Test
  public void numeric_value_referenced_in_code() {
    lint()
      .files(
        java("package foo;\n" +
             "import android.os.Build;\n" +
             "public class Example {\n" +
             "  public void versionCodeMention() {\n" +
             "    if (Build.VERSION.SDK_INT >= 22) {\n" +
             "    }\n" +
             "  }\n" +
             "}")
      )
      .issues(VersionCodeDetector.VERSION_CODE_USAGE)
      .run()
      .expectClean();
  }

  @Test
  public void non_version_code_constant_referenced_in_code() {
    lint()
      .files(
        java("package foo;\n" +
               "import android.os.Build;\n" +
               "public class Example {\n" +
               "  private final static int LOLLIPOP = 21;\n" +
               "  public void versionCodeMention() {\n" +
               "    if (Build.VERSION.SDK_INT >= LOLLIPOP) {\n" +
               "    }\n" +
               "  }\n" +
               "}")
      )
      .issues(VersionCodeDetector.VERSION_CODE_USAGE)
      .run()
      .expectClean();
  }

  @Test
  public void version_code_constant_referenced_in_TargetApi_attribute_and_inner_class_import() {
    lint()
      .files(
        java("package foo;\n" +
             "import android.os.Build.VERSION_CODES;\n" +
             "import android.annotation.TargetApi;\n" +
             "public class Example {\n" +
             "  @TargetApi(VERSION_CODES.N)\n" +
             "  public void versionCodeMention() {\n" +
             "  }\n" +
             "}")
      )
      .issues(VersionCodeDetector.VERSION_CODE_USAGE)
      .run()
      .expect("src/foo/Example.java:5: Warning: Using 'VERSION_CODES' reference instead of the numeric value 24 [VersionCodeUsage]\n" +
              "  @TargetApi(VERSION_CODES.N)\n" +
              "             ~~~~~~~~~~~~~~~\n" +
              "0 errors, 1 warnings")
      .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with 24:\n" +
                      "@@ -5 +5\n" +
                      "-   @TargetApi(VERSION_CODES.N)\n" +
                      "+   @TargetApi(24)");
  }

  @Test
  public void version_code_constant_referenced_in_RequiresApi_attribute_with_named_parameter() {
    lint()
      .files(
        java("package foo;\n" +
             "import android.os.Build;\n" +
             "import android.annotation.RequiresApi;\n" +
             "public class Example {\n" +
             "  @RequiresApi(app = Build.VERSION_CODES.M)\n" +
             "  public void versionCodeMention() {\n" +
             "  }\n" +
             "}")
      )
      .issues(VersionCodeDetector.VERSION_CODE_USAGE)
      .run()
      .expect("src/foo/Example.java:5: Warning: Using 'VERSION_CODES' reference instead of the numeric value 23 [VersionCodeUsage]\n" +
              "  @RequiresApi(app = Build.VERSION_CODES.M)\n" +
              "                     ~~~~~~~~~~~~~~~~~~~~~\n" +
              "0 errors, 1 warnings")
      .expectFixDiffs("Fix for src/foo/Example.java line 5: Replace with 23:\n" +
                      "@@ -5 +5\n" +
                      "-   @RequiresApi(app = Build.VERSION_CODES.M)\n" +
                      "+   @RequiresApi(app = 23)");
  }
}
