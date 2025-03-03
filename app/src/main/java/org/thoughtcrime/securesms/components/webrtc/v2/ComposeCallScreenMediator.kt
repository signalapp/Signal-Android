/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.webrtc.CallParticipantListUpdate
import org.thoughtcrime.securesms.components.webrtc.CallParticipantsState
import org.thoughtcrime.securesms.components.webrtc.CallReactionScrubber.Companion.CUSTOM_REACTION_BOTTOM_SHEET_TAG
import org.thoughtcrime.securesms.components.webrtc.WebRtcControls
import org.thoughtcrime.securesms.components.webrtc.controls.CallInfoView
import org.thoughtcrime.securesms.components.webrtc.controls.ControlsAndInfoViewModel
import org.thoughtcrime.securesms.components.webrtc.controls.RaiseHandSnackbar
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcEphemeralState
import org.thoughtcrime.securesms.webrtc.CallParticipantsViewState
import kotlin.time.Duration.Companion.seconds

/**
 * Compose call screen wrapper
 */
class ComposeCallScreenMediator(private val activity: WebRtcCallActivity, viewModel: WebRtcCallViewModel) : CallScreenMediator, AdditionalActionsListener {

  companion object {
    private val TAG = Log.tag(ComposeCallScreenMediator::class)
  }

  private val callScreenViewModel = ViewModelProvider(activity)[CallScreenViewModel::class]
  private val controlsAndInfoViewModel = ViewModelProvider(activity)[ControlsAndInfoViewModel::class]
  private val callInfoCallbacks = CallInfoCallbacks(activity, controlsAndInfoViewModel)

  private val controlsListener = MutableStateFlow<CallScreenControlsListener>(CallScreenControlsListener.Empty)
  private val controlsVisibilityListener = MutableStateFlow<CallControlsVisibilityListener>(CallControlsVisibilityListener.Empty)
  private val pendingParticipantsViewListener = MutableStateFlow<PendingParticipantsListener>(PendingParticipantsListener.Empty)

  init {
    activity.setContent {
      val recipient by viewModel.getRecipientFlow().collectAsStateWithLifecycle(Recipient.UNKNOWN)
      val webRtcCallState by callScreenViewModel.callState.collectAsStateWithLifecycle()
      val callScreenState by callScreenViewModel.callScreenState.collectAsStateWithLifecycle()
      val callControlsState by viewModel.getCallControlsState().collectAsStateWithLifecycle(CallControlsState())
      val callParticipantsViewState by callScreenViewModel.callParticipantsViewState.collectAsStateWithLifecycle()
      val callParticipantsState = remember(callParticipantsViewState) { callParticipantsViewState.callParticipantsState }
      val callParticipantsPagerState = remember(callParticipantsState) {
        CallParticipantsPagerState(
          callParticipants = callParticipantsState.gridParticipants,
          focusedParticipant = callParticipantsState.focusedParticipant,
          isRenderInPip = callParticipantsState.isInPipMode,
          hideAvatar = callParticipantsState.hideAvatar
        )
      }
      val dialog by callScreenViewModel.dialog.collectAsStateWithLifecycle(CallScreenDialogType.NONE)
      val callScreenControlsListener by controlsListener.collectAsStateWithLifecycle()
      val callScreenSheetDisplayListener = remember {
        object : CallScreenSheetDisplayListener {
          override fun onAudioDeviceSheetDisplayChanged(displayed: Boolean) {
            callScreenViewModel.callScreenState.update { it.copy(isDisplayingAudioToggleSheet = displayed) }
          }

          override fun onOverflowDisplayChanged(displayed: Boolean) {
            callScreenViewModel.callScreenState.update { it.copy(displayAdditionalActionsDialog = displayed) }
          }

          override fun onVideoTooltipDismissed() {
            callScreenViewModel.callScreenState.update { it.copy(displayVideoTooltip = false) }
          }
        }
      }

      val callControlsVisibilityListener by controlsVisibilityListener.collectAsStateWithLifecycle()
      val onControlsToggled: (Boolean) -> Unit = remember(controlsVisibilityListener) {
        {
          if (it) {
            callControlsVisibilityListener.onShown()
          } else {
            callControlsVisibilityListener.onHidden()
          }
        }
      }

      val pendingParticipantsListener by this.pendingParticipantsViewListener.collectAsStateWithLifecycle()

      val callScreenController = CallScreenController.rememberCallScreenController(
        skipHiddenState = callControlsState.skipHiddenState,
        onControlsToggled = onControlsToggled
      )

      LaunchedEffect(callScreenController) {
        callScreenViewModel.callScreenControllerEvents.collectLatest {
          callScreenController.handleEvent(it)
        }
      }

      SignalTheme(isDarkMode = true) {
        CallScreen(
          callRecipient = recipient,
          webRtcCallState = webRtcCallState,
          isRemoteVideoOffer = viewModel.isAnswerWithVideoAvailable(),
          callScreenState = callScreenState,
          callControlsState = callControlsState,
          callScreenController = callScreenController,
          callScreenControlsListener = callScreenControlsListener,
          additionalActionsListener = this,
          callScreenSheetDisplayListener = callScreenSheetDisplayListener,
          callParticipantsPagerState = callParticipantsPagerState,
          pendingParticipantsListener = pendingParticipantsListener,
          overflowParticipants = callParticipantsState.listParticipants,
          localParticipant = callParticipantsState.localParticipant,
          localRenderState = callParticipantsState.localRenderState,
          reactions = callParticipantsState.reactions,
          callScreenDialogType = dialog,
          callInfoView = {
            CallInfoView.View(
              webRtcCallViewModel = viewModel,
              controlsAndInfoViewModel = controlsAndInfoViewModel,
              callbacks = callInfoCallbacks,
              modifier = Modifier
                .alpha(it)
            )
          },
          raiseHandSnackbar = {
            RaiseHandSnackbar.View(
              webRtcCallViewModel = viewModel,
              showCallInfoListener = { showCallInfo() },
              modifier = it
            )
          },
          onNavigationClick = { activity.onBackPressedDispatcher.onBackPressed() },
          onLocalPictureInPictureClicked = viewModel::onLocalPictureInPictureClicked,
          onControlsToggled = onControlsToggled,
          onCallScreenDialogDismissed = { callScreenViewModel.dialog.update { CallScreenDialogType.NONE } }
        )
      }
    }
  }

