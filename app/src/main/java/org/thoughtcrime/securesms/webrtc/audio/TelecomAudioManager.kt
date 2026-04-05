/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.webrtc.audio

import android.content.Context
import androidx.annotation.RequiresApi
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.AndroidTelecomUtil

/**
 * Lightweight [SignalAudioManager] used when Jetpack Core Telecom is managing the call.
 *
 * Core Telecom owns device routing (earpiece, speaker, bluetooth, wired headset) and audio focus
 * via the platform telecom framework. This manager only handles:
 * - Audio mode transitions (MODE_RINGTONE / MODE_IN_COMMUNICATION)
 * - Ringtone and sound effect playback
 * - Mic mute state
 * - Forwarding user device selection to Core Telecom via [AndroidTelecomUtil]
 *
 * Device availability and active device updates flow from [org.thoughtcrime.securesms.service.webrtc.TelecomCallController] directly
 * to [org.thoughtcrime.securesms.service.webrtc.SignalCallManager.onAudioDeviceChanged], bypassing this class entirely.
 */
@RequiresApi(34)
class TelecomAudioManager(context: Context, eventListener: EventListener?) : SignalAudioManager(context, eventListener) {

  companion object {
    private val TAG = Log.tag(TelecomAudioManager::class)
  }

  override fun initialize() {
    Log.i(TAG, "initialize(): state=$state")
    if (state == State.UNINITIALIZED) {
      savedAudioMode = androidAudioManager.mode
      savedIsMicrophoneMute = androidAudioManager.isMicrophoneMute
      setMicrophoneMute(false)
      state = State.PREINITIALIZED
    }
  }

  override fun start() {
    Log.i(TAG, "start(): state=$state")
    if (state == State.RUNNING) {
      Log.w(TAG, "Skipping, already active")
      return
    }

    incomingRinger.stop()
    outgoingRinger.stop()

    state = State.RUNNING

    Log.i(TAG, "start(): platform audio mode is ${androidAudioManager.mode}, not overriding — letting telecom framework manage")

    val volume: Float = androidAudioManager.ringVolumeWithMinimum()
    soundPool.play(connectedSoundId, volume, volume, 0, 0, 1.0f)
  }

  override fun stop(playDisconnect: Boolean) {
    Log.i(TAG, "stop(): playDisconnect=$playDisconnect state=$state")

    incomingRinger.stop()
    outgoingRinger.stop()

    if (playDisconnect && state != State.UNINITIALIZED) {
      val volume: Float = androidAudioManager.ringVolumeWithMinimum()
      soundPool.play(disconnectedSoundId, volume, volume, 0, 0, 1.0f)
    }

    if (state != State.UNINITIALIZED) {
      setMicrophoneMute(savedIsMicrophoneMute)
    }

    state = State.UNINITIALIZED
  }

  override fun setDefaultAudioDevice(recipientId: RecipientId?, newDefaultDevice: AudioDevice, clearUserEarpieceSelection: Boolean) {
    if (recipientId != null) {
      val currentDevice = AndroidTelecomUtil.getActiveAudioDevice(recipientId)
      if (currentDevice == AudioDevice.BLUETOOTH || currentDevice == AudioDevice.WIRED_HEADSET) {
        Log.i(TAG, "setDefaultAudioDevice(): device=$newDefaultDevice, but current device is $currentDevice — keeping external device")
        return
      }

      if (newDefaultDevice == AudioDevice.EARPIECE) {
        Log.i(TAG, "setDefaultAudioDevice(): device=EARPIECE — no-op, letting telecom framework decide default routing")
        return
      }

      Log.i(TAG, "setDefaultAudioDevice(): device=$newDefaultDevice (delegating to telecom)")
      AndroidTelecomUtil.selectAudioDevice(recipientId, newDefaultDevice)
    }
  }

  override fun selectAudioDevice(recipientId: RecipientId?, device: Int, isId: Boolean) {
    val audioDevice: AudioDevice = if (isId) {
      Log.w(TAG, "selectAudioDevice(): unexpected isId=true for telecom call, ignoring")
      return
    } else {
      AudioDevice.entries[device]
    }

    Log.i(TAG, "selectAudioDevice(): device=$audioDevice (delegating to telecom)")
    if (recipientId != null) {
      AndroidTelecomUtil.selectAudioDevice(recipientId, audioDevice)
    }
  }
}
