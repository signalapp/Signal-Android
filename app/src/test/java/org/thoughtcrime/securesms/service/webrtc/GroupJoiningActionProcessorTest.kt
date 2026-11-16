/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import android.app.Application
import io.mockk.Ordering
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallManager
import org.signal.ringrtc.GroupCall
import org.signal.ringrtc.NetworkRoute
import org.thoughtcrime.securesms.ringrtc.CameraState
import org.thoughtcrime.securesms.ringrtc.RemotePeer
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.thoughtcrime.securesms.util.NetworkUtil
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import org.thoughtcrime.securesms.webrtc.locks.LockManager
import org.webrtc.PeerConnection

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class GroupJoiningActionProcessorTest {

  companion object {
    private val GROUP_RECIPIENT_ID = RecipientId.from(1L)

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  private val webRtcInteractor: WebRtcInteractor = mockk(relaxed = true)
  private val groupCall: GroupCall = mockk(relaxed = true)
  private val localDeviceState: GroupCall.LocalDeviceState = mockk(relaxed = true)
  private val connectedProcessor: GroupConnectedActionProcessor = mockk(relaxed = true)

  private lateinit var processor: GroupJoiningActionProcessor

  @Before
  fun setUp() {
    every { groupCall.localDeviceState } returns localDeviceState
    every { localDeviceState.connectionState } returns GroupCall.ConnectionState.CONNECTED
    every { localDeviceState.joinState } returns GroupCall.JoinState.JOINED
    every { localDeviceState.networkRoute } returns NetworkRoute(PeerConnection.AdapterType.UNKNOWN)

    mockkStatic(NetworkUtil::class)
    every { NetworkUtil.getCallingDataMode(any(), any()) } returns CallManager.DataMode.NORMAL

    mockkStatic(WebRtcUtil::class)
    every { WebRtcUtil.getInCallPhoneState(any(), any(), any()) } returns LockManager.PhoneState.IN_CALL

    mockkObject(MultiPeerActionProcessorFactory.GroupActionProcessorFactory)
    every { MultiPeerActionProcessorFactory.GroupActionProcessorFactory.createConnectedActionProcessor(any()) } returns connectedProcessor

    processor = GroupJoiningActionProcessor(MultiPeerActionProcessorFactory.GroupActionProcessorFactory, webRtcInteractor)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `Given local video is on, when the group call is joined, then I expect audio to default to the speaker after audio starts`() {
    processor.handleGroupLocalDeviceStateChanged(joiningGroupCall(cameraEnabled = true, activeDevice = SignalAudioManager.AudioDevice.EARPIECE))

    verify(ordering = Ordering.ORDERED) {
      webRtcInteractor.startAudioCommunication()
      webRtcInteractor.setDefaultAudioDevice(GROUP_RECIPIENT_ID, SignalAudioManager.AudioDevice.SPEAKER_PHONE, false)
    }
  }

  @Test
  fun `Given local video is on and the speaker was already requested during the ring, when the group call is joined, then I expect the speaker default to be reapplied`() {
    processor.handleGroupLocalDeviceStateChanged(joiningGroupCall(cameraEnabled = true, activeDevice = SignalAudioManager.AudioDevice.SPEAKER_PHONE))

    verify { webRtcInteractor.setDefaultAudioDevice(GROUP_RECIPIENT_ID, SignalAudioManager.AudioDevice.SPEAKER_PHONE, false) }
  }

  @Test
  fun `Given local video is off, when the group call is joined, then I expect the audio default to be left alone`() {
    processor.handleGroupLocalDeviceStateChanged(joiningGroupCall(cameraEnabled = false, activeDevice = SignalAudioManager.AudioDevice.EARPIECE))

    verify(exactly = 0) { webRtcInteractor.setDefaultAudioDevice(any(), any(), any()) }
  }

  private fun joiningGroupCall(cameraEnabled: Boolean, activeDevice: SignalAudioManager.AudioDevice): WebRtcServiceState {
    return WebRtcServiceState(processor)
      .builder()
      .changeCallInfoState()
      .activePeer(RemotePeer(GROUP_RECIPIENT_ID, RemotePeer.GROUP_CALL_ID))
      .groupCall(groupCall)
      .commit()
      .changeLocalDeviceState()
      .cameraState(if (cameraEnabled) CameraState(CameraState.Direction.FRONT, 2) else CameraState.UNKNOWN)
      .setActiveDevice(activeDevice)
      .commit()
      .build()
  }
}
