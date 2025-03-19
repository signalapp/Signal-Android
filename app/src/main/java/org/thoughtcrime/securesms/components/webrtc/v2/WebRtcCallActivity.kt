/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.Manifest
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Surface
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.ringrtc.GroupCall
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.sensors.Orientation
import org.thoughtcrime.securesms.components.webrtc.CallLinkProfileKeySender
import org.thoughtcrime.securesms.components.webrtc.CallParticipantsState
import org.thoughtcrime.securesms.components.webrtc.CallReactionScrubber
import org.thoughtcrime.securesms.components.webrtc.CallToastPopupWindow
import org.thoughtcrime.securesms.components.webrtc.GroupCallSafetyNumberChangeNotificationUtil
import org.thoughtcrime.securesms.components.webrtc.InCallStatus
import org.thoughtcrime.securesms.components.webrtc.PendingParticipantsBottomSheet
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioDevice
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioOutput
import org.thoughtcrime.securesms.components.webrtc.WebRtcControls
import org.thoughtcrime.securesms.components.webrtc.requests.CallLinkIncomingRequestSheet
import org.thoughtcrime.securesms.conversation.ui.error.SafetyNumberChangeDialog
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messagerequests.CalleeMustAcceptMessageRequestActivity
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.ratelimit.RecaptchaProofBottomSheetFragment
import org.thoughtcrime.securesms.ratelimit.RecaptchaRequiredEvent
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.service.webrtc.CallLinkDisconnectReason
import org.thoughtcrime.securesms.service.webrtc.SignalCallManager
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.EllapsedTimeFormatter
import org.thoughtcrime.securesms.util.FullscreenHelper
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.ThrottledDebouncer
import org.thoughtcrime.securesms.util.VibrateUtil
import org.thoughtcrime.securesms.util.WindowUtil
import org.thoughtcrime.securesms.webrtc.CallParticipantsViewState
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager.ChosenAudioDeviceIdentifier
import org.whispersystems.signalservice.api.messages.calls.HangupMessage
import kotlin.time.Duration.Companion.seconds

/** Conversion */
class WebRtcCallActivity : BaseActivity(), SafetyNumberChangeDialog.Callback, ReactWithAnyEmojiBottomSheetDialogFragment.Callback, RecaptchaProofBottomSheetFragment.Callback {

  companion object {
    val TAG = Log.tag(WebRtcCallActivity::class)

    private const val STANDARD_DELAY_FINISH = 1000L
    private const val VIBRATE_DURATION = 50
  }

  private lateinit var fullscreenHelper: FullscreenHelper
  private lateinit var callScreen: CallScreenMediator
  private var videoTooltip: Dismissible? = null
  private var switchCameraTooltip: Dismissible? = null
  private val viewModel: WebRtcCallViewModel by viewModels()
  private var enableVideoIfAvailable: Boolean = false
  private var hasWarnedAboutBluetooth: Boolean = false
  private lateinit var windowLayoutInfoConsumer: WindowLayoutInfoConsumer
  private lateinit var windowInfoTrackerCallbackAdapter: WindowInfoTrackerCallbackAdapter
  private lateinit var requestNewSizesThrottle: ThrottledDebouncer
  private lateinit var pipBuilderParams: PictureInPictureParams.Builder
  private val lifecycleDisposable = LifecycleDisposable()
  private var lastCallLinkDisconnectDialogShowTime: Long = 0L
  private var enterPipOnResume: Boolean = false
  private var lastProcessedIntentTimestamp = 0L
  private var previousEvent: WebRtcViewModel? = null
  private var ephemeralStateDisposable = Disposable.empty()
  private val callPermissionsDialogController = CallPermissionsDialogController()

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  @SuppressLint("MissingInflatedId")
  override fun onCreate(savedInstanceState: Bundle?) {
    val callIntent: CallIntent = getCallIntent()
    Log.i(TAG, "onCreate(${callIntent.isStartedFromFullScreen})")

    lifecycleDisposable.bindTo(this)

    if (Build.VERSION.SDK_INT >= 27) {
      setShowWhenLocked(true)
    } else {
      window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    }

    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    super.onCreate(savedInstanceState)

    requestWindowFeature(Window.FEATURE_NO_TITLE)

    fullscreenHelper = FullscreenHelper(this)

    volumeControlStream = AudioManager.STREAM_VOICE_CALL

    initializeResources()
    initializeViewModel()
    initializePictureInPictureParams()

    callScreen.setControlsAndInfoVisibilityListener(FadeCallback())

    fullscreenHelper.showAndHideWithSystemUI(
      window,
      findViewById(R.id.call_screen_header_gradient),
      findViewById(R.id.webrtc_call_view_toolbar_text),
      findViewById(R.id.webrtc_call_view_toolbar_no_text)
    )

    if (savedInstanceState == null) {
      logIntent(callIntent)

      if (callIntent.action == CallIntent.Action.ANSWER_VIDEO) {
        enableVideoIfAvailable = true
      } else if (callIntent.action == CallIntent.Action.ANSWER_AUDIO || callIntent.isStartedFromFullScreen) {
        enableVideoIfAvailable = false
      } else {
        enableVideoIfAvailable = callIntent.shouldEnableVideoIfAvailable
        callIntent.shouldEnableVideoIfAvailable = false
      }

      processIntent(callIntent)
    } else {
      Log.d(TAG, "Activity likely rotated, not processing intent")
    }

    registerSystemPipChangeListeners()

    windowLayoutInfoConsumer = WindowLayoutInfoConsumer()

    windowInfoTrackerCallbackAdapter = WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(this))
    windowInfoTrackerCallbackAdapter.addWindowLayoutInfoListener(this, SignalExecutors.BOUNDED, windowLayoutInfoConsumer)

