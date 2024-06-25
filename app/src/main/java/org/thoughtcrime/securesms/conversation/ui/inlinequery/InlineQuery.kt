package org.thoughtcrime.securesms.conversation.ui.inlinequery

/**
 * Represents an inline query via compose text.
 */
sealed class InlineQuery(val query: String) {
  object NoQuery : InlineQuery("")
  class Emoji(query: String) : InlineQuery(query.replace('_', ' '))
  class Mention(query: String) : InlineQuery(query)
}
