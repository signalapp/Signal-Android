package org.signal.lint;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class AlertDialogBuilderDetector extends Detector implements Detector.UastScanner {

  static final Issue ALERT_DIALOG_BUILDER_USAGE = Issue.create("AlertDialogBuilderUsage",
                                                               "Creating dialog with AlertDialog.Builder instead of MaterialAlertDialogBuilder",
                                                               "Signal utilizes MaterialAlertDialogBuilder for more consistent and pleasant AlertDialogs.",
                                                               Category.MESSAGES,
                                                               5,
                                                               Severity.WARNING,
                                                               new Implementation(AlertDialogBuilderDetector.class, Scope.JAVA_FILE_SCOPE));

  @Override
  public @Nullable List<String> getApplicableConstructorTypes() {
    return Arrays.asList("android.app.AlertDialog.Builder", "androidx.appcompat.app.AlertDialog.Builder");
  }

  @Override
  public void visitConstructor(JavaContext context, @NotNull UCallExpression call, @NotNull PsiMethod method) {
    JavaEvaluator evaluator = context.getEvaluator();

    if (evaluator.isMemberInClass(method, "android.app.AlertDialog.Builder")) {
      LintFix fix = quickFixIssueAlertDialogBuilder(call);
      context.report(ALERT_DIALOG_BUILDER_USAGE,
                     call,
                     context.getLocation(call),
                     "Using 'android.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder",
                     fix);
    }

    if (evaluator.isMemberInClass(method, "androidx.appcompat.app.AlertDialog.Builder")) {
      LintFix fix = quickFixIssueAlertDialogBuilder(call);
      context.report(ALERT_DIALOG_BUILDER_USAGE,
                     call,
                     context.getLocation(call),
                     "Using 'androidx.appcompat.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder",
                     fix);
    }
  }

  private LintFix quickFixIssueAlertDialogBuilder(@NotNull UCallExpression alertBuilderCall) {
    List<UExpression> arguments = alertBuilderCall.getValueArguments();
    UExpression       context   = arguments.get(0);

    String fixSource = "new com.google.android.material.dialog.MaterialAlertDialogBuilder";

    switch (arguments.size()) {
      case 1:
        fixSource += String.format("(%s)", context);
        break;
      case 2:
        UExpression themeOverride = arguments.get(1);
        fixSource += String.format("(%s, %s)", context, themeOverride);
        break;

      default:
        throw new IllegalStateException("MaterialAlertDialogBuilder overloads should have 1 or 2 arguments");
    }

    String               builderCallSource = alertBuilderCall.asSourceString();
    LintFix.GroupBuilder fixGrouper        = fix().group();
    fixGrouper.add(fix().replace().text(builderCallSource).shortenNames().reformat(true).with(fixSource).build());
    return fixGrouper.build();
  }
}
