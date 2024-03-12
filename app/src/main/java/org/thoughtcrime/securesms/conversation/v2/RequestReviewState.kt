/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Indicates if we should present an additional review warning banner
 * for an individual or group.
 */
data class RequestReviewState(
  val individualReviewState: IndividualReviewState? = null,
  val groupReviewState: GroupReviewState? = null
) {

  fun shouldShowReviewBanner(): Boolean {
    return individualReviewState != null || groupReviewState != null
  }

  /** Recipient is in message request state and has similar name as someone else */
  data class IndividualReviewState(val target: Recipient, val firstDuplicate: Recipient)

  /** Group has multiple members with similar names */
  data class GroupReviewState(val groupId: GroupId.V2, val target: Recipient, val firstDuplicate: Recipient, val count: Int)
}
