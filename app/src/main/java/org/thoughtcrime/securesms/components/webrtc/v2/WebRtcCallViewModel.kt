/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.toPublisher
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.signal.core.util.ThreadUtil
import org.thoughtcrime.securesms.components.webrtc.CallParticipantListUpdate
import org.thoughtcrime.securesms.components.webrtc.CallParticipantsState
import org.thoughtcrime.securesms.components.webrtc.InCallStatus
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioOutput
import org.thoughtcrime.securesms.components.webrtc.WebRtcCallRepository
import org.thoughtcrime.securesms.components.webrtc.WebRtcControls
import org.thoughtcrime.securesms.components.webrtc.WebRtcLocalRenderState
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.CallParticipantId
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.groups.LiveGroup
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.LiveRecipient
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.PendingParticipantCollection
import org.thoughtcrime.securesms.service.webrtc.state.PendingParticipantsState
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcEphemeralState
import org.thoughtcrime.securesms.util.NetworkUtil
import org.thoughtcrime.securesms.util.SingleLiveEvent
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import java.util.Collections

class WebRtcCallViewModel : ViewModel() {
  private val internalMicrophoneEnabled = MutableLiveData(true)
  private val isInPipMode = MutableLiveData(false)
  private val webRtcControls = MutableLiveData(WebRtcControls.NONE)
  private val foldableState = MutableLiveData(WebRtcControls.FoldableState.flat())
  private val controlsWithFoldableState: LiveData<WebRtcControls> = LiveDataUtil.combineLatest(foldableState, webRtcControls, this::updateControlsFoldableState)
  private val realWebRtcControls: LiveData<WebRtcControls> = LiveDataUtil.combineLatest(isInPipMode, controlsWithFoldableState, this::getRealWebRtcControls)
  private val events = SingleLiveEvent<CallEvent>()
  private val elapsed = BehaviorSubject.createDefault(-1L)
  private val liveRecipient = MutableLiveData(Recipient.UNKNOWN.live())
  private val participantsState = BehaviorSubject.createDefault(CallParticipantsState.STARTING_STATE)
  private val callParticipantListUpdate = SingleLiveEvent<CallParticipantListUpdate>()
  private val identityChangedRecipients = MutableLiveData<Collection<RecipientId>>(Collections.emptyList())
  private val safetyNumberChangeEvent: LiveData<SafetyNumberChangeEvent> = LiveDataUtil.combineLatest(isInPipMode, identityChangedRecipients, ::SafetyNumberChangeEvent)
  private val groupRecipient: LiveData<Recipient> = LiveDataUtil.filter(liveRecipient.switchMap(LiveRecipient::getLiveData), Recipient::isActiveGroup)
  private val groupMembers: LiveData<List<GroupMemberEntry.FullMember>> = groupRecipient.switchMap { r -> LiveGroup(r.requireGroupId()).fullMembers }
  private val groupMembersChanged: LiveData<List<GroupMemberEntry.FullMember>> = LiveDataUtil.skip(groupMembers, 1)
  private val groupMembersCount: LiveData<Int> = groupMembers.map { it.size }
  private val shouldShowSpeakerHint: Observable<Boolean> = participantsState.map(this::shouldShowSpeakerHint)
  private val isLandscapeEnabled = MutableLiveData<Boolean>()
  private val canEnterPipMode = MutableLiveData(false)
  private val groupMemberStateUpdater = Observer<List<GroupMemberEntry.FullMember>> { m -> participantsState.onNext(CallParticipantsState.update(participantsState.value!!, m)) }
  private val ephemeralState = MutableLiveData<WebRtcEphemeralState>()
  private val recipientId = BehaviorProcessor.createDefault(RecipientId.UNKNOWN)

  private val pendingParticipants = BehaviorSubject.create<PendingParticipantCollection>()

  private val elapsedTimeHandler = Handler(Looper.getMainLooper())
  private val elapsedTimeRunnable = Runnable { handleTick() }
  private val stopOutgoingRingingMode = Runnable { stopOutgoingRingingMode() }

  private var canDisplayTooltipIfNeeded = true
  private var canDisplaySwitchCameraTooltipIfNeeded = true
  private var canDisplayPopupIfNeeded = true
  private var hasEnabledLocalVideo = false
  private var wasInOutgoingRingingMode = false
  private var callConnectedTime = -1L
  private var answerWithVideoAvailable = false
  private var previousParticipantList = Collections.emptyList<CallParticipant>()
  private var switchOnFirstScreenShare = true
  private var showScreenShareTip = true

