/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import android.app.Application
import android.telecom.DisconnectCause
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallId
import org.signal.ringrtc.CallManager
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.notifications.DoNotDisturbUtil
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.ringrtc.CameraState
import org.thoughtcrime.securesms.ringrtc.OutgoingVideoSourceRouter
import org.thoughtcrime.securesms.ringrtc.RemotePeer
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager

/**
 * State transition tests for the incoming 1:1 call processor, focused on the
 * accepted-but-not-yet-connected window: once a call is accepted, late vanity
 * toggles and deny requests (which can arrive off stale CALL_INCOMING state)
 * must be ignored.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, instrumentedPackages = ["org.signal.ringrtc"])
class IncomingCallActionProcessorTest {

  companion object {
    private val CALL_ID = CallId(42L)

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @get:Rule
  val signalStore = MockSignalStoreRule(relaxed = setOf(SettingsValues::class))

  private val callManager: CallManager = mockk(relaxed = true)
  private val webRtcInteractor: WebRtcInteractor = mockk(relaxed = true)
  private val router: OutgoingVideoSourceRouter = mockk(relaxed = true)
  private val localSink: BroadcastVideoSink = mockk()

  private val processor = IncomingCallActionProcessor(webRtcInteractor)

  @Before
  fun setUp() {
    every { webRtcInteractor.callManager } returns callManager
    every { webRtcInteractor.startWebRtcCallActivityIfPossible() } returns true
    every { router.cameraState } returns CameraState(CameraState.Direction.FRONT, 2)

    mockkObject(Recipient.Companion)
    every { Recipient.resolved(any()) } returns mockk(relaxed = true)

    mockkObject(SignalDatabase.Companion)
    every { SignalDatabase.calls } returns mockk(relaxed = true)

    mockkStatic(DoNotDisturbUtil::class)
    every { DoNotDisturbUtil.shouldDisturbUserWithCall(any(), any()) } returns true
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `Given an accepted telecom call, when I mute before it connects, then I expect the mute to be applied and recorded`() {
    val state = incomingRingingCall(accepted = true, usesTelecom = true)

    val result = processor.handleSetMuteAudio(state, true)

    assertThat(result.localDeviceState.isMicrophoneEnabled).isFalse()
    verify { webRtcInteractor.setMicrophoneMute(true) }
  }

  @Test
  fun `Given an accepted non-telecom call, when I mute before it connects, then I expect only the state to change`() {
    val state = incomingRingingCall(accepted = true, usesTelecom = false)

    val result = processor.handleSetMuteAudio(state, true)

    assertThat(result.localDeviceState.isMicrophoneEnabled).isFalse()
    verify(exactly = 0) { webRtcInteractor.setMicrophoneMute(any()) }
  }

  @Test
  fun `Given a ringing video call, when I handleAcceptCall, then I expect the audio to be prepared before accepting`() {
    val state = incomingRingingCall()

    val result = processor.handleAcceptCall(state, true)

    assertThat(result.getCallSetupState(CALL_ID).isAccepted).isTrue()
    verify { webRtcInteractor.prepareAudioForAccept(false) }
    verify(exactly = 0) { callManager.acceptCall(any()) }
  }

  @Test
  fun `Given a ringing telecom call, when I handleAcceptCall, then I expect telecom to answer before the audio is prepared`() {
    val state = incomingRingingCall(usesTelecom = true)
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleAcceptCall(state, false)

    verifyOrder {
      webRtcInteractor.activateCall(activePeer.id, CALL_ID.longValue(), false)
      webRtcInteractor.prepareAudioForAccept(false)
    }
  }

  @Test
  fun `Given a ringing telecom call on bluetooth, when I handleAcceptCall, then I expect the accept to wait for bluetooth audio`() {
    val state = incomingRingingCall(usesTelecom = true, activeDevice = SignalAudioManager.AudioDevice.BLUETOOTH)

    processor.handleAcceptCall(state, false)

    verify { webRtcInteractor.prepareAudioForAccept(true) }
  }

  @Test
  fun `Given a ringing non-telecom call on bluetooth, when I handleAcceptCall, then I expect the accept not to wait for bluetooth audio`() {
    val state = incomingRingingCall(usesTelecom = false, activeDevice = SignalAudioManager.AudioDevice.BLUETOOTH)

    processor.handleAcceptCall(state, false)

    verify { webRtcInteractor.prepareAudioForAccept(false) }
  }

  @Test
  fun `Given a telecom conflict on an incoming call, when I handleDropCall as busy, then I expect a busy message and a missed call`() {
    val state = incomingRingingCall(usesTelecom = true, localRinging = false)
    val activePeer = state.callInfoState.requireActivePeer()

    val result = processor.handleDropCall(state, CALL_ID.longValue(), true)

    verify { webRtcInteractor.sendCallMessage(activePeer, match { it.busyMessage.isPresent }) }
    verify { webRtcInteractor.insertMissedCall(activePeer, any(), any()) }
    verify { callManager.hangup() }
    assertThat(result.callInfoState.activePeer).isNull()
  }

  @Test
  fun `Given a ringing telecom call, when telecom fails without a conflict, then I expect a missed call and no busy message`() {
    val state = incomingRingingCall(usesTelecom = true, telecomApproved = false)
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleDropCall(state, CALL_ID.longValue(), false)

    verify(exactly = 0) { webRtcInteractor.sendCallMessage(any(), any()) }
    verify { webRtcInteractor.insertMissedCall(activePeer, any(), any()) }
    verify { callManager.hangup() }
  }

  @Test
  fun `Given an incoming call that is not ringing, when I handleDropCall, then I expect no busy message`() {
    val state = incomingRingingCall(usesTelecom = true, localRinging = false)

    processor.handleDropCall(state, CALL_ID.longValue(), false)

    verify(exactly = 0) { webRtcInteractor.sendCallMessage(any(), any()) }
    verify { callManager.hangup() }
  }

  @Test
  fun `Given a ringing non-telecom call, when I handleAcceptCall, then I expect no telecom answer`() {
    val state = incomingRingingCall(usesTelecom = false)

    processor.handleAcceptCall(state, false)

    verify(exactly = 0) { webRtcInteractor.activateCall(any(), any(), any()) }
  }

  @Test
  fun `Given an accepted call, when the audio is ready, then I expect the call to be accepted`() {
    val state = processor.handleAcceptCall(incomingRingingCall(), true)

    processor.handleAudioReadyForAccept(state)

    verify { callManager.acceptCall(CALL_ID) }
  }

  @Test
  fun `Given a call denied while the audio was preparing, when the audio is ready, then I expect no accept`() {
    val accepted = processor.handleAcceptCall(incomingRingingCall(), true)
    val denied = processor.handleDenyCall(accepted)

    processor.handleAudioReadyForAccept(denied)

    assertThat(denied.callInfoState.activePeer).isNull()
    verify(exactly = 0) { callManager.acceptCall(any()) }
  }

  @Test
  fun `Given an accepted call still waiting on audio, when I handleDenyCall, then I expect the call to be rejected`() {
    val state = processor.handleAcceptCall(incomingRingingCall(), true)
    val activePeer = state.callInfoState.requireActivePeer()

    val result = processor.handleDenyCall(state)

    verify { webRtcInteractor.rejectIncomingCall(activePeer.id, CALL_ID.longValue()) }
    verify { callManager.hangup() }
    assertThat(result.callInfoState.activePeer).isNull()
  }

  @Test
  fun `Given a ringing unaccepted video call, when I enable vanity, then I expect the vanity camera to start`() {
    val state = incomingRingingCall(accepted = false, cameraEnabled = false)

    processor.handleSetIncomingRingingVanity(state, true)

    verify { router.setVanitySink(localSink) }
    verify { router.setEnabled(true) }
  }

  @Test
  fun `Given an accepted call, when I enable vanity, then I expect no change`() {
    val state = incomingRingingCall(accepted = true, cameraEnabled = false)

    val result = processor.handleSetIncomingRingingVanity(state, true)

    assertThat(result).isSameInstanceAs(state)
    verify(exactly = 0) { router.setVanitySink(any()) }
    verify(exactly = 0) { router.setEnabled(any()) }
  }

  @Test
  fun `Given an accepted call with the camera on, when I disable vanity, then I expect the camera to stay on`() {
    val state = incomingRingingCall(accepted = true, cameraEnabled = true)

    val result = processor.handleSetIncomingRingingVanity(state, false)

    assertThat(result).isSameInstanceAs(state)
    verify(exactly = 0) { router.setEnabled(any()) }
  }

  @Test
  fun `Given an accepted call, when I handleDenyCall, then I expect the deny to be ignored`() {
    val state = incomingRingingCall(accepted = true)

    val result = processor.handleDenyCall(state)

    assertThat(result).isSameInstanceAs(state)
    verify(exactly = 0) { webRtcInteractor.sendNotAcceptedCallEventSyncMessage(any(), any(), any()) }
    verify(exactly = 0) { webRtcInteractor.rejectIncomingCall(any(), any()) }
    verify(exactly = 0) { callManager.hangup() }
  }

  @Test
  fun `Given a ringing unaccepted call, when I handleDenyCall, then I expect the call to be rejected and terminated`() {
    val state = incomingRingingCall(accepted = false)
    val activePeer = state.callInfoState.requireActivePeer()

    val result = processor.handleDenyCall(state)

    verify { webRtcInteractor.sendNotAcceptedCallEventSyncMessage(activePeer, false, true) }
    verify { webRtcInteractor.rejectIncomingCall(activePeer.id, CALL_ID.longValue()) }
    verify { callManager.hangup() }
    assertThat(result.callInfoState.activePeer).isNull()
  }

  @Test
  fun `Given a telecom call not yet ringing, when I handleDenyCall, then I expect the deny to be deferred`() {
    val state = incomingRingingCall(usesTelecom = true, localRinging = false)

    val result = processor.handleDenyCall(state)

    assertThat(result.getCallSetupState(CALL_ID).denyPending).isTrue()
    verify(exactly = 0) { webRtcInteractor.rejectIncomingCall(any(), any()) }
    verify(exactly = 0) { callManager.hangup() }
  }

  @Test
  fun `Given a non-telecom call not yet ringing, when I handleDenyCall, then I expect the deny to be ignored`() {
    val state = incomingRingingCall(usesTelecom = false, localRinging = false)

    val result = processor.handleDenyCall(state)

    assertThat(result).isSameInstanceAs(state)
    verify(exactly = 0) { webRtcInteractor.rejectIncomingCall(any(), any()) }
  }

  @Test
  fun `Given a telecom call accepted before ringing, when the audio is ready, then I expect the accept to be deferred`() {
    val state = incomingRingingCall(accepted = true, waitForAudio = true, usesTelecom = true, localRinging = false)

    val result = processor.handleAudioReadyForAccept(state)

    assertThat(result.getCallSetupState(CALL_ID).waitForAudio).isFalse()
    verify(exactly = 0) { callManager.acceptCall(any()) }
  }

  @Test
  fun `Given a telecom call accepted before ringing, when I handleDenyCall, then I expect the deny to be ignored`() {
    val state = incomingRingingCall(accepted = true, usesTelecom = true, localRinging = false)

    val result = processor.handleDenyCall(state)

    assertThat(result).isSameInstanceAs(state)
  }

  @Test
  fun `Given a telecom call accepted before ringing whose audio wait already ended, when telecom adds it, then I expect it to be answered and to wait for audio again without ringing`() {
    val state = incomingRingingCall(accepted = true, usesTelecom = true, localRinging = false)
    val activePeer = state.callInfoState.requireActivePeer()

    val ringing = processor.handleLocalRinging(state, activePeer)
    verify(exactly = 0) { callManager.acceptCall(any()) }

    val approved = processor.handleSetTelecomApproved(ringing, CALL_ID.longValue(), activePeer.id)

    assertThat(approved.getCallSetupState(CALL_ID).waitForAudio).isTrue()
    verify { webRtcInteractor.activateCall(activePeer.id, CALL_ID.longValue(), false) }
    verify { webRtcInteractor.prepareAudioForAccept(false) }
    verify(exactly = 0) { callManager.acceptCall(any()) }
    verify(exactly = 0) { webRtcInteractor.startIncomingRinger(any(), any()) }
    verify(exactly = 0) { webRtcInteractor.startWebRtcCallActivityIfPossible() }

    processor.handleAudioReadyForAccept(approved)

    verify(exactly = 1) { callManager.acceptCall(CALL_ID) }
  }

  @Test
  fun `Given a non-telecom call accepted before ringing, when the audio is ready and then it starts ringing, then I expect a single accept after ringing`() {
    val state = incomingRingingCall(accepted = true, waitForAudio = true, localRinging = false)
    val activePeer = state.callInfoState.requireActivePeer()

    val ready = processor.handleAudioReadyForAccept(state)
    verify(exactly = 0) { callManager.acceptCall(any()) }

    processor.handleLocalRinging(ready, activePeer)

    verify(exactly = 1) { callManager.acceptCall(CALL_ID) }
  }

  @Test
  fun `Given a non-telecom call accepted before ringing, when it starts ringing, then I expect it to be accepted without ringing`() {
    val state = incomingRingingCall(accepted = true, localRinging = false)
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleLocalRinging(state, activePeer)

    verify { callManager.acceptCall(CALL_ID) }
    verify(exactly = 0) { webRtcInteractor.activateCall(any(), any(), any()) }
    verify(exactly = 0) { webRtcInteractor.startIncomingRinger(any(), any()) }
  }

  @Test
  fun `Given a telecom call accepted before ringing still waiting on audio, when telecom adds it, then I expect an answer but no accept and no ringing`() {
    val state = incomingRingingCall(accepted = true, waitForAudio = true, usesTelecom = true, localRinging = false)
    val activePeer = state.callInfoState.requireActivePeer()

    val ringing = processor.handleLocalRinging(state, activePeer)
    processor.handleSetTelecomApproved(ringing, CALL_ID.longValue(), activePeer.id)

    verify { webRtcInteractor.activateCall(activePeer.id, CALL_ID.longValue(), false) }
    verify(exactly = 0) { callManager.acceptCall(any()) }
    verify(exactly = 0) { webRtcInteractor.startIncomingRinger(any(), any()) }
  }

  @Test
  fun `Given a telecom call not yet added, when it starts ringing, then I expect it to be added to telecom without ringing yet`() {
    val state = incomingRingingCall(usesTelecom = true, localRinging = false)
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleLocalRinging(state, activePeer)

    verify { webRtcInteractor.addNewIncomingCall(activePeer.id, CALL_ID.longValue(), true) }
    verify(exactly = 0) { webRtcInteractor.setCallInProgressNotification(any(), any<RemotePeer>(), any(), any()) }
    verify(exactly = 0) { webRtcInteractor.startWebRtcCallActivityIfPossible() }
  }

  @Test
  fun `Given a telecom call added at ringing, when telecom approves it, then I expect it to ring`() {
    val state = incomingRingingCall(usesTelecom = true, localRinging = false)
    val activePeer = state.callInfoState.requireActivePeer()

    val ringing = processor.handleLocalRinging(state, activePeer)
    val result = processor.handleSetTelecomApproved(ringing, CALL_ID.longValue(), activePeer.id)

    assertThat(result.getCallSetupState(CALL_ID).telecomApproved).isTrue()
    verify { webRtcInteractor.startWebRtcCallActivityIfPossible() }
    verify { webRtcInteractor.setCallInProgressNotification(CallNotificationBuilder.TYPE_INCOMING_RINGING, activePeer, true, true) }
  }

  @Test
  fun `Given a telecom call not yet ringing, when telecom approves it, then I expect no ringing`() {
    val state = incomingRingingCall(usesTelecom = true, localRinging = false)
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleSetTelecomApproved(state, CALL_ID.longValue(), activePeer.id)

    verify(exactly = 0) { webRtcInteractor.setCallInProgressNotification(any(), any<RemotePeer>(), any(), any()) }
    verify(exactly = 0) { webRtcInteractor.startWebRtcCallActivityIfPossible() }
  }

  @Test
  fun `Given a non-telecom call, when it starts ringing, then I expect it to ring without telecom`() {
    val state = incomingRingingCall(localRinging = false)
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleLocalRinging(state, activePeer)

    verify(exactly = 0) { webRtcInteractor.addNewIncomingCall(any(), any(), any()) }
    verify { webRtcInteractor.setCallInProgressNotification(CallNotificationBuilder.TYPE_INCOMING_RINGING, activePeer, true, false) }
  }

  @Test
  fun `Given a ringing telecom call not yet approved, when I handleAcceptCall, then I expect telecom to answer once it approves and the accept to wait for audio again`() {
    val state = incomingRingingCall(usesTelecom = true, telecomApproved = false)
    val activePeer = state.callInfoState.requireActivePeer()

    val accepted = processor.handleAcceptCall(state, false)
    verify(exactly = 0) { webRtcInteractor.activateCall(any(), any(), any()) }

    val audioReady = processor.handleAudioReadyForAccept(accepted)
    verify(exactly = 0) { callManager.acceptCall(any()) }

    val approved = processor.handleSetTelecomApproved(audioReady, CALL_ID.longValue(), activePeer.id)

    verify(exactly = 1) { webRtcInteractor.activateCall(activePeer.id, CALL_ID.longValue(), false) }
    verify(exactly = 2) { webRtcInteractor.prepareAudioForAccept(false) }
    verify(exactly = 0) { callManager.acceptCall(any()) }
    verify(exactly = 0) { webRtcInteractor.startIncomingRinger(any(), any()) }

    processor.handleAudioReadyForAccept(approved)

    verify(exactly = 1) { callManager.acceptCall(CALL_ID) }
  }

  @Test
  fun `Given a call denied before ringing, when it starts ringing, then I expect it to be rejected without ringing`() {
    val state = incomingRingingCall(usesTelecom = true, localRinging = false, denyPending = true)
    val activePeer = state.callInfoState.requireActivePeer()

    val result = processor.handleLocalRinging(state, activePeer)

    verify(exactly = 0) { webRtcInteractor.addNewIncomingCall(any(), any(), any()) }
    verify { webRtcInteractor.rejectIncomingCall(activePeer.id, CALL_ID.longValue()) }
    verify { callManager.hangup() }
    verify(exactly = 0) { webRtcInteractor.startIncomingRinger(any(), any()) }
    assertThat(result.callInfoState.activePeer).isNull()
  }

  @Test
  fun `Given a ringing unaccepted call, when I handleLocalHangup, then I expect the call to be rejected and terminated`() {
    val state = incomingRingingCall(accepted = false)
    val activePeer = state.callInfoState.requireActivePeer()

    val result = processor.handleLocalHangup(state)

    verify { webRtcInteractor.rejectIncomingCall(activePeer.id, CALL_ID.longValue()) }
    verify { callManager.hangup() }
    assertThat(result.callInfoState.activePeer).isNull()
  }

  @Test
  fun `Given an accepted call still connecting, when I handleLocalHangup, then I expect the call to be hung up without a reject`() {
    mockkObject(AppDependencies)
    every { AppDependencies.signalServiceAccountManager } returns mockk(relaxed = true)
    every { AppDependencies.signalServiceMessageSender } returns mockk(relaxed = true)
    val state = incomingRingingCall(accepted = true)

    val result = processor.handleLocalHangup(state)

    verify(exactly = 0) { webRtcInteractor.rejectIncomingCall(any(), any()) }
    verify(exactly = 0) { webRtcInteractor.sendNotAcceptedCallEventSyncMessage(any(), any(), any()) }
    verify { webRtcInteractor.sendAcceptedCallEventSyncMessage(any(), false, any()) }
    verify { callManager.hangup() }
    assertThat(result.callInfoState.activePeer).isNull()
  }

  @Test
  fun `Given an accepted telecom call, when I handleLocalHangup, then I expect telecom to be told the hangup was local`() {
    mockkObject(AppDependencies)
    every { AppDependencies.signalServiceAccountManager } returns mockk(relaxed = true)
    every { AppDependencies.signalServiceMessageSender } returns mockk(relaxed = true)
    val state = incomingRingingCall(accepted = true, usesTelecom = true)
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleLocalHangup(state)

    verify { webRtcInteractor.terminateCall(activePeer.id, CALL_ID.longValue(), DisconnectCause.LOCAL) }
  }

  @Test
  fun `Given an accepted telecom call, when the remote hangs up, then I expect telecom to be told the hangup was remote`() {
    val state = incomingRingingCall(accepted = true, usesTelecom = true)
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleEndedRemote(state, CallManager.CallEndReason.REMOTE_HANGUP, activePeer)

    verify { webRtcInteractor.terminateCall(activePeer.id, CALL_ID.longValue(), DisconnectCause.REMOTE) }
  }

  @Test
  fun `Given a non-telecom call, when the remote hangs up, then I expect telecom not to be told`() {
    val state = incomingRingingCall(accepted = true, usesTelecom = false)
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleEndedRemote(state, CallManager.CallEndReason.REMOTE_HANGUP, activePeer)

    verify(exactly = 0) { webRtcInteractor.terminateCall(any(), any(), any()) }
  }

  @Test
  fun `Given a call not yet ringing, when I handleLocalHangup, then I expect the hangup to be deferred`() {
    val state = incomingRingingCall(accepted = true, usesTelecom = true, localRinging = false)

    val result = processor.handleLocalHangup(state)

    assertThat(result.getCallSetupState(CALL_ID).denyPending).isTrue()
    verify(exactly = 0) { callManager.hangup() }
  }

  @Test
  fun `Given a call accepted and hung up before ringing, when it starts ringing, then I expect it to be hung up without a reject`() {
    mockkObject(AppDependencies)
    every { AppDependencies.signalServiceAccountManager } returns mockk(relaxed = true)
    every { AppDependencies.signalServiceMessageSender } returns mockk(relaxed = true)
    val state = incomingRingingCall(accepted = true, usesTelecom = true, localRinging = false, denyPending = true)
    val activePeer = state.callInfoState.requireActivePeer()

    val result = processor.handleLocalRinging(state, activePeer)

    verify(exactly = 0) { webRtcInteractor.addNewIncomingCall(any(), any(), any()) }
    verify { callManager.hangup() }
    verify(exactly = 0) { callManager.acceptCall(any()) }
    verify(exactly = 0) { webRtcInteractor.rejectIncomingCall(any(), any()) }
    verify(exactly = 0) { webRtcInteractor.sendNotAcceptedCallEventSyncMessage(any(), any(), any()) }
    verify { webRtcInteractor.sendAcceptedCallEventSyncMessage(any(), false, any()) }
    verify { webRtcInteractor.terminateCall(activePeer.id, CALL_ID.longValue(), DisconnectCause.LOCAL) }
    assertThat(result.callInfoState.activePeer).isNull()
  }

  @Test
  fun `Given an accepted call, when the remote hangs up before connecting, then I expect no missed call`() {
    val state = incomingRingingCall(accepted = true)
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleEndedRemote(state, CallManager.CallEndReason.REMOTE_HANGUP, activePeer)

    verify(exactly = 0) { webRtcInteractor.insertMissedCall(any(), any(), any()) }
  }

  @Test
  fun `Given an unaccepted ringing call, when the remote hangs up, then I expect a missed call`() {
    val state = incomingRingingCall(accepted = false)
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleEndedRemote(state, CallManager.CallEndReason.REMOTE_HANGUP, activePeer)

    verify { webRtcInteractor.insertMissedCall(activePeer, activePeer.callStartTimestamp, true) }
  }

  private fun incomingRingingCall(
    accepted: Boolean = false,
    cameraEnabled: Boolean = false,
    usesTelecom: Boolean = false,
    localRinging: Boolean = true,
    waitForAudio: Boolean = false,
    denyPending: Boolean = false,
    telecomApproved: Boolean = usesTelecom && localRinging,
    activeDevice: SignalAudioManager.AudioDevice = SignalAudioManager.AudioDevice.NONE
  ): WebRtcServiceState {
    val peer = RemotePeer(RecipientId.from(1L), CALL_ID)
    peer.answering()
    if (localRinging) {
      peer.localRinging()
    }

    return WebRtcServiceState(processor)
      .builder()
      .changeCallInfoState()
      .callState(WebRtcViewModel.State.CALL_INCOMING)
      .putRemotePeer(peer)
      .activePeer(peer)
      .commit()
      .changeCallSetupState(CALL_ID)
      .isRemoteVideoOffer(true)
      .accepted(accepted)
      .usesTelecom(usesTelecom)
      .telecomApproved(telecomApproved)
      .waitForAudio(waitForAudio)
      .denyPending(denyPending)
      .commit()
      .changeLocalDeviceState()
      .cameraState(if (cameraEnabled) CameraState(CameraState.Direction.FRONT, 2) else CameraState.UNKNOWN)
      .setActiveDevice(activeDevice)
      .commit()
      .changeVideoState()
      .router(router)
      .localSink(localSink)
      .commit()
      .build()
  }
}
