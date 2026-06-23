package org.signal.fastlint.rules

import com.intellij.psi.PsiReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.signal.fastlint.JavaFileContext
import org.signal.fastlint.JavaReferenceRule
import org.signal.fastlint.KotlinFileContext
import org.signal.fastlint.KtNameRule
import org.signal.fastlint.Reporter

/** Flags references to Build.VERSION_CODES.* constants; Signal convention is the numeric API level. */
object VersionCodeRule : KtNameRule, JavaReferenceRule {

  private const val MESSAGE = "Using 'VERSION_CODES' reference instead of the numeric value"

  override fun onName(name: KtSimpleNameExpression, context: KotlinFileContext, reporter: Reporter) {
    if (name.getReferencedName() == "VERSION_CODES") {
      reporter.report("VersionCodeUsage", name, context.lineOf(name.textOffset), MESSAGE)
    }
  }

  override fun onReference(reference: PsiReferenceExpression, context: JavaFileContext, reporter: Reporter) {
    if (reference.referenceName == "VERSION_CODES") {
      reporter.report("VersionCodeUsage", reference, context.lineOf(reference.textOffset), MESSAGE)
    }
  }
}
