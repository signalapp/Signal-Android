/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList.BodyRange
import org.thoughtcrime.securesms.mms.QuoteModel

class InvisibleInkDraftTest {
  @Test
  fun rejectsChangedText() {
    assertThat(draft().matches(draft(body = "@Ada goodbye"))).isFalse()
  }

  @Test
  fun rejectsChangedFormatting() {
    val ranges = BodyRangeList(ranges = listOf(BodyRange(start = 5, length = 5, style = BodyRange.Style.BOLD)))

    assertThat(draft().matches(draft(bodyRanges = ranges))).isFalse()
  }

  @Test
  fun rejectsChangedMentionRecipient() {
    assertThat(draft().matches(draft(mentionRecipient = RecipientId.from(2)))).isFalse()
  }

  @Test
  fun acceptsAnEquivalentQuoteSnapshotAndRejectsAChangedQuote() {
    assertThat(draft().matches(draft())).isTrue()
    assertThat(draft().matches(draft(quoteBody = "Something else"))).isFalse()
  }

  private fun draft(
    body: String = "@Ada hello",
    mentionRecipient: RecipientId = RecipientId.from(1),
    bodyRanges: BodyRangeList? = null,
    quoteBody: String = "A message"
  ): InvisibleInkDraft {
    val quote = QuoteModel(
      id = 10,
      author = RecipientId.from(3),
      text = quoteBody,
      isOriginalMissing = false,
      attachment = null,
      mentions = null,
      type = QuoteModel.Type.NORMAL,
      bodyRanges = null
    )

    return InvisibleInkDraft(body, listOf(Mention(mentionRecipient, 0, 4)), bodyRanges, quote)
  }
}
