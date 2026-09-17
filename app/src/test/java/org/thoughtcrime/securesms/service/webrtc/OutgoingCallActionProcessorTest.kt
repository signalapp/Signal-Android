/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import android.app.Application
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

  private fun outgoingDialingCall(videoCall: Boolean = true, cameraEnabled: Boolean = videoCall): WebRtcServiceState {
    val peer = RemotePeer(RecipientId.from(1L), CALL_ID)
    peer.dialing()

    return WebRtcServiceState(processor)
      .builder()
      .changeCallInfoState()
      .callState(WebRtcViewModel.State.CALL_OUTGOING)
      .activePeer(peer)
      .commit()
      .changeCallSetupState(CALL_ID)
      .enableVideoOnCreate(videoCall)
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
