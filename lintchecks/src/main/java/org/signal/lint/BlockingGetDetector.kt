package org.signal.lint

import com.android.tools.lint.detector.api.Category.Companion.MESSAGES
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope.Companion.JAVA_FILE_SCOPE
import com.android.tools.lint.detector.api.Severity.WARNING
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

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
class BlockingGetDetector : Detector(), Detector.UastScanner {
  override fun getApplicableMethodNames(): List<String> {
    return listOf("blockingGet")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator

    if (evaluator.isMemberInClass(method, "io.reactivex.rxjava3.core.Single")) {
      context.report(
        issue = UNSAFE_BLOCKING_GET,
        location = context.getLocation(node),
        message = "Using 'Single#blockingGet' instead of 'RxExtensions.safeBlockingGet'",
        quickfixData = null
      )
    }

    if (evaluator.isMemberInClass(method, "io.reactivex.rxjava3.core.Observable")) {
      context.report(
        issue = UNSAFE_BLOCKING_GET,
        location = context.getLocation(node),
        message = "Using 'Observable#blockingGet' instead of 'RxExtensions.safeBlockingGet'",
        quickfixData = null
      )
    }

    if (evaluator.isMemberInClass(method, "io.reactivex.rxjava3.core.Flowable")) {
      context.report(
        issue = UNSAFE_BLOCKING_GET,
        location = context.getLocation(node),
        message = "Using 'Flowable#blockingGet' instead of 'RxExtensions.safeBlockingGet'",
        quickfixData = null
      )
    }
  }

  companion object {
    val UNSAFE_BLOCKING_GET: Issue = Issue.create(
      id = "UnsafeBlockingGet",
      briefDescription = "BlockingGet is considered unsafe and should be avoided.",
      explanation = "Prefer exposing the Observable instead. If you need to block, use RxExtensions.safeBlockingGet",
      category = MESSAGES,
      priority = 5,
      severity = WARNING,
      implementation = Implementation(BlockingGetDetector::class.java, JAVA_FILE_SCOPE)
    )
  }
}
