package org.signal.fastlint.rules

import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
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
 * Flags calls to the app logger (org.signal.core.util.logging.Log) that pass an inline string tag
 * instead of a tag constant.
 */
object LogTagInlinedRule : KtCallRule, JavaCallRule {

  private val LOG_METHODS = setOf("v", "d", "i", "w", "e", "wtf")
  private const val SIGNAL_LOG = "org.signal.core.util.logging.Log"

  override fun onCall(call: KtCallExpression, context: KotlinFileContext, reporter: Reporter) {
    val callee = (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return
    if (callee !in LOG_METHODS) {
      return
    }
    val receiver = (call.getQualifiedExpressionForSelector() as? KtDotQualifiedExpression)?.receiverExpression?.text ?: return
    if (context.resolveFqn(receiver) != SIGNAL_LOG) {
      return
    }
    val firstArg = call.valueArguments.firstOrNull()?.getArgumentExpression()
    if (firstArg != null && firstArg !is KtNameReferenceExpression && firstArg !is KtDotQualifiedExpression) {
      reporter.report("LogTagInlined", call, context.lineOf(call.textOffset), "Not using a tag constant")
    }
  }

  override fun onCall(call: PsiMethodCallExpression, context: JavaFileContext, reporter: Reporter) {
    val callee = call.methodExpression.referenceName ?: return
    if (callee !in LOG_METHODS) {
      return
    }
    val receiver = call.methodExpression.qualifierExpression?.text ?: return
    if (context.resolveFqn(receiver) != SIGNAL_LOG) {
      return
    }
    val firstArg = call.argumentList.expressions.firstOrNull()
    if (firstArg != null && firstArg !is PsiReferenceExpression) {
      reporter.report("LogTagInlined", call, context.lineOf(call.textOffset), "Not using a tag constant")
    }
  }
}
