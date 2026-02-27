/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import androidx.annotation.ColorInt
import org.signal.core.util.BidiUtil
import org.signal.core.util.BreakIteratorCompat
import org.signal.core.util.StringUtil
import org.thoughtcrime.securesms.components.emoji.EmojiUtil

/**
 * A member's custom label within a group.
 */
data class MemberLabel(
  val emoji: String?,
  val text: String
) {

  /**
   * The label text formatted for display.
   *
   * Use this in all rendering contexts. Use [text] only when comparing or persisting values.
   */
  val displayText: String get() = BidiUtil.isolateBidi(text)

  companion object {
    const val MIN_LABEL_GRAPHEMES = 1
    const val MAX_LABEL_GRAPHEMES = 24
    const val MAX_LABEL_BYTES = 96
    const val MAX_EMOJI_BYTES = 48

    /**
     * Truncates label [text] to the grapheme and byte limits without any whitespace normalization.
     */
    @JvmStatic
    fun truncateLabelText(text: String): String {
      val breakIterator = BreakIteratorCompat.getInstance()
      breakIterator.setText(text)
      val graphemeTruncated = breakIterator.take(MAX_LABEL_GRAPHEMES).toString()
      return StringUtil.trimToFit(graphemeTruncated, MAX_LABEL_BYTES)
    }

    /**
     * Sanitizes and truncates label [text].
     */
    @JvmStatic
    fun sanitizeLabelText(text: String): String {
      val collapsed = StringUtil.trimToVisualBounds(text.replace(Regex("\\s+"), " "))
      return truncateLabelText(collapsed)
    }

    /**
     * Returns [emoji] if it is a single valid emoji within [MAX_EMOJI_BYTES], otherwise null.
     */
    @JvmStatic
    fun sanitizeEmoji(emoji: String?): String? {
      val trimmed = StringUtil.trimToFit(emoji, MAX_EMOJI_BYTES)
      return trimmed.takeIf { it.isNotBlank() && EmojiUtil.isEmoji(it) }
    }
  }
}

data class StyledMemberLabel(
  val label: MemberLabel,
  @param:ColorInt val tintColor: Int
)
