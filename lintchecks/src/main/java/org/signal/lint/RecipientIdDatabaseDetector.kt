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

class RecipientIdDatabaseDetector : Detector(), SourceCodeScanner {
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

        val doesNotExtendDatabase = node.extendsList?.referencedTypes.orEmpty().none { it.className == "Database" }
        if (doesNotExtendDatabase) {
          return
        }

        val implementsReference = node.interfaces.any { it.qualifiedName == "org.thoughtcrime.securesms.database.RecipientIdDatabaseReference" }
        if (implementsReference) {
          return
        }

        val recipientFields = node.allFields
          .filter { it.type.equalsToText("java.lang.String") }
          .filter { it.name.lowercase(Locale.getDefault()).contains("recipient") }

        for (field in recipientFields) {
          context.report(
            issue = RECIPIENT_ID_DATABASE_REFERENCE_ISSUE,
            scope = field,
            location = context.getLocation(field),
            message = "If you reference a RecipientId in your table, you must implement the RecipientIdDatabaseReference interface.",
            quickfixData = null
          )
        }
      }
    }
  }

  companion object {
    val RECIPIENT_ID_DATABASE_REFERENCE_ISSUE: Issue = Issue.create(
      id = "RecipientIdDatabaseReferenceUsage",
      briefDescription = "Referencing a RecipientId in a database without implementing RecipientIdDatabaseReference.",
      explanation = "If you reference a RecipientId in a column, you need to be able to handle the remapping of one RecipientId to another, which RecipientIdDatabaseReference enforces.",
      category = MESSAGES,
      priority = 5,
      severity = ERROR,
      implementation = Implementation(RecipientIdDatabaseDetector::class.java, JAVA_FILE_SCOPE)
    )

    private val EXEMPTED_CLASSES = setOf("org.thoughtcrime.securesms.database.RecipientDatabase")
  }
}