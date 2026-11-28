/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.webrtc.audio

import android.app.Application
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.SoundPool
import assertk.assertThat
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [34])
class TelecomAudioManagerTest {

  companion object {
    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private lateinit var eventListener: SignalAudioManager.EventListener

  @Before
  fun setUp() {
    eventListener = mockk(relaxed = true)

    val soundPool: SoundPool = mockk(relaxed = true)
    every { AppDependencies.androidCallAudioManager.createSoundPool() } returns soundPool
    every { soundPool.load(any<Context>(), any(), any()) } returns 1
  }

  @Test
  fun `Given telecom has not applied the mode, when I prepare for accept, then I expect the accept to wait for the mode`() {
    val manager = TelecomAudioManager(AppDependencies.application, eventListener)

    try {
      manager.setStateForTesting(SignalAudioManager.State.PREINITIALIZED, AudioManager.MODE_RINGTONE)

      manager.handleCommand(AudioManagerCommand.PrepareForAccept())
      idleAudioHandler(manager, TelecomAudioManager.TELECOM_ACCEPT_GATE_TIMEOUT_MS / 2)
      verify(exactly = 0) { eventListener.onAudioReadyForAccept() }

      manager.modeChangedListener.onModeChanged(AudioManager.MODE_IN_COMMUNICATION)
      verify(exactly = 1) { eventListener.onAudioReadyForAccept() }

      idleAudioHandler(manager, TelecomAudioManager.TELECOM_ACCEPT_GATE_TIMEOUT_MS)
      verify(exactly = 1) { eventListener.onAudioReadyForAccept() }
    } finally {
      shutdownManager(manager)
    }
  }

  @Test
  fun `Given an accept is waiting, when telecom applies the mode, then I expect the accept to proceed before audio state is read`() {
    val manager = TelecomAudioManager(AppDependencies.application, eventListener)

    try {
      manager.setStateForTesting(SignalAudioManager.State.PREINITIALIZED, AudioManager.MODE_RINGTONE)

      manager.handleCommand(AudioManagerCommand.PrepareForAccept())
      idleAudioHandler(manager, 0)

      manager.modeChangedListener.onModeChanged(AudioManager.MODE_IN_COMMUNICATION)

      verifyOrder {
        eventListener.onAudioReadyForAccept()
        AppDependencies.androidCallAudioManager.isMicrophoneMute
      }
      verify(exactly = 0) { AppDependencies.androidCallAudioManager.communicationDevice }
    } finally {
      shutdownManager(manager)
    }
  }

  @Test
  fun `Given telecom already applied the mode, when I prepare for accept, then I expect the accept to proceed`() {
    val manager = TelecomAudioManager(AppDependencies.application, eventListener)

    try {
      manager.setStateForTesting(SignalAudioManager.State.PREINITIALIZED, AudioManager.MODE_IN_COMMUNICATION)

      manager.handleCommand(AudioManagerCommand.PrepareForAccept())
      idleAudioHandler(manager, 0)

      verify(exactly = 1) { eventListener.onAudioReadyForAccept() }
    } finally {
      shutdownManager(manager)
    }
  }

  @Test
  fun `Given the call is on bluetooth, when telecom applies the mode before the bluetooth device, then I expect the accept to wait for the bluetooth device`() {
    val manager = TelecomAudioManager(AppDependencies.application, eventListener)

    try {
      manager.setStateForTesting(SignalAudioManager.State.PREINITIALIZED, AudioManager.MODE_RINGTONE)

      manager.handleCommand(AudioManagerCommand.PrepareForAccept(waitForBluetooth = true))
      idleAudioHandler(manager, 0)

      manager.modeChangedListener.onModeChanged(AudioManager.MODE_IN_COMMUNICATION)
      verify(exactly = 0) { eventListener.onAudioReadyForAccept() }

      manager.communicationDeviceChangedListener.onCommunicationDeviceChanged(audioDevice(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
      verify(exactly = 0) { eventListener.onAudioReadyForAccept() }

      manager.communicationDeviceChangedListener.onCommunicationDeviceChanged(audioDevice(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
      verify(exactly = 1) { eventListener.onAudioReadyForAccept() }
    } finally {
      shutdownManager(manager)
    }
  }

  @Test
  fun `Given the call is on bluetooth, when the bluetooth device is set before telecom applies the mode, then I expect the accept to proceed on the mode`() {
    val manager = TelecomAudioManager(AppDependencies.application, eventListener)

    try {
      manager.setStateForTesting(SignalAudioManager.State.PREINITIALIZED, AudioManager.MODE_RINGTONE)

      manager.handleCommand(AudioManagerCommand.PrepareForAccept(waitForBluetooth = true))
      idleAudioHandler(manager, 0)

      manager.communicationDeviceChangedListener.onCommunicationDeviceChanged(audioDevice(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
      verify(exactly = 0) { eventListener.onAudioReadyForAccept() }

      manager.modeChangedListener.onModeChanged(AudioManager.MODE_IN_COMMUNICATION)
      verify(exactly = 1) { eventListener.onAudioReadyForAccept() }
    } finally {
      shutdownManager(manager)
    }
  }

  @Test
  fun `Given the call is on bluetooth, when the bluetooth device never arrives, then I expect the backstop to proceed`() {
    val manager = TelecomAudioManager(AppDependencies.application, eventListener)

    try {
      manager.setStateForTesting(SignalAudioManager.State.PREINITIALIZED, AudioManager.MODE_RINGTONE)

      manager.handleCommand(AudioManagerCommand.PrepareForAccept(waitForBluetooth = true))
      idleAudioHandler(manager, 0)
      manager.modeChangedListener.onModeChanged(AudioManager.MODE_IN_COMMUNICATION)
      verify(exactly = 0) { eventListener.onAudioReadyForAccept() }

      idleAudioHandler(manager, TelecomAudioManager.TELECOM_ACCEPT_GATE_TIMEOUT_MS)
      verify(exactly = 1) { eventListener.onAudioReadyForAccept() }
    } finally {
      shutdownManager(manager)
    }
  }

  @Test
  fun `Given the mode change is never reported, when the backstop expires, then I expect the accept to proceed`() {
    val manager = TelecomAudioManager(AppDependencies.application, eventListener)

    try {
      manager.setStateForTesting(SignalAudioManager.State.PREINITIALIZED, AudioManager.MODE_RINGTONE)

      manager.handleCommand(AudioManagerCommand.PrepareForAccept())
      idleAudioHandler(manager, TelecomAudioManager.TELECOM_ACCEPT_GATE_TIMEOUT_MS / 2)
      verify(exactly = 0) { eventListener.onAudioReadyForAccept() }

      idleAudioHandler(manager, TelecomAudioManager.TELECOM_ACCEPT_GATE_TIMEOUT_MS)
      verify(exactly = 1) { eventListener.onAudioReadyForAccept() }
    } finally {
      shutdownManager(manager)
    }
  }

  @Test
  fun `Given a telecom call, when it rings and stops, then I expect the audio mode to be left to telecom`() {
    val manager = TelecomAudioManager(AppDependencies.application, eventListener)

    try {
      manager.setStateForTesting(SignalAudioManager.State.PREINITIALIZED, AudioManager.MODE_RINGTONE)

      manager.handleCommand(AudioManagerCommand.StartIncomingRinger(null, false))
      manager.handleCommand(AudioManagerCommand.Stop(false))
      idleAudioHandler(manager, 0)

      verify(exactly = 0) { AppDependencies.androidCallAudioManager.setMode(any()) }
    } finally {
      shutdownManager(manager)
    }
  }

  private fun audioDevice(type: Int): AudioDeviceInfo {
    return mockk(relaxed = true) {
      every { this@mockk.type } returns type
    }
  }

  private fun idleAudioHandler(manager: TelecomAudioManager, millis: Long) {
    shadowOf(manager.looperForTesting).idleFor(Duration.ofMillis(millis))
  }

  private fun shutdownManager(manager: TelecomAudioManager) {
    manager.setStateForTesting(SignalAudioManager.State.UNINITIALIZED, AudioManager.MODE_INVALID)
    val stopped = CountDownLatch(1)
    manager.shutdown { stopped.countDown() }
    assertThat(stopped.await(5, TimeUnit.SECONDS)).isTrue()
  }
}
