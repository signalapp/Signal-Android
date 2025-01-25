package org.signal.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category.Companion.CORRECTNESS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope.Companion.JAVA_FILE_SCOPE
import com.android.tools.lint.detector.api.Severity.WARNING
import com.intellij.psi.PsiTypes
import org.jetbrains.uast.UExpression

class VersionCodeDetector : Detector(), Detector.UastScanner {
  override fun getApplicableUastTypes(): List<Class<UExpression>> {
    return listOf(UExpression::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return ExpressionChecker(context)
  }

  private inner class ExpressionChecker(private val context: JavaContext) : UElementHandler() {
    private val evaluator = context.evaluator
    private val versionCodeClass = evaluator.findClass("android.os.Build.VERSION_CODES")

    override fun visitExpression(node: UExpression) {
      if (versionCodeClass != null && node.getExpressionType() === PsiTypes.intType()) {
        val javaPsi = node.javaPsi

        if (javaPsi != null) {
          val resolved = evaluator.resolve(javaPsi)

          if (resolved != null && resolved.parent == versionCodeClass) {
            val evaluated = node.evaluate()

            if (evaluated != null) {
              context.report(
                issue = VERSION_CODE_USAGE,
                scope = node,
                location = context.getLocation(node),
                message = "Using 'VERSION_CODES' reference instead of the numeric value $evaluated",
                quickfixData = quickFixIssueInlineValue(node, evaluated.toString())
              )
            } else {
              context.report(
                issue = VERSION_CODE_USAGE,
                scope = node,
                location = context.getLocation(node),
                message = "Using 'VERSION_CODES' reference instead of the numeric value",
                quickfixData = null
              )
            }
          }
        }
      }
    }
  }

  private fun quickFixIssueInlineValue(node: UExpression, fixSource: String): LintFix {
    return fix()
      .group()
      .add(
        fix()
          .replace()
          .text(node.asSourceString())
          .reformat(true)
          .with(fixSource)
          .build()
      )
      .build()
  }

  companion object {
    val VERSION_CODE_USAGE: Issue = Issue.create(
      id = "VersionCodeUsage",
      briefDescription = "Using 'VERSION_CODES' reference instead of the numeric value",
      explanation = "Signal style is to use the numeric value.",
      category = CORRECTNESS,
      priority = 5,
      severity = WARNING,
      implementation = Implementation(VersionCodeDetector::class.java, JAVA_FILE_SCOPE)
    )
  }
}