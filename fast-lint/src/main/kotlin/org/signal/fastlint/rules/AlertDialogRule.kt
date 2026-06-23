package org.signal.fastlint.rules

import com.intellij.psi.PsiNewExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.signal.fastlint.JavaFileContext
import org.signal.fastlint.JavaNewRule
import org.signal.fastlint.KotlinFileContext
import org.signal.fastlint.KtCallRule
import org.signal.fastlint.Reporter

/** Flags framework/appcompat AlertDialog.Builder usage in favor of MaterialAlertDialogBuilder. */
object AlertDialogRule : KtCallRule, JavaNewRule {

  private val ALERT_DIALOG_FQNS = setOf("android.app.AlertDialog", "androidx.appcompat.app.AlertDialog")

  private fun message(fqn: String) = "Using $fqn.Builder instead of MaterialAlertDialogBuilder"

  override fun onCall(call: KtCallExpression, context: KotlinFileContext, reporter: Reporter) {
    val callee = (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return
    if (callee != "Builder") {
      return
    }
    val receiver = (call.getQualifiedExpressionForSelector() as? KtDotQualifiedExpression)?.receiverExpression?.text ?: return
    if (!receiver.endsWith("AlertDialog")) {
      return
    }
    val fqn = context.resolveFqn(receiver)
    if (fqn in ALERT_DIALOG_FQNS) {
      reporter.report("AlertDialogBuilderUsage", call, context.lineOf(call.textOffset), message(fqn))
    }
  }

  override fun onNew(expression: PsiNewExpression, context: JavaFileContext, reporter: Reporter) {
    val ref = expression.classReference?.text ?: return
    if (!ref.endsWith("AlertDialog.Builder")) {
      return
    }
    val fqn = context.resolveFqn(ref.removeSuffix(".Builder"))
    if (fqn in ALERT_DIALOG_FQNS) {
      reporter.report("AlertDialogBuilderUsage", expression, context.lineOf(expression.textOffset), message(fqn))
    }
  }
}
