/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc

import android.app.Application
import android.media.AudioDeviceInfo
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCompat

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [31])
class WebRtcAudioPicker31Test {

  companion object {
    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private lateinit var audioManagerCompat: AudioManagerCompat
  private lateinit var outputState: ToggleButtonOutputState
  private var lastSelectedDevice: WebRtcAudioDevice? = null
  private var lastUpdatedAudioOutput: WebRtcAudioOutput? = null
  private var pickerHidden: Boolean = false

  private val listener = OnAudioOutputChangedListener { device -> lastSelectedDevice = device }
  private val stateUpdater = object : AudioStateUpdater {
    override fun updateAudioOutputState(audioOutput: WebRtcAudioOutput) {
      lastUpdatedAudioOutput = audioOutput
    }

    override fun hidePicker() {
      pickerHidden = true
    }
  }

  @Before
  fun setUp() {
    audioManagerCompat = AppDependencies.androidCallAudioManager
    outputState = ToggleButtonOutputState()
    lastSelectedDevice = null
    lastUpdatedAudioOutput = null
    pickerHidden = false
  }

  private fun createDevice(type: Int, id: Int): AudioDeviceInfo {
    return mockk<AudioDeviceInfo> {
      every { getType() } returns type
      every { getId() } returns id
    }
  }

  @Test
  fun `cycleToNextDevice cycles from earpiece to speaker when communicationDevice is set`() {
    val earpiece = createDevice(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, 1)
    val speaker = createDevice(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2)

    every { audioManagerCompat.availableCommunicationDevices } returns listOf(earpiece, speaker)
    every { audioManagerCompat.communicationDevice } returns earpiece

    outputState.isEarpieceAvailable = true
    outputState.setCurrentOutput(WebRtcAudioOutput.HANDSET)

    val picker = WebRtcAudioPicker31(listener, outputState, stateUpdater)
    picker.cycleToNextDevice()

    assertThat(lastSelectedDevice?.webRtcAudioOutput).isEqualTo(WebRtcAudioOutput.SPEAKER)
    assertThat(lastSelectedDevice?.deviceId).isEqualTo(2)
  }

  @Test
  fun `cycleToNextDevice cycles from speaker to earpiece when communicationDevice is set`() {
    val earpiece = createDevice(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, 1)
    val speaker = createDevice(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2)

    every { audioManagerCompat.availableCommunicationDevices } returns listOf(earpiece, speaker)
    every { audioManagerCompat.communicationDevice } returns speaker

    outputState.isEarpieceAvailable = true
    outputState.setCurrentOutput(WebRtcAudioOutput.SPEAKER)

    val picker = WebRtcAudioPicker31(listener, outputState, stateUpdater)
    picker.cycleToNextDevice()

    assertThat(lastSelectedDevice?.webRtcAudioOutput).isEqualTo(WebRtcAudioOutput.HANDSET)
    assertThat(lastSelectedDevice?.deviceId).isEqualTo(1)
  }

  @Test
  fun `cycleToNextDevice falls back to type lookup when communicationDevice is null`() {
    val earpiece = createDevice(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, 1)
    val speaker = createDevice(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2)

    every { audioManagerCompat.availableCommunicationDevices } returns listOf(earpiece, speaker)
    every { audioManagerCompat.communicationDevice } returns null

    outputState.isEarpieceAvailable = true
    outputState.setCurrentOutput(WebRtcAudioOutput.HANDSET)

    val picker = WebRtcAudioPicker31(listener, outputState, stateUpdater)
    picker.cycleToNextDevice()

    // peekNext() from HANDSET should be SPEAKER
    assertThat(lastSelectedDevice?.webRtcAudioOutput).isEqualTo(WebRtcAudioOutput.SPEAKER)
    assertThat(lastSelectedDevice?.deviceId).isEqualTo(2)
  }

  @Test
  fun `cycleToNextDevice falls back to first device when target type not found`() {
    val speaker = createDevice(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2)

    every { audioManagerCompat.availableCommunicationDevices } returns listOf(speaker)
    every { audioManagerCompat.communicationDevice } returns null

    // outputState only has SPEAKER, peekNext() cycles to SPEAKER
    // but simulate a mismatch: current is SPEAKER, next is SPEAKER
    // Let's set up a case where the target type doesn't match any device
    outputState.isEarpieceAvailable = true
    outputState.setCurrentOutput(WebRtcAudioOutput.SPEAKER)

    val picker = WebRtcAudioPicker31(listener, outputState, stateUpdater)
    picker.cycleToNextDevice()

    // peekNext() from SPEAKER → HANDSET, but only speaker device exists
    // falls back to first device (speaker)
    assertThat(lastSelectedDevice?.webRtcAudioOutput).isEqualTo(WebRtcAudioOutput.SPEAKER)
    assertThat(lastSelectedDevice?.deviceId).isEqualTo(2)
  }

  @Test
  fun `cycleToNextDevice does nothing when no devices available`() {
    every { audioManagerCompat.availableCommunicationDevices } returns emptyList()

    val picker = WebRtcAudioPicker31(listener, outputState, stateUpdater)
    picker.cycleToNextDevice()

    assertThat(lastSelectedDevice).isNull()
    assertThat(lastUpdatedAudioOutput).isNull()
  }

  @Test
  fun `cycleToNextDevice updates state via onAudioDeviceSelected`() {
    val earpiece = createDevice(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, 1)
    val speaker = createDevice(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2)

    every { audioManagerCompat.availableCommunicationDevices } returns listOf(earpiece, speaker)
    every { audioManagerCompat.communicationDevice } returns earpiece

    outputState.isEarpieceAvailable = true
    outputState.setCurrentOutput(WebRtcAudioOutput.HANDSET)

    val picker = WebRtcAudioPicker31(listener, outputState, stateUpdater)
    picker.cycleToNextDevice()

    assertThat(lastUpdatedAudioOutput).isEqualTo(WebRtcAudioOutput.SPEAKER)
  }

  @Test
  fun `cycleToNextDevice wraps around with three devices`() {
    val earpiece = createDevice(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, 1)
    val speaker = createDevice(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2)
    val bluetooth = createDevice(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 3)

    every { audioManagerCompat.availableCommunicationDevices } returns listOf(earpiece, speaker, bluetooth)
    every { audioManagerCompat.communicationDevice } returns bluetooth

    outputState.isEarpieceAvailable = true
    outputState.isBluetoothHeadsetAvailable = true
    outputState.setCurrentOutput(WebRtcAudioOutput.BLUETOOTH_HEADSET)

    val picker = WebRtcAudioPicker31(listener, outputState, stateUpdater)
    picker.cycleToNextDevice()

    // bluetooth is last, wraps around to earpiece
    assertThat(lastSelectedDevice?.webRtcAudioOutput).isEqualTo(WebRtcAudioOutput.HANDSET)
    assertThat(lastSelectedDevice?.deviceId).isEqualTo(1)
  }
}
