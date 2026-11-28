package org.thoughtcrime.securesms.keyboard.emoji

import org.signal.emoji.EmojiCategory
import org.signal.emoji.EmojiPageModel
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel

fun EmojiPageModel.toMappingModels(): List<MappingModel<*>> {
  return displayEmoji.map {
    val isTextEmoji = EmojiCategory.EMOTICONS.key == key

    if (isTextEmoji) {
      EmojiPageViewGridAdapter.EmojiTextModel(key, it)
    } else {
      EmojiPageViewGridAdapter.EmojiModel(key, it)
    }
  }
}
