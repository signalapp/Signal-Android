package org.thoughtcrime.securesms.contacts.paged

import androidx.annotation.CheckResult
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.stories.Stories

class ContactSearchRepository {
  @CheckResult
  fun filterOutUnselectableContactSearchKeys(contactSearchKeys: Set<ContactSearchKey>): Single<Set<ContactSearchSelectionResult>> {
    return Single.fromCallable {
      contactSearchKeys.map {
        val isSelectable = when (it) {
          is ContactSearchKey.Expand -> false
          is ContactSearchKey.Header -> false
          is ContactSearchKey.RecipientSearchKey.KnownRecipient -> canSelectRecipient(it.recipientId)
          is ContactSearchKey.RecipientSearchKey.Story -> canSelectRecipient(it.recipientId)
        }
        ContactSearchSelectionResult(it, isSelectable)
      }.toSet()
    }
  }

  private fun canSelectRecipient(recipientId: RecipientId): Boolean {
    val recipient = Recipient.resolved(recipientId)
    return if (recipient.isPushV2Group) {
      val record = SignalDatabase.groups.getGroup(recipient.requireGroupId())
      !(record.isPresent && record.get().isAnnouncementGroup && !record.get().isAdmin(Recipient.self()))
    } else {
      true
    }
  }

  @CheckResult
  fun markDisplayAsStory(recipientIds: Collection<RecipientId>): Completable {
    return Completable.fromAction {
      SignalDatabase.groups.setShowAsStoryState(recipientIds, GroupTable.ShowAsStoryState.ALWAYS)
      SignalDatabase.recipients.markNeedsSync(recipientIds)
      StorageSyncHelper.scheduleSyncForDataChange()
    }.subscribeOn(Schedulers.io())
  }

  @CheckResult
  fun unmarkDisplayAsStory(groupId: GroupId): Completable {
    return Completable.fromAction {
      SignalDatabase.groups.setShowAsStoryState(groupId, GroupTable.ShowAsStoryState.NEVER)
      SignalDatabase.recipients.markNeedsSync(Recipient.externalGroupExact(groupId).id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }.subscribeOn(Schedulers.io())
  }

  @CheckResult
  fun deletePrivateStory(distributionListId: DistributionListId): Completable {
    return Completable.fromAction {
      SignalDatabase.distributionLists.deleteList(distributionListId)
      Stories.onStorySettingsChanged(distributionListId)
    }.subscribeOn(Schedulers.io())
  }
}
