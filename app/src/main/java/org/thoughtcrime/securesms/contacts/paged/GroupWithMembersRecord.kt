/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged

import org.thoughtcrime.securesms.database.model.GroupRecord

/**
 * A group whose members matched a search query, paired with the date of its thread.
 */
data class GroupWithMembersRecord(
  val groupRecord: GroupRecord,
  val threadDate: Long
)
