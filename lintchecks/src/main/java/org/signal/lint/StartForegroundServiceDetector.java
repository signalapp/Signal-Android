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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class StartForegroundServiceDetector extends Detector implements Detector.UastScanner {

  static final Issue START_FOREGROUND_SERVICE_ISSUE = Issue.create("StartForegroundServiceUsage",
                                                                   "Starting a foreground service using ContextCompat.startForegroundService instead of ForegroundServiceUtil",
                                                                   "Starting a foreground service may fail, and we should prefer our utils to make sure they're started correctly",
                                                                   Category.MESSAGES,
                                                                   5,
                                                                   Severity.ERROR,
                                                                   new Implementation(StartForegroundServiceDetector.class, Scope.JAVA_FILE_SCOPE));

  @Override
  public List<String> getApplicableMethodNames() {
    return Arrays.asList("startForegroundService");
  }

  @Override
  public void visitMethodCall(@NotNull JavaContext context, @NotNull UCallExpression call, @NotNull PsiMethod method) {
    JavaEvaluator evaluator = context.getEvaluator();

    if (context.getUastFile() != null && context.getUastFile().getClasses().stream().anyMatch(it -> "ForegroundServiceUtil".equals(it.getName()))) {
      return;
    }

    if (evaluator.isMemberInClass(method, "androidx.core.content.ContextCompat")) {
      context.report(START_FOREGROUND_SERVICE_ISSUE, call, context.getLocation(call), "Using 'ContextCompat.startForegroundService' instead of a ForegroundServiceUtil");
    } else if (evaluator.isMemberInClass(method, "android.content.Context")) {
      context.report(START_FOREGROUND_SERVICE_ISSUE, call, context.getLocation(call), "Using 'Context.startForegroundService' instead of a ForegroundServiceUtil");
    }
  }
}