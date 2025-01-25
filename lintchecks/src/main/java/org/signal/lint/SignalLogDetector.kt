package org.signal.lint

import com.android.tools.lint.detector.api.Category.Companion.MESSAGES
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope.Companion.JAVA_FILE_SCOPE
import com.android.tools.lint.detector.api.Severity.ERROR
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.java.JavaUSimpleNameReferenceExpression
import org.jetbrains.uast.kotlin.KotlinUQualifiedReferenceExpression
import org.jetbrains.uast.kotlin.KotlinUSimpleReferenceExpression

class SignalLogDetector : Detector(), Detector.UastScanner {
  override fun getApplicableMethodNames(): List<String> {
    return listOf("v", "d", "i", "w", "e", "wtf")
  }

  @Suppress("UnstableApiUsage")
  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator

    if (evaluator.isMemberInClass(method, "android.util.Log")) {
      context.report(
        issue = LOG_NOT_SIGNAL,
        scope = node,
        location = context.getLocation(node),
        message = "Using 'android.util.Log' instead of a Signal Logger",
        quickfixData = quickFixIssueLog(node)
      )
    }

    if (evaluator.isMemberInClass(method, "org.signal.glide.Log")) {
      context.report(
        issue = LOG_NOT_SIGNAL,
        scope = node,
        location = context.getLocation(node),
        message = "Using 'org.signal.glide.Log' instead of a Signal Logger",
        quickfixData = quickFixIssueLog(node)
      )
    }

    if (evaluator.isMemberInClass(method, "org.signal.libsignal.protocol.logging.Log")) {
      context.report(
        issue = LOG_NOT_APP,
        scope = node,
        location = context.getLocation(node),
        message = "Using Signal server logger instead of app level Logger",
        quickfixData = quickFixIssueLog(node)
      )
    }

    if (evaluator.isMemberInClass(method, "org.signal.core.util.logging.Log")) {
      val arguments = node.valueArguments
      val tag = arguments[0]

      val invalidTagType = setOf(
        JavaUSimpleNameReferenceExpression::class,
        KotlinUSimpleReferenceExpression::class,
        KotlinUQualifiedReferenceExpression::class
      ).none { it.isInstance(tag) }

      if (invalidTagType) {
        context.report(
          issue = INLINE_TAG,
          scope = node,
          location = context.getLocation(node),
          message = "Not using a tag constant"
        )
      }
    }
  }

  private fun quickFixIssueLog(logCall: UCallExpression): LintFix {
    val arguments = logCall.valueArguments
    val methodName = logCall.methodName
    val tag = arguments[0]

    var fixSource = "org.signal.core.util.logging.Log."

    when (arguments.size) {
      2 -> {
        val msgOrThrowable = arguments[1]
        fixSource += String.format("%s(%s, %s)", methodName, tag, msgOrThrowable.asSourceString())
      }

      3 -> {
        val msg = arguments[1]
        val throwable = arguments[2]
        fixSource += String.format("%s(%s, %s, %s)", methodName, tag, msg.asSourceString(), throwable.asSourceString())
      }

      else -> throw IllegalStateException("Log overloads should have 2 or 3 arguments")
    }

    return fix()
      .group()
      .add(
        fix()
          .replace()
          .text(logCall.asSourceString())
          .shortenNames()
          .reformat(true)
          .with(fixSource)
          .build()
      )
      .build()
  }


  companion object {
    val LOG_NOT_SIGNAL: Issue = Issue.create(
      id = "LogNotSignal",
      briefDescription = "Logging call to Android Log instead of Signal's Logger",
      explanation = "Signal has its own logger which must be used.",
      category = MESSAGES,
      priority = 5,
      severity = ERROR,
      implementation = Implementation(SignalLogDetector::class.java, JAVA_FILE_SCOPE)
    )

    val LOG_NOT_APP: Issue = Issue.create(
      id = "LogNotAppSignal",
      briefDescription = "Logging call to Signal Service Log instead of App level Logger",
      explanation = "Signal app layer has its own logger which must be used.",
      category = MESSAGES,
      priority = 5,
      severity = ERROR,
      implementation = Implementation(SignalLogDetector::class.java, JAVA_FILE_SCOPE)
    )

    val INLINE_TAG: Issue = Issue.create(
      id = "LogTagInlined",
      briefDescription = "Use of an inline string in a TAG",
      explanation = "Often a sign of left in temporary log statements, always use a tag constant.",
      category = MESSAGES,
      priority = 5,
      severity = ERROR,
      implementation = Implementation(SignalLogDetector::class.java, JAVA_FILE_SCOPE)
    )
  }
}
