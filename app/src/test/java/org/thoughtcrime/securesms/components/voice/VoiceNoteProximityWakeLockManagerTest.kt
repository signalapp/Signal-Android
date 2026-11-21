/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.voice

import android.app.Application
import android.hardware.Sensor
import android.media.AudioDeviceCallback
import android.media.AudioManager
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.FlagSet
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import org.signal.core.util.ServiceUtil
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class VoiceNoteProximityWakeLockManagerTest {
  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private val mediaController = mockk<MediaController>(relaxed = true)
  private val commands = mutableListOf<Bundle>()
  private val playbackListener = slot<Player.Listener>()
  private val deviceCallback = slot<AudioDeviceCallback>()
  private lateinit var sensor: Sensor
  private lateinit var sensorManager: ShadowSensorManager

  @Before
  fun setUp() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    sensor = ShadowSensor.newInstance(Sensor.TYPE_PROXIMITY)
    shadowOf(sensor).setMaximumRange(5f)
    sensorManager = shadowOf(ServiceUtil.getSensorManager(activity))
    sensorManager.addSensor(sensor)

    every { mediaController.playWhenReady } returns true
    every { mediaController.playbackState } returns Player.STATE_READY
    every { mediaController.playbackSuppressionReason } returns Player.PLAYBACK_SUPPRESSION_REASON_NONE
    every { mediaController.addListener(capture(playbackListener)) } returns Unit
    every { mediaController.sendCustomCommand(any(), capture(commands)) } returns mockk()
    every { AppDependencies.androidCallAudioManager.registerAudioDeviceCallback(capture(deviceCallback), any()) } returns Unit

    // Playback is already in progress when the activity attaches to the session.
    VoiceNoteProximityWakeLockManager(activity, mediaController)
  }

  @Test
  fun `first far reading keeps the screen lock armed and subsequent rapid changes switch routes`() {
    sendDistance(5f)
    assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
    sendDistance(0f)
    sendDistance(5f)

    assertEquals(listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_MUSIC), requestedStreams())
  }

  @Test
  @OptIn(UnstableApi::class)
  fun `connecting a headset during private playback clears the route and stops proximity monitoring`() {
    sendDistance(0f)
    every { mediaController.playbackState } returns Player.STATE_BUFFERING
    playbackListener.captured.onEvents(mediaController, Player.Events(FlagSet.Builder().add(Player.EVENT_PLAYBACK_STATE_CHANGED).build()))
    assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)

    every { AppDependencies.androidCallAudioManager.isHeadsetConnected } returns true
    deviceCallback.captured.onAudioDevicesAdded(arrayOf(mockk()))

    assertEquals(listOf(AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_MUSIC), requestedStreams())
    assertTrue(sensorManager.listeners.isEmpty())
    assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
  }

  @Test
  fun `removing a headset while playback continues rearms proximity monitoring`() {
    every { AppDependencies.androidCallAudioManager.isHeadsetConnected } returns true
    deviceCallback.captured.onAudioDevicesAdded(arrayOf(mockk()))
    every { AppDependencies.androidCallAudioManager.isHeadsetConnected } returns false
    deviceCallback.captured.onAudioDevicesRemoved(arrayOf(mockk()))
    sendDistance(0f)

    assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
    assertEquals(listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_VOICE_CALL), requestedStreams())
  }

  private fun sendDistance(distance: Float) {
    val event = SensorEventBuilder.newBuilder().setSensor(sensor).setValues(floatArrayOf(distance)).build()
    sensorManager.sendSensorEventToListeners(event, sensor)
  }

  private fun requestedStreams() = commands.map { it.getInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM) }
}
