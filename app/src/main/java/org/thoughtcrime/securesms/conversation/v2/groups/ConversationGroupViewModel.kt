package org.thoughtcrime.securesms.conversation.v2.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.core.util.Result
import org.signal.core.util.concurrent.subscribeWithSubject
import org.thoughtcrime.securesms.conversation.v2.ConversationRecipientRepository
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.v2.GroupBlockJoinRequestResult
import org.thoughtcrime.securesms.groups.v2.GroupManagementRepository
import org.thoughtcrime.securesms.jobs.ForceUpdateGroupV2Job
import org.thoughtcrime.securesms.jobs.GroupV2UpdateSelfProfileKeyJob
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Manages group state and actions for conversations.
 */
class ConversationGroupViewModel(
  private val groupManagementRepository: GroupManagementRepository = GroupManagementRepository(),
  private val recipientRepository: ConversationRecipientRepository
) : ViewModel() {

  private val disposables = CompositeDisposable()

  private val _groupRecord: BehaviorSubject<GroupRecord> = recipientRepository
    .groupRecord
    .filter { it.isPresent }
    .map { it.get() }
    .subscribeWithSubject(BehaviorSubject.create(), disposables)

  private val _groupActiveState: Subject<ConversationGroupActiveState> = BehaviorSubject.create()
  private val _memberLevel: BehaviorSubject<ConversationGroupMemberLevel> = BehaviorSubject.create()

  private var firstTimeInviteFriendsTriggered: Boolean = false

  val groupRecordSnapshot: GroupRecord?
    get() = _groupRecord.value

  init {
    disposables += _groupRecord.subscribe { groupRecord ->
      _groupActiveState.onNext(ConversationGroupActiveState(groupRecord.isActive, groupRecord.isV2Group))
      _memberLevel.onNext(ConversationGroupMemberLevel(groupRecord.memberLevel(Recipient.self()), groupRecord.isAnnouncementGroup))
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
    return _groupRecord
      .firstOrError()
      .flatMap {
        groupManagementRepository.blockJoinRequests(it.id.requireV2(), recipient)
      }
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun cancelJoinRequest(): Single<Result<Unit, GroupChangeFailureReason>> {
    return _groupRecord
      .firstOrError()
      .flatMap { group ->
        groupManagementRepository.cancelJoinRequest(group.id.requireV2())
      }
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun onSuggestedMembersBannerDismissed() {
    _groupRecord
      .firstOrError()
      .flatMapCompletable { group ->
        groupManagementRepository.removeUnmigratedV1Members(group.id.requireV2())
      }
      .subscribe()
      .addTo(disposables)
  }

  /**
   * Emits the group id if we are the only member of the group.
   */
  fun checkJustSelfInGroup(): Maybe<GroupId.V2> {
    if (firstTimeInviteFriendsTriggered) {
      return Maybe.empty()
    }

    firstTimeInviteFriendsTriggered = true

    return _groupRecord
      .firstOrError()
      .flatMapMaybe { groupRecord ->
        groupManagementRepository.isJustSelf(groupRecord.id).flatMapMaybe {
          if (it && groupRecord.id.isV2) Maybe.just(groupRecord.id.requireV2()) else Maybe.empty()
        }
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun updateGroupStateIfNeeded() {
    recipientRepository
      .conversationRecipient
      .firstOrError()
      .onErrorComplete()
      .filter { it.isPushV2Group && !it.isBlocked }
      .subscribe {
        val groupId = it.requireGroupId().requireV2()
        AppDependencies.jobManager
          .startChain(RequestGroupV2InfoJob(groupId))
          .then(GroupV2UpdateSelfProfileKeyJob.withoutLimits(groupId))
          .enqueue()

        ForceUpdateGroupV2Job.enqueueIfNecessary(groupId)
      }
      .addTo(disposables)
  }

  class Factory(private val recipientRepository: ConversationRecipientRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(ConversationGroupViewModel(recipientRepository = recipientRepository)) as T
    }
  }
}
