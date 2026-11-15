/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.mms.QuoteModel

internal class InvisibleInkDraft(
  val body: String,
  val mentions: List<Mention>,
  val bodyRanges: BodyRangeList?,
  val quote: QuoteModel?
) {
  fun matches(other: InvisibleInkDraft): Boolean {
    return body == other.body &&
      mentions == other.mentions &&
      bodyRanges == other.bodyRanges &&
      quote?.id == other.quote?.id &&
      quote?.author == other.quote?.author &&
      quote?.text == other.quote?.text &&
      quote?.isOriginalMissing == other.quote?.isOriginalMissing &&
      quote?.attachment == other.quote?.attachment &&
      quote?.mentions == other.quote?.mentions &&
      quote?.type == other.quote?.type &&
      quote?.bodyRanges == other.quote?.bodyRanges
  }
}

internal data class PendingInvisibleInkDraft(val confirmationId: String, val draft: InvisibleInkDraft)
