/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import org.thoughtcrime.securesms.recipients.Recipient

/** A member of the group, flattened into something the screen can render directly. */
data class GroupMember(
  val recipient: Recipient,
  val isAdmin: Boolean
)
