/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.selectrecipients

import org.signal.core.models.recipients.RecipientId

/**
 * [SelectRecipientsScreen] state.
 */
data class SelectRecipientsState(
  /** Who the profile already allows through. Shown picked and can't be unpicked here; removing happens on the profile's details. */
  val currentMembers: Set<RecipientId> = emptySet(),
  /** The people and groups newly picked to be added. */
  val selection: Set<RecipientId> = emptySet(),
  val isSaving: Boolean = false
) {
  /** Everyone the profile will allow through once saved. */
  val allowedMembers: Set<RecipientId>
    get() = currentMembers + selection

  val canSave: Boolean
    get() = allowedMembers.isNotEmpty() && !isSaving

  override fun toString(): String = "SelectRecipientsState(currentMembers=${currentMembers.size}, selection=${selection.size}, isSaving=$isSaving)"
}
