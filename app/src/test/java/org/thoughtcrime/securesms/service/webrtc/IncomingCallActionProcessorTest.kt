/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import android.app.Application
import assertk.assertThat
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallId
import org.signal.ringrtc.CallManager
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.ringrtc.CameraState
import org.thoughtcrime.securesms.ringrtc.OutgoingVideoSourceRouter
import org.thoughtcrime.securesms.ringrtc.RemotePeer
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState
import org.thoughtcrime.securesms.testutil.SystemOutLogger

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

  private val callManager: CallManager = mockk(relaxed = true)
  private val webRtcInteractor: WebRtcInteractor = mockk(relaxed = true)
  private val router: OutgoingVideoSourceRouter = mockk(relaxed = true)
  private val localSink: BroadcastVideoSink = mockk()

  private val processor = IncomingCallActionProcessor(webRtcInteractor)

  @Before
  fun setUp() {
    every { webRtcInteractor.callManager } returns callManager
    every { router.cameraState } returns CameraState(CameraState.Direction.FRONT, 2)
  }

  @Test
  fun `Given a ringing video call, when I handleAcceptCall, then I expect the audio to be prepared before accepting`() {
    val state = incomingRingingCall()

    val result = processor.handleAcceptCall(state, true)

    assertThat(result.getCallSetupState(CALL_ID).isAccepted).isTrue()
    verify { webRtcInteractor.prepareAudioForAccept() }
    verify(exactly = 0) { callManager.acceptCall(any()) }
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

    verify { webRtcInteractor.rejectIncomingCall(activePeer.id) }
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
    verify(exactly = 0) { webRtcInteractor.rejectIncomingCall(any()) }
    verify(exactly = 0) { callManager.hangup() }
  }

  @Test
  fun `Given a ringing unaccepted call, when I handleDenyCall, then I expect the call to be rejected and terminated`() {
    val state = incomingRingingCall(accepted = false)
    val activePeer = state.callInfoState.requireActivePeer()

    val result = processor.handleDenyCall(state)

    verify { webRtcInteractor.sendNotAcceptedCallEventSyncMessage(activePeer, false, true) }
    verify { webRtcInteractor.rejectIncomingCall(activePeer.id) }
    verify { callManager.hangup() }
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
    cameraEnabled: Boolean = false
  ): WebRtcServiceState {
    val peer = RemotePeer(RecipientId.from(1L), CALL_ID)
    peer.answering()
    peer.localRinging()

    return WebRtcServiceState(processor)
      .builder()
      .changeCallInfoState()
      .callState(WebRtcViewModel.State.CALL_INCOMING)
      .activePeer(peer)
      .commit()
      .changeCallSetupState(CALL_ID)
      .isRemoteVideoOffer(true)
      .accepted(accepted)
      .commit()
      .changeLocalDeviceState()
      .cameraState(if (cameraEnabled) CameraState(CameraState.Direction.FRONT, 2) else CameraState.UNKNOWN)
      .commit()
      .changeVideoState()
      .router(router)
      .localSink(localSink)
      .commit()
      .build()
  }
}
