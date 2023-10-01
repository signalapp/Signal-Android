/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc

import org.thoughtcrime.securesms.database.identity.IdentityRecordList
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.NullMessageSendJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * In-Memory Cache that keeps track of whom we've sent NullMessages to. This is
 * something that really only needs to happen once so that profile information is
 * displayed correctly, so we maintain an application process scoped cache.
 */
object CallLinkNullMessageSender {
  private val cache = hashSetOf<RecipientId>()

  /**
   * Invoked after pressing "Join" or "Continue" on the safety number change dialog before
   * or during a call.
   */
  @JvmStatic
  fun onSendAnyway(recipientIds: Set<RecipientId>) {
    val toSendMessagesTo: Set<RecipientId> = recipientIds - cache
    cache += recipientIds

    val jobs: List<NullMessageSendJob> = toSendMessagesTo.map { NullMessageSendJob(it) }
    ApplicationDependencies.getJobManager().addAll(jobs)
  }

  /**
   * Given the set of recipients, for each unblocked recipient we don't distrust, send a NullMessage
   */
  fun onRecipientsUpdated(recipients: Set<Recipient>) {
    val nonBlockedRecipients: List<Recipient> = recipients.filterNot { it.isBlocked }

    val identityRecords: IdentityRecordList = ApplicationDependencies
      .getProtocolStore()
      .aci()
      .identities()
      .getIdentityRecords(nonBlockedRecipients)

    val untrustedAndUnverifiedRecipients = if (identityRecords.isUntrusted(false) || identityRecords.isUnverified(false)) {
      (identityRecords.untrustedRecipients + identityRecords.unverifiedRecipients).toSet()
    } else {
      emptySet()
    }

    val trustedRecipients: Set<RecipientId> = (nonBlockedRecipients - untrustedAndUnverifiedRecipients).map { it.id }.toSet()
    onSendAnyway(trustedRecipients)
  }
}
