package org.thoughtcrime.securesms.components.settings.conversation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.core.util.Result
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.readToList
import org.thoughtcrime.securesms.components.settings.conversation.preferences.ButtonStripPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.CallPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.LegacyGroupPreference
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.LiveGroup
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.v2.GroupAddMembersResult
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messagerequests.MessageRequestRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil
import org.thoughtcrime.securesms.util.livedata.Store

sealed class ConversationSettingsViewModel(
  private val callMessageIds: LongArray,
  private val repository: ConversationSettingsRepository,
  private val messageRequestRepository: MessageRequestRepository,
  specificSettingsState: SpecificSettingsState
) : ViewModel() {

  @Volatile
  private var cleared = false

  protected val store = Store(
    ConversationSettingsState(
      specificSettingsState = specificSettingsState,
      isDeprecatedOrUnregistered = SignalStore.misc.isClientDeprecated || TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application)
    )
  )
  protected val internalEvents: Subject<ConversationSettingsEvent> = PublishSubject.create()

  private val sharedMediaUpdateTrigger = MutableLiveData(Unit)

  val state: LiveData<ConversationSettingsState> = store.stateLiveData
  val events: Observable<ConversationSettingsEvent> = internalEvents.observeOn(AndroidSchedulers.mainThread())

  protected val disposable = CompositeDisposable()

  init {
    val threadId: LiveData<Long> = state.map { it.threadId }.distinctUntilChanged()
    val updater: LiveData<Long> = LiveDataUtil.combineLatest(threadId, sharedMediaUpdateTrigger) { tId, _ -> tId }

    val sharedMedia: LiveData<List<MediaTable.MediaRecord>> = LiveDataUtil.mapAsync(SignalExecutors.BOUNDED, updater) { tId ->
      repository.getThreadMedia(threadId = tId, limit = 100)?.readToList { cursor ->
        MediaTable.MediaRecord.from(cursor)
      } ?: emptyList()
    }

    store.update(repository.getCallEvents(callMessageIds).toObservable()) { callRecords, state ->
      state.copy(calls = callRecords.map { (call, messageRecord) -> CallPreference.Model(call, messageRecord) })
    }

    store.update(sharedMedia) { mediaRecords, state ->
      if (!cleared) {
        state.copy(
          sharedMedia = mediaRecords,
          sharedMediaIds = mediaRecords.mapNotNull { it.attachment?.attachmentId?.id },
          sharedMediaLoaded = true,
          displayInternalRecipientDetails = repository.isInternalRecipientDetailsEnabled()
        )
      } else {
        state.copy(sharedMedia = emptyList())
      }
    }
  }

  fun refreshSharedMedia() {
    sharedMediaUpdateTrigger.postValue(Unit)
  }

  fun onReportSpam(): Maybe<Unit> {
    return if (store.state.threadId > 0 && store.state.recipient != Recipient.UNKNOWN) {
      messageRequestRepository.reportSpamMessageRequest(store.state.recipient.id, store.state.threadId)
        .observeOn(AndroidSchedulers.mainThread())
        .toSingle { Unit }
        .toMaybe()
    } else {
      Maybe.empty()
    }
  }

  fun onBlockAndReportSpam(): Maybe<Result<Unit, GroupChangeFailureReason>> {
    return if (store.state.threadId > 0 && store.state.recipient != Recipient.UNKNOWN) {
      messageRequestRepository.blockAndReportSpamMessageRequest(store.state.recipient.id, store.state.threadId)
        .observeOn(AndroidSchedulers.mainThread())
        .toMaybe()
    } else {
      Maybe.empty()
    }
  }

  open fun refreshRecipient(): Unit = error("This ViewModel does not support this interaction")

  abstract fun setMuteUntil(muteUntil: Long)

  abstract fun unmute()

  abstract fun block()

  abstract fun unblock()

  abstract fun onAddToGroup()

  abstract fun onAddToGroupComplete(selected: List<RecipientId>, onComplete: () -> Unit)

  abstract fun revealAllMembers()

  override fun onCleared() {
    cleared = true
    store.clear()
    disposable.clear()
  }

  private class RecipientSettingsViewModel(
    private val recipientId: RecipientId,
    private val callMessageIds: LongArray,
    private val repository: ConversationSettingsRepository,
    messageRequestRepository: MessageRequestRepository
  ) : ConversationSettingsViewModel(
    callMessageIds,
    repository,
    messageRequestRepository,
    SpecificSettingsState.RecipientSettingsState()
  ) {

    private val liveRecipient = Recipient.live(recipientId)

    init {
      disposable += StoryViewState.getForRecipientId(recipientId).subscribe { storyViewState ->
        store.update { it.copy(storyViewState = storyViewState) }
      }

      store.update(liveRecipient.liveData) { recipient, state ->
        val isAudioAvailable = recipient.isRegistered &&
          !recipient.isGroup &&
          !recipient.isBlocked &&
          !recipient.isSelf &&
          !recipient.isReleaseNotes

        state.copy(
          recipient = recipient,
          buttonStripState = ButtonStripPreference.State(
            isMessageAvailable = callMessageIds.isNotEmpty(),
            isVideoAvailable = recipient.registered == RecipientTable.RegisteredState.REGISTERED && !recipient.isSelf && !recipient.isBlocked && !recipient.isReleaseNotes,
            isAudioAvailable = isAudioAvailable,
            isAudioSecure = recipient.registered == RecipientTable.RegisteredState.REGISTERED,
            isMuted = recipient.isMuted,
            isMuteAvailable = !recipient.isSelf,
            isSearchAvailable = callMessageIds.isEmpty()
          ),
          disappearingMessagesLifespan = recipient.expiresInSeconds,
          canModifyBlockedState = !recipient.isSelf && RecipientUtil.isBlockable(recipient),
          specificSettingsState = state.requireRecipientSettingsState().copy(
            contactLinkState = when {
              recipient.isSelf || recipient.isReleaseNotes || recipient.isBlocked -> ContactLinkState.NONE
              recipient.isSystemContact -> ContactLinkState.OPEN
              recipient.hasE164 && recipient.shouldShowE164 -> ContactLinkState.ADD
              else -> ContactLinkState.NONE
            }
          )
        )
      }

      repository.getThreadId(recipientId) { threadId ->
        store.update { state ->
          state.copy(threadId = threadId)
        }
      }

      if (recipientId != Recipient.self().id) {
        repository.getGroupsInCommon(recipientId) { groupsInCommon ->
          store.update { state ->
            val recipientSettings = state.requireRecipientSettingsState()
            val canShowMore = !recipientSettings.groupsInCommonExpanded && groupsInCommon.size > 6

            state.copy(
              specificSettingsState = recipientSettings.copy(
                allGroupsInCommon = groupsInCommon,
                groupsInCommon = if (!canShowMore) groupsInCommon else groupsInCommon.take(5),
                canShowMoreGroupsInCommon = canShowMore
              )
            )
          }
        }

        repository.hasGroups { hasGroups ->
          store.update { state ->
            val recipientSettings = state.requireRecipientSettingsState()
            state.copy(
              specificSettingsState = recipientSettings.copy(
                selfHasGroups = hasGroups
              )
            )
          }
        }

        repository.getIdentity(recipientId) { identityRecord ->
          store.update { state ->
            state.copy(specificSettingsState = state.requireRecipientSettingsState().copy(identityRecord = identityRecord))
          }
        }
      }
    }

    override fun onAddToGroup() {
      repository.getGroupMembership(recipientId) {
        internalEvents.onNext(ConversationSettingsEvent.AddToAGroup(recipientId, it))
      }
    }

    override fun onAddToGroupComplete(selected: List<RecipientId>, onComplete: () -> Unit) {
    }

    override fun revealAllMembers() {
      store.update { state ->
        state.copy(
          specificSettingsState = state.requireRecipientSettingsState().copy(
            groupsInCommon = state.requireRecipientSettingsState().allGroupsInCommon,
            groupsInCommonExpanded = true,
            canShowMoreGroupsInCommon = false
          )
        )
      }
    }

    override fun refreshRecipient() {
      repository.refreshRecipient(recipientId)
    }

    override fun setMuteUntil(muteUntil: Long) {
      repository.setMuteUntil(recipientId, muteUntil)
    }

    override fun unmute() {
      repository.setMuteUntil(recipientId, 0)
    }

    override fun block() {
      repository.block(recipientId)
    }

    override fun unblock() {
      repository.unblock(recipientId)
    }
  }

  private class GroupSettingsViewModel(
    private val groupId: GroupId,
    private val callMessageIds: LongArray,
    private val repository: ConversationSettingsRepository,
    messageRequestRepository: MessageRequestRepository
  ) : ConversationSettingsViewModel(callMessageIds, repository, messageRequestRepository, SpecificSettingsState.GroupSettingsState(groupId)) {

    private val liveGroup = LiveGroup(groupId)

    init {
      disposable += repository.getStoryViewState(groupId).subscribe { storyViewState ->
        store.update { it.copy(storyViewState = storyViewState) }
      }

      val recipientAndIsActive = LiveDataUtil.combineLatest(liveGroup.groupRecipient, liveGroup.isActive) { r, a -> r to a }
      store.update(recipientAndIsActive) { (recipient, isActive), state ->
        state.copy(
          recipient = recipient,
          buttonStripState = ButtonStripPreference.State(
            isMessageAvailable = callMessageIds.isNotEmpty(),
            isVideoAvailable = recipient.isPushV2Group && !recipient.isBlocked && isActive,
            isAudioAvailable = false,
            isAudioSecure = recipient.isPushV2Group,
            isMuted = recipient.isMuted,
            isMuteAvailable = true,
            isSearchAvailable = callMessageIds.isEmpty(),
            isAddToStoryAvailable = recipient.isPushV2Group && !recipient.isBlocked && isActive && !SignalStore.story.isFeatureDisabled
          ),
          canModifyBlockedState = RecipientUtil.isBlockable(recipient),
          specificSettingsState = state.requireGroupSettingsState().copy(
            legacyGroupState = getLegacyGroupState()
          )
        )
      }

      repository.getThreadId(groupId) { threadId ->
        store.update { state ->
          state.copy(threadId = threadId)
        }
      }

      store.update(liveGroup.selfCanEditGroupAttributes()) { selfCanEditGroupAttributes, state ->
        state.copy(
          specificSettingsState = state.requireGroupSettingsState().copy(
            canEditGroupAttributes = selfCanEditGroupAttributes
          )
        )
      }

      store.update(liveGroup.isSelfAdmin) { isSelfAdmin, state ->
        state.copy(
          specificSettingsState = state.requireGroupSettingsState().copy(
            isSelfAdmin = isSelfAdmin
          )
        )
      }

      store.update(liveGroup.expireMessages) { expireMessages, state ->
        state.copy(
          disappearingMessagesLifespan = expireMessages
        )
      }

      store.update(liveGroup.selfCanAddMembers()) { canAddMembers, state ->
        state.copy(
          specificSettingsState = state.requireGroupSettingsState().copy(
            canAddToGroup = canAddMembers
          )
        )
      }

      store.update(liveGroup.fullMembers) { fullMembers, state ->
        val groupState = state.requireGroupSettingsState()
        val canShowMore = !groupState.groupMembersExpanded && fullMembers.size > 6

        state.copy(
          specificSettingsState = groupState.copy(
            allMembers = fullMembers,
            members = if (!canShowMore) fullMembers else fullMembers.take(5),
            canShowMoreGroupMembers = canShowMore
          )
        )
      }

      store.update(liveGroup.isAnnouncementGroup) { announcementGroup, state ->
        state.copy(
          specificSettingsState = state.requireGroupSettingsState().copy(
            isAnnouncementGroup = announcementGroup
          )
        )
      }

      val isMessageRequestAccepted: LiveData<Boolean> = LiveDataUtil.mapAsync(liveGroup.groupRecipient) { r -> repository.isMessageRequestAccepted(r) }
      val descriptionState: LiveData<DescriptionState> = LiveDataUtil.combineLatest(liveGroup.description, isMessageRequestAccepted, ::DescriptionState)

      store.update(descriptionState) { d, state ->
        state.copy(
          specificSettingsState = state.requireGroupSettingsState().copy(
            groupDescription = d.description,
            groupDescriptionShouldLinkify = d.canLinkify,
            groupDescriptionLoaded = true
          )
        )
      }

      store.update(liveGroup.isActive) { isActive, state ->
        state.copy(
          specificSettingsState = state.requireGroupSettingsState().copy(
            canLeave = isActive && groupId.isPush
          )
        )
      }

      store.update(liveGroup.title) { title, state ->
        state.copy(
          specificSettingsState = state.requireGroupSettingsState().copy(
            groupTitle = title,
            groupTitleLoaded = true
          )
        )
      }

      store.update(liveGroup.groupLink) { groupLink, state ->
        state.copy(
          specificSettingsState = state.requireGroupSettingsState().copy(
            groupLinkEnabled = groupLink.isEnabled
          )
        )
      }

      store.update(repository.getMembershipCountDescription(liveGroup)) { description, state ->
        state.copy(
          specificSettingsState = state.requireGroupSettingsState().copy(
            membershipCountDescription = description
          )
        )
      }
    }

    private fun getLegacyGroupState(): LegacyGroupPreference.State {
      return if (groupId.isMms) {
        LegacyGroupPreference.State.MMS_WARNING
      } else {
        LegacyGroupPreference.State.NONE
      }
    }

    override fun onAddToGroup() {
      repository.getGroupCapacity(groupId) { capacityResult ->
        if (capacityResult.getRemainingCapacity() > 0) {
          internalEvents.onNext(
            ConversationSettingsEvent.AddMembersToGroup(
              groupId,
              capacityResult.getSelectionWarning(),
              capacityResult.getSelectionLimit(),
              capacityResult.isAnnouncementGroup,
              capacityResult.getMembersWithoutSelf()
            )
          )
        } else {
          internalEvents.onNext(ConversationSettingsEvent.ShowGroupHardLimitDialog)
        }
      }
    }

    override fun onAddToGroupComplete(selected: List<RecipientId>, onComplete: () -> Unit) {
      repository.addMembers(groupId, selected) {
        ThreadUtil.runOnMain { onComplete() }

        when (it) {
          is GroupAddMembersResult.Success -> {
            if (it.newMembersInvited.isNotEmpty()) {
              internalEvents.onNext(ConversationSettingsEvent.ShowGroupInvitesSentDialog(it.newMembersInvited))
            }

            if (it.numberOfMembersAdded > 0) {
              internalEvents.onNext(ConversationSettingsEvent.ShowMembersAdded(it.numberOfMembersAdded))
            }
          }
          is GroupAddMembersResult.Failure -> internalEvents.onNext(ConversationSettingsEvent.ShowAddMembersToGroupError(it.reason))
        }
      }
    }

    override fun revealAllMembers() {
      store.update { state ->
        state.copy(
          specificSettingsState = state.requireGroupSettingsState().copy(
            members = state.requireGroupSettingsState().allMembers,
            groupMembersExpanded = true,
            canShowMoreGroupMembers = false
          )
        )
      }
    }

    override fun setMuteUntil(muteUntil: Long) {
      repository.setMuteUntil(groupId, muteUntil)
    }

    override fun unmute() {
      repository.setMuteUntil(groupId, 0)
    }

    override fun block() {
      repository.block(groupId)
    }

    override fun unblock() {
      repository.unblock(groupId)
    }
  }

  class Factory(
    private val recipientId: RecipientId? = null,
    private val groupId: GroupId? = null,
    private val callMessageIds: LongArray,
    private val repository: ConversationSettingsRepository,
    private val messageRequestRepository: MessageRequestRepository
  ) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(
        modelClass.cast(
          when {
            recipientId != null -> RecipientSettingsViewModel(recipientId, callMessageIds, repository, messageRequestRepository)
            groupId != null -> GroupSettingsViewModel(groupId, callMessageIds, repository, messageRequestRepository)
            else -> error("One of RecipientId or GroupId required.")
          }
        )
      )
    }
  }

  private class DescriptionState(
    val description: String?,
    val canLinkify: Boolean
  )
}
