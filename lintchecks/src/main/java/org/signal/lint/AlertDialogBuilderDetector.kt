package org.signal.lint

import com.android.tools.lint.detector.api.Category.Companion.MESSAGES
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope.Companion.JAVA_FILE_SCOPE
import com.android.tools.lint.detector.api.Severity.WARNING
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class AlertDialogBuilderDetector : Detector(), Detector.UastScanner {
  override fun getApplicableConstructorTypes(): List<String> {
    return listOf("android.app.AlertDialog.Builder", "androidx.appcompat.app.AlertDialog.Builder")
  }

  override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
    val evaluator = context.evaluator

    if (evaluator.isMemberInClass(constructor, "android.app.AlertDialog.Builder")) {
      context.report(
        issue = ALERT_DIALOG_BUILDER_USAGE,
        scope = node,
        location = context.getLocation(node),
        message = "Using 'android.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder",
        quickfixData = quickFixIssueAlertDialogBuilder(node)
      )
    }

    if (evaluator.isMemberInClass(constructor, "androidx.appcompat.app.AlertDialog.Builder")) {
      context.report(
        issue = ALERT_DIALOG_BUILDER_USAGE,
        scope = node,
        location = context.getLocation(node),
        message = "Using 'androidx.appcompat.app.AlertDialog.Builder' instead of com.google.android.material.dialog.MaterialAlertDialogBuilder",
        quickfixData = quickFixIssueAlertDialogBuilder(node)
      )
    }
  }

  private fun quickFixIssueAlertDialogBuilder(alertBuilderCall: UCallExpression): LintFix {
    val arguments = alertBuilderCall.valueArguments
    val context = arguments[0]

    var fixSource = "new com.google.android.material.dialog.MaterialAlertDialogBuilder"

    when (arguments.size) {
      1 -> fixSource += String.format("(%s)", context)
      2 -> {
        val themeOverride = arguments[1]
        fixSource += String.format("(%s, %s)", context, themeOverride)
      }

      else -> throw IllegalStateException("MaterialAlertDialogBuilder overloads should have 1 or 2 arguments")
    }

    return fix()
      .group()
      .add(
        fix()
          .replace()
          .text(alertBuilderCall.asSourceString())
          .shortenNames()
          .reformat(true)
          .with(fixSource)
          .build()
      )
      .build()
  }

  companion object {
    val ALERT_DIALOG_BUILDER_USAGE: Issue = Issue.create(
      id = "AlertDialogBuilderUsage",
      briefDescription = "Creating dialog with AlertDialog.Builder instead of MaterialAlertDialogBuilder",
      explanation = "Signal utilizes MaterialAlertDialogBuilder for more consistent and pleasant AlertDialogs.",
      category = MESSAGES,
      priority = 5,
      severity = WARNING,
      implementation = Implementation(AlertDialogBuilderDetector::class.java, JAVA_FILE_SCOPE)
    )
  }
}