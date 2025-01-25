package org.signal.lint

import com.android.tools.lint.detector.api.Category.Companion.MESSAGES
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope.Companion.JAVA_FILE_SCOPE
import com.android.tools.lint.detector.api.Severity.ERROR
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class StartForegroundServiceDetector : Detector(), Detector.UastScanner {
  override fun getApplicableMethodNames(): List<String> {
    return listOf("startForegroundService")
  }

  override fun visitMethodCall(context: JavaContext, call: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator

    val classes = context.uastFile?.classes.orEmpty()
    val isForegroundServiceUtil = classes.any { it.name == "ForegroundServiceUtil" }
    if (isForegroundServiceUtil) {
      return
    }

    if (evaluator.isMemberInClass(method, "androidx.core.content.ContextCompat")) {
      context.report(
        issue = START_FOREGROUND_SERVICE_ISSUE,
        scope = call,
        location = context.getLocation(call),
        message = "Using 'ContextCompat.startForegroundService' instead of a ForegroundServiceUtil"
      )
    } else if (evaluator.isMemberInClass(method, "android.content.Context")) {
      context.report(
        issue = START_FOREGROUND_SERVICE_ISSUE,
        scope = call,
        location = context.getLocation(call),
        message = "Using 'Context.startForegroundService' instead of a ForegroundServiceUtil"
      )
    }
  }

  companion object {
    val START_FOREGROUND_SERVICE_ISSUE: Issue = Issue.create(
      id = "StartForegroundServiceUsage",
      briefDescription = "Starting a foreground service using ContextCompat.startForegroundService instead of ForegroundServiceUtil",
      explanation = "Starting a foreground service may fail, and we should prefer our utils to make sure they're started correctly",
      category = MESSAGES,
      priority = 5,
      severity = ERROR,
      implementation = Implementation(StartForegroundServiceDetector::class.java, JAVA_FILE_SCOPE)
    )
  }
}
