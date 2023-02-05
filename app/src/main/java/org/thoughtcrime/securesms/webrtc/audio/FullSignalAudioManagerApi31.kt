package org.thoughtcrime.securesms.webrtc.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import androidx.annotation.RequiresApi
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * API 31 introduces new audio manager methods to handle audio routing, including to Bluetooth devices.
 * This is important because API 31 also introduces new, more restrictive bluetooth permissioning,
 * and the previous SignalAudioManager implementation would have required us to ask for (poorly labeled & scary) Bluetooth permissions.
 */
@RequiresApi(31)
class FullSignalAudioManagerApi31(context: Context, eventListener: EventListener?) : SignalAudioManager(context, eventListener) {
  private val TAG = "SignalAudioManager31"

  private var currentAudioDevice: AudioDevice = AudioDevice.NONE
  private var defaultAudioDevice: AudioDevice = AudioDevice.EARPIECE
  private var userSelectedAudioDevice: AudioDevice = AudioDevice.NONE
  private var savedAudioMode = AudioManager.MODE_INVALID
  private var savedIsSpeakerPhoneOn = false
  private var savedIsMicrophoneMute = false
  private var hasWiredHeadset = false
  private var hasBluetoothHeadset = false
  private var autoSwitchToWiredHeadset = true
  private var autoSwitchToBluetooth = true

