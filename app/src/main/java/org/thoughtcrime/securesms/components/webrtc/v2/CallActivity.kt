/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.components.webrtc.CallParticipantsState
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioDevice
import org.thoughtcrime.securesms.components.webrtc.WebRtcCallViewModel
import org.thoughtcrime.securesms.components.webrtc.controls.CallInfoView
import org.thoughtcrime.securesms.components.webrtc.controls.ControlsAndInfoViewModel
import org.thoughtcrime.securesms.components.webrtc.controls.RaiseHandSnackbar
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.messagerequests.CalleeMustAcceptMessageRequestActivity
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.util.FullscreenHelper
import org.thoughtcrime.securesms.util.VibrateUtil
import org.thoughtcrime.securesms.util.viewModel
import org.whispersystems.signalservice.api.messages.calls.HangupMessage
import kotlin.time.Duration.Companion.seconds

/**
 * Entry-point for receiving and making Signal calls.
 */
class CallActivity : BaseActivity(), CallControlsCallback {

  companion object {
    private val TAG = Log.tag(CallActivity::class.java)

    private const val VIBRATE_DURATION = 50
  }

  private val callPermissionsDialogController = CallPermissionsDialogController()
  private val lifecycleDisposable = LifecycleDisposable()

  private val webRtcCallViewModel: WebRtcCallViewModel by viewModels()
  private val controlsAndInfoViewModel: ControlsAndInfoViewModel by viewModels()
  private val viewModel: CallViewModel by viewModel {
    CallViewModel(
      webRtcCallViewModel,
      controlsAndInfoViewModel
    )
  }

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val fullscreenHelper = FullscreenHelper(this)

    lifecycleDisposable.bindTo(this)

    val callInfoCallbacks = CallInfoCallbacks(this, controlsAndInfoViewModel)