  override fun setWebRtcCallState(callState: WebRtcViewModel.State) {
    callScreenViewModel.callState.update { callState }
  }

  override fun setControlsAndInfoVisibilityListener(listener: CallControlsVisibilityListener) {
    controlsVisibilityListener.update { listener }
  }

  override fun onStateRestored() {
    Log.d(TAG, "Ignoring call to onStateRestored.")
  }

  override fun toggleOverflowPopup() {
    callScreenViewModel.callScreenState.update {
      it.copy(displayAdditionalActionsDialog = !it.displayAdditionalActionsDialog)
    }
  }

  override fun restartHideControlsTimer() {
    callScreenViewModel.emitControllerEvent(CallScreenController.Event.RESTART_HIDE_CONTROLS_TIMER)
  }

  override fun showCallInfo() {
    callScreenViewModel.emitControllerEvent(CallScreenController.Event.SHOW_CALL_INFO)
  }

  override fun toggleControls() {
    callScreenViewModel.emitControllerEvent(CallScreenController.Event.TOGGLE_CONTROLS)
  }

  override fun setControlsListener(controlsListener: CallScreenControlsListener) {
    this.controlsListener.update { controlsListener }
  }

  override fun setMicEnabled(enabled: Boolean) {
    Log.d(TAG, "Ignoring call to setMicEnabled.")
  }

  override fun setStatus(status: String) {
    callScreenViewModel.callScreenState.update { it.copy(callStatus = status) }
  }

  override fun setRecipient(recipient: Recipient) {
    callScreenViewModel.callScreenState.update { it.copy(callRecipientId = recipient.id) }
  }

  override fun setWebRtcControls(webRtcControls: WebRtcControls) {
    Log.d(TAG, "Ignoring call to setWebRtcControls.")
  }

  override fun updateCallParticipants(callParticipantsViewState: CallParticipantsViewState) {
    callScreenViewModel.callParticipantsViewState.update { callParticipantsViewState }
  }

  override fun maybeDismissAudioPicker() {
    callScreenViewModel.emitControllerEvent(CallScreenController.Event.DISMISS_AUDIO_PICKER)
  }

  override fun setPendingParticipantsViewListener(pendingParticipantsViewListener: PendingParticipantsListener) {
    this.pendingParticipantsViewListener.update { pendingParticipantsViewListener }
  }

  override fun updatePendingParticipantsList(pendingParticipantsList: PendingParticipantsState) {
    callScreenViewModel.callScreenState.update { it.copy(pendingParticipantsState = pendingParticipantsList) }
  }

  /**
   * This is a no-op since this state is controlled by [CallControlsState]
   */
  override fun setRingGroup(ringGroup: Boolean) {
    Log.d(TAG, "Ignoring call to setRingGroup.")
  }

  override fun switchToSpeakerView() {
    callScreenViewModel.emitControllerEvent(CallScreenController.Event.SWITCH_TO_SPEAKER_VIEW)
  }

