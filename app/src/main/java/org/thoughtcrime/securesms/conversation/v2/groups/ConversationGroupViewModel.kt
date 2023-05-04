package org.thoughtcrime.securesms.conversation.v2.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import org.thoughtcrime.securesms.conversation.v2.ConversationRecipientRepository
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.groups.GroupsV1MigrationUtil
import org.thoughtcrime.securesms.groups.v2.GroupBlockJoinRequestResult
import org.thoughtcrime.securesms.groups.v2.GroupManagementRepository
import org.thoughtcrime.securesms.profiles.spoofing.ReviewUtil
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Manages group state and actions for conversations.
 */
class ConversationGroupViewModel(
  private val threadId: Long,
  private val groupManagementRepository: GroupManagementRepository = GroupManagementRepository(),
  private val recipientRepository: ConversationRecipientRepository
) : ViewModel() {

  private val disposables = CompositeDisposable()
  private val _recipient: Subject<Recipient> = BehaviorSubject.create()
  private val _groupRecord: Subject<GroupRecord> = BehaviorSubject.create()
  private val _groupActiveState: Subject<ConversationGroupActiveState> = BehaviorSubject.create()
  private val _memberLevel: BehaviorSubject<ConversationGroupMemberLevel> = BehaviorSubject.create()
  private val _actionableRequestingMembersCount: Subject<Int> = BehaviorSubject.create()
  private val _gv1MigrationSuggestions: Subject<List<RecipientId>> = BehaviorSubject.create()
  private val _reviewState: Subject<ConversationGroupReviewState> = BehaviorSubject.create()

  init {
    disposables += recipientRepository
      .conversationRecipient
      .filter { it.isGroup }
      .subscribeBy(onNext = _recipient::onNext)

    disposables += _recipient
      .switchMap {
        Observable.fromCallable {
          SignalDatabase.groups.getGroup(it.id).get()
        }
      }
      .subscribeBy(onNext = _groupRecord::onNext)

    val duplicates = _groupRecord.map {
      if (it.isV2Group) {
        ReviewUtil.getDuplicatedRecipients(it.id.requireV2()).map { it.recipient }
      } else {
        emptyList()
      }
    }

    disposables += Observable.combineLatest(_groupRecord, duplicates) { record, dupes ->
      if (dupes.isEmpty()) {
        ConversationGroupReviewState.EMPTY
      } else {
        ConversationGroupReviewState(record.id.requireV2(), dupes[0], dupes.size)
      }
    }.subscribeBy(onNext = _reviewState::onNext)

    disposables += _groupRecord.subscribe { groupRecord ->
      _groupActiveState.onNext(ConversationGroupActiveState(groupRecord.isActive, groupRecord.isV2Group))
      _memberLevel.onNext(ConversationGroupMemberLevel(groupRecord.memberLevel(Recipient.self()), groupRecord.isAnnouncementGroup))
      _actionableRequestingMembersCount.onNext(getActionableRequestingMembersCount(groupRecord))
      _gv1MigrationSuggestions.onNext(getGv1MigrationSuggestions(groupRecord))
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun isNonAdminInAnnouncementGroup(): Boolean {
    val memberLevel = _memberLevel.value ?: return false
    return memberLevel.groupTableMemberLevel != GroupTable.MemberLevel.ADMINISTRATOR && memberLevel.isAnnouncementGroup
  }

  fun blockJoinRequests(recipient: Recipient): Single<GroupBlockJoinRequestResult> {
    return _recipient.firstOrError().flatMap {
      groupManagementRepository.blockJoinRequests(it.requireGroupId().requireV2(), recipient)
    }.observeOn(AndroidSchedulers.mainThread())
  }

  private fun getActionableRequestingMembersCount(groupRecord: GroupRecord): Int {
    return if (groupRecord.isV2Group && groupRecord.memberLevel(Recipient.self()) == GroupTable.MemberLevel.ADMINISTRATOR) {
      groupRecord.requireV2GroupProperties()
        .decryptedGroup
        .requestingMembersCount
    } else {
      0
    }
  }

  private fun getGv1MigrationSuggestions(groupRecord: GroupRecord): List<RecipientId> {
    return if (!groupRecord.isActive || !groupRecord.isV2Group || groupRecord.isPendingMember(Recipient.self())) {
      emptyList()
    } else {
      groupRecord.unmigratedV1Members
        .filterNot { groupRecord.members.contains(it) }
        .map { Recipient.resolved(it) }
        .filter { GroupsV1MigrationUtil.isAutoMigratable(it) }
        .map { it.id }
    }
  }

  class Factory(private val threadId: Long, private val recipientRepository: ConversationRecipientRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(ConversationGroupViewModel(threadId, recipientRepository = recipientRepository)) as T
    }
  }
}
