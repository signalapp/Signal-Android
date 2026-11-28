/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * The outcome of [AddGroupDetailsRepository.createGroup].
 */
sealed interface GroupCreateResult {
  data class Success(
    val groupId: RecipientId,
    val threadId: Long,
    val invitedMembers: List<Recipient>
  ) : GroupCreateResult

  data object RetryableFailure : GroupCreateResult

  data object Failure : GroupCreateResult
}
