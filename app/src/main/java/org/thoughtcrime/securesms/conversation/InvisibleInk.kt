/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import org.signal.core.util.utf8Size
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList.BodyRange
import org.whispersystems.signalservice.api.messages.SignalServiceMessageLimits
import java.util.regex.Pattern

internal object InvisibleInk {
  private const val MAX_GRAPHEME_CODE_POINTS = 16
  private val graphemePattern = Pattern.compile("\\X")

  fun isEligible(body: CharSequence, mentionCount: Int, existingRanges: BodyRangeList?): Boolean {
    val styleCount = existingRanges?.ranges?.count { it.style != BodyRange.Style.SPOILER } ?: 0
    return body.isNotBlank() &&
      body.utf8Size() <= SignalServiceMessageLimits.MAX_INLINE_BODY_SIZE_BYTES &&
      mentionCount + styleCount < SignalServiceMessageLimits.MAX_BODY_RANGE_COUNT &&
      olderClientsPreserveFormatting(body)
  }

  fun bodyRanges(body: CharSequence, existingRanges: BodyRangeList?): BodyRangeList {
    val ranges = buildList {
      add(BodyRange(start = 0, length = body.length, style = BodyRange.Style.SPOILER))
      existingRanges?.ranges?.filterTo(this) { it.style != BodyRange.Style.SPOILER }
    }

    return BodyRangeList(ranges = ranges)
  }

  private fun olderClientsPreserveFormatting(body: CharSequence): Boolean {
    // Older iOS recipients can discard all style ranges when display sanitization rewrites the body.
    if (body.any { it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' }) {
      return false
    }

    val matcher = graphemePattern.matcher(body)
    while (matcher.find()) {
      if (Character.codePointCount(body, matcher.start(), matcher.end()) > MAX_GRAPHEME_CODE_POINTS) {
        return false
      }
    }
    return true
  }
}
