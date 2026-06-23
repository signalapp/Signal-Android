package org.signal.fastlint.rules

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import org.signal.fastlint.JavaClassRule
import org.signal.fastlint.JavaFileContext
import org.signal.fastlint.KotlinFileContext
import org.signal.fastlint.KtClassRule
import org.signal.fastlint.Reporter
import java.util.Locale

/**
 * A class extending "Database" with a String column whose name contains "recipient"/"thread" must
 * implement Recipient/ThreadIdDatabaseReference. Type information is approximated syntactically.
 */
object DatabaseReferenceRule : KtClassRule, JavaClassRule {

  override fun onClass(klass: KtClassOrObject, context: KotlinFileContext, reporter: Reporter) {
    val fields = klass.body?.properties.orEmpty().filter { it.isStringTyped() }.mapNotNull { it.name }
    check(klass, context.lineOf(klass.textOffset), klass.superTypeListEntries.map { it.text }, emptyList(), fields, reporter)
  }

  override fun onClass(klass: PsiClass, context: JavaFileContext, reporter: Reporter) {
    // Read fields syntactically (direct children only) to avoid building a member cache, which would
    // resolve superclasses and route into the (uninitialized) Kotlin resolver.
    val fields = PsiTreeUtil.getChildrenOfTypeAsList(klass, PsiField::class.java)
      .filter { it.typeElement?.text == "String" || it.typeElement?.text == "java.lang.String" }
      .map { it.name }
    check(
      element = klass,
      line = context.lineOf(klass.textOffset),
      superTypes = klass.extendsList?.referenceElements?.map { it.text }.orEmpty(),
      implementsTypes = klass.implementsList?.referenceElements?.map { it.text }.orEmpty(),
      fieldNames = fields,
      reporter = reporter
    )
  }

  private fun check(
    element: PsiElement,
    line: Int,
    superTypes: List<String>,
    implementsTypes: List<String>,
    fieldNames: List<String>,
    reporter: Reporter
  ) {
    val all = superTypes + implementsTypes
    val extendsDatabase = all.any { it.substringBefore('(').substringAfterLast('.').trim() == "Database" }
    if (!extendsDatabase) {
      return
    }

    val implementsRecipient = all.any { it.contains("RecipientIdDatabaseReference") }
    val implementsThread = all.any { it.contains("ThreadIdDatabaseReference") }

    fieldNames.forEach { name ->
      val lower = name.lowercase(Locale.US)
      if (!implementsRecipient && lower.contains("recipient")) {
        reporter.report("RecipientIdDatabaseReferenceUsage", element, line, "References a RecipientId ('$name') without implementing RecipientIdDatabaseReference")
      }
      if (!implementsThread && lower.contains("thread")) {
        reporter.report("ThreadIdDatabaseReferenceUsage", element, line, "References a thread id ('$name') without implementing ThreadIdDatabaseReference")
      }
    }
  }

  private fun KtProperty.isStringTyped(): Boolean {
    val typeText = typeReference?.text ?: return false
    return typeText == "String" || typeText == "kotlin.String"
  }
}