  /**
   * This is a no-op since this state is controlled by [CallControlsState]
   */
  override fun enableRingGroup(canRing: Boolean) {
    Log.d(TAG, "Ignoring call to enableRingGroup.")
  }

  override fun showSpeakerViewHint() {
    callScreenViewModel.callScreenState.update { it.copy(displaySwipeToSpeakerHint = true) }
  }

  override fun hideSpeakerViewHint() {
    callScreenViewModel.callScreenState.update { it.copy(displaySwipeToSpeakerHint = false) }
  }

  override fun showVideoTooltip(): Dismissible {
    callScreenViewModel.callScreenState.update { it.copy(displayVideoTooltip = true) }

    return Dismissible {
      callScreenViewModel.callScreenState.update { it.copy(displayVideoTooltip = false) }
    }
  }

  override fun showCameraTooltip(): Dismissible {
    callScreenViewModel.callScreenState.update { it.copy(displaySwitchCameraTooltip = true) }

    return Dismissible {
      callScreenViewModel.callScreenState.update { it.copy(displaySwitchCameraTooltip = false) }
    }
  }

  override fun onCallStateUpdate(callControlsChange: CallControlsChange) {
    callScreenViewModel.emitCallControlsChange(callControlsChange)
  }

  override fun dismissCallOverflowPopup() {
    callScreenViewModel.callScreenState.update { it.copy(displayAdditionalActionsDialog = false) }
  }

  override fun onParticipantListUpdate(callParticipantListUpdate: CallParticipantListUpdate) {
    callScreenViewModel.callParticipantListUpdate.update { callParticipantListUpdate }
  }

  override fun enableParticipantUpdatePopup(enabled: Boolean) {
    callScreenViewModel.callScreenState.update { it.copy(isParticipantUpdatePopupEnabled = enabled) }
  }

  override fun enableCallStateUpdatePopup(enabled: Boolean) {
    callScreenViewModel.callScreenState.update { it.copy(isCallStateUpdatePopupEnabled = enabled) }
  }

  override fun showWifiToCellularPopupWindow() {
    callScreenViewModel.callScreenState.update { it.copy(displayWifiToCellularPopup = true) }
  }

  override fun hideMissingPermissionsNotice() {
    callScreenViewModel.callScreenState.update { it.copy(displayMissingPermissionsNotice = false) }
  }

  override fun onReactWithAnyClick() {
    val bottomSheet = ReactWithAnyEmojiBottomSheetDialogFragment.createForCallingReactions()
    bottomSheet.show(activity.supportFragmentManager, CUSTOM_REACTION_BOTTOM_SHEET_TAG)
    callScreenViewModel.callScreenState.update { it.copy(displayAdditionalActionsDialog = false) }
  }

  override fun onReactClick(reaction: String) {
    AppDependencies.signalCallManager.react(reaction)
    callScreenViewModel.callScreenState.update { it.copy(displayAdditionalActionsDialog = false) }
  }

  override fun onRaiseHandClick(raised: Boolean) {
    AppDependencies.signalCallManager.raiseHand(raised)
    callScreenViewModel.callScreenState.update { it.copy(displayAdditionalActionsDialog = false) }
  }

  /**
   * State holder for compose call screen
   */
  class CallScreenViewModel : ViewModel() {
    val callScreenControllerEvents = MutableSharedFlow<CallScreenController.Event>()
    val callState = MutableStateFlow(WebRtcViewModel.State.IDLE)
    val callScreenState = MutableStateFlow(
      CallScreenState(
        reactions = SignalStore.emoji.reactions.map {
          SignalStore.emoji.getPreferredVariation(it)
        }.toPersistentList()
      )
    )
    val dialog = MutableStateFlow(CallScreenDialogType.NONE)
    val callParticipantsViewState = MutableStateFlow(
      CallParticipantsViewState(
        callParticipantsState = CallParticipantsState(),
        ephemeralState = WebRtcEphemeralState(),
        isPortrait = true,
        isLandscapeEnabled = true
      )
    )

    private var callControlsChangeJob: Job? = null

    val callParticipantListUpdate = MutableStateFlow(CallParticipantListUpdate.computeDeltaUpdate(emptyList(), emptyList()))

    fun emitControllerEvent(controllerEvent: CallScreenController.Event) {
      viewModelScope.launch { callScreenControllerEvents.emit(controllerEvent) }
    }

    fun emitCallControlsChange(callControlsChange: CallControlsChange) {
      viewModelScope.launch {
        callControlsChangeJob?.cancelAndJoin()
        callControlsChangeJob = launch {
          callScreenState.update { it.copy(callControlsChange = callControlsChange) }
          delay(2.seconds)
          callScreenState.update { it.copy(callControlsChange = null) }
        }
      }
    }
  }
}
