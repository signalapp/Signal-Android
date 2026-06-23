package org.signal.fastlint.rules

import com.intellij.psi.PsiMethodCallExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.signal.fastlint.JavaCallRule
import org.signal.fastlint.JavaFileContext
import org.signal.fastlint.KotlinFileContext
import org.signal.fastlint.KtCallRule
import org.signal.fastlint.Reporter

/**
 * Flags logging methods (v/d/i/w/e/wtf) called on any receiver other than the app's logger
 * (org.signal.core.util.logging.Log). The single-character method names keep this targeted at
 * logger-style calls.
 *
 * Allowed besides Log itself: chains off Log such as Log.internal(), the sensitive-data logger
 * SensitiveLog, and any call within the logging package (which implements the logger).
 */
object LogNotSignalRule : KtCallRule, JavaCallRule {

  private val LOG_METHODS = setOf("v", "d", "i", "w", "e", "wtf")
  private const val SIGNAL_LOG = "org.signal.core.util.logging.Log"
  private const val SENSITIVE_LOG = "org.signal.registration.util.SensitiveLog"
  private const val LOGGING_PACKAGE = "org.signal.core.util.logging"

  // Logger implementations delegate to a wrapped logger, so we do not flag them against themselves.
  private val LOGGER_IMPLEMENTATION_FILES = setOf("SensitiveLog.kt", "SensitiveLog.java")

  override fun onCall(call: KtCallExpression, context: KotlinFileContext, reporter: Reporter) {
    val callee = (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return
    if (callee !in LOG_METHODS) {
      return
    }
    if (isLoggerImplementation(context.file.name, context.ktFile.packageFqName.asString())) {
      return
    }
    val receiver = (call.getQualifiedExpressionForSelector() as? KtDotQualifiedExpression)?.receiverExpression?.text ?: return
    val fqn = context.resolveFqn(receiver)
    if (!isAllowedLogger(fqn)) {
      reporter.report("LogNotSignal", call, context.lineOf(call.textOffset), "Using '$fqn' instead of a Signal Logger")
    }
  }

  override fun onCall(call: PsiMethodCallExpression, context: JavaFileContext, reporter: Reporter) {
    val callee = call.methodExpression.referenceName ?: return
    if (callee !in LOG_METHODS) {
      return
    }
    if (isLoggerImplementation(context.file.name, context.javaFile.packageName)) {
      return
    }
    val receiver = call.methodExpression.qualifierExpression?.text ?: return
    val fqn = context.resolveFqn(receiver)
    if (!isAllowedLogger(fqn)) {
      reporter.report("LogNotSignal", call, context.lineOf(call.textOffset), "Using '$fqn' instead of a Signal Logger")
    }
  }

  private fun isAllowedLogger(fqn: String): Boolean {
    return fqn == SIGNAL_LOG || fqn.startsWith("$SIGNAL_LOG.") || fqn == SENSITIVE_LOG
  }

  private fun isLoggerImplementation(fileName: String, packageName: String): Boolean {
    return packageName == LOGGING_PACKAGE || fileName in LOGGER_IMPLEMENTATION_FILES
  }
}
