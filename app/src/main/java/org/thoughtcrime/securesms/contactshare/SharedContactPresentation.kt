/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.recipients.RecipientId

/** What a shared contact bubble needs to present itself. */
data class SharedContactPresentation(
  val isOnSignal: Boolean,
  val recipientIds: List<RecipientId>
) {
  companion object {
    /** For a message that carries no card, so callers never have to null check. */
    @JvmField
    val EMPTY = SharedContactPresentation(isOnSignal = false, recipientIds = emptyList())

    @JvmStatic
    @WorkerThread
    fun resolve(contact: Contact): SharedContactPresentation {
      return SharedContactPresentation(
        isOnSignal = contact.isOnSignal,
        recipientIds = ContactUtil.getExistingRecipients(contact)
      )
    }
  }
}
