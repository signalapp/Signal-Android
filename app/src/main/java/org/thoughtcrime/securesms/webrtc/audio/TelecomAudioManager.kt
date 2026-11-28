/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.webrtc.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
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
@RequiresApi(31)
class TelecomAudioManager(context: Context, eventListener: EventListener?) : SignalAudioManager(context, eventListener) {

  companion object {
    private val TAG = Log.tag(TelecomAudioManager::class)

    @VisibleForTesting
    const val TELECOM_ACCEPT_GATE_TIMEOUT_MS = 2500L
  }

  override val acceptGateTimeoutMs: Long = TELECOM_ACCEPT_GATE_TIMEOUT_MS

  @VisibleForTesting
  internal val modeChangedListener = AudioManager.OnModeChangedListener { mode ->
    Log.i(TAG, "OnModeChangedListener: applied ${getModeName(mode)} state: $state")

    appliedMode = mode

    if (mode == AudioManager.MODE_IN_COMMUNICATION) {
      if (isAudioReadyForAccept()) {
        notifyReadyForAccept("mode applied")
      } else {
        Log.i(TAG, "OnModeChangedListener: mode applied, waiting for the bluetooth communication device")
      }
      Log.i(TAG, "OnModeChangedListener: micMute: ${androidAudioManager.isMicrophoneMute}")
    }
  }

  private var bluetoothCommunicationDevice: AudioDeviceInfo? = null
  private var waitForBluetoothOnAccept = false

  @VisibleForTesting
  internal val communicationDeviceChangedListener = AudioManager.OnCommunicationDeviceChangedListener { device ->
    Log.i(TAG, "OnCommunicationDeviceChangedListener: ${CallAudioLogging.describeDevice(device)} mode: ${getModeName(appliedMode)} state: $state")
    bluetoothCommunicationDevice = device?.takeIf { it.isBluetoothCommunicationDevice() }
    if (waitForBluetoothOnAccept && isAudioReadyForAccept()) {
      notifyReadyForAccept("mode applied and bluetooth communication device set")
    }
  }

  private fun AudioDeviceInfo.isBluetoothCommunicationDevice(): Boolean {
    return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_HEARING_AID
  }

  private fun isAudioReadyForAccept(): Boolean {
    return appliedMode == AudioManager.MODE_IN_COMMUNICATION && (!waitForBluetoothOnAccept || bluetoothCommunicationDevice != null)
  }

