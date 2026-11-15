/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.thoughtcrime.securesms.database.BodyAdjustment
import org.thoughtcrime.securesms.database.adjustBodyRanges
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList.BodyRange
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList.BodyRange.Style

class InvisibleInkTest {
  @Test
  fun coversTheEntireBodyInUtf16() {
    val ranges = InvisibleInk.bodyRanges("A🦊\nB", null)

    assertThat(ranges.ranges).isEqualTo(listOf(BodyRange(start = 0, length = 5, style = Style.SPOILER)))
  }

  @Test
  fun replacesExistingSpoilersAndPreservesOtherRanges() {
    val bold = BodyRange(start = 1, length = 4, style = Style.BOLD)
    val italic = BodyRange(start = 0, length = 1, style = Style.ITALIC)
    val original = BodyRangeList(ranges = listOf(BodyRange(start = 3, length = 2, style = Style.SPOILER), bold, italic))

    val ranges = InvisibleInk.bodyRanges("a secret", original)

    assertThat(ranges.ranges).isEqualTo(listOf(BodyRange(start = 0, length = 8, style = Style.SPOILER), bold, italic))
    assertThat(original.ranges[0]).isEqualTo(BodyRange(start = 3, length = 2, style = Style.SPOILER))
  }

  @Test
  fun coversTheEntireBodyAfterMentionPlaceholderConversion() {
    val ranges = InvisibleInk.bodyRanges("@Ada 🦊", null)
      .adjustBodyRanges(listOf(BodyAdjustment(startIndex = 0, oldLength = 4, newLength = 1)))

    assertThat(ranges?.ranges).isEqualTo(listOf(BodyRange(start = 0, length = 4, style = Style.SPOILER)))
  }

  @Test
  fun acceptsOnlyNonBlankInlineBodies() {
    assertThat(InvisibleInk.isEligible("  \n", 0, null)).isFalse()
    assertThat(InvisibleInk.isEligible("🦊".repeat(512), 0, null)).isTrue()
    assertThat(InvisibleInk.isEligible("🦊".repeat(512) + "a", 0, null)).isFalse()
  }

  @Test
  fun reservesARangeForTheFullBodySpoiler() {
    val body = "a".repeat(500)
    val bold = List(248) { BodyRange(start = it * 2, length = 1, style = Style.BOLD) }
    val ranges = BodyRangeList(ranges = bold + BodyRange(start = 0, length = 2, style = Style.SPOILER))

    assertThat(InvisibleInk.isEligible(body, mentionCount = 1, existingRanges = ranges)).isTrue()
    assertThat(InvisibleInk.isEligible(body, mentionCount = 2, existingRanges = ranges)).isFalse()
  }

  @Test
  fun excludesDirectionalFormattingThatOlderClientsMayNormalize() {
    assertThat(InvisibleInk.isEligible("\u202Esecret", 0, null)).isFalse()
    assertThat(InvisibleInk.isEligible("\u2067secret", 0, null)).isFalse()
    assertThat(InvisibleInk.isEligible("סוד", 0, null)).isTrue()
  }

  @Test
  fun excludesOversizedGraphemesThatOlderClientsMayReplace() {
    assertThat(InvisibleInk.isEligible("a" + "\u0301".repeat(15), 0, null)).isTrue()
    assertThat(InvisibleInk.isEligible("a" + "\u0301".repeat(16), 0, null)).isFalse()
  }
}
