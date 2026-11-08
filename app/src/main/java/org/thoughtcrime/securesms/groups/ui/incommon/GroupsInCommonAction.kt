/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.incommon

import org.thoughtcrime.securesms.recipients.Recipient

/**
 * One-shot side effects that have to be carried out by the host rather than the screen itself.
 */
sealed interface GroupsInCommonAction {

  /** Open the group conversation. */
  data class OpenGroupConversation(val group: Recipient) : GroupsInCommonAction

  /** Leave the screen. */
  data object NavigateBack : GroupsInCommonAction
}
