package org.signal.lint;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;

import java.util.List;

/**
 * Detects usages of Rx observable stream's blockingGet method. This is considered harmful, as
 * blockingGet will take any error it emits and throw it as a runtime error. The alternative options
 * are to:
 *
 * 1. Provide a synchronous method instead of relying on an observable method.
 * 2. Pass the observable to the caller to allow them to wait on it via a flatMap or other operator.
 * 3. Utilize safeBlockingGet, which will bubble up the interrupted exception.
 *
 * Note that (1) is the most preferred route here.
 */
@SuppressWarnings("UnstableApiUsage")
public final class BlockingGetDetector extends Detector implements Detector.UastScanner {

  static final Issue UNSAFE_BLOCKING_GET = Issue.create("UnsafeBlockingGet",
                                                        "BlockingGet is considered unsafe and should be avoided.",
                                                        "Prefer exposing the Observable instead. If you need to block, use RxExtensions.safeBlockingGet",
                                                        Category.MESSAGES,
                                                        5,
                                                        Severity.WARNING,
                                                        new Implementation(BlockingGetDetector.class, Scope.JAVA_FILE_SCOPE));

  @Override
  public List<String> getApplicableMethodNames() {
    return List.of("blockingGet");
  }

  @Override
  public void visitMethodCall(@NotNull JavaContext context, @NotNull UCallExpression node, @NotNull PsiMethod method) {
    JavaEvaluator evaluator = context.getEvaluator();

    if (evaluator.isMemberInClass(method, "io.reactivex.rxjava3.core.Single")) {
      context.report(UNSAFE_BLOCKING_GET,
                     context.getLocation(node),
                     "Using 'Single#blockingGet' instead of 'RxExtensions.safeBlockingGet'",
                     null);
    }

    if (evaluator.isMemberInClass(method, "io.reactivex.rxjava3.core.Observable")) {
      context.report(UNSAFE_BLOCKING_GET,
                     context.getLocation(node),
                     "Using 'Observable#blockingGet' instead of 'RxExtensions.safeBlockingGet'",
                     null);
    }

    if (evaluator.isMemberInClass(method, "io.reactivex.rxjava3.core.Flowable")) {
      context.report(UNSAFE_BLOCKING_GET,
                     context.getLocation(node),
                     "Using 'Flowable#blockingGet' instead of 'RxExtensions.safeBlockingGet'",
                     null);
    }
  }
}