  private val deviceCallback = object : AudioDeviceCallback() {
    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
      Log.i(TAG, "onAudioDevicesAdded: ${addedDevices.joinToString { CallAudioLogging.describeDevice(it) }} state: $state")
    }

    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
      Log.i(TAG, "onAudioDevicesRemoved: ${removedDevices.joinToString { CallAudioLogging.describeDevice(it) }} state: $state")
    }
  }

  private val audioRecordingCallback = CallAudioRecordingCallback(
    tag = TAG,
    describeState = { "state: $state mode: ${getModeName(appliedMode)}" },
    onSilencedChanged = { eventListener?.onMicrophoneSilencedChanged(it) }
  )

  override fun initialize() {
    Log.i(TAG, "initialize(): state=$state")
    if (state == State.UNINITIALIZED) {
      appliedMode = androidAudioManager.mode
      bluetoothCommunicationDevice = androidAudioManager.communicationDevice?.takeIf { it.isBluetoothCommunicationDevice() }
      savedIsMicrophoneMute = androidAudioManager.isMicrophoneMute
      Log.i(TAG, "initialize(): saved system mic mute=$savedIsMicrophoneMute, unmuting for the call")
      setMicrophoneMute(false)
      androidAudioManager.registerAudioRecordingCallback(audioRecordingCallback, handler)
      androidAudioManager.registerAudioDeviceCallback(deviceCallback, handler)
      val api31AudioManager = androidAudioManager as AudioManagerCompat.Api31AudioManagerCompat
      api31AudioManager.addOnModeChangedListener(handler::post, modeChangedListener)
      api31AudioManager.addOnCommunicationDeviceChangedListener(handler::post, communicationDeviceChangedListener)
      onAudioListenersRegistered()
      state = State.PREINITIALIZED
    }
  }

  override fun onSetMicrophoneMute(muted: Boolean) {
    if (state == State.UNINITIALIZED) {
      Log.w(TAG, "onSetMicrophoneMute(): muted=$muted ignored, not initialized")
      return
    }

    Log.i(TAG, "onSetMicrophoneMute(): muted=$muted system=${androidAudioManager.isMicrophoneMute}")
    setMicrophoneMute(muted)
  }

  override fun onPrepareForAccept(waitForBluetooth: Boolean) {
    Log.i(TAG, "onPrepareForAccept(): state=$state appliedMode=${getModeName(appliedMode)} waitForBluetooth=$waitForBluetooth bluetoothDevice=${bluetoothCommunicationDevice != null}, waiting for telecom")
    incomingRinger.stop()
    waitForBluetoothOnAccept = waitForBluetooth
    if (isAudioReadyForAccept()) {
      notifyReadyForAccept("audio already ready")
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

    Log.i(TAG, "start(): applied mode is ${getModeName(appliedMode)}, telecom manages the mode")
    CallAudioLogging.logActiveRecordingConfigurations(TAG, "start", androidAudioManager.activeRecordingConfigurations, getModeName(appliedMode))

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
      releaseAudioListeners()
      Log.i(TAG, "stop(): restoring system mic mute=$savedIsMicrophoneMute")
      super.setMicrophoneMute(savedIsMicrophoneMute)
    }

    state = State.UNINITIALIZED
    resetAcceptState()
    appliedMode = AudioManager.MODE_INVALID
    bluetoothCommunicationDevice = null
    waitForBluetoothOnAccept = false
    AndroidTelecomUtil.clearAppliedSystemMute(this)
  }

  override fun unregisterAudioListeners() {
    androidAudioManager.unregisterAudioRecordingCallback(audioRecordingCallback)
    androidAudioManager.unregisterAudioDeviceCallback(deviceCallback)
    val api31AudioManager = androidAudioManager as AudioManagerCompat.Api31AudioManagerCompat
    api31AudioManager.removeOnModeChangedListener(modeChangedListener)
    api31AudioManager.removeOnCommunicationDeviceChangedListener(communicationDeviceChangedListener)
  }

  override fun startIncomingRinger(ringtoneUri: Uri?, vibrate: Boolean) {
    Log.i(TAG, "startIncomingRinger(): uri: ${if (ringtoneUri != null) "present" else "null"} vibrate: $vibrate appliedMode: ${getModeName(appliedMode)}")
    setMicrophoneMute(false)
    incomingRinger.start(ringtoneUri, vibrate)
  }

  override fun startOutgoingRinger() {
    Log.i(TAG, "startOutgoingRinger(): appliedMode: ${getModeName(appliedMode)}")
    setMicrophoneMute(false)
    outgoingRinger.start(OutgoingRinger.Type.RINGING)
  }

  override fun setMicrophoneMute(on: Boolean) {
    super.setMicrophoneMute(on)
    AndroidTelecomUtil.setAppliedSystemMute(this, on)
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
      AndroidTelecomUtil.selectDefaultAudioDevice(recipientId, newDefaultDevice)
    }
  }

  override fun selectAudioDevice(recipientId: RecipientId?, device: Int, isId: Boolean) {
    if (recipientId == null) {
      Log.w(TAG, "selectAudioDevice(): device=$device isId=$isId ignored, no recipient")
      return
    }

    if (isId) {
      Log.i(TAG, "selectAudioDevice(): deviceId=$device (delegating to telecom)")
      AndroidTelecomUtil.selectAudioDevice(recipientId, device)
    } else {
      val audioDevice = AudioDevice.entries[device]
      Log.i(TAG, "selectAudioDevice(): device=$audioDevice (delegating to telecom)")
      AndroidTelecomUtil.selectAudioDevice(recipientId, audioDevice)
    }
  }
}
