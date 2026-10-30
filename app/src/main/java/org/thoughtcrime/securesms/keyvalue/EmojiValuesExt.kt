/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import org.signal.emoji.EmojiUtil
import org.signal.storage.keyvalue.EmojiValues

/**
 * The emoji the user has selected as their default reactions. Any stored reaction that's unreadable is replaced with the default for its slot.
 * For the unfiltered list, see [EmojiValues.rawReactions].
 */
val EmojiValues.reactions: List<String>
  get() {
    val raw = rawReactions
    return EmojiValues.DEFAULT_REACTIONS_LIST.mapIndexed { i, default ->
      if (raw.size > i && EmojiUtil.isEmoji(raw[i])) {
        raw[i]
      } else {
        default
      }
    }
  }

fun EmojiValues.setPreferredVariation(emoji: String) {
  setPreferredVariation(EmojiUtil.getCanonicalRepresentation(emoji), emoji)
}

fun EmojiValues.getPreferredVariation(emoji: String): String {
  return getPreferredVariation(EmojiUtil.getCanonicalRepresentation(emoji), emoji)
}