    requestNewSizesThrottle = ThrottledDebouncer(1.seconds.inWholeMilliseconds)

    initializePendingParticipantFragmentListener()

    WindowUtil.setNavigationBarColor(this, ContextCompat.getColor(this, R.color.signal_dark_colorSurface))

    if (!hasCameraPermission() && !hasAudioPermission()) {
      askCameraAudioPermissions {
        callScreen.setMicEnabled(viewModel.microphoneEnabled.value)
        handleSetMuteVideo(false)
      }
    } else if (!hasAudioPermission()) {
      askAudioPermissions {
        callScreen.setMicEnabled(viewModel.microphoneEnabled.value)
      }
    }
  }

  override fun onRestoreInstanceState(savedInstanceState: Bundle) {
    super.onRestoreInstanceState(savedInstanceState)
    callScreen.onStateRestored()
  }

  override fun onStart() {
    super.onStart()

    ephemeralStateDisposable = AppDependencies.signalCallManager
      .ephemeralStates()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(viewModel::updateFromEphemeralState)
  }

  override fun onResume() {
    Log.i(TAG, "onResume()")
    super.onResume()

    initializeScreenshotSecurity()

    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }

    val rtcViewModel = EventBus.getDefault().getStickyEvent(WebRtcViewModel::class.java)
    if (rtcViewModel == null) {
      Log.w(TAG, "Activity resumed without service event, perform delay destroy")
      ThreadUtil.runOnMainDelayed({
        val delayedViewModel = EventBus.getDefault().getStickyEvent(WebRtcViewModel::class.java)
        if (delayedViewModel == null) {
          Log.w(TAG, "Activity still without service event, finishing activity")
          finish()
        } else {
          Log.i(TAG, "Event found after delay")
        }
      }, 1.seconds.inWholeMilliseconds)
    }

    if (enterPipOnResume) {
      enterPipOnResume = false
      enterPipModeIfPossible()
    }

    if (SignalStore.rateLimit.needsRecaptcha()) {
      RecaptchaProofBottomSheetFragment.show(supportFragmentManager)
    }
  }

  override fun onNewIntent(intent: Intent) {
    val callIntent = getCallIntent()
    Log.i(TAG, "onNewIntent(${callIntent.isStartedFromFullScreen})")
    super.onNewIntent(intent)
    logIntent(callIntent)
    processIntent(callIntent)
  }

  override fun onPause() {
    Log.i(TAG, "onPause")
    super.onPause()

    if (!isInPipMode() || isFinishing) {
      EventBus.getDefault().unregister(this)
    }

    if (!callPermissionsDialogController.isAskingForPermission && !viewModel.isCallStarting && !isChangingConfigurations) {
      val state = viewModel.callParticipantsStateSnapshot
      if (state.callState.isPreJoinOrNetworkUnavailable || (Build.VERSION.SDK_INT >= 27 && state.callState.isIncomingOrHandledElsewhere)) {
        if (getCallIntent().isStartedFromFullScreen && state.callState == WebRtcViewModel.State.CALL_INCOMING) {
          Log.w(TAG, "Pausing during full-screen incoming call view. Refusing to finish.")
        } else {
          finish()
        }
      }
    }
  }

  override fun onStop() {
    Log.i(TAG, "onStop")
    super.onStop()

    ephemeralStateDisposable.dispose()

    if (!isInPipMode() || isFinishing) {
      EventBus.getDefault().unregister(this)
      requestNewSizesThrottle.clear()
    }

    AppDependencies.signalCallManager.setEnableVideo(false)

    if (!viewModel.isCallStarting && !isChangingConfigurations) {
      val state = viewModel.callParticipantsStateSnapshot
      if (state.callState.isPreJoinOrNetworkUnavailable || state.callState.isIncomingOrHandledElsewhere) {
        AppDependencies.signalCallManager.cancelPreJoin()
      } else if (state.callState.inOngoingCall && isInPipMode()) {
        AppDependencies.signalCallManager.relaunchPipOnForeground()
      }
    }
  }

  override fun onDestroy() {
    Log.d(TAG, "onDestroy")
    super.onDestroy()
    windowInfoTrackerCallbackAdapter.removeWindowLayoutInfoListener(windowLayoutInfoConsumer)
    EventBus.getDefault().unregister(this)
  }

  @SuppressLint("MissingSuperCall")
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  @SuppressLint("MissingSuperCall")
  override fun onUserLeaveHint() {
    Log.d(TAG, "onUserLeaveHint", Exception())
    super.onUserLeaveHint()
    if (viewModel.callParticipantsStateSnapshot.callState != WebRtcViewModel.State.CALL_INCOMING) {
      enterPipModeIfPossible()
    }
  }

  override fun onBackPressed() {
    if (viewModel.callParticipantsStateSnapshot.callState == WebRtcViewModel.State.CALL_INCOMING || !enterPipModeIfPossible()) {
      super.onBackPressed()
    }
  }

  override fun onSendAnywayAfterSafetyNumberChange(changedRecipients: MutableList<RecipientId>) {
    val state: CallParticipantsState = viewModel.callParticipantsStateSnapshot ?: return

    if (state.isCallLink) {
      CallLinkProfileKeySender.onSendAnyway(HashSet(changedRecipients))
    }

    if (state.groupCallState.isConnected) {
      AppDependencies.signalCallManager.groupApproveSafetyChange(changedRecipients)
    } else {
      viewModel.startCall(state.localParticipant.isVideoEnabled)
    }
  }

  override fun onMessageResentAfterSafetyNumberChange() = Unit

  override fun onCanceled() {
    val state: CallParticipantsState = viewModel.callParticipantsStateSnapshot
    if (state.groupCallState.isNotIdle) {
      if (state.callState.isPreJoinOrNetworkUnavailable) {
        AppDependencies.signalCallManager.cancelPreJoin()
        finish()
      } else {
        handleEndCall()
      }
    } else {
      handleTerminate(viewModel.recipient.get(), HangupMessage.Type.NORMAL)
    }
  }

  override fun onReactWithAnyEmojiDialogDismissed() = Unit

  override fun onReactWithAnyEmojiSelected(emoji: String) {
    AppDependencies.signalCallManager.react(emoji)
    callScreen.dismissCallOverflowPopup()
  }

  override fun onProofCompleted() {
    AppDependencies.signalCallManager.resendMediaKeys()
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onRecaptchaRequiredEvent(recaptchaRequiredEvent: RecaptchaRequiredEvent) {
    RecaptchaProofBottomSheetFragment.show(supportFragmentManager)
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onEventMainThread(event: WebRtcViewModel) {
    Log.i(TAG, "Got message from service: ${event.describeDifference(previousEvent)}")

    val previousCallState: WebRtcViewModel.State? = previousEvent?.state

    previousEvent = event

    viewModel.setRecipient(event.recipient)
    callScreen.setRecipient(event.recipient)
    event.isRemoteVideoOffer
    callScreen.setWebRtcCallState(event.state)

    when (event.state) {
      WebRtcViewModel.State.IDLE -> Unit
      WebRtcViewModel.State.CALL_PRE_JOIN -> handleCallPreJoin(event)
      WebRtcViewModel.State.CALL_INCOMING -> {
        if (previousCallState == WebRtcViewModel.State.NETWORK_FAILURE) {
          Log.d(TAG, "Incoming call directly from network failure state. Recreating activity.")
          recreate()
          return
        }
      }
      WebRtcViewModel.State.CALL_OUTGOING -> handleOutgoingCall(event)
      WebRtcViewModel.State.CALL_CONNECTED -> handleCallConnected(event)
      WebRtcViewModel.State.CALL_RINGING -> handleCallRinging()
      WebRtcViewModel.State.CALL_BUSY -> handleCallBusy()
      WebRtcViewModel.State.CALL_DISCONNECTED -> {
        if (event.groupCallEndReason == GroupCall.GroupCallEndReason.HAS_MAX_DEVICES) {
          handleGroupCallHasMaxDevices(event.recipient)
        } else {
          handleTerminate(event.recipient, HangupMessage.Type.NORMAL)
        }
      }
      WebRtcViewModel.State.CALL_DISCONNECTED_GLARE -> handleGlare(event.recipient)
      WebRtcViewModel.State.CALL_NEEDS_PERMISSION -> handleTerminate(event.recipient, HangupMessage.Type.NEED_PERMISSION)
      WebRtcViewModel.State.CALL_RECONNECTING -> handleCallReconnecting()
      WebRtcViewModel.State.NETWORK_FAILURE -> handleServerFailure()
      WebRtcViewModel.State.RECIPIENT_UNAVAILABLE -> handleRecipientUnavailable()
      WebRtcViewModel.State.NO_SUCH_USER -> handleNoSuchUser(event)
      WebRtcViewModel.State.UNTRUSTED_IDENTITY -> handleUntrustedIdentity(event)
      WebRtcViewModel.State.CALL_ACCEPTED_ELSEWHERE -> handleTerminate(event.recipient, HangupMessage.Type.ACCEPTED)
      WebRtcViewModel.State.CALL_DECLINED_ELSEWHERE -> handleTerminate(event.recipient, HangupMessage.Type.DECLINED)
      WebRtcViewModel.State.CALL_ONGOING_ELSEWHERE -> handleTerminate(event.recipient, HangupMessage.Type.BUSY)
    }

    if (event.callLinkDisconnectReason != null && event.callLinkDisconnectReason.postedAt > lastCallLinkDisconnectDialogShowTime) {
      lastCallLinkDisconnectDialogShowTime = System.currentTimeMillis()

      when (event.callLinkDisconnectReason) {
        is CallLinkDisconnectReason.RemovedFromCall -> displayRemovedFromCallLinkDialog()
        is CallLinkDisconnectReason.DeniedRequestToJoinCall -> displayDeniedRequestToJoinCallLinkDialog()
      }
    }

    val enableVideo = event.localParticipant.cameraState.cameraCount > 0 && enableVideoIfAvailable
    viewModel.updateFromWebRtcViewModel(event, enableVideo)

    if (enableVideo) {
      enableVideoIfAvailable = false
      handleSetMuteVideo(false)
    }

    if (event.bluetoothPermissionDenied && !hasWarnedAboutBluetooth && !isFinishing) {
      MaterialAlertDialogBuilder(this)
        .setTitle(R.string.WebRtcCallActivity__bluetooth_permission_denied)
        .setMessage(R.string.WebRtcCallActivity__please_enable_the_nearby_devices_permission_to_use_bluetooth_during_a_call)
        .setPositiveButton(R.string.WebRtcCallActivity__open_settings) { _, _ -> startActivity(Permissions.getApplicationSettingsIntent(this)) }
        .setNegativeButton(R.string.WebRtcCallActivity__not_now, null)
        .show()

      hasWarnedAboutBluetooth = true
    }
  }

  private fun getCallIntent(): CallIntent {
    return CallIntent(intent)
  }

  private fun initializeResources() {
    callScreen = CallScreenMediator.create(this, viewModel)
    callScreen.setControlsListener(ControlsListener())

    val viewRoot = rootView()
  }

  private fun initializeViewModel() {
    val orientation: Orientation = resolveOrientationFromContext()
    if (orientation == Orientation.PORTRAIT_BOTTOM_EDGE) {
      WindowUtil.setNavigationBarColor(this, ContextCompat.getColor(this, R.color.signal_dark_colorSurface2))
      WindowUtil.clearTranslucentNavigationBar(window)
    }

    AppDependencies.signalCallManager.orientationChanged(true, orientation.degrees)

    viewModel.setIsLandscapeEnabled(true)
    viewModel.setIsInPipMode(isInPipMode())

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          viewModel.microphoneEnabled.collectLatest {
            callScreen.setMicEnabled(it)
          }
        }

        launch {
          viewModel.getWebRtcControls().collectLatest {
            callScreen.setWebRtcControls(it)
          }
        }

        launch {
          viewModel.getEvents().collect { handleViewModelEvent(it) }
        }

        launch {
          viewModel.getInCallStatus().collectLatest {
            handleInCallStatus(it)
          }
        }

        launch {
          viewModel.getRecipientFlow().collectLatest {
            callScreen.setRecipient(it)
          }
        }

        launch {
          val isStartedFromCallLink = getCallIntent().isStartedFromCallLink
          combine(
            viewModel.callParticipantsState,
            viewModel.getEphemeralState().filterNotNull()
          ) { state, ephemeralState ->
            CallParticipantsViewState(state, ephemeralState, orientation == Orientation.PORTRAIT_BOTTOM_EDGE, true)
          }.collectLatest(callScreen::updateCallParticipants)
        }

        launch {
          viewModel.getCallParticipantListUpdate().collectLatest(callScreen::onParticipantListUpdate)
        }

        launch {
          viewModel.getSafetyNumberChangeEvent().collect { handleSafetyNumberChangeEvent(it) }
        }

        launch {
          viewModel.getGroupMembersChanged().collectLatest { updateGroupMembersForGroupCall() }
        }

        launch {
          viewModel.getGroupMemberCount().collectLatest { handleGroupMemberCountChange(it) }
        }

        launch {
          viewModel.shouldShowSpeakerHint().collectLatest { updateSpeakerHint(it) }
        }
      }
    }

    rootView().viewTreeObserver.addOnGlobalLayoutListener {
      val state = viewModel.callParticipantsStateSnapshot
      if (state.needsNewRequestSizes()) {
        requestNewSizesThrottle.publish { AppDependencies.signalCallManager.updateRenderedResolutions() }
      }
    }

    addOnPictureInPictureModeChangedListener { info ->
      viewModel.setIsInPipMode(info.isInPictureInPictureMode)
      callScreen.enableParticipantUpdatePopup(!info.isInPictureInPictureMode)
      callScreen.enableCallStateUpdatePopup(!info.isInPictureInPictureMode)
      if (info.isInPictureInPictureMode) {
        callScreen.maybeDismissAudioPicker()
      }
      viewModel.setIsLandscapeEnabled(info.isInPictureInPictureMode)
    }

    callScreen.setPendingParticipantsViewListener(PendingParticipantsViewListener())

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          viewModel.getPendingParticipants().collect(callScreen::updatePendingParticipantsList)
        }
      }
    }
  }

  private fun initializePictureInPictureParams() {
    if (isSystemPipEnabledAndAvailable()) {
      val orientation = resolveOrientationFromContext()
      val aspectRatio = if (orientation == Orientation.PORTRAIT_BOTTOM_EDGE) {
        Rational(9, 16)
      } else {
        Rational(16, 9)
      }

      pipBuilderParams = PictureInPictureParams.Builder()
      pipBuilderParams.setAspectRatio(aspectRatio)

      if (Build.VERSION.SDK_INT >= 31) {
        lifecycleScope.launch {
          lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
              viewModel.canEnterPipMode().collectLatest {
                pipBuilderParams.setAutoEnterEnabled(it)
                tryToSetPictureInPictureParams()
              }
            }
          }
        }
      } else {
        tryToSetPictureInPictureParams()
      }
    }
  }

  private fun logIntent(callIntent: CallIntent) {
    Log.d(TAG, callIntent.toString())
  }

  private fun processIntent(callIntent: CallIntent) {
    when (callIntent.action) {
      CallIntent.Action.ANSWER_AUDIO -> handleAnswerWithAudio()
      CallIntent.Action.ANSWER_VIDEO -> handleAnswerWithVideo()
      CallIntent.Action.DENY -> handleDenyCall()
      CallIntent.Action.END_CALL -> handleEndCall()
      else -> Unit
    }

    if (System.currentTimeMillis() - lastProcessedIntentTimestamp > 1.seconds.inWholeMilliseconds) {
      enterPipOnResume = callIntent.shouldLaunchInPip
    }

    lastProcessedIntentTimestamp = System.currentTimeMillis()
  }

  private fun registerSystemPipChangeListeners() {
    addOnPictureInPictureModeChangedListener {
      CallReactionScrubber.dismissCustomEmojiBottomSheet(supportFragmentManager)
    }
  }

  private fun initializePendingParticipantFragmentListener() {
    supportFragmentManager.setFragmentResultListener(
      PendingParticipantsBottomSheet.REQUEST_KEY,
      this
    ) { _, result ->
      val action: PendingParticipantsBottomSheet.Action = PendingParticipantsBottomSheet.getAction(result)
      val recipientIds = viewModel.getPendingParticipantsSnapshot().getUnresolvedPendingParticipants().map { it.recipient.id }

      when (action) {
        PendingParticipantsBottomSheet.Action.NONE -> Unit
        PendingParticipantsBottomSheet.Action.APPROVE_ALL -> {
          MaterialAlertDialogBuilder(this)
            .setTitle(resources.getQuantityString(R.plurals.WebRtcCallActivity__approve_d_requests, recipientIds.size, recipientIds.size))
            .setMessage(resources.getQuantityString(R.plurals.WebRtcCallActivity__d_people_will_be_added_to_the_call, recipientIds.size, recipientIds.size))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.WebRtcCallActivity__approve_all) { _, _ ->
              for (id in recipientIds) {
                AppDependencies.signalCallManager.setCallLinkJoinRequestAccepted(id)
              }
            }
            .show()
        }

        PendingParticipantsBottomSheet.Action.DENY_ALL -> {
          MaterialAlertDialogBuilder(this)
            .setTitle(resources.getQuantityString(R.plurals.WebRtcCallActivity__deny_d_requests, recipientIds.size, recipientIds.size))
            .setMessage(resources.getQuantityString(R.plurals.WebRtcCallActivity__d_people_will_not_be_added_to_the_call, recipientIds.size, recipientIds.size))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.WebRtcCallActivity__deny_all) { _, _ ->
              for (id in recipientIds) {
                AppDependencies.signalCallManager.setCallLinkJoinRequestRejected(id)
              }
            }
            .show()
        }
      }
    }
  }

  private fun hasCameraPermission(): Boolean {
    return Permissions.hasAll(this, Manifest.permission.CAMERA)
  }

  private fun hasAudioPermission(): Boolean {
    return Permissions.hasAll(this, Manifest.permission.RECORD_AUDIO)
  }

  private fun askCameraAudioPermissions(onGranted: () -> Unit) {
    callPermissionsDialogController.requestCameraAndAudioPermission(
      activity = this,
      onAllGranted = onGranted,
      onCameraGranted = { callScreen.hideMissingPermissionsNotice() },
      onAudioDenied = this::handleDenyCall
    )
  }

  private fun askCameraPermissions(onGranted: () -> Unit) {
    callPermissionsDialogController.requestCameraPermission(this) {
      onGranted()
      callScreen.hideMissingPermissionsNotice()
    }
  }

  private fun askAudioPermissions(onGranted: () -> Unit) {
    callPermissionsDialogController.requestAudioPermission(
      activity = this,
      onGranted = onGranted,
      onDenied = this::handleDenyCall
    )
  }

  private fun handleSetAudioHandset() {
    AppDependencies.signalCallManager.selectAudioDevice(ChosenAudioDeviceIdentifier(SignalAudioManager.AudioDevice.EARPIECE))
  }

  private fun handleSetAudioSpeaker() {
    AppDependencies.signalCallManager.selectAudioDevice(ChosenAudioDeviceIdentifier(SignalAudioManager.AudioDevice.SPEAKER_PHONE))
  }

  private fun handleSetAudioBluetooth() {
    AppDependencies.signalCallManager.selectAudioDevice(ChosenAudioDeviceIdentifier(SignalAudioManager.AudioDevice.BLUETOOTH))
  }

  private fun handleSetAudioWiredHeadset() {
    AppDependencies.signalCallManager.selectAudioDevice(ChosenAudioDeviceIdentifier(SignalAudioManager.AudioDevice.WIRED_HEADSET))
  }

  private fun handleSetMuteAudio(enabled: Boolean) {
    AppDependencies.signalCallManager.setMuteAudio(enabled)
  }

  private fun handleSetMuteVideo(muted: Boolean) {
    val recipient = viewModel.recipient.get()

    if (recipient != Recipient.UNKNOWN) {
      askCameraPermissions {
        AppDependencies.signalCallManager.setEnableVideo(!muted)
      }
    }
  }

  private fun handleFlipCamera() {
    AppDependencies.signalCallManager.flipCamera()
  }

  private fun handleAnswerWithAudio() {
    askAudioPermissions {
      callScreen.setStatus(getString(R.string.RedPhone_answering))
      AppDependencies.signalCallManager.acceptCall(false)
    }
  }

  private fun handleAnswerWithVideo() {
    val onGranted: () -> Unit = {
      callScreen.setStatus(getString(R.string.RedPhone_answering))
      AppDependencies.signalCallManager.acceptCall(true)
      handleSetMuteVideo(false)
    }

    if (!hasCameraPermission() && !hasAudioPermission()) {
      askCameraAudioPermissions(onGranted)
    } else if (!hasAudioPermission()) {
      askAudioPermissions(onGranted)
    } else {
      askCameraPermissions(onGranted)
    }
  }

  private fun handleDenyCall() {
    val recipient = viewModel.recipient.get()
    if (recipient != Recipient.UNKNOWN) {
      AppDependencies.signalCallManager.denyCall()

      callScreen.setRecipient(recipient)
      callScreen.setStatus(getString(R.string.RedPhone_ending_call))
      delayedFinish()
    }
  }

  private fun handleEndCall() {
    Log.i(TAG, "Hangup pressed, handling termination now...")
    AppDependencies.signalCallManager.localHangup()
  }

  private fun handleOutgoingCall(event: WebRtcViewModel) {
    if (event.groupState.isNotIdle) {
      callScreen.setStatusFromGroupCallState(this, event.groupState)
    } else {
      callScreen.setStatus(getString(R.string.WebRtcCallActivity__calling))
    }
  }

  private fun handleGroupCallHasMaxDevices(recipient: Recipient) {
    MaterialAlertDialogBuilder(this)
      .setMessage(R.string.WebRtcCallView__call_is_full)
      .setPositiveButton(android.R.string.ok) { _, _ -> handleTerminate(recipient, HangupMessage.Type.NORMAL) }
      .show()
  }

  private fun handleTerminate(recipient: Recipient, hangupType: HangupMessage.Type) {
    Log.i(TAG, "handleTerminate called: $hangupType")

    callScreen.setStatusFromHangupType(this, hangupType)

    EventBus.getDefault().removeStickyEvent(WebRtcViewModel::class.java)

    if (hangupType == HangupMessage.Type.NEED_PERMISSION) {
      startActivity(CalleeMustAcceptMessageRequestActivity.createIntent(this, recipient.id))
    }

    delayedFinish()
  }

  private fun handleGlare(recipient: Recipient) {
    Log.i(TAG, "handleGlare: ${recipient.id}")
    callScreen.setStatus("")
  }

  private fun handleCallRinging() {
    callScreen.setStatus(getString(R.string.RedPhone_ringing))
  }

  private fun handleCallBusy() {
    EventBus.getDefault().removeStickyEvent(WebRtcViewModel::class.java)
    callScreen.setStatus(getString(R.string.RedPhone_busy))
    delayedFinish(SignalCallManager.BUSY_TONE_LENGTH.toLong())
  }

  private fun handleCallPreJoin(event: WebRtcViewModel) {
    if (event.groupState.isNotIdle) {
      callScreen.setRingGroup(event.ringGroup)

      if (event.ringGroup && event.areRemoteDevicesInCall()) {
        AppDependencies.signalCallManager.setRingGroup(false)
      }
    }
  }

  private fun handleCallConnected(event: WebRtcViewModel) {
    window.addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES)
    if (event.groupState.isNotIdleOrConnected) {
      callScreen.setStatusFromGroupCallState(this, event.groupState)
    }
  }

  private fun handleCallReconnecting() {
    callScreen.setStatus(getString(R.string.WebRtcCallView__reconnecting))
    VibrateUtil.vibrate(this, VIBRATE_DURATION)
  }

  private fun handleRecipientUnavailable() {
    EventBus.getDefault().removeStickyEvent(WebRtcViewModel::class.java)
    callScreen.setStatus(getString(R.string.RedPhone_recipient_unavailable))
    delayedFinish()
  }

  private fun handleServerFailure() {
    EventBus.getDefault().removeStickyEvent(WebRtcViewModel::class.java)
    callScreen.setStatus(getString(R.string.RedPhone_network_failed))
  }

  private fun handleNoSuchUser(event: WebRtcViewModel) {
    if (isFinishing) return
    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.RedPhone_number_not_registered)
      .setIcon(R.drawable.symbol_error_triangle_fill_24)
      .setMessage(R.string.RedPhone_the_number_you_dialed_does_not_support_secure_voice)
      .setCancelable(true)
      .setPositiveButton(R.string.RedPhone_got_it) { _, _ -> handleTerminate(event.recipient, HangupMessage.Type.NORMAL) }
      .setOnCancelListener { handleTerminate(event.recipient, HangupMessage.Type.NORMAL) }
      .show()
  }

  private fun handleUntrustedIdentity(event: WebRtcViewModel) {
    val theirKey = event.remoteParticipants[0].identityKey
    val recipient = event.remoteParticipants[0].recipient

    if (theirKey == null) {
      Log.w(TAG, "Untrusted identity without an identity key.")
    }

    SafetyNumberBottomSheet.forCall(recipient.id).show(supportFragmentManager)
  }

  private fun rootView(): ViewGroup = findViewById(android.R.id.content)

  private fun handleViewModelEvent(event: CallEvent) {
    when (event) {
      is CallEvent.StartCall -> startCall(event.isVideoCall)
      is CallEvent.ShowGroupCallSafetyNumberChange -> SafetyNumberBottomSheet.forGroupCall(event.identityRecords).show(supportFragmentManager)
      is CallEvent.SwitchToSpeaker -> callScreen.switchToSpeakerView()
      is CallEvent.ShowSwipeToSpeakerHint -> CallToastPopupWindow.show(rootView())
      is CallEvent.ShowVideoTooltip -> {
        if (isInPipMode()) return

        if (videoTooltip == null) {
          videoTooltip = callScreen.showVideoTooltip()
        }
      }

      is CallEvent.DismissVideoTooltip -> {
        if (isInPipMode()) return

        videoTooltip?.dismiss()
        videoTooltip = null
      }

      is CallEvent.ShowWifiToCellularPopup -> {
        if (isInPipMode()) return
        callScreen.showWifiToCellularPopupWindow()
      }

      is CallEvent.ShowSwitchCameraTooltip -> {
        if (isInPipMode()) return

        if (switchCameraTooltip == null) {
          switchCameraTooltip = callScreen.showCameraTooltip()
        }
      }

      is CallEvent.DismissSwitchCameraTooltip -> {
        if (isInPipMode()) return

        switchCameraTooltip?.dismiss()
        switchCameraTooltip = null
      }
    }
  }

  private fun handleInCallStatus(inCallStatus: InCallStatus) {
    when (inCallStatus) {
      is InCallStatus.ElapsedTime -> {
        val formatter: EllapsedTimeFormatter = EllapsedTimeFormatter.fromDurationMillis(inCallStatus.elapsedTime) ?: return
        callScreen.setStatus(getString(R.string.WebRtcCallActivity__signal_s, formatter.toString()))
      }
      is InCallStatus.PendingCallLinkUsers -> {
        val waiting = inCallStatus.pendingUserCount

        callScreen.setStatus(
          resources.getQuantityString(
            R.plurals.WebRtcCallActivity__d_people_waiting,
            waiting,
            waiting
          )
        )
      }
      is InCallStatus.JoinedCallLinkUsers -> {
        val joined = inCallStatus.joinedUserCount

        callScreen.setStatus(
          resources.getQuantityString(
            R.plurals.WebRtcCallActivity__d_people,
            joined,
            joined
          )
        )
      }
    }
  }

  private fun handleSafetyNumberChangeEvent(safetyNumberChangeEvent: WebRtcCallViewModel.SafetyNumberChangeEvent) {
    if (safetyNumberChangeEvent.recipientIds.isNotEmpty()) {
      if (safetyNumberChangeEvent.isInPipMode) {
        GroupCallSafetyNumberChangeNotificationUtil.showNotification(this, viewModel.recipient.get())
      } else {
        GroupCallSafetyNumberChangeNotificationUtil.cancelNotification(this, viewModel.recipient.get())
        SafetyNumberBottomSheet.forDuringGroupCall(safetyNumberChangeEvent.recipientIds).show(supportFragmentManager)
      }
    }
  }

  private fun updateGroupMembersForGroupCall() {
    AppDependencies.signalCallManager.requestUpdateGroupMembers()
  }

  private fun handleGroupMemberCountChange(count: Int) {
    val canRing = count <= RemoteConfig.maxGroupCallRingSize
    callScreen.enableRingGroup(canRing)
    AppDependencies.signalCallManager.setRingGroup(canRing)
  }

  private fun updateSpeakerHint(enabled: Boolean) {
    if (enabled) {
      callScreen.showSpeakerViewHint()
    } else {
      callScreen.hideSpeakerViewHint()
    }
  }

  private fun initializeScreenshotSecurity() {
    if (TextSecurePreferences.isScreenSecurityEnabled(this)) {
      window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
      window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
  }

  private fun enterPipModeIfPossible(): Boolean {
    if (isSystemPipEnabledAndAvailable()) {
      if (viewModel.canEnterPipMode().value == true) {
        try {
          enterPictureInPictureMode(pipBuilderParams.build())
        } catch (e: Exception) {
          Log.w(TAG, "Device lied to us about supporting PiP", e)
          return false
        }

        return true
      }

      if (Build.VERSION.SDK_INT >= 31) {
        pipBuilderParams.setAutoEnterEnabled(false)
      }
    }

    return false
  }

  private fun isInPipMode(): Boolean {
    return isSystemPipEnabledAndAvailable() && isInPictureInPictureMode
  }

  private fun isSystemPipEnabledAndAvailable(): Boolean {
    return Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
  }

  private fun resolveOrientationFromContext(): Orientation {
    val displayOrientation = resources.configuration.orientation
    val displayRotation = ContextCompat.getDisplayOrDefault(this).rotation

    return if (displayOrientation == Configuration.ORIENTATION_PORTRAIT) {
      Orientation.PORTRAIT_BOTTOM_EDGE
    } else if (displayRotation == Surface.ROTATION_270) {
      Orientation.LANDSCAPE_RIGHT_EDGE
    } else {
      Orientation.LANDSCAPE_LEFT_EDGE
    }
  }

  private fun tryToSetPictureInPictureParams() {
    if (Build.VERSION.SDK_INT >= 26) {
      try {
        setPictureInPictureParams(pipBuilderParams.build())
      } catch (e: Exception) {
        Log.w(TAG, "System lied about having PiP available.", e)
      }
    }
  }

  private fun startCall(isVideoCall: Boolean) {
    enableVideoIfAvailable = isVideoCall

    if (isVideoCall) {
      AppDependencies.signalCallManager.startOutgoingVideoCall(viewModel.recipient.get())
    } else {
      AppDependencies.signalCallManager.startOutgoingAudioCall(viewModel.recipient.get())
    }

    MessageSender.onMessageSent()
  }

  private fun delayedFinish(delayMillis: Long = STANDARD_DELAY_FINISH) {
    rootView().postDelayed(this::finish, delayMillis)
  }

  private fun displayRemovedFromCallLinkDialog() {
    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.WebRtcCallActivity__removed_from_call)
      .setMessage(R.string.WebRtcCallActivity__someone_has_removed_you_from_the_call)
      .setPositiveButton(android.R.string.ok, null)
      .show()
  }

  private fun displayDeniedRequestToJoinCallLinkDialog() {
    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.WebRtcCallActivity__join_request_denied)
      .setMessage(R.string.WebRtcCallActivity__your_request_to_join_this_call_has_been_denied)
      .setPositiveButton(android.R.string.ok, null)
      .show()
  }

  private fun maybeDisplaySpeakerphonePopup(nextOutput: WebRtcAudioOutput) {
    val currentOutput = viewModel.getCurrentAudioOutput()
    if (currentOutput == WebRtcAudioOutput.SPEAKER && nextOutput != WebRtcAudioOutput.SPEAKER) {
      callScreen.onCallStateUpdate(CallControlsChange.SPEAKER_OFF)
    } else if (currentOutput != WebRtcAudioOutput.SPEAKER && nextOutput == WebRtcAudioOutput.SPEAKER) {
      callScreen.onCallStateUpdate(CallControlsChange.SPEAKER_ON)
    }
  }

  private inner class WindowLayoutInfoConsumer : Consumer<WindowLayoutInfo> {
    override fun accept(value: WindowLayoutInfo) {
      Log.d(TAG, "On WindowLayoutInfo accepted: $value")

      val feature: FoldingFeature? = value.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
      if (feature != null) {
        val bounds = feature.bounds
        if (feature.isSeparating) {
          Log.d(TAG, "OnWindowLayoutInfo accepted: ensure call view is in table-top diplay mode")
          viewModel.setFoldableState(WebRtcControls.FoldableState.folded(bounds.top))
        } else {
          Log.d(TAG, "OnWindowLayoutInfo accepted: ensure call view is in flat display mode")
          viewModel.setFoldableState(WebRtcControls.FoldableState.flat())
        }
      }
    }
  }

  private inner class FadeCallback : CallControlsVisibilityListener {
    override fun onShown() {
      fullscreenHelper.showSystemUI()
    }

    override fun onHidden() {
      val controlState = viewModel.getWebRtcControls().value
      if (!controlState.displayErrorControls()) {
        fullscreenHelper.hideSystemUI()
        videoTooltip?.dismiss()
      }
    }
  }

  private inner class ControlsListener : CallScreenControlsListener {
    override fun onStartCall(isVideoCall: Boolean) {
      viewModel.startCall(isVideoCall)
    }

    override fun onCancelStartCall() {
      finish()
    }

    override fun onAudioOutputChanged(audioOutput: WebRtcAudioOutput) {
      maybeDisplaySpeakerphonePopup(audioOutput)
      when (audioOutput) {
        WebRtcAudioOutput.HANDSET -> handleSetAudioHandset()
        WebRtcAudioOutput.BLUETOOTH_HEADSET -> handleSetAudioBluetooth()
        WebRtcAudioOutput.SPEAKER -> handleSetAudioSpeaker()
        WebRtcAudioOutput.WIRED_HEADSET -> handleSetAudioWiredHeadset()
      }
    }

    @RequiresApi(31)
    override fun onAudioOutputChanged31(audioOutput: WebRtcAudioDevice) {
      maybeDisplaySpeakerphonePopup(audioOutput.webRtcAudioOutput)
      AppDependencies.signalCallManager.selectAudioDevice(ChosenAudioDeviceIdentifier(audioOutput.deviceId!!))
    }

    override fun onVideoChanged(isVideoEnabled: Boolean) {
      handleSetMuteVideo(!isVideoEnabled)
    }

    override fun onMicChanged(isMicEnabled: Boolean) {
      askAudioPermissions {
        callScreen.onCallStateUpdate(if (isMicEnabled) CallControlsChange.MIC_ON else CallControlsChange.MIC_OFF)
        handleSetMuteAudio(!isMicEnabled)
      }
    }

    override fun onOverflowClicked() {
      callScreen.toggleOverflowPopup()
    }

    override fun onDismissOverflow() {
      callScreen.dismissCallOverflowPopup()
    }

    override fun onCameraDirectionChanged() {
      handleFlipCamera()
    }

    override fun onEndCallPressed() {
      handleEndCall()
    }

    override fun onDenyCallPressed() {
      handleDenyCall()
    }

    override fun onAcceptCallWithVoiceOnlyPressed() {
      handleAnswerWithAudio()
    }

    override fun onAcceptCallPressed() {
      if (viewModel.isAnswerWithVideoAvailable()) {
        handleAnswerWithVideo()
      } else {
        handleAnswerWithAudio()
      }
    }

    override fun onPageChanged(page: CallParticipantsState.SelectedPage) {
      viewModel.setIsViewingFocusedParticipant(page)
    }

    override fun onLocalPictureInPictureClicked() {
      viewModel.onLocalPictureInPictureClicked()
      callScreen.restartHideControlsTimer()
    }

    override fun onRingGroupChanged(ringGroup: Boolean, ringingAllowed: Boolean) {
      if (ringingAllowed) {
        AppDependencies.signalCallManager.setRingGroup(ringGroup)
        callScreen.onCallStateUpdate(if (ringGroup) CallControlsChange.RINGING_ON else CallControlsChange.RINGING_OFF)
      } else {
        AppDependencies.signalCallManager.setRingGroup(false)
        callScreen.onCallStateUpdate(CallControlsChange.RINGING_DISABLED)
      }
    }

    override fun onCallInfoClicked() {
      callScreen.showCallInfo()
    }

    override fun onNavigateUpClicked() {
      onBackPressed()
    }

    override fun toggleControls() {
      val controlState = viewModel.getWebRtcControls().value
      if (!controlState.displayIncomingCallButtons() && !controlState.displayErrorControls()) {
        callScreen.toggleControls()
      }
    }

    override fun onAudioPermissionsRequested(onGranted: Runnable?) {
      askAudioPermissions { onGranted?.run() }
    }
  }

  private inner class PendingParticipantsViewListener : PendingParticipantsListener {
    override fun onLaunchRecipientSheet(pendingRecipient: Recipient) {
      CallLinkIncomingRequestSheet.show(supportFragmentManager, pendingRecipient.id)
    }

    override fun onAllowPendingRecipient(pendingRecipient: Recipient) {
      AppDependencies.signalCallManager.setCallLinkJoinRequestAccepted(pendingRecipient.id)
    }

    override fun onRejectPendingRecipient(pendingRecipient: Recipient) {
      AppDependencies.signalCallManager.setCallLinkJoinRequestRejected(pendingRecipient.id)
    }

    override fun onLaunchPendingRequestsSheet() {
      PendingParticipantsBottomSheet().show(supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}