  var isCallStarting = false
    private set

  init {
    groupMembers.observeForever(groupMemberStateUpdater)
  }

  override fun onCleared() {
    super.onCleared()
    cancelTimer()
    groupMembers.removeObserver(groupMemberStateUpdater)
  }

  val microphoneEnabled: LiveData<Boolean> get() = internalMicrophoneEnabled.distinctUntilChanged()

  fun getWebRtcControls(): LiveData<WebRtcControls> = realWebRtcControls

  val recipient: LiveRecipient get() = liveRecipient.value!!

  fun getRecipientFlowable(): Flowable<Recipient> {
    return recipientId
      .switchMap { Recipient.observable(it).toFlowable(BackpressureStrategy.LATEST) }
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun setRecipient(recipient: Recipient) {
    recipientId.onNext(recipient.id)
    liveRecipient.value = recipient.live()
  }

  fun setFoldableState(foldableState: WebRtcControls.FoldableState) {
    this.foldableState.postValue(foldableState)
    ThreadUtil.runOnMain { participantsState.onNext(CallParticipantsState.update(participantsState.value!!, foldableState)) }
  }

  fun getEvents(): LiveData<CallEvent> {
    return events
  }

  fun getInCallStatus(): Observable<InCallStatus> {
    val elapsedTime: Observable<Long> = elapsed.map { timeInCall -> if (callConnectedTime == -1L) -1L else timeInCall }

    return Observable.combineLatest(
      elapsedTime,
      pendingParticipants,
      participantsState
    ) { time, pendingParticipants, participantsState ->
      if (!recipient.get().isCallLink) {
        return@combineLatest InCallStatus.ElapsedTime(time)
      }

      val pending: Set<PendingParticipantCollection.Entry> = pendingParticipants.getUnresolvedPendingParticipants()

      if (pending.isNotEmpty()) {
        InCallStatus.PendingCallLinkUsers(pending.size)
      } else {
        InCallStatus.JoinedCallLinkUsers(participantsState.participantCount.orElse(0L).toInt())
      }
    }.distinctUntilChanged().observeOn(AndroidSchedulers.mainThread())
  }

  fun getCallControlsState(lifecycleOwner: LifecycleOwner): Flowable<CallControlsState> {
    val groupSize: Flowable<Int> = recipientId.filter { it != RecipientId.UNKNOWN }
      .switchMap { Recipient.observable(it).toFlowable(BackpressureStrategy.LATEST) }
      .map {
        if (it.isActiveGroup) {
          SignalDatabase.groups.getGroupMemberIds(it.requireGroupId(), GroupTable.MemberSet.FULL_MEMBERS_INCLUDING_SELF).size
        } else {
          0
        }
      }

    return Flowable.combineLatest(
      callParticipantsState.toFlowable(BackpressureStrategy.LATEST),
      getWebRtcControls().toPublisher(lifecycleOwner),
      groupSize,
      CallControlsState::fromViewModelData
    )
  }

  val callParticipantsState: Observable<CallParticipantsState> get() = participantsState

  val callParticipantsStateSnapshot: CallParticipantsState? get() = participantsState.value

  fun getCallParticipantListUpdate(): LiveData<CallParticipantListUpdate> {
    return callParticipantListUpdate
  }

  fun getSafetyNumberChangeEvent(): LiveData<SafetyNumberChangeEvent> {
    return safetyNumberChangeEvent
  }

  fun getGroupMembersChanged(): LiveData<List<GroupMemberEntry.FullMember>> {
    return groupMembersChanged
  }

  fun getGroupMemberCount(): LiveData<Int> {
    return groupMembersCount
  }

  fun shouldShowSpeakerHint(): Observable<Boolean> {
    return shouldShowSpeakerHint.observeOn(AndroidSchedulers.mainThread())
  }

  fun getCurrentAudioOutput(): WebRtcAudioOutput {
    return getWebRtcControls().value!!.audioOutput
  }

  fun getEphemeralState(): LiveData<WebRtcEphemeralState> {
    return ephemeralState
  }

  fun canEnterPipMode(): LiveData<Boolean> {
    return canEnterPipMode
  }

  fun isAnswerWithVideoAvailable(): Boolean {
    return answerWithVideoAvailable
  }

  fun getPendingParticipants(): Observable<PendingParticipantsState> {
    val isInPipMode: Observable<Boolean> = participantsState.map { it.isInPipMode }.distinctUntilChanged()
    return Observable.combineLatest(pendingParticipants, isInPipMode, ::PendingParticipantsState)
  }

  fun getPendingParticipantsSnapshot(): PendingParticipantCollection {
    return pendingParticipants.value!!
  }

  fun setIsInPipMode(isInPipMode: Boolean) {
    this.isInPipMode.value = isInPipMode
    participantsState.onNext(CallParticipantsState.update(participantsState.value!!, isInPipMode))
  }

  fun setIsLandscapeEnabled(isLandscapeEnabled: Boolean) {
    this.isLandscapeEnabled.postValue(isLandscapeEnabled)
  }

  @MainThread
  fun setIsViewingFocusedParticipant(page: CallParticipantsState.SelectedPage) {
    if (page == CallParticipantsState.SelectedPage.FOCUSED) {
      SignalStore.tooltips.markGroupCallSpeakerViewSeen()
    }

    val state = participantsState.value!!
    if (showScreenShareTip &&
      state.focusedParticipant.isScreenSharing &&
      state.isViewingFocusedParticipant &&
      page == CallParticipantsState.SelectedPage.GRID
    ) {
      showScreenShareTip = false
      events.value = CallEvent.ShowSwipeToSpeakerHint
    }

    participantsState.onNext(CallParticipantsState.update(participantsState.value!!, page))
  }

  fun onLocalPictureInPictureClicked() {
    val state = participantsState.value!!
    participantsState.onNext(CallParticipantsState.setExpanded(participantsState.value!!, state.localRenderState != WebRtcLocalRenderState.EXPANDED))
  }

  fun onDismissedVideoTooltip() {
    canDisplayTooltipIfNeeded = false
  }

  fun onDismissedSwitchCameraTooltip() {
    canDisplaySwitchCameraTooltipIfNeeded = false
    SignalStore.tooltips.markCallingSwitchCameraTooltipSeen()
  }

  @MainThread
  fun updateFromWebRtcViewModel(webRtcViewModel: WebRtcViewModel, enableVideo: Boolean) {
    canEnterPipMode.value = !webRtcViewModel.state.isPreJoinOrNetworkUnavailable
    if (isCallStarting && webRtcViewModel.state.isPassedPreJoin) {
      isCallStarting = false
    }

    val localParticipant = webRtcViewModel.localParticipant

    internalMicrophoneEnabled.value = localParticipant.isMicrophoneEnabled

    val state: CallParticipantsState = participantsState.value!!
    val wasScreenSharing: Boolean = state.focusedParticipant.isScreenSharing
    val newState: CallParticipantsState = CallParticipantsState.update(state, webRtcViewModel, enableVideo)

    participantsState.onNext(newState)
    if (switchOnFirstScreenShare && !wasScreenSharing && newState.focusedParticipant.isScreenSharing) {
      switchOnFirstScreenShare = false
      events.value = CallEvent.SwitchToSpeaker
    }

    if (webRtcViewModel.groupState.isConnected) {
      if (!containsPlaceholders(previousParticipantList)) {
        val update = CallParticipantListUpdate.computeDeltaUpdate(previousParticipantList, webRtcViewModel.remoteParticipants)
        callParticipantListUpdate.value = update
      }

      previousParticipantList = webRtcViewModel.remoteParticipants
      identityChangedRecipients.value = webRtcViewModel.identityChangedParticipants
    }

    updateWebRtcControls(
      webRtcViewModel.state,
      webRtcViewModel.groupState,
      localParticipant.cameraState.isEnabled,
      webRtcViewModel.isRemoteVideoEnabled,
      webRtcViewModel.isRemoteVideoOffer,
      localParticipant.isMoreThanOneCameraAvailable,
      webRtcViewModel.hasAtLeastOneRemote,
      webRtcViewModel.activeDevice,
      webRtcViewModel.availableDevices,
      webRtcViewModel.remoteDevicesCount.orElse(0L),
      webRtcViewModel.participantLimit,
      webRtcViewModel.recipient.isCallLink,
      webRtcViewModel.remoteParticipants.size > CallParticipantsState.SMALL_GROUP_MAX
    )

    pendingParticipants.onNext(webRtcViewModel.pendingParticipants)

    if (newState.isInOutgoingRingingMode) {
      cancelTimer()
      if (!wasInOutgoingRingingMode) {
        elapsedTimeHandler.postDelayed(stopOutgoingRingingMode, CallParticipantsState.MAX_OUTGOING_GROUP_RING_DURATION)
      }
      wasInOutgoingRingingMode = true
    } else {
      if (webRtcViewModel.state == WebRtcViewModel.State.CALL_CONNECTED && callConnectedTime == -1L) {
        callConnectedTime = if (wasInOutgoingRingingMode) System.currentTimeMillis() else webRtcViewModel.callConnectedTime
        startTimer()
      } else if (webRtcViewModel.state != WebRtcViewModel.State.CALL_CONNECTED || webRtcViewModel.groupState.isNotIdleOrConnected) {
        cancelTimer()
        callConnectedTime = -1L
      }
    }

    if (webRtcViewModel.state == WebRtcViewModel.State.CALL_PRE_JOIN && webRtcViewModel.groupState.isNotIdle) {
      // Set flag

      if (webRtcViewModel.ringGroup && webRtcViewModel.areRemoteDevicesInCall()) {
        AppDependencies.signalCallManager.setRingGroup(false)
      }
    }

    if (localParticipant.cameraState.isEnabled) {
      canDisplayTooltipIfNeeded = false
      hasEnabledLocalVideo = true
      events.value = CallEvent.DismissVideoTooltip
    }

    if (canDisplayTooltipIfNeeded && webRtcViewModel.isRemoteVideoEnabled && !hasEnabledLocalVideo) {
      canDisplayTooltipIfNeeded = false
      events.value = CallEvent.ShowVideoTooltip
    }

    if (canDisplayPopupIfNeeded && webRtcViewModel.isCellularConnection && NetworkUtil.isConnectedWifi(AppDependencies.application)) {
      canDisplayPopupIfNeeded = false
      events.value = CallEvent.ShowWifiToCellularPopup
    } else if (!webRtcViewModel.isCellularConnection) {
      canDisplayPopupIfNeeded = true
    }

    if (SignalStore.tooltips.showCallingSwitchCameraTooltip() &&
      canDisplaySwitchCameraTooltipIfNeeded &&
      localParticipant.cameraState.isEnabled &&
      webRtcViewModel.state == WebRtcViewModel.State.CALL_CONNECTED &&
      newState.allRemoteParticipants.isNotEmpty()
    ) {
      canDisplaySwitchCameraTooltipIfNeeded = false
      events.value = CallEvent.ShowSwitchCameraTooltip
    }
  }

  @MainThread
  fun updateFromEphemeralState(state: WebRtcEphemeralState) {
    ephemeralState.value = state
  }

  fun startCall(isVideoCall: Boolean) {
    isCallStarting = true
    val recipient = recipient.get()
    if (recipient.isGroup) {
      WebRtcCallRepository.getIdentityRecords(recipient) { identityRecords ->
        if (identityRecords.isUntrusted(false) || identityRecords.isUnverified(false)) {
          val records = identityRecords.unverifiedRecords + identityRecords.untrustedRecords
          events.postValue(CallEvent.ShowGroupCallSafetyNumberChange(records))
        } else {
          events.postValue(CallEvent.StartCall(isVideoCall))
        }
      }
    } else {
      events.postValue(CallEvent.StartCall(isVideoCall))
    }
  }

  private fun stopOutgoingRingingMode() {
    if (callConnectedTime == -1L) {
      callConnectedTime = System.currentTimeMillis()
      startTimer()
    }
  }

  private fun handleTick() {
    if (callConnectedTime == -1L) {
      return
    }

    val newValue = (System.currentTimeMillis() - callConnectedTime) / 1000
    elapsed.onNext(newValue)
    elapsedTimeHandler.postDelayed(elapsedTimeRunnable, 1000)
  }

  private fun updateWebRtcControls(
    state: WebRtcViewModel.State,
    groupState: WebRtcViewModel.GroupCallState,
    isLocalVideoEnabled: Boolean,
    isRemoteVideoEnabled: Boolean,
    isRemoteVideoOffer: Boolean,
    isMoreThanOneCameraAvailable: Boolean,
    hasAtLeastOneRemote: Boolean,
    activeDevice: SignalAudioManager.AudioDevice,
    availableDevices: Set<SignalAudioManager.AudioDevice>,
    remoteDevicesCount: Long,
    participantLimit: Long?,
    isCallLink: Boolean,
    hasParticipantOverflow: Boolean
  ) {
    val callState = when (state) {
      WebRtcViewModel.State.CALL_PRE_JOIN -> WebRtcControls.CallState.PRE_JOIN
      WebRtcViewModel.State.CALL_INCOMING -> {
        answerWithVideoAvailable = isRemoteVideoOffer
        WebRtcControls.CallState.INCOMING
      }
      WebRtcViewModel.State.CALL_OUTGOING, WebRtcViewModel.State.CALL_RINGING -> WebRtcControls.CallState.OUTGOING
      WebRtcViewModel.State.CALL_BUSY, WebRtcViewModel.State.CALL_NEEDS_PERMISSION, WebRtcViewModel.State.CALL_DISCONNECTED -> WebRtcControls.CallState.ENDING
      WebRtcViewModel.State.CALL_DISCONNECTED_GLARE -> WebRtcControls.CallState.INCOMING
      WebRtcViewModel.State.CALL_RECONNECTING -> WebRtcControls.CallState.RECONNECTING
      WebRtcViewModel.State.NETWORK_FAILURE -> WebRtcControls.CallState.ERROR
      WebRtcViewModel.State.CALL_ACCEPTED_ELSEWHERE, WebRtcViewModel.State.CALL_DECLINED_ELSEWHERE, WebRtcViewModel.State.CALL_ONGOING_ELSEWHERE -> WebRtcControls.CallState.HANDLED_ELSEWHERE
      else -> WebRtcControls.CallState.ONGOING
    }

    val groupCallState = when (groupState) {
      WebRtcViewModel.GroupCallState.DISCONNECTED -> WebRtcControls.GroupCallState.DISCONNECTED
      WebRtcViewModel.GroupCallState.CONNECTING, WebRtcViewModel.GroupCallState.RECONNECTING -> {
        if (participantLimit == null || remoteDevicesCount < participantLimit) WebRtcControls.GroupCallState.CONNECTING else WebRtcControls.GroupCallState.FULL
      }
      WebRtcViewModel.GroupCallState.CONNECTED, WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINING, WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINED -> WebRtcControls.GroupCallState.CONNECTED
      WebRtcViewModel.GroupCallState.CONNECTED_AND_PENDING -> WebRtcControls.GroupCallState.PENDING
      else -> WebRtcControls.GroupCallState.NONE
    }

    webRtcControls.value = WebRtcControls(
      isLocalVideoEnabled,
      isRemoteVideoEnabled || isRemoteVideoOffer,
      isMoreThanOneCameraAvailable,
      isInPipMode.value == true,
      hasAtLeastOneRemote,
      callState,
      groupCallState,
      participantLimit,
      WebRtcControls.FoldableState.flat(),
      activeDevice,
      availableDevices,
      isCallLink,
      hasParticipantOverflow
    )
  }

  private fun updateControlsFoldableState(foldableState: WebRtcControls.FoldableState, controls: WebRtcControls): WebRtcControls {
    return controls.withFoldableState(foldableState)
  }

  private fun getRealWebRtcControls(isInPipMode: Boolean, controls: WebRtcControls): WebRtcControls {
    return if (isInPipMode) WebRtcControls.PIP else controls
  }

  private fun shouldShowSpeakerHint(state: CallParticipantsState): Boolean {
    return !state.isInPipMode &&
      state.remoteDevicesCount.orElse(0L) > 1L &&
      state.groupCallState.isConnected &&
      !SignalStore.tooltips.hasSeenGroupCallSpeakerView()
  }

  private fun startTimer() {
    cancelTimer()
    elapsedTimeHandler.removeCallbacks(stopOutgoingRingingMode)
    elapsedTimeHandler.post(elapsedTimeRunnable)
  }

  private fun cancelTimer() {
    return elapsedTimeHandler.removeCallbacks(elapsedTimeRunnable)
  }

  private fun containsPlaceholders(callParticipants: List<CallParticipant>): Boolean {
    return callParticipants.any { it.callParticipantId.demuxId == CallParticipantId.DEFAULT_ID }
  }

  class SafetyNumberChangeEvent(
    val isInPipMode: Boolean,
    val recipientIds: Collection<RecipientId>
  )
}
