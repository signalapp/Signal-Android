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

class CardViewDetector : Detector(), Detector.UastScanner {
  override fun getApplicableConstructorTypes(): List<String> {
    return listOf("androidx.cardview.widget.CardView")
  }

  override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
    val evaluator = context.evaluator

    if (evaluator.isMemberInClass(constructor, "androidx.cardview.widget.CardView")) {
      context.report(
        issue = CARD_VIEW_USAGE,
        scope = node,
        location = context.getLocation(node),
        message = "Using 'androidx.cardview.widget.CardView' instead of com.google.android.material.card.MaterialCardView",
        quickfixData = quickFixIssueAlertDialogBuilder(node)
      )
    }
  }

  private fun quickFixIssueAlertDialogBuilder(alertBuilderCall: UCallExpression): LintFix {
    val arguments = alertBuilderCall.valueArguments
    val context = arguments[0]

    var fixSource = "new com.google.android.material.card.MaterialCardView"

    //Context context, AttributeSet attrs, int defStyleAttr
    when (arguments.size) {
      1 -> fixSource += String.format("(%s)", context)
      2 -> {
        val attrs = arguments[1]
        fixSource += String.format("(%s, %s)", context, attrs)
      }

      3 -> {
        val attributes = arguments[1]
        val defStyleAttr = arguments[2]
        fixSource += String.format("(%s, %s, %s)", context, attributes, defStyleAttr)
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
    val CARD_VIEW_USAGE: Issue = Issue.create(
      id = "CardViewUsage",
      briefDescription = "Utilizing CardView instead of MaterialCardView subclass",
      explanation = "Signal utilizes MaterialCardView for more consistent and pleasant CardViews.",
      category = MESSAGES,
      priority = 5,
      severity = WARNING,
      implementation = Implementation(CardViewDetector::class.java, JAVA_FILE_SCOPE)
    )
  }
}
