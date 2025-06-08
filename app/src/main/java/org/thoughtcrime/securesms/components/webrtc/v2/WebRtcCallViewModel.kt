/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
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
import org.thoughtcrime.securesms.events.GroupCallSpeechEvent
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.LiveRecipient
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.PendingParticipantCollection
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcEphemeralState
import org.thoughtcrime.securesms.util.NetworkUtil
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class WebRtcCallViewModel : ViewModel() {
  private val callPeerRepository = CallPeerRepository(viewModelScope)

  private val internalMicrophoneEnabled = MutableStateFlow(true)
  private val remoteMutedBy = MutableStateFlow<CallParticipant?>(null)
  private val isInPipMode = MutableStateFlow(false)
  private val webRtcControls = MutableStateFlow(WebRtcControls.NONE)
  private val foldableState = MutableStateFlow(WebRtcControls.FoldableState.flat())
  private val identityChangedRecipients = MutableStateFlow<Collection<RecipientId>>(Collections.emptyList())
  private val isLandscapeEnabled = MutableStateFlow<Boolean?>(null)
  private val canEnterPipMode = MutableStateFlow(false)
  private val ephemeralState = MutableStateFlow<WebRtcEphemeralState?>(null)
  private val remoteMutesReported = MutableStateFlow(HashSet<CallParticipantId>())

  private val controlsWithFoldableState: Flow<WebRtcControls> = combine(foldableState, webRtcControls, this::updateControlsFoldableState)
  private val realWebRtcControls: StateFlow<WebRtcControls> = combine(isInPipMode, controlsWithFoldableState, this::getRealWebRtcControls)
    .stateIn(viewModelScope, SharingStarted.Eagerly, WebRtcControls.NONE)
  private val safetyNumberChangeEvent: Flow<SafetyNumberChangeEvent> = combine(isInPipMode, identityChangedRecipients, ::SafetyNumberChangeEvent)

  private val events = MutableSharedFlow<CallEvent>()
  private val callParticipantListUpdate = MutableSharedFlow<CallParticipantListUpdate>()

  private val elapsed = MutableStateFlow(-1L)
  private val participantsState = MutableStateFlow(CallParticipantsState.STARTING_STATE)
  private val pendingParticipants = MutableStateFlow(PendingParticipantCollection())

  private val groupMemberStateUpdater = FlowCollector<List<GroupMemberEntry.FullMember>> { m -> participantsState.update { CallParticipantsState.update(it, m) } }

  private val shouldShowSpeakerHint: Flow<Boolean> = participantsState.map(this::shouldShowSpeakerHint)

  private val elapsedTimeHandler = Handler(Looper.getMainLooper())
  private val elapsedTimeRunnable = Runnable { handleTick() }
  private val stopOutgoingRingingMode = Runnable { stopOutgoingRingingMode() }

  val groupCallSpeechEvents = MutableStateFlow<GroupCallSpeechEvent?>(null)

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
    viewModelScope.launch {
      callPeerRepository.groupMembers.collect(groupMemberStateUpdater)
    }
  }

  override fun onCleared() {
    super.onCleared()
    cancelTimer()
  }

  val microphoneEnabled: StateFlow<Boolean> get() = internalMicrophoneEnabled

  fun getWebRtcControls(): StateFlow<WebRtcControls> = realWebRtcControls

  val recipient: LiveRecipient get() = callPeerRepository.liveRecipient.value

  fun getRecipientFlow(): Flow<Recipient> {
    return callPeerRepository.recipientId.flatMapLatest {
      Recipient.observable(it).asFlow()
    }
  }

  fun setRecipient(recipient: Recipient) {
    callPeerRepository.recipientId.value = recipient.id
  }

  fun setFoldableState(foldableState: WebRtcControls.FoldableState) {
    this.foldableState.update { foldableState }
    participantsState.update { CallParticipantsState.update(it, foldableState) }
  }

  fun getEvents(): Flow<CallEvent> {
    return events
  }

  fun getInCallStatus(): Flow<InCallStatus> {
    val elapsedTime: Flow<Long> = elapsed.map { timeInCall -> if (callConnectedTime == -1L) -1L else timeInCall }

    return combine(
      elapsedTime,
      pendingParticipants,
      participantsState
    ) { time, pendingParticipants, participantsState ->
      if (!recipient.get().isCallLink) {
        return@combine InCallStatus.ElapsedTime(time)
      }

      val pending: Set<PendingParticipantCollection.Entry> = pendingParticipants.getUnresolvedPendingParticipants()

      if (pending.isNotEmpty()) {
        InCallStatus.PendingCallLinkUsers(pending.size)
      } else {
        InCallStatus.JoinedCallLinkUsers(participantsState.participantCount.orElse(0L).toInt())
      }
    }.distinctUntilChanged()
  }

  fun getCallControlsState(): Flow<CallControlsState> {
    val groupSize: Flow<Int> = callPeerRepository.recipientId.filter { it != RecipientId.UNKNOWN }
      .flatMapLatest { Recipient.observable(it).asFlow() }
      .map {
        if (it.isActiveGroup) {
          SignalDatabase.groups.getGroupMemberIds(it.requireGroupId(), GroupTable.MemberSet.FULL_MEMBERS_INCLUDING_SELF).size
        } else {
          0
        }
      }

    return combine(
      callParticipantsState,
      getWebRtcControls(),
      groupSize,
      CallControlsState::fromViewModelData
    )
  }

  val callParticipantsState: Flow<CallParticipantsState> get() = participantsState

  val callParticipantsStateSnapshot: CallParticipantsState get() = participantsState.value

  fun getCallParticipantListUpdate(): Flow<CallParticipantListUpdate> {
    return callParticipantListUpdate
  }

  fun getSafetyNumberChangeEvent(): Flow<SafetyNumberChangeEvent> {
    return safetyNumberChangeEvent
  }

  fun getGroupMembersChanged(): Flow<List<GroupMemberEntry.FullMember>> {
    return callPeerRepository.groupMembersChanged
  }

  fun getGroupMemberCount(): Flow<Int> {
    return callPeerRepository.groupMembersCount
  }

  fun shouldShowSpeakerHint(): Flow<Boolean> {
    return shouldShowSpeakerHint
  }

  fun getCurrentAudioOutput(): WebRtcAudioOutput {
    return getWebRtcControls().value.audioOutput
  }

  fun getEphemeralState(): Flow<WebRtcEphemeralState?> {
    return ephemeralState
  }

  fun canEnterPipMode(): StateFlow<Boolean> {
    return canEnterPipMode
  }

  fun isAnswerWithVideoAvailable(): Boolean {
    return answerWithVideoAvailable
  }

  fun getPendingParticipants(): Flow<PendingParticipantsState> {
    val isInPipMode: Flow<Boolean> = participantsState.map { it.isInPipMode }.distinctUntilChanged()
    return combine(pendingParticipants, isInPipMode, ::PendingParticipantsState)
  }

  fun getPendingParticipantsSnapshot(): PendingParticipantCollection {
    return pendingParticipants.value
  }

  fun setIsInPipMode(isInPipMode: Boolean) {
    this.isInPipMode.update { isInPipMode }
    participantsState.update { CallParticipantsState.update(it, isInPipMode) }
  }

  fun setIsLandscapeEnabled(isLandscapeEnabled: Boolean) {
    this.isLandscapeEnabled.update { isLandscapeEnabled }
  }

  @MainThread
  fun setIsViewingFocusedParticipant(page: CallParticipantsState.SelectedPage) {
    if (page == CallParticipantsState.SelectedPage.FOCUSED) {
      SignalStore.tooltips.markGroupCallSpeakerViewSeen()
    }

    val state = participantsState.value
    if (showScreenShareTip &&
      state.focusedParticipant.isScreenSharing &&
      state.isViewingFocusedParticipant &&
      page == CallParticipantsState.SelectedPage.GRID
    ) {
      showScreenShareTip = false
      viewModelScope.launch {
        events.emit(CallEvent.ShowSwipeToSpeakerHint)
      }
    }

    participantsState.update { CallParticipantsState.update(it, page) }
  }

  fun onLocalPictureInPictureClicked() {
    participantsState.update {
      CallParticipantsState.setExpanded(it, it.localRenderState != WebRtcLocalRenderState.EXPANDED)
    }
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

    groupCallSpeechEvents.update {
      webRtcViewModel.groupCallSpeechEvent
    }

    val localParticipant = webRtcViewModel.localParticipant

    if (remoteMutedBy.value == null && webRtcViewModel.remoteMutedBy != null) {
      remoteMutedBy.update { webRtcViewModel.remoteMutedBy }
      viewModelScope.launch {
        events.emit(
          CallEvent.ShowRemoteMuteToast(
            muted = Recipient.self(),
            mutedBy = remoteMutedBy.value!!.recipient
          )
        )
      }
    }

    internalMicrophoneEnabled.value = localParticipant.isMicrophoneEnabled

    if (internalMicrophoneEnabled.value) {
      remoteMutedBy.update { null }
    }

    val state: CallParticipantsState = participantsState.value!!
    val wasScreenSharing: Boolean = state.focusedParticipant.isScreenSharing
    val newState: CallParticipantsState = CallParticipantsState.update(state, webRtcViewModel, enableVideo)

    participantsState.update { newState }
    if (switchOnFirstScreenShare && !wasScreenSharing && newState.focusedParticipant.isScreenSharing) {
      switchOnFirstScreenShare = false
      viewModelScope.launch {
        events.emit(CallEvent.SwitchToSpeaker)
      }
    }

    if (webRtcViewModel.groupState.isConnected) {
      if (!containsPlaceholders(previousParticipantList)) {
        val update = CallParticipantListUpdate.computeDeltaUpdate(previousParticipantList, webRtcViewModel.remoteParticipants)
        viewModelScope.launch {
          callParticipantListUpdate.emit(update)
        }
      }

      for (remote in webRtcViewModel.remoteParticipants) {
        if (remote.remotelyMutedBy == null) {
          remoteMutesReported.value.remove(remote.callParticipantId)
        } else if (!remoteMutesReported.value.contains(remote.callParticipantId)) {
          remoteMutesReported.value.add(remote.callParticipantId)
          if (remote.callParticipantId.recipientId == remote.remotelyMutedBy.id) {
            // Ignore self-mutes if we're not the recipient (handled above)
            continue
          }
          viewModelScope.launch {
            events.emit(
              CallEvent.ShowRemoteMuteToast(
                muted = remote.recipient,
                mutedBy = remote.remotelyMutedBy
              )
            )
          }
        }
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

    pendingParticipants.update { webRtcViewModel.pendingParticipants }

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
      emitEvent(CallEvent.DismissVideoTooltip)
    }

    if (canDisplayTooltipIfNeeded && webRtcViewModel.isRemoteVideoEnabled && !hasEnabledLocalVideo) {
      canDisplayTooltipIfNeeded = false
      emitEvent(CallEvent.ShowVideoTooltip)
    }

    if (canDisplayPopupIfNeeded && webRtcViewModel.isCellularConnection && NetworkUtil.isConnectedWifi(AppDependencies.application)) {
      canDisplayPopupIfNeeded = false
      emitEvent(CallEvent.ShowWifiToCellularPopup)
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
      emitEvent(CallEvent.ShowSwitchCameraTooltip)
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
          emitEvent(CallEvent.ShowGroupCallSafetyNumberChange(records))
        } else {
          emitEvent(CallEvent.StartCall(isVideoCall))
        }
      }
    } else {
      emitEvent(CallEvent.StartCall(isVideoCall))
    }
  }

  private fun emitEvent(callEvent: CallEvent) {
    viewModelScope.launch {
      events.emit(callEvent)
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
    elapsed.update { newValue }
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
