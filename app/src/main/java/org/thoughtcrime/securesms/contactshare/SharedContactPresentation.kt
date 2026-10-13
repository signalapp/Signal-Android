/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import androidx.annotation.WorkerThread
import org.signal.core.models.ServiceId.ACI
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil

/** What a shared contact bubble needs to present itself. */
data class SharedContactPresentation(
  val isOnSignal: Boolean,
  val recipientIds: List<RecipientId>,
  val canAddToGroup: Boolean = false
) {
  companion object {
    /** For a message that carries no card, so callers never have to null check. */
    @JvmField
    val EMPTY = SharedContactPresentation(isOnSignal = false, recipientIds = emptyList())

    @JvmStatic
    @JvmOverloads
    @WorkerThread
    fun resolve(contact: Contact, groupRecord: GroupRecord? = null): SharedContactPresentation {
      val isOnSignal = contact.isOnSignal

      return SharedContactPresentation(
        isOnSignal = isOnSignal,
        recipientIds = ContactUtil.getExistingRecipients(contact),
        canAddToGroup = isOnSignal && groupRecord != null && canAddToGroup(contact, groupRecord)
      )
    }

    @WorkerThread
    private fun canAddToGroup(contact: Contact, groupRecord: GroupRecord): Boolean {
      if (!groupRecord.isV2Group || !groupRecord.hasV2GroupProperties || !groupRecord.isActive) {
        return false
      }

      val self = Recipient.self()
      if (!groupRecord.membershipAdditionAccessControl.allows(groupRecord.memberLevel(self))) {
        return false
      }

      val aci: ACI = contact.signalAci ?: return false
      if (aci == self.aci.orElse(null)) {
        return false
      }

      val group = groupRecord.requireV2GroupProperties().decryptedGroup

      return !DecryptedGroupUtil.findMemberByAci(group.members, aci).isPresent &&
        !DecryptedGroupUtil.findPendingByServiceId(group.pendingMembers, aci).isPresent &&
        !DecryptedGroupUtil.findRequestingByAci(group.requestingMembers, aci).isPresent
    }
  }
}