  private val deviceCallback = object : AudioDeviceCallback() {

    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
      super.onAudioDevicesAdded(addedDevices)
      updateAudioDeviceState()
    }

    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
      super.onAudioDevicesRemoved(removedDevices)
      updateAudioDeviceState()
    }
  }

  override fun setDefaultAudioDevice(recipientId: RecipientId?, newDefaultDevice: AudioDevice, clearUserEarpieceSelection: Boolean) {
    Log.d(TAG, "setDefaultAudioDevice(): currentDefault: $defaultAudioDevice device: $newDefaultDevice clearUser: $clearUserEarpieceSelection")
    defaultAudioDevice = when (newDefaultDevice) {
      AudioDevice.SPEAKER_PHONE -> newDefaultDevice
      AudioDevice.EARPIECE -> {
        if (androidAudioManager.hasEarpiece(context)) {
          newDefaultDevice
        } else {
          AudioDevice.SPEAKER_PHONE
        }
      }
      else -> throw AssertionError("Invalid default audio device selection")
    }

    if (clearUserEarpieceSelection && userSelectedAudioDevice == AudioDevice.EARPIECE) {
      Log.d(TAG, "Clearing user setting of earpiece")
      userSelectedAudioDevice = AudioDevice.NONE
    }

    Log.d(TAG, "New default: $defaultAudioDevice userSelected: $userSelectedAudioDevice")
    updateAudioDeviceState()
  }

  override fun initialize() {
    if (state == State.UNINITIALIZED) {
      savedAudioMode = androidAudioManager.mode
      savedIsSpeakerPhoneOn = androidAudioManager.isSpeakerphoneOn
      savedIsMicrophoneMute = androidAudioManager.isMicrophoneMute
      hasWiredHeadset = androidAudioManager.isWiredHeadsetOn

      val focusedGained = androidAudioManager.requestCallAudioFocus()
      if (!focusedGained) {
        handler.postDelayed({ androidAudioManager.requestCallAudioFocus() }, 500)
      }

      setMicrophoneMute(false)

      updateAudioDeviceState()
      androidAudioManager.registerAudioDeviceCallback(deviceCallback, handler)
      state = State.PREINITIALIZED

      Log.d(TAG, "Initialized")
    }
  }

  override fun start() {
    incomingRinger.stop()
    outgoingRinger.stop()

    val focusedGained = androidAudioManager.requestCallAudioFocus()
    if (!focusedGained) {
      handler.postDelayed({ androidAudioManager.requestCallAudioFocus() }, 500)
    }

    state = State.RUNNING
    androidAudioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    val volume: Float = androidAudioManager.ringVolumeWithMinimum()
    soundPool.play(connectedSoundId, volume, volume, 0, 0, 1.0f)

    Log.d(TAG, "Started")
  }

  override fun stop(playDisconnect: Boolean) {
    incomingRinger.stop()
    outgoingRinger.stop()

    if (playDisconnect && state != State.UNINITIALIZED) {
      val volume: Float = androidAudioManager.ringVolumeWithMinimum()
      soundPool.play(disconnectedSoundId, volume, volume, 0, 0, 1.0f)
    }
    state = State.UNINITIALIZED
    androidAudioManager.unregisterAudioDeviceCallback(deviceCallback)
    androidAudioManager.clearCommunicationDevice()
    setSpeakerphoneOn(savedIsSpeakerPhoneOn)
    setMicrophoneMute(savedIsMicrophoneMute)
    androidAudioManager.mode = savedAudioMode
    androidAudioManager.abandonCallAudioFocus()
    Log.d(TAG, "Abandoned audio focus for VOICE_CALL streams")

    Log.d(TAG, "Stopped")
  }

  override fun selectAudioDevice(recipientId: RecipientId?, device: AudioDevice) {
    val devices: List<AudioDeviceInfo> = androidAudioManager.availableCommunicationDevices

    val availableDevices: List<AudioDevice> = devices.map { AudioDeviceMapping.fromPlatformType(it.type) }
    val actualDevice = if (device == AudioDevice.EARPIECE && availableDevices.contains(AudioDevice.WIRED_HEADSET)) AudioDevice.WIRED_HEADSET else device
    Log.d(TAG, "selectAudioDevice(): device: $device actualDevice: $actualDevice")
    if (!availableDevices.contains(actualDevice)) {
      Log.w(TAG, "Can not select $actualDevice from available $availableDevices")
    }
    userSelectedAudioDevice = actualDevice
    updateAudioDeviceState()
  }

  override fun startIncomingRinger(ringtoneUri: Uri?, vibrate: Boolean) {
    Log.i(TAG, "startIncomingRinger(): uri: ${if (ringtoneUri != null) "present" else "null"} vibrate: $vibrate")
    androidAudioManager.mode = AudioManager.MODE_RINGTONE
    setMicrophoneMute(false)
    setDefaultAudioDevice(null, AudioDevice.SPEAKER_PHONE, false)
    incomingRinger.start(ringtoneUri, vibrate)
  }

  override fun startOutgoingRinger() {
    Log.i(TAG, "startOutgoingRinger(): currentDevice: $selectedAudioDevice")
    androidAudioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    setMicrophoneMute(false)
    outgoingRinger.start(OutgoingRinger.Type.RINGING)
  }

  private fun setSpeakerphoneOn(on: Boolean) {
    if (androidAudioManager.isSpeakerphoneOn != on) {
      androidAudioManager.isSpeakerphoneOn = on
    }
  }

  private fun setMicrophoneMute(on: Boolean) {
    if (androidAudioManager.isMicrophoneMute != on) {
      androidAudioManager.isMicrophoneMute = on
    }
  }

  private fun updateAudioDeviceState() {
    handler.assertHandlerThread()

    val communicationDevice: AudioDeviceInfo? = androidAudioManager.communicationDevice
    currentAudioDevice = if (communicationDevice == null) {
      AudioDevice.NONE
    } else {
      AudioDeviceMapping.fromPlatformType(communicationDevice.type)
    }
    val availableCommunicationDevices: List<AudioDeviceInfo> = androidAudioManager.availableCommunicationDevices
    availableCommunicationDevices.forEach { Log.d(TAG, "Detected communication device of type: ${it.type}") }
    hasBluetoothHeadset = availableCommunicationDevices.any { AudioDeviceMapping.fromPlatformType(it.type) == AudioDevice.BLUETOOTH }
    hasWiredHeadset = availableCommunicationDevices.any { AudioDeviceMapping.fromPlatformType(it.type) == AudioDevice.WIRED_HEADSET }
    Log.i(
      TAG,
      "updateAudioDeviceState(): " +
        "wired: $hasWiredHeadset " +
        "bt: $hasBluetoothHeadset " +
        "available: $availableCommunicationDevices " +
        "selected: $selectedAudioDevice " +
        "userSelected: $userSelectedAudioDevice"
    )
    val audioDevices: MutableSet<AudioDevice> = mutableSetOf(AudioDevice.SPEAKER_PHONE)

    if (hasBluetoothHeadset) {
      audioDevices += AudioDevice.BLUETOOTH
    }

    if (hasWiredHeadset) {
      audioDevices += AudioDevice.WIRED_HEADSET
    } else {
      autoSwitchToWiredHeadset = true
      if (androidAudioManager.hasEarpiece(context)) {
        audioDevices += AudioDevice.EARPIECE
      }
    }

    if (!hasBluetoothHeadset && userSelectedAudioDevice == AudioDevice.BLUETOOTH) {
      userSelectedAudioDevice = AudioDevice.NONE
    }

    if (hasWiredHeadset && autoSwitchToWiredHeadset) {
      userSelectedAudioDevice = AudioDevice.WIRED_HEADSET
      autoSwitchToWiredHeadset = false
    }

    if (!hasWiredHeadset && userSelectedAudioDevice == AudioDevice.WIRED_HEADSET) {
      userSelectedAudioDevice = AudioDevice.NONE
    }

    if (!autoSwitchToBluetooth && !hasBluetoothHeadset) {
      autoSwitchToBluetooth = true
    }

    if (autoSwitchToBluetooth && hasBluetoothHeadset) {
      userSelectedAudioDevice = AudioDevice.BLUETOOTH
      autoSwitchToBluetooth = false
    }

    val deviceToSet: AudioDevice = when {
      audioDevices.contains(userSelectedAudioDevice) -> userSelectedAudioDevice
      audioDevices.contains(defaultAudioDevice) -> defaultAudioDevice
      else -> AudioDevice.SPEAKER_PHONE
    }

    if (deviceToSet != currentAudioDevice)
      try {
        val chosenDevice: AudioDeviceInfo = availableCommunicationDevices.first { AudioDeviceMapping.getEquivalentPlatformTypes(deviceToSet).contains(it.type) }
        val result = androidAudioManager.setCommunicationDevice(chosenDevice)
        if (result) {
          Log.i(TAG, "Set active device to ID ${chosenDevice.id}, type ${chosenDevice.type}")
          currentAudioDevice = deviceToSet
          eventListener?.onAudioDeviceChanged(currentAudioDevice, availableCommunicationDevices.map { AudioDeviceMapping.fromPlatformType(it.type) }.toSet())
        } else {
          Log.w(TAG, "Setting device $chosenDevice failed.")
        }
      } catch (e: NoSuchElementException) {
        androidAudioManager.clearCommunicationDevice()
      }
  }
}
