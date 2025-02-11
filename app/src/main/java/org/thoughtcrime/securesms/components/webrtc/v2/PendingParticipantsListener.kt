/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import org.thoughtcrime.securesms.recipients.Recipient

interface PendingParticipantsListener {
  /**
   * Display the sheet containing the request for the top level participant
   */
  fun onLaunchRecipientSheet(pendingRecipient: Recipient)

  /**
   * Given recipient should be admitted to the call
   */
  fun onAllowPendingRecipient(pendingRecipient: Recipient)

  /**
   * Given recipient should be rejected from the call
   */
  fun onRejectPendingRecipient(pendingRecipient: Recipient)

  /**
   * Display the sheet containing all of the requests for the given call
   */
  fun onLaunchPendingRequestsSheet()

  object Empty : PendingParticipantsListener {
    override fun onLaunchRecipientSheet(pendingRecipient: Recipient) = Unit
    override fun onAllowPendingRecipient(pendingRecipient: Recipient) = Unit
    override fun onRejectPendingRecipient(pendingRecipient: Recipient) = Unit
    override fun onLaunchPendingRequestsSheet() = Unit
  }
}
