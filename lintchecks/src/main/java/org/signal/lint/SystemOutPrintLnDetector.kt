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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

/**
 * Lint detector that flags usage of System.out.println and kotlin.io.println methods.
 */
class SystemOutPrintLnDetector : Detector(), Detector.UastScanner {

  override fun getApplicableMethodNames(): List<String> {
    return listOf("println", "print")
  }

  @Suppress("UnstableApiUsage")
  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator

    if (evaluator.isMemberInClass(method, "java.io.PrintStream")) {
      if (isSystemOutCall(node.receiver)) {
        context.report(
          issue = SYSTEM_OUT_PRINTLN_USAGE,
          scope = node,
          location = context.getLocation(node),
          message = "Using 'System.out.${method.name}' instead of Signal Logger",
          quickfixData = createQuickFix(node)
        )
      }
    }

    // Check for kotlin.io.println (top-level function)
    if (method.name == "println" && evaluator.isMemberInClass(method, "kotlin.io.ConsoleKt")) {
      context.report(
        issue = KOTLIN_IO_PRINTLN_USAGE,
        scope = node,
        location = context.getLocation(node),
        message = "Using 'kotlin.io.println' instead of Signal Logger.",
        quickfixData = createQuickFix(node)
      )
    }
  }

  private fun isSystemOutCall(receiver: UExpression?): Boolean {
    return receiver is UQualifiedReferenceExpression &&
           receiver.selector.asRenderString() == "out" &&
           receiver.receiver.asRenderString().endsWith("System")
  }

  private fun createQuickFix(node: UCallExpression): LintFix {
    val arguments = node.valueArguments
    val message = if (arguments.isNotEmpty()) arguments[0].asSourceString() else "\"\""

    val fixSource = "org.signal.core.util.logging.Log.d(TAG, $message)"

    return fix()
      .group()
      .add(
        fix()
          .replace()
          .text(node.sourcePsi?.text)
          .shortenNames()
          .reformat(true)
          .with(fixSource)
          .build()
      )
      .build()
  }

  companion object {
    val SYSTEM_OUT_PRINTLN_USAGE: Issue = Issue.create(
      id = "SystemOutPrintLnUsage",
      briefDescription = "Usage of System.out.println/print",
      explanation = "System.out.println/print should not be used in production code. Use Signal Logger instead.",
      category = MESSAGES,
      priority = 5,
      severity = ERROR,
      implementation = Implementation(SystemOutPrintLnDetector::class.java, JAVA_FILE_SCOPE)
    )

    val KOTLIN_IO_PRINTLN_USAGE: Issue = Issue.create(
      id = "KotlinIOPrintLnUsage",
      briefDescription = "Usage of kotlin.io.println",
      explanation = "kotlin.io.println should not be used in production code. Use proper logging instead.",
      category = MESSAGES,
      priority = 5,
      severity = ERROR,
      implementation = Implementation(SystemOutPrintLnDetector::class.java, JAVA_FILE_SCOPE)
    )
  }
}