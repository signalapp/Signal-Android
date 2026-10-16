/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.incommon

import org.signal.core.models.recipients.RecipientId

sealed interface GroupsInCommonEvent {

  /** The user tapped on a row representing a group. */
  data class GroupClicked(val groupId: RecipientId) : GroupsInCommonEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : GroupsInCommonEvent
}
