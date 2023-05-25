/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import org.thoughtcrime.securesms.database.identity.IdentityRecordList

/**
 * Current state for all participants identity keys in a conversation excluding self.
 */
data class IdentityRecordsState(
  val isVerified: Boolean,
  val identityRecords: IdentityRecordList,
  val isGroup: Boolean
) {
  val isUnverified: Boolean = identityRecords.isUnverified
}
