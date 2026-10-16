package org.thoughtcrime.securesms.stories.settings.select

import kotlinx.coroutines.withContext
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.CursorUtil
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListRecord
import org.thoughtcrime.securesms.stories.Stories

object BaseStoryRecipientSelectionRepository {

  suspend fun getRecord(distributionListId: DistributionListId): DistributionListRecord = withContext(SignalDispatchers.Default) {
    SignalDatabase.distributionLists.getList(distributionListId) ?: error("Record does not exist.")
  }

  suspend fun updateDistributionListMembership(distributionListRecord: DistributionListRecord, recipients: Set<RecipientId>) {
    withContext(SignalDispatchers.Default) {
      val currentRecipients = SignalDatabase.distributionLists.getRawMembers(distributionListRecord.id, distributionListRecord.privacyMode).toSet()
      val oldNotNew = currentRecipients - recipients
      val newNotOld = recipients - currentRecipients

      oldNotNew.forEach {
        SignalDatabase.distributionLists.removeMemberFromList(distributionListRecord.id, distributionListRecord.privacyMode, it)
      }

      newNotOld.forEach {
        SignalDatabase.distributionLists.addMemberToList(distributionListRecord.id, distributionListRecord.privacyMode, it)
      }

      Stories.onStorySettingsChanged(distributionListRecord.id)
    }
  }

  suspend fun getAllSignalContacts(): Set<RecipientId> = withContext(SignalDispatchers.Default) {
    SignalDatabase.recipients.getSignalContacts(RecipientTable.IncludeSelfMode.Exclude).use {
      val recipientSet = mutableSetOf<RecipientId>()
      while (it.moveToNext()) {
        recipientSet.add(RecipientId.from(CursorUtil.requireLong(it, RecipientTable.ID)))
      }

      recipientSet
    }
  }
}
