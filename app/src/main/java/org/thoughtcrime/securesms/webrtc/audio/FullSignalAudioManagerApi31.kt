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
  private val TAG = Log.tag(FullSignalAudioManagerApi31::class.java)

  private var defaultDevice = AudioDevice.EARPIECE
  private val deviceCallback = object : AudioDeviceCallback() {

    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
      super.onAudioDevicesAdded(addedDevices)
      if (state == State.RUNNING) {
        // Switch to any new audio devices immediately.
        Log.i(TAG, "onAudioDevicesAdded $addedDevices")
        val firstNewlyAddedDevice = addedDevices.firstNotNullOf { AudioDeviceMapping.fromPlatformType(it.type) }
        selectAudioDevice(null, firstNewlyAddedDevice)
      }
    }

    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
      super.onAudioDevicesRemoved(removedDevices)
      if (state == State.RUNNING) {
        audioDeviceChangedCallback()
      }
    }
  }

  override fun setDefaultAudioDevice(recipientId: RecipientId?, newDefaultDevice: AudioDevice, clearUserEarpieceSelection: Boolean) {
    defaultDevice = newDefaultDevice
  }

  override fun initialize() {
    val focusedGained = androidAudioManager.requestCallAudioFocus()
    if (!focusedGained) {
      handler.postDelayed({ androidAudioManager.requestCallAudioFocus() }, 500)
    }
    androidAudioManager.registerAudioDeviceCallback(deviceCallback, handler)
    state = State.PREINITIALIZED
  }

  override fun start() {
    incomingRinger.stop()
    outgoingRinger.stop()

    val focusedGained = androidAudioManager.requestCallAudioFocus()
    if (!focusedGained) {
      handler.postDelayed({ androidAudioManager.requestCallAudioFocus() }, 500)
    }

    if (androidAudioManager.availableCommunicationDevices.any { AudioDeviceMapping.getEquivalentPlatformTypes(AudioDevice.BLUETOOTH).contains(it.type) }) {
      selectAudioDevice(null, AudioDevice.BLUETOOTH)
    } else if (androidAudioManager.availableCommunicationDevices.any { AudioDeviceMapping.getEquivalentPlatformTypes(AudioDevice.WIRED_HEADSET).contains(it.type) }) {
      selectAudioDevice(null, AudioDevice.WIRED_HEADSET)
    }

    state = State.RUNNING
    androidAudioManager.mode = AudioManager.MODE_IN_COMMUNICATION
  }

  override fun stop(playDisconnect: Boolean) {
    incomingRinger.stop()
    outgoingRinger.stop()

    if (playDisconnect && state != State.UNINITIALIZED) {
      val volume: Float = androidAudioManager.ringVolumeWithMinimum()
      soundPool.play(disconnectedSoundId, volume, volume, 0, 0, 1.0f)
    }
    androidAudioManager.unregisterAudioDeviceCallback(deviceCallback)
    androidAudioManager.clearCommunicationDevice()
    state = State.UNINITIALIZED

    androidAudioManager.abandonCallAudioFocus()
  }

  override fun selectAudioDevice(recipientId: RecipientId?, device: AudioDevice) {
    val devices: List<AudioDeviceInfo> = androidAudioManager.availableCommunicationDevices

    try {
      val chosenDevice: AudioDeviceInfo = devices.first { AudioDeviceMapping.getEquivalentPlatformTypes(device).contains(it.type) }
      val result = androidAudioManager.setCommunicationDevice(chosenDevice)
      if (result) {
        Log.i(TAG, "Set active device to ID ${chosenDevice.id}, type ${chosenDevice.type}")
        eventListener?.onAudioDeviceChanged(activeDevice = device, devices = devices.map { AudioDeviceMapping.fromPlatformType(it.type) }.toSet())
      } else {
        Log.w(TAG, "Setting device $chosenDevice failed.")
      }
    } catch (e: NoSuchElementException) {
      androidAudioManager.clearCommunicationDevice()
    }
  }

  private fun audioDeviceChangedCallback() {
    val activeDevice: AudioDevice = AudioDeviceMapping.fromPlatformType(androidAudioManager.communicationDevice.type)
    val devices: Set<AudioDevice> = androidAudioManager.availableCommunicationDevices.map { AudioDeviceMapping.fromPlatformType(it.type) }.toSet()
    eventListener?.onAudioDeviceChanged(activeDevice, devices)
  }

  override fun startIncomingRinger(ringtoneUri: Uri?, vibrate: Boolean) {
    Log.i(TAG, "startIncomingRinger(): uri: ${if (ringtoneUri != null) "present" else "null"} vibrate: $vibrate")
    androidAudioManager.mode = AudioManager.MODE_RINGTONE
    if (androidAudioManager.isMicrophoneMute) {
      androidAudioManager.isMicrophoneMute = false
    }

    incomingRinger.start(ringtoneUri, vibrate)
  }

  override fun startOutgoingRinger() {
    androidAudioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    if (androidAudioManager.isMicrophoneMute) {
      androidAudioManager.isMicrophoneMute = false
    }
    outgoingRinger.start(OutgoingRinger.Type.RINGING)
  }
}