    observeCallEvents()
    viewModel.processCallIntent(CallIntent(intent))

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.CREATED) {
        viewModel.callActions.collect {
          when (it) {
            CallViewModel.Action.EnableVideo -> onVideoToggleClick(true)
            is CallViewModel.Action.ShowGroupCallSafetyNumberChangeDialog -> SafetyNumberBottomSheet.forGroupCall(it.untrustedIdentities).show(supportFragmentManager)
            CallViewModel.Action.SwitchToSpeaker -> Unit // TODO - Switch user to speaker view.
          }
        }
      }
    }

    setContent {
      val lifecycleOwner = LocalLifecycleOwner.current
      val callControlsState by webRtcCallViewModel.getCallControlsState(lifecycleOwner).subscribeAsState(initial = CallControlsState())
      val callParticipantsState by webRtcCallViewModel.callParticipantsState.subscribeAsState(initial = CallParticipantsState())
      val callScreenState by viewModel.callScreenState.collectAsState()
      val recipient by remember(callScreenState.callRecipientId) {
        Recipient.observable(callScreenState.callRecipientId)
      }.subscribeAsState(Recipient.UNKNOWN)

      LaunchedEffect(callControlsState.isGroupRingingAllowed) {
        viewModel.onGroupRingAllowedChanged(callControlsState.isGroupRingingAllowed)
      }

      LaunchedEffect(callParticipantsState.callState) {
        if (callParticipantsState.callState == WebRtcViewModel.State.CALL_CONNECTED) {
          window.addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES)
        }

        if (callParticipantsState.callState == WebRtcViewModel.State.CALL_RECONNECTING) {
          VibrateUtil.vibrate(this@CallActivity, VIBRATE_DURATION)
        }
      }

      LaunchedEffect(callScreenState.hangup) {
        val hangup = callScreenState.hangup
        if (hangup != null) {
          if (hangup.hangupMessageType == HangupMessage.Type.NEED_PERMISSION) {
            startActivity(CalleeMustAcceptMessageRequestActivity.createIntent(this@CallActivity, callParticipantsState.recipient.id))
          } else {
            delay(hangup.delay)
          }
          finish()
        }
      }

      var areControlsVisible by remember { mutableStateOf(true) }

      LaunchedEffect(areControlsVisible) {
        if (areControlsVisible) {
          fullscreenHelper.showSystemUI()
        } else {
          fullscreenHelper.hideSystemUI()
        }
      }

      val callScreenDialogType by viewModel.dialog.collectAsState(CallScreenDialogType.NONE)

      SignalTheme {
        Surface {
          CallScreen(
            callRecipient = recipient,
            webRtcCallState = callParticipantsState.callState,
            callScreenState = callScreenState,
            callControlsState = callControlsState,
            callControlsCallback = this,
            callParticipantsPagerState = CallParticipantsPagerState(
              callParticipants = callParticipantsState.gridParticipants,
              focusedParticipant = callParticipantsState.focusedParticipant,
              isRenderInPip = callParticipantsState.isInPipMode,
              hideAvatar = callParticipantsState.hideAvatar
            ),
            overflowParticipants = callParticipantsState.listParticipants,
            localParticipant = callParticipantsState.localParticipant,
            localRenderState = callParticipantsState.localRenderState,
            callScreenDialogType = callScreenDialogType,
            callInfoView = {
              CallInfoView.View(
                webRtcCallViewModel = webRtcCallViewModel,
                controlsAndInfoViewModel = controlsAndInfoViewModel,
                callbacks = callInfoCallbacks,
                modifier = Modifier
                  .alpha(it)
              )
            },
            raiseHandSnackbar = {
              RaiseHandSnackbar.View(
                webRtcCallViewModel = webRtcCallViewModel,
                showCallInfoListener = { /*TODO*/ },
                modifier = it
              )
            },
            onNavigationClick = { finish() },
            onLocalPictureInPictureClicked = webRtcCallViewModel::onLocalPictureInPictureClicked,
            onControlsToggled = { areControlsVisible = it },
            onCallScreenDialogDismissed = viewModel::onCallScreenDialogDismissed
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    viewModel.processCallIntent(CallIntent(intent))
  }

  override fun onResume() {
    Log.i(TAG, "onResume")
    super.onResume()

    if (!EventBus.getDefault().isRegistered(viewModel)) {
      EventBus.getDefault().register(viewModel)
    }

    val stickyEvent = EventBus.getDefault().getStickyEvent(WebRtcViewModel::class.java)
    if (stickyEvent == null) {
      Log.w(TAG, "Activity resumed without service event, perform delay destroy.")

      lifecycleScope.launch {
        delay(1.seconds)
        val retryEvent = EventBus.getDefault().getStickyEvent(WebRtcViewModel::class.java)
        if (retryEvent == null) {
          Log.w(TAG, "Activity still without service event, finishing.")
          finish()
        } else {
          Log.i(TAG, "Event found after delay.")
        }
      }
    }

    if (viewModel.consumeEnterPipOnResume()) {
      // TODO enterPipModeIfPossible()
    }
  }

  override fun onPause() {
    Log.i(TAG, "onPause")
    super.onPause()

    if (!callPermissionsDialogController.isAskingForPermission && !webRtcCallViewModel.isCallStarting && !isChangingConfigurations) {
      val state = webRtcCallViewModel.callParticipantsStateSnapshot
      if (state != null && state.callState.isPreJoinOrNetworkUnavailable) {
        finish()
      }
    }
  }

  override fun onStop() {
    Log.i(TAG, "onStop")
    super.onStop()

    /*
    TODO
    ephemeralStateDisposable.dispose();
     */

    if (!isInPipMode() || isFinishing) {
      viewModel.unregisterEventBus()
      // TODO
      // requestNewSizesThrottle.clear();
    }

    AppDependencies.signalCallManager.setEnableVideo(false)

    if (!webRtcCallViewModel.isCallStarting && !isChangingConfigurations) {
      val state = webRtcCallViewModel.callParticipantsStateSnapshot
      if (state != null) {
        if (state.callState.isPreJoinOrNetworkUnavailable) {
          AppDependencies.signalCallManager.cancelPreJoin()
        } else if (state.callState.inOngoingCall && isInPipMode()) {
          AppDependencies.signalCallManager.relaunchPipOnForeground()
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    //     TODO windowInfoTrackerCallbackAdapter.removeWindowLayoutInfoListener(windowLayoutInfoConsumer);
    viewModel.unregisterEventBus()
  }

  @SuppressLint("MissingSuperCall")
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  override fun onAudioDeviceSheetDisplayChanged(displayed: Boolean) {
    viewModel.onAudioDeviceSheetDisplayChanged(displayed)
  }

  override fun onSelectedAudioDeviceChanged(audioDevice: WebRtcAudioDevice) {
    viewModel.onSelectedAudioDeviceChanged(audioDevice)
  }

  override fun onVideoToggleClick(enabled: Boolean) {
    if (webRtcCallViewModel.recipient.get() != Recipient.UNKNOWN) {
      callPermissionsDialogController.requestCameraPermission(
        activity = this,
        onAllGranted = { viewModel.onVideoToggleChanged(enabled) }
      )
    }
  }

  override fun onMicToggleClick(enabled: Boolean) {
    callPermissionsDialogController.requestAudioPermission(
      activity = this,
      onGranted = { viewModel.onMicToggledChanged(enabled) },
      onDenied = { viewModel.deny() }
    )
  }

  override fun onGroupRingingToggleClick(enabled: Boolean, allowed: Boolean) {
    viewModel.onGroupRingToggleChanged(enabled, allowed)
  }

  override fun onAdditionalActionsClick() {
    viewModel.onAdditionalActionsClick()
  }

  override fun onStartCallClick(isVideoCall: Boolean) {
    webRtcCallViewModel.startCall(isVideoCall)
  }

  override fun onEndCallClick() {
    viewModel.hangup()
  }

  override fun onVideoTooltipDismissed() {
    viewModel.onVideoTooltipDismissed()
  }

  private fun observeCallEvents() {
    webRtcCallViewModel.events.observe(this) { event ->
      viewModel.onCallEvent(event)
    }
  }

  private fun isInPipMode(): Boolean {
    return isSystemPipEnabledAndAvailable() && isInPictureInPictureMode
  }

  private fun isSystemPipEnabledAndAvailable(): Boolean {
    return Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
  }
}
