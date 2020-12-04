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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.java.JavaUSimpleNameReferenceExpression;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class SignalLogDetector extends Detector implements Detector.UastScanner {

  static final Issue LOG_NOT_SIGNAL = Issue.create("LogNotSignal",
                                                   "Logging call to Android Log instead of Signal's Logger",
                                                   "Signal has its own logger which must be used.",
                                                   Category.MESSAGES,
                                                   5,
                                                   Severity.ERROR,
                                                   new Implementation(SignalLogDetector.class, Scope.JAVA_FILE_SCOPE));

  static final Issue LOG_NOT_APP = Issue.create("LogNotAppSignal",
                                                "Logging call to Signal Service Log instead of App level Logger",
                                                "Signal app layer has its own logger which must be used.",
                                                Category.MESSAGES,
                                                5,
                                                Severity.ERROR,
                                                new Implementation(SignalLogDetector.class, Scope.JAVA_FILE_SCOPE));

  static final Issue INLINE_TAG = Issue.create("LogTagInlined",
                                               "Use of an inline string in a TAG",
                                               "Often a sign of left in temporary log statements, always use a tag constant.",
                                               Category.MESSAGES,
                                               5,
                                               Severity.ERROR,
                                               new Implementation(SignalLogDetector.class, Scope.JAVA_FILE_SCOPE));

  @Override
  public List<String> getApplicableMethodNames() {
    return Arrays.asList("v", "d", "i", "w", "e", "wtf");
  }

  @Override
  public void visitMethodCall(JavaContext context, @NotNull UCallExpression call, @NotNull PsiMethod method) {
    JavaEvaluator evaluator = context.getEvaluator();

    if (evaluator.isMemberInClass(method, "android.util.Log")) {
      LintFix fix = quickFixIssueLog(call);
      context.report(LOG_NOT_SIGNAL, call, context.getLocation(call), "Using 'android.util.Log' instead of a Signal Logger", fix);
    }

    if (evaluator.isMemberInClass(method, "org.whispersystems.libsignal.logging.Log")) {
      LintFix fix = quickFixIssueLog(call);
      context.report(LOG_NOT_APP, call, context.getLocation(call), "Using Signal server logger instead of app level Logger", fix);
    }

    if (evaluator.isMemberInClass(method, "org.signal.core.util.logging.Log")) {
      List<UExpression> arguments  = call.getValueArguments();
      UExpression       tag        = arguments.get(0);
      if (!(tag instanceof JavaUSimpleNameReferenceExpression)) {
        context.report(INLINE_TAG, call, context.getLocation(call), "Not using a tag constant");
      }
    }
  }

  private LintFix quickFixIssueLog(@NotNull UCallExpression logCall) {
    List<UExpression> arguments  = logCall.getValueArguments();
    String            methodName = logCall.getMethodName();
    UExpression       tag        = arguments.get(0);

    String fixSource = "org.signal.core.util.logging.Log.";

    switch (arguments.size()) {
      case 2:
        UExpression msgOrThrowable = arguments.get(1);
        fixSource += String.format("%s(%s, %s)", methodName, tag, msgOrThrowable.asSourceString());
        break;

      case 3:
        UExpression msg = arguments.get(1);
        UExpression throwable = arguments.get(2);
        fixSource += String.format("%s(%s, %s, %s)", methodName, tag, msg.asSourceString(), throwable.asSourceString());
        break;

      default:
        throw new IllegalStateException("Log overloads should have 2 or 3 arguments");
    }

    String logCallSource = logCall.asSourceString();
    LintFix.GroupBuilder fixGrouper = fix().group();
    fixGrouper.add(fix().replace().text(logCallSource).shortenNames().reformat(true).with(fixSource).build());
    return fixGrouper.build();
  }


}