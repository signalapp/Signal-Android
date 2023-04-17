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

  private var defaultAudioDevice: AudioDevice = AudioDevice.EARPIECE
  private var userSelectedAudioDevice: AudioDeviceInfo? = null
  private var savedAudioMode = AudioManager.MODE_INVALID
  private var savedIsSpeakerPhoneOn = false
  private var savedIsMicrophoneMute = false
  private var hasWiredHeadset = false

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

    val userSelectedDeviceType: AudioDevice = userSelectedAudioDevice?.type?.let { AudioDeviceMapping.fromPlatformType(it) } ?: AudioDevice.NONE
    if (clearUserEarpieceSelection && userSelectedDeviceType == AudioDevice.EARPIECE) {
      Log.d(TAG, "Clearing user setting of earpiece")
      userSelectedAudioDevice = null
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

  override fun selectAudioDevice(recipientId: RecipientId?, device: Int, isId: Boolean) {
    if (!isId) {
      throw IllegalArgumentException("Must supply a device address for API 31+.")
    }

    Log.d(TAG, "Selecting $device")

    userSelectedAudioDevice = androidAudioManager.availableCommunicationDevices.find { it.id == device }

    updateAudioDeviceState()
  }

  override fun startIncomingRinger(ringtoneUri: Uri?, vibrate: Boolean) {
    Log.i(TAG, "startIncomingRinger(): uri: ${if (ringtoneUri != null) "present" else "null"} vibrate: $vibrate")
    androidAudioManager.mode = AudioManager.MODE_RINGTONE
    setMicrophoneMute(false)
    setDefaultAudioDevice(recipientId = null, newDefaultDevice = AudioDevice.SPEAKER_PHONE, clearUserEarpieceSelection = false)
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

    val currentAudioDevice: AudioDeviceInfo? = androidAudioManager.communicationDevice

    val availableCommunicationDevices: List<AudioDeviceInfo> = androidAudioManager.availableCommunicationDevices
    if (userSelectedAudioDevice != null) {
      androidAudioManager.communicationDevice = userSelectedAudioDevice
      eventListener?.onAudioDeviceChanged(AudioDeviceMapping.fromPlatformType(userSelectedAudioDevice!!.type), availableCommunicationDevices.map { AudioDeviceMapping.fromPlatformType(it.type) }.toSet())
    } else {
      var candidate: AudioDeviceInfo? = null
      val searchOrder: List<AudioDevice> = listOf(AudioDevice.BLUETOOTH, AudioDevice.WIRED_HEADSET, defaultAudioDevice, AudioDevice.EARPIECE, AudioDevice.SPEAKER_PHONE, AudioDevice.NONE).distinct()
      for (deviceType in searchOrder) {
        candidate = availableCommunicationDevices.find { AudioDeviceMapping.fromPlatformType(it.type) == deviceType }
        if (candidate != null) {
          break
        }
      }

      when (candidate) {
        null -> {
          Log.e(TAG, "Tried to switch audio devices but could not find suitable device in list of types: ${availableCommunicationDevices.map { it.type }.joinToString()}")
          androidAudioManager.clearCommunicationDevice()
        }
        else -> {
          Log.d(TAG, "Switching to new device of type ${candidate.type} from ${currentAudioDevice?.type}")
          androidAudioManager.communicationDevice = candidate
          eventListener?.onAudioDeviceChanged(AudioDeviceMapping.fromPlatformType(candidate.type), availableCommunicationDevices.map { AudioDeviceMapping.fromPlatformType(it.type) }.toSet())
        }
      }
    }
  }
}
