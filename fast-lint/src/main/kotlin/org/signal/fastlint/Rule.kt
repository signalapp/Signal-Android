package org.signal.fastlint

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import java.io.File

/**
 * Marker for a fast-lint rule. A rule implements one or more of the capability interfaces below
 * (e.g. [KtCallRule], [JavaClassRule], [XmlElementRule]) for the node types it cares about. The
 * engine partitions registered rules by capability, so a rule is only invoked for the node types
 * it actually handles. Rules are stateless and registered in [org.signal.fastlint.rules.ALL_RULES].
 */
interface Rule

interface KtCallRule : Rule {
  fun onCall(call: KtCallExpression, context: KotlinFileContext, reporter: Reporter)
}

interface KtNameRule : Rule {
  fun onName(name: KtSimpleNameExpression, context: KotlinFileContext, reporter: Reporter)
}

interface KtClassRule : Rule {
  fun onClass(klass: KtClassOrObject, context: KotlinFileContext, reporter: Reporter)
}

interface JavaCallRule : Rule {
  fun onCall(call: PsiMethodCallExpression, context: JavaFileContext, reporter: Reporter)
}

interface JavaNewRule : Rule {
  fun onNew(expression: PsiNewExpression, context: JavaFileContext, reporter: Reporter)
}

interface JavaReferenceRule : Rule {
  fun onReference(reference: PsiReferenceExpression, context: JavaFileContext, reporter: Reporter)
}

interface JavaClassRule : Rule {
  fun onClass(klass: PsiClass, context: JavaFileContext, reporter: Reporter)
}

interface XmlElementRule : Rule {
  fun onStartElement(element: XmlStartElement, context: XmlFileContext, sink: XmlSink)
}

interface XmlStringResourceRule : Rule {
  fun onStringResource(name: String?, value: String, line: Int, context: XmlFileContext, sink: XmlSink)
}

/** Reports findings for code (Kotlin/Java) rules. Handles @Suppress/@SuppressLint suppression. */
interface Reporter {
  fun report(checkId: String, element: PsiElement, line: Int, message: String)
}

/** Reports findings for XML rules. Handles tools:ignore suppression. */
interface XmlSink {
  fun report(checkId: String, line: Int, message: String)
}

/** Per-file context for Kotlin rules: the imports table and offset->line / receiver resolution. */
class KotlinFileContext(val file: File, val ktFile: KtFile) {
  val imports: Map<String, String> = buildKotlinImports(ktFile)
  fun lineOf(offset: Int): Int = lineNumberOf(ktFile.text, offset)
  fun resolveFqn(receiver: String): String = resolveReceiver(receiver, imports)
}

/** Per-file context for Java rules. */
class JavaFileContext(val file: File, val javaFile: PsiJavaFile, val text: CharSequence) {
  val imports: Map<String, String> = buildJavaImports(javaFile)
  fun lineOf(offset: Int): Int = lineNumberOf(text, offset)
  fun resolveFqn(receiver: String): String = resolveReceiver(receiver, imports)
}

/** Per-file context for XML rules. */
class XmlFileContext(val file: File, val isLayout: Boolean, val isValues: Boolean)

class XmlAttribute(val prefix: String, val localName: String, val value: String)

class XmlStartElement(val localName: String, val line: Int, val attributes: List<XmlAttribute>)

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

private val SUPPRESS_ANNOTATIONS = setOf("Suppress", "SuppressLint", "SuppressWarnings")

internal fun lineNumberOf(text: CharSequence, offset: Int): Int {
  var line = 1
  val end = minOf(offset, text.length)
  var i = 0
  while (i < end) {
    if (text[i] == '\n') {
      line++
    }
    i++
  }
  return line
}

/** Resolves a receiver like "Log" or "Build.VERSION_CODES" to a fully-qualified name via imports. */
internal fun resolveReceiver(receiver: String, imports: Map<String, String>): String {
  val head = receiver.substringBefore('.')
  val mapped = imports[head] ?: return receiver
  return if ('.' in receiver) {
    mapped + receiver.substring(receiver.indexOf('.'))
  } else {
    mapped
  }
}

private fun buildKotlinImports(ktFile: KtFile): Map<String, String> {
  val imports = HashMap<String, String>()
  ktFile.importList?.imports?.forEach { imp ->
    val fq = imp.importedFqName?.asString() ?: return@forEach
    imports[imp.aliasName ?: fq.substringAfterLast('.')] = fq
  }
  return imports
}

private fun buildJavaImports(javaFile: PsiJavaFile): Map<String, String> {
  val imports = HashMap<String, String>()
  javaFile.importList?.importStatements?.forEach { imp ->
    val qn = imp.qualifiedName ?: return@forEach
    if (!imp.isOnDemand) {
      imports[qn.substringAfterLast('.')] = qn
    }
  }
  return imports
}

/** True if any @Suppress/@SuppressLint/@SuppressWarnings on the element or an ancestor names checkId. */
internal fun isSuppressed(element: PsiElement, checkId: String): Boolean {
  val needle = "\"$checkId\""
  var current: PsiElement? = element
  while (current != null) {
    when (val node = current) {
      is KtModifierListOwner ->
        for (entry in node.annotationEntries) {
          val name = entry.shortName?.asString() ?: continue
          if (name in SUPPRESS_ANNOTATIONS) {
            val args = entry.valueArgumentList?.text ?: ""
            if (args.contains(needle) || args.contains("\"all\"")) {
              return true
            }
          }
        }

      is PsiModifierListOwner ->
        for (annotation: PsiAnnotation in node.modifierList?.annotations.orEmpty()) {
          val name = annotation.nameReferenceElement?.referenceName ?: continue
          if (name in SUPPRESS_ANNOTATIONS) {
            val args = annotation.parameterList.text
            if (args.contains(needle) || args.contains("\"all\"")) {
              return true
            }
          }
        }
    }
    current = current.parent
  }
  return false
}
