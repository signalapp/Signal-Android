/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import android.app.Application
import android.telecom.DisconnectCause
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
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
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.ringrtc.CameraState
import org.thoughtcrime.securesms.ringrtc.OutgoingVideoSourceRouter
import org.thoughtcrime.securesms.ringrtc.RemotePeer
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import org.thoughtcrime.securesms.webrtc.locks.LockManager
import org.webrtc.PeerConnection
import org.whispersystems.signalservice.api.messages.calls.OfferMessage

/**
 * State transition tests for the outgoing 1:1 call processor, focused on calls that die during setup
 * and still have an ongoing call log entry to resolve.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, instrumentedPackages = ["org.signal.ringrtc"])
class OutgoingCallActionProcessorTest {

  companion object {
    private val CALL_ID = CallId(42L)
    private val OTHER_CALL_ID = CallId(43L)

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  private val callManager: CallManager = mockk(relaxed = true)
  private val webRtcInteractor: WebRtcInteractor = mockk(relaxed = true)
  private val router: OutgoingVideoSourceRouter = mockk(relaxed = true)
  private val localSink: BroadcastVideoSink = mockk()

  private val processor = OutgoingCallActionProcessor(webRtcInteractor)

  @Before
  fun setUp() {
    every { webRtcInteractor.callManager } returns callManager
    every { router.cameraState } returns CameraState(CameraState.Direction.FRONT, 2)

    mockkObject(Recipient.Companion)
    every { Recipient.resolved(any()) } returns mockk(relaxed = true)

    mockkObject(SignalDatabase.Companion)
    every { SignalDatabase.threads } returns mockk(relaxed = true)
    every { SignalDatabase.calls } returns mockk(relaxed = true)

    mockkStatic(RecipientUtil::class)
    every { RecipientUtil.setAndSendUniversalExpireTimerIfNecessary(any(), any(), any()) } returns null

    mockkStatic(WebRtcUtil::class)
    every { WebRtcUtil.getInCallPhoneState(any(), any(), any()) } returns LockManager.PhoneState.IN_VIDEO
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `Given a dialing video call, when it ends with a connection failure, then I expect a not accepted sync event`() {
    val state = outgoingDialingCall()
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleEnded(state, CallManager.CallEndReason.CONNECTION_FAILURE, activePeer)

    verify { webRtcInteractor.sendNotAcceptedCallEventSyncMessage(activePeer, true, true) }
  }

  @Test
  fun `Given a dialing audio call, when it times out, then I expect a not accepted sync event`() {
    val state = outgoingDialingCall(videoCall = false)
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleEnded(state, CallManager.CallEndReason.TIMEOUT, activePeer)

    verify { webRtcInteractor.sendNotAcceptedCallEventSyncMessage(activePeer, true, false) }
  }

  @Test
  fun `Given a dialing video call, when setup fails, then I expect a not accepted sync event`() {
    val state = outgoingDialingCall()
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleSetupFailure(state, CALL_ID)

    verify { webRtcInteractor.sendNotAcceptedCallEventSyncMessage(activePeer, true, true) }
  }

  @Test
  fun `Given a dialing video call, when an unrelated call ends, then I expect no sync event`() {
    val state = outgoingDialingCall()

    processor.handleEnded(state, CallManager.CallEndReason.CONNECTION_FAILURE, RemotePeer(RecipientId.from(2L), OTHER_CALL_ID))

    verify(exactly = 0) { webRtcInteractor.sendNotAcceptedCallEventSyncMessage(any(), any(), any()) }
  }

  @Test
  fun `Given a dialing video call, when an unrelated call fails setup, then I expect no sync event`() {
    val state = outgoingDialingCall()

    processor.handleSetupFailure(state, OTHER_CALL_ID)

    verify(exactly = 0) { webRtcInteractor.sendNotAcceptedCallEventSyncMessage(any(), any(), any()) }
  }

  @Test
  fun `Given a video call that has not started its camera yet, when setup fails, then I expect a video sync event`() {
    val state = outgoingDialingCall(videoCall = true, cameraEnabled = false)
    val activePeer = state.callInfoState.requireActivePeer()

    processor.handleSetupFailure(state, CALL_ID)

    verify { webRtcInteractor.sendNotAcceptedCallEventSyncMessage(activePeer, true, true) }
  }

  @Test
  fun `Given a telecom call awaiting approval, when turn servers arrive, then I expect the call not to proceed`() {
    val state = outgoingDialingCall(usesTelecom = true)

    val result = processor.handleTurnServerUpdate(state, listOf(PeerConnection.IceServer.builder("turn:example.org").createIceServer()), false)

    assertThat(result.getCallSetupState(CALL_ID).usesTelecom).isTrue()
    assertThat(result.getCallSetupState(CALL_ID).telecomApproved).isFalse()
    verify(exactly = 0) { webRtcInteractor.callManager }
  }

  @Test
  fun `Given a telecom call with no turn servers, when telecom approves it, then I expect the call not to proceed`() {
    val state = outgoingDialingCall(usesTelecom = true)

    val result = processor.handleSetTelecomApproved(state, CALL_ID.longValue(), RecipientId.from(1L))

    assertThat(result.getCallSetupState(CALL_ID).telecomApproved).isTrue()
    verify(exactly = 0) { webRtcInteractor.callManager }
  }

  @Test
  fun `Given a telecom call, when I mute, then I expect the system microphone to be muted`() {
    val state = outgoingDialingCall(usesTelecom = true)

    val result = processor.handleSetMuteAudio(state, true)

    assertThat(result.localDeviceState.isMicrophoneEnabled).isFalse()
    verify { webRtcInteractor.setMicrophoneMute(true) }
  }

  @Test
  fun `Given a telecom call, when I unmute, then I expect the system microphone to be unmuted`() {
    val state = outgoingDialingCall(usesTelecom = true)

    processor.handleSetMuteAudio(state, false)

    verify { webRtcInteractor.setMicrophoneMute(false) }
  }

  @Test
  fun `Given a non-telecom call, when I mute, then I expect the system microphone to be left alone`() {
    val state = outgoingDialingCall(usesTelecom = false)

    val result = processor.handleSetMuteAudio(state, true)

    assertThat(result.localDeviceState.isMicrophoneEnabled).isFalse()
    verify(exactly = 0) { webRtcInteractor.setMicrophoneMute(any()) }
  }

  @Test
  fun `Given an outgoing call with no active peer yet, when I mute, then I expect the local mute to be recorded`() {
    val result = processor.handleSetMuteAudio(idleState(microphoneEnabled = true), true)

    assertThat(result.localDeviceState.isMicrophoneEnabled).isFalse()
    verify(exactly = 0) { webRtcInteractor.setMicrophoneMute(any()) }
  }

  @Test
  fun `Given an active telecom call, when a stale call is approved, then I expect only the stale call to be terminated`() {
    val state = outgoingDialingCall(usesTelecom = true)

    val result = processor.handleSetTelecomApproved(state, OTHER_CALL_ID.longValue(), RecipientId.from(1L))

    assertThat(result.getCallSetupState(CALL_ID).telecomApproved).isFalse()
    verify { webRtcInteractor.terminateCall(RecipientId.from(1L), OTHER_CALL_ID.longValue(), DisconnectCause.LOCAL) }
    verify(exactly = 0) { webRtcInteractor.terminateCall(any(), CALL_ID.longValue(), any()) }
  }

  @Test
  fun `Given a dialing call, when an unknown call is dropped, then I expect the active call to be left alone`() {
    val state = outgoingDialingCall()

    val result = processor.handleDropCall(state, OTHER_CALL_ID.longValue(), false)

    assertThat(result.callInfoState.callState).isEqualTo(WebRtcViewModel.State.CALL_OUTGOING)
    verify(exactly = 0) { callManager.hangup() }
  }

  @Test
  fun `Given a telecom call, when it starts, then I expect the ringer to wait for telecom approval`() {
    every { webRtcInteractor.shouldUseTelecom() } returns true

    val result = processor.handleStartOutgoingCall(idleState(microphoneEnabled = false), RemotePeer(RecipientId.from(1L), CALL_ID), OfferMessage.Type.AUDIO_CALL)

    assertThat(result.getCallSetupState(CALL_ID).usesTelecom).isTrue()
    verify(exactly = 0) { webRtcInteractor.startOutgoingRinger() }
    verify(exactly = 0) { webRtcInteractor.setMicrophoneMute(any()) }
    verify { webRtcInteractor.addNewOutgoingCall(RecipientId.from(1L), CALL_ID.longValue(), false, null) }
  }

  @Test
  fun `Given a muted telecom call, when telecom approves it, then I expect the system microphone to be muted after the ringer starts`() {
    val state = outgoingDialingCall(videoCall = false, usesTelecom = true, microphoneEnabled = false)

    val result = processor.handleSetTelecomApproved(state, CALL_ID.longValue(), RecipientId.from(1L))

    assertThat(result.getCallSetupState(CALL_ID).telecomApproved).isTrue()
    verifyOrder {
      webRtcInteractor.startOutgoingRinger()
      webRtcInteractor.setMicrophoneMute(true)
    }
  }

  @Test
  fun `Given an unmuted telecom call, when telecom approves it, then I expect the ringer to start and the system microphone to be left alone`() {
    val state = outgoingDialingCall(videoCall = false, usesTelecom = true, microphoneEnabled = true)

    processor.handleSetTelecomApproved(state, CALL_ID.longValue(), RecipientId.from(1L))

    verify { webRtcInteractor.startOutgoingRinger() }
    verify(exactly = 0) { webRtcInteractor.setMicrophoneMute(any()) }
  }

  @Test
  fun `Given a non-telecom call, when it starts, then I expect the ringer to start immediately`() {
    every { webRtcInteractor.shouldUseTelecom() } returns false

    processor.handleStartOutgoingCall(idleState(microphoneEnabled = true), RemotePeer(RecipientId.from(1L), CALL_ID), OfferMessage.Type.AUDIO_CALL)

    verify { webRtcInteractor.startOutgoingRinger() }
  }

  @Test
  fun `Given a muted non-telecom call, when it starts, then I expect no telecom call and the system microphone to be left alone`() {
    every { webRtcInteractor.shouldUseTelecom() } returns false

    val result = processor.handleStartOutgoingCall(idleState(microphoneEnabled = false), RemotePeer(RecipientId.from(1L), CALL_ID), OfferMessage.Type.VIDEO_CALL)

    assertThat(result.getCallSetupState(CALL_ID).usesTelecom).isFalse()
    verify(exactly = 0) { webRtcInteractor.setMicrophoneMute(any()) }
    verify(exactly = 0) { webRtcInteractor.addNewOutgoingCall(any(), any(), any(), any()) }
  }

  @Test
  fun `Given an audio device picked before the call, when a telecom call starts, then I expect it to be passed to telecom`() {
    every { webRtcInteractor.shouldUseTelecom() } returns true

    processor.handleStartOutgoingCall(idleState(microphoneEnabled = true, userSelectedAudioDevice = SignalAudioManager.AudioDevice.SPEAKER_PHONE), RemotePeer(RecipientId.from(1L), CALL_ID), OfferMessage.Type.AUDIO_CALL)

    verify { webRtcInteractor.addNewOutgoingCall(RecipientId.from(1L), CALL_ID.longValue(), false, SignalAudioManager.AudioDevice.SPEAKER_PHONE) }
  }

  private fun idleState(microphoneEnabled: Boolean, userSelectedAudioDevice: SignalAudioManager.AudioDevice? = null): WebRtcServiceState {
    return WebRtcServiceState(processor)
      .builder()
      .changeLocalDeviceState()
      .isMicrophoneEnabled(microphoneEnabled)
      .setUserSelectedAudioDevice(userSelectedAudioDevice)
      .build()
  }

  private fun outgoingDialingCall(videoCall: Boolean = true, cameraEnabled: Boolean = videoCall, usesTelecom: Boolean = false, microphoneEnabled: Boolean = true): WebRtcServiceState {
    val peer = RemotePeer(RecipientId.from(1L), CALL_ID)
    peer.dialing()

    return WebRtcServiceState(processor)
      .builder()
      .changeCallInfoState()
      .callState(WebRtcViewModel.State.CALL_OUTGOING)
      .putRemotePeer(peer)
      .activePeer(peer)
      .commit()
      .changeCallSetupState(CALL_ID)
      .enableVideoOnCreate(videoCall)
      .usesTelecom(usesTelecom)
      .commit()
      .changeLocalDeviceState()
      .cameraState(if (cameraEnabled) CameraState(CameraState.Direction.FRONT, 2) else CameraState.UNKNOWN)
      .isMicrophoneEnabled(microphoneEnabled)
      .commit()
      .changeVideoState()
      .router(router)
      .localSink(localSink)
      .commit()
      .build()
  }
}
