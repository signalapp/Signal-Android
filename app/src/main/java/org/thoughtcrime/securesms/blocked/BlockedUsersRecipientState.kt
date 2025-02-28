/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.blocked

import androidx.compose.runtime.Immutable
import com.google.common.base.Objects
import org.thoughtcrime.securesms.recipients.Recipient

@Immutable
class BlockedUserRecipientState (val recipient: Recipient, val displayName: String, val username: String? = null){

  override fun equals(other: Any?): Boolean {
    if (other !is Recipient) return false
    return recipient.hasSameContent(other)
  }
  override fun hashCode(): Int {
    return Objects.hashCode(
      recipient,
      displayName,
      username,
    )
  }
}