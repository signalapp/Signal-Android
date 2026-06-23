package org.signal.fastlint

import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.java.syntax.JavaElementTypeConverterExtension
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.syntax.psi.CommonElementTypeConverterFactory
import com.intellij.platform.syntax.psi.ElementTypeConverters
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.signal.fastlint.rules.ALL_RULES
import java.io.File
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException

/** Aggregate result of a repository scan. */
data class RunResult(
  val kotlinFiles: Int,
  val javaFiles: Int,
  val xmlFiles: Int,
  val findings: List<Finding>
)

/**
 * The fast-lint engine. Sets up the Kotlin/Java PSI front-ends once, partitions the registered
 * [rules] by the node types they handle, and analyzes Kotlin, Java, and XML sources. Reusable across
 * many files; not thread-safe (PSI parsing is single-threaded). Close to release the parser.
 */
class FastLint(rules: List<Rule> = ALL_RULES) : AutoCloseable {

  private val ktCallRules = rules.filterIsInstance<KtCallRule>()
  private val ktNameRules = rules.filterIsInstance<KtNameRule>()
  private val ktClassRules = rules.filterIsInstance<KtClassRule>()
  private val javaCallRules = rules.filterIsInstance<JavaCallRule>()
  private val javaNewRules = rules.filterIsInstance<JavaNewRule>()
  private val javaReferenceRules = rules.filterIsInstance<JavaReferenceRule>()
  private val javaClassRules = rules.filterIsInstance<JavaClassRule>()
  private val xmlElementRules = rules.filterIsInstance<XmlElementRule>()
  private val xmlStringResourceRules = rules.filterIsInstance<XmlStringResourceRule>()

  private val disposable: Disposable = Disposer.newDisposable()
  private val ktFactory: KtPsiFactory
  private val psiFactory: PsiFileFactory

  init {
    val env = createKotlinEnvironment(disposable)
    registerJavaConverter(disposable)
    ktFactory = KtPsiFactory(env.project, markGenerated = false)
    psiFactory = PsiFileFactory.getInstance(env.project)
  }

  fun run(root: File): RunResult {
    var kotlin = 0
    var java = 0
    var xml = 0
    val findings = ArrayList<Finding>()
    for (file in sourceFiles(root)) {
      val source = file.readText()
      when (file.extension) {
        "kt" -> {
          findings.addAll(analyzeKotlin(file, source))
          kotlin++
        }
        "java" -> {
          findings.addAll(analyzeJava(file, source))
          java++
        }
        "xml" -> {
          findings.addAll(analyzeXml(file, source))
          xml++
        }
      }
    }
    return RunResult(kotlin, java, xml, findings)
  }

