package org.thoughtcrime.securesms.conversation.ui.inlinequery

import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.adapter.mapping.AnyMappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

class InlineQueryAdapter(listener: (AnyMappingModel) -> Unit) : MappingAdapter() {
  init {
    registerFactory(InlineQueryEmojiResult.Model::class.java, { InlineQueryEmojiResult.ViewHolder(it, listener) }, R.layout.inline_query_emoji_result)
  }
}
