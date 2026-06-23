package org.signal.fastlint.rules

import org.signal.fastlint.XmlFileContext
import org.signal.fastlint.XmlSink
import org.signal.fastlint.XmlStringResourceRule

/**
 * Flags string resource values that aapt rejects:
 *  - an unescaped apostrophe outside a double-quoted span, or an unbalanced (odd) unescaped double
 *    quote — a backslash escapes the next character, an unescaped double quote toggles a "quoting"
 *    span (in which apostrophes are allowed), and an apostrophe outside such a span must be \';
 *  - a value starting with '@' or '?' that is not a resource / theme-attribute reference, which aapt
 *    interprets as a (broken) reference unless escaped as \@ / \?.
 */
object StringResourceEscapingRule : XmlStringResourceRule {

  // A resource reference: @[+][*][package:]type/name, e.g. @string/foo, @+id/foo, @android:id/foo.
  private val RESOURCE_REFERENCE = Regex("^@\\+?\\*?(\\w+:)?\\w+/.+")

  override fun onStringResource(name: String?, value: String, line: Int, context: XmlFileContext, sink: XmlSink) {
    checkQuotes(value, line, sink)
    checkLeadingReferenceCharacter(value, line, sink)
  }

  private fun checkQuotes(value: String, line: Int, sink: XmlSink) {
    var inQuotedSpan = false
    var unescapedDoubleQuotes = 0
    var unescapedApostropheOutsideQuotes = false

    var i = 0
    while (i < value.length) {
      when (value[i]) {
        '\\' -> i++ // The next character is escaped; skip it.
        '"' -> {
          unescapedDoubleQuotes++
          inQuotedSpan = !inQuotedSpan
        }
        '\'' -> if (!inQuotedSpan) {
          unescapedApostropheOutsideQuotes = true
        }
      }
      i++
    }

    if (unescapedApostropheOutsideQuotes) {
      sink.report("StringResourceEscaping", line, "Apostrophe in a string resource must be escaped as \\' or the value wrapped in double quotes")
    }
    if (unescapedDoubleQuotes % 2 != 0) {
      sink.report("StringResourceEscaping", line, "Unbalanced double quote in a string resource; escape a literal quote as \\\"")
    }
  }

  private fun checkLeadingReferenceCharacter(value: String, line: Int, sink: XmlSink) {
    val trimmed = value.trimStart()
    if (trimmed.isEmpty()) {
      return
    }
    when (trimmed[0]) {
      '@' -> if (!isResourceReference(trimmed)) {
        sink.report("StringResourceEscaping", line, "A string resource starting with '@' must be escaped as \\@ unless it is a resource reference")
      }
      '?' -> if (!isThemeReference(trimmed)) {
        sink.report("StringResourceEscaping", line, "A string resource starting with '?' must be escaped as \\? unless it is a theme attribute reference")
      }
    }
  }

  private fun isResourceReference(value: String): Boolean {
    return value == "@null" || value == "@empty" || RESOURCE_REFERENCE.containsMatchIn(value)
  }

  private fun isThemeReference(value: String): Boolean {
    // ?attr/x, ?android:attr/x, or the ?colorPrimary shorthand: '?' followed by an identifier.
    return value.length > 1 && (value[1].isLetter() || value[1] == '_')
  }
}