  fun analyzeKotlin(file: File, source: String): List<Finding> {
    val ktFile = ktFactory.createFile(file.name, source)
    val context = KotlinFileContext(file, ktFile)
    val findings = ArrayList<Finding>()
    val reporter = SuppressingReporter(file, findings)

    ktFile.accept(object : KtTreeVisitorVoid() {
      override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        for (rule in ktCallRules) rule.onCall(expression, context, reporter)
      }

      override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        super.visitSimpleNameExpression(expression)
        for (rule in ktNameRules) rule.onName(expression, context, reporter)
      }

      override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        super.visitClassOrObject(classOrObject)
        for (rule in ktClassRules) rule.onClass(classOrObject, context, reporter)
      }
    })
    return findings
  }

  fun analyzeJava(file: File, source: String): List<Finding> {
    val javaFile = psiFactory.createFileFromText(file.name, JavaLanguage.INSTANCE, source) as? PsiJavaFile ?: return emptyList()
    val context = JavaFileContext(file, javaFile, source)
    val findings = ArrayList<Finding>()
    val reporter = SuppressingReporter(file, findings)

    javaFile.accept(object : JavaRecursiveElementVisitor() {
      override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
        super.visitMethodCallExpression(call)
        for (rule in javaCallRules) rule.onCall(call, context, reporter)
      }

      override fun visitNewExpression(expression: PsiNewExpression) {
        super.visitNewExpression(expression)
        for (rule in javaNewRules) rule.onNew(expression, context, reporter)
      }

      override fun visitReferenceExpression(expression: PsiReferenceExpression) {
        super.visitReferenceExpression(expression)
        for (rule in javaReferenceRules) rule.onReference(expression, context, reporter)
      }

      override fun visitClass(aClass: PsiClass) {
        super.visitClass(aClass)
        for (rule in javaClassRules) rule.onClass(aClass, context, reporter)
      }
    })
    return findings
  }

  fun analyzeXml(file: File, source: String): List<Finding> {
    val path = file.path
    val isLayout = "/res/layout" in path
    val isValues = VALUES_FILE.containsMatchIn(path)
    if (!isLayout && !isValues) {
      return emptyList()
    }
    if (xmlElementRules.isEmpty() && xmlStringResourceRules.isEmpty()) {
      return emptyList()
    }

    val context = XmlFileContext(file, isLayout, isValues)
    val findings = ArrayList<Finding>()
    val ignoreStack = ArrayDeque<Set<String>>()

    val reader = XML_INPUT_FACTORY.createXMLStreamReader(StringReader(source))
    try {
      var inString = false
      var stringName: String? = null
      var stringLine = 0
      var stringIgnores: Set<String> = emptySet()
      val stringText = StringBuilder()

      while (reader.hasNext()) {
        when (reader.next()) {
          XMLStreamConstants.START_ELEMENT -> {
            val line = reader.location.lineNumber
            val here = HashSet<String>()
            val attributes = ArrayList<XmlAttribute>(reader.attributeCount)
            for (i in 0 until reader.attributeCount) {
              val prefix = reader.getAttributePrefix(i) ?: ""
              val local = reader.getAttributeLocalName(i)
              val value = reader.getAttributeValue(i)
              attributes.add(XmlAttribute(prefix, local, value))
              if (prefix == "tools" && local == "ignore") {
                value.split(',').forEach { here.add(it.trim()) }
              }
            }

            if (xmlElementRules.isNotEmpty()) {
              val element = XmlStartElement(reader.localName, line, attributes)
              val sink = IgnoringXmlSink(file, findings, here, ignoreStack)
              for (rule in xmlElementRules) rule.onStartElement(element, context, sink)
            }

            if (isValues && reader.localName == "string" && xmlStringResourceRules.isNotEmpty()) {
              inString = true
              stringName = attributes.firstOrNull { it.localName == "name" }?.value
              stringLine = line
              stringIgnores = unionIgnores(here, ignoreStack)
              stringText.setLength(0)
            }
            ignoreStack.addLast(here)
          }

          XMLStreamConstants.CHARACTERS -> {
            if (inString) {
              stringText.append(reader.text)
            }
          }

          XMLStreamConstants.END_ELEMENT -> {
            if (inString && reader.localName == "string") {
              val sink = SetIgnoringXmlSink(file, findings, stringIgnores)
              for (rule in xmlStringResourceRules) rule.onStringResource(stringName, stringText.toString(), stringLine, context, sink)
              inString = false
            }
            ignoreStack.removeLast()
          }
        }
      }
    } catch (e: XMLStreamException) {
      // Skip files that aren't well-formed standalone XML (e.g. fragments); lint skips these too.
    } finally {
      reader.close()
    }
    return findings
  }

  override fun close() = Disposer.dispose(disposable)

  private class SuppressingReporter(val file: File, val out: MutableList<Finding>) : Reporter {
    override fun report(checkId: String, element: PsiElement, line: Int, message: String) {
      if (!isSuppressed(element, checkId)) {
        out.add(Finding(checkId, file, line, message))
      }
    }
  }

  private class IgnoringXmlSink(
    val file: File,
    val out: MutableList<Finding>,
    val here: Set<String>,
    val ancestors: ArrayDeque<Set<String>>
  ) : XmlSink {
    override fun report(checkId: String, line: Int, message: String) {
      if (here.contains(checkId) || here.contains("all")) {
        return
      }
      if (ancestors.any { it.contains(checkId) || it.contains("all") }) {
        return
      }
      out.add(Finding(checkId, file, line, message))
    }
  }

  private class SetIgnoringXmlSink(val file: File, val out: MutableList<Finding>, val ignores: Set<String>) : XmlSink {
    override fun report(checkId: String, line: Int, message: String) {
      if (ignores.contains(checkId) || ignores.contains("all")) {
        return
      }
      out.add(Finding(checkId, file, line, message))
    }
  }

  companion object {
    private val VALUES_FILE = Regex("/res/values/.*\\.xml$")
    private val EXCLUDED_DIRS = setOf("build", ".git", ".gradle", ".idea", "test", "androidTest", "testFixtures")

    // Vendored third-party forks (Glide, PhotoView) are not held to Signal conventions.
    private val EXCLUDED_MODULE_PATHS = listOf("lib/glide/", "lib/photoview/")

    private val XML_INPUT_FACTORY: XMLInputFactory = XMLInputFactory.newInstance().apply {
      setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true)
      setProperty(XMLInputFactory.SUPPORT_DTD, false)
    }

    private fun unionIgnores(here: Set<String>, ancestors: ArrayDeque<Set<String>>): Set<String> {
      if (here.isEmpty() && ancestors.all { it.isEmpty() }) {
        return emptySet()
      }
      val result = HashSet(here)
      ancestors.forEach { result.addAll(it) }
      return result
    }

    /**
     * KotlinCoreEnvironment registers the Kotlin token->IElementType converter for the new
     * platform.syntax framework, but not Java's, so parsing Java source otherwise fails with
     * "IElementType for token WHITE_SPACE is missing". Register the common + Java converters ourselves.
     */
    private fun registerJavaConverter(disposable: Disposable) {
      val converters = ElementTypeConverters.instance
      converters.addExplicitExtension(JavaLanguage.INSTANCE, CommonElementTypeConverterFactory(), disposable)
      converters.addExplicitExtension(JavaLanguage.INSTANCE, JavaElementTypeConverterExtension(), disposable)
    }

    // createForProduction is K1 API (opt-in, slated to become an error in Kotlin 2.3). We
    // deliberately use the parse-only K1 PSI environment (as ktlint does); migrating to the
    // Analysis API is unnecessary for syntax-only checks.
    @OptIn(K1Deprecation::class)
    private fun createKotlinEnvironment(disposable: Disposable): KotlinCoreEnvironment {
      val config = CompilerConfiguration().apply {
        put(CommonConfigurationKeys.MODULE_NAME, "fastlint")
        put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
      }
      return KotlinCoreEnvironment.createForProduction(disposable, config, EnvironmentConfigFiles.JVM_CONFIG_FILES)
    }

    fun sourceFiles(root: File): List<File> {
      val rootPath = root.path
      val out = ArrayList<File>(16384)
      root.walkTopDown()
        .onEnter { it.name !in EXCLUDED_DIRS }
        .forEach { f ->
          if (f.isFile && "/src/" in f.path && "/test/" !in f.path && "/androidTest/" !in f.path) {
            val relative = f.path.removePrefix(rootPath).trimStart('/')
            if (EXCLUDED_MODULE_PATHS.any { relative.startsWith(it) }) {
              return@forEach
            }
            when (f.extension) {
              "kt", "java", "xml" -> out.add(f)
            }
          }
        }
      return out
    }
  }
}
