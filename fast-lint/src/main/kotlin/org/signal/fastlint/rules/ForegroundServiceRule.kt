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

/** Flags Context/ContextCompat.startForegroundService outside ForegroundServiceUtil. */
object ForegroundServiceRule : KtCallRule, JavaCallRule {

  private const val MESSAGE = "Using startForegroundService instead of ForegroundServiceUtil"

  override fun onCall(call: KtCallExpression, context: KotlinFileContext, reporter: Reporter) {
    val callee = (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return
    if (callee != "startForegroundService" || context.file.name == "ForegroundServiceUtil.kt") {
      return
    }
    val receiver = (call.getQualifiedExpressionForSelector() as? KtDotQualifiedExpression)?.receiverExpression?.text
    if (isLikelyContextReceiver(receiver)) {
      reporter.report("StartForegroundServiceUsage", call, context.lineOf(call.textOffset), MESSAGE)
    }
  }

  override fun onCall(call: PsiMethodCallExpression, context: JavaFileContext, reporter: Reporter) {
    val callee = call.methodExpression.referenceName ?: return
    if (callee != "startForegroundService" || context.file.name == "ForegroundServiceUtil.java") {
      return
    }
    if (isLikelyContextReceiver(call.methodExpression.qualifierExpression?.text)) {
      reporter.report("StartForegroundServiceUsage", call, context.lineOf(call.textOffset), MESSAGE)
    }
  }

  /**
   * The rule only targets the framework call (Context / ContextCompat). Without type resolution we
   * approximate: a null/lowercase receiver is a context instance, "ContextCompat" is the helper; an
   * upper-case qualifier is a class (a Signal wrapper like FcmFetchManager), so skip it.
   */
  private fun isLikelyContextReceiver(receiver: String?): Boolean {
    return receiver == null || receiver == "ContextCompat" || receiver.firstOrNull()?.isLowerCase() == true
  }
}
