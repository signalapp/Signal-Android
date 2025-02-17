package org.signal.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category.Companion.MESSAGES
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope.Companion.JAVA_FILE_SCOPE
import com.android.tools.lint.detector.api.Severity.ERROR
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass
import java.util.Locale

class ThreadIdDatabaseDetector : Detector(), SourceCodeScanner {
  override fun getApplicableUastTypes(): List<Class<UClass>> {
    return listOf(UClass::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitClass(node: UClass) {
        if (node.qualifiedName == null) {
          return
        }

        if (node.extendsList == null) {
          return
        }

        if (EXEMPTED_CLASSES.contains(node.qualifiedName)) {
          return
        }

        val referencedTypes = node.extendsList?.referencedTypes.orEmpty()
        val doesNotExtendDatabase = referencedTypes.none { classType -> "Database" == classType.className }
        if (doesNotExtendDatabase) {
          return
        }

        val implementsReference = node.interfaces.any { nodeInterface ->
          "org.thoughtcrime.securesms.database.ThreadIdDatabaseReference" == nodeInterface.qualifiedName
        }
        if (implementsReference) {
          return
        }

        val recipientFields = node.allFields
          .filter { field -> field.type.equalsToText("java.lang.String") }
          .filter { field -> field.name.lowercase(Locale.getDefault()).contains("thread") }

        for (field in recipientFields) {
          context.report(
            issue = THREAD_ID_DATABASE_REFERENCE_ISSUE,
            scope = field,
            location = context.getLocation(field),
            message = "If you reference a thread ID in your table, you must implement the ThreadIdDatabaseReference interface.",
            quickfixData = null
          )
        }
      }
    }
  }

  companion object {
    val THREAD_ID_DATABASE_REFERENCE_ISSUE: Issue = Issue.create(
      id = "ThreadIdDatabaseReferenceUsage",
      briefDescription = "Referencing a thread ID in a database without implementing ThreadIdDatabaseReference.",
      explanation = "If you reference a thread ID in a column, you need to be able to handle the remapping of one thread ID to another, which ThreadIdDatabaseReference enforces.",
      category = MESSAGES,
      priority = 5,
      severity = ERROR,
      implementation = Implementation(ThreadIdDatabaseDetector::class.java, JAVA_FILE_SCOPE)
    )

    private val EXEMPTED_CLASSES = setOf("org.thoughtcrime.securesms.database.ThreadDatabase")
  }
}
