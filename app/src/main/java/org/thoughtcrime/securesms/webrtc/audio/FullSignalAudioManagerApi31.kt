package org.thoughtcrime.securesms.webrtc.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
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

  private val communicationDeviceChangedListener = AudioManager.OnCommunicationDeviceChangedListener { device ->
    if (device != null) {
      Log.i(TAG, "OnCommunicationDeviceChangedListener: id: ${device.id} type: ${getDeviceTypeName(device.type)}")
    } else {
      Log.w(TAG, "OnCommunicationDeviceChangedListener: null")
    }
  }

  private val modeChangedListener = AudioManager.OnModeChangedListener { mode ->
    Log.i(TAG, "OnModeChangedListener: ${getModeName(mode)}")
    if (state == State.RUNNING && mode != AudioManager.MODE_IN_COMMUNICATION) {
      Log.w(TAG, "OnModeChangedListener: Not MODE_IN_COMMUNICATION during a call. state: $state")
    }
  }

  private val audioRecordingCallback = object : AudioManager.AudioRecordingCallback() {
    override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
      if (configs.isEmpty()) {
        Log.i(TAG, "AudioRecordingCallback: no active recordings")
      } else {
        for (config in configs) {
          val deviceName = config.audioDevice?.let { getDeviceTypeName(it.type) } ?: "null"
          Log.i(TAG, "AudioRecordingCallback: silenced: ${config.isClientSilenced} source: ${getAudioSourceName(config.audioSource)} device: $deviceName")
        }
      }
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

    Log.d(TAG, "New default: $defaultAudioDevice userSelected: ${userSelectedAudioDevice?.id} of type ${userSelectedAudioDevice?.type}")
    updateAudioDeviceState()
  }

  override fun initialize() {
    if (state == State.UNINITIALIZED) {
      savedAudioMode = androidAudioManager.mode
      savedIsSpeakerPhoneOn = androidAudioManager.isSpeakerphoneOn
      savedIsMicrophoneMute = androidAudioManager.isMicrophoneMute
      hasWiredHeadset = androidAudioManager.isWiredHeadsetOn

      Log.i(TAG, "initialize: savedMode: ${getModeName(savedAudioMode)} savedSpeaker: $savedIsSpeakerPhoneOn savedMicMute: $savedIsMicrophoneMute wiredHeadset: $hasWiredHeadset")

      val focusGained = androidAudioManager.requestCallAudioFocus()
      if (!focusGained) {
        Log.w(TAG, "initialize: audio focus request failed, scheduling retry")
        handler.postDelayed({
          val retryGained = androidAudioManager.requestCallAudioFocus()
          Log.i(TAG, "initialize: audio focus retry result: $retryGained")
        }, 500)
      }

      setMicrophoneMute(false)

      updateAudioDeviceState()

      androidAudioManager.registerAudioDeviceCallback(deviceCallback, handler)
      androidAudioManager.registerAudioRecordingCallback(audioRecordingCallback, handler)
      val api31AudioManager = androidAudioManager as AudioManagerCompat.Api31AudioManagerCompat
      api31AudioManager.addOnModeChangedListener(handler::post, modeChangedListener)
      api31AudioManager.addOnCommunicationDeviceChangedListener(handler::post, communicationDeviceChangedListener)

      state = State.PREINITIALIZED

      Log.d(TAG, "Initialized")
    }
  }

  override fun start() {
    Log.i(TAG, "start: currentState: $state currentMode: ${getModeName(androidAudioManager.mode)}")

    incomingRinger.stop()
    outgoingRinger.stop()

    val focusGained = androidAudioManager.requestCallAudioFocus()
    if (!focusGained) {
      Log.w(TAG, "start: audio focus request failed, scheduling retry")
      handler.postDelayed({
        val retryGained = androidAudioManager.requestCallAudioFocus()
        Log.i(TAG, "start: audio focus retry result: $retryGained")
      }, 500)
    }

    state = State.RUNNING
    Log.i(TAG, "start: setting mode to MODE_IN_COMMUNICATION")
    androidAudioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    val volume: Float = androidAudioManager.ringVolumeWithMinimum()
    soundPool.play(connectedSoundId, volume, volume, 0, 0, 1.0f)

    Log.d(TAG, "Started")
  }

  override fun stop(playDisconnect: Boolean) {
    Log.i(TAG, "stop: playDisconnect: $playDisconnect currentState: $state")

    incomingRinger.stop()
    outgoingRinger.stop()

    if (playDisconnect && state != State.UNINITIALIZED) {
      val volume: Float = androidAudioManager.ringVolumeWithMinimum()
      soundPool.play(disconnectedSoundId, volume, volume, 0, 0, 1.0f)
    }
    if (state != State.UNINITIALIZED) {
      androidAudioManager.unregisterAudioDeviceCallback(deviceCallback)
      androidAudioManager.unregisterAudioRecordingCallback(audioRecordingCallback)
      val api31AudioManager = androidAudioManager as AudioManagerCompat.Api31AudioManagerCompat
      api31AudioManager.removeOnModeChangedListener(modeChangedListener)
      api31AudioManager.removeOnCommunicationDeviceChangedListener(communicationDeviceChangedListener)
    }

    if (state == State.UNINITIALIZED && userSelectedAudioDevice != null) {
      Log.d(
        TAG,
        "Stopping audio manager after selecting audio device but never initializing. " +
          "This indicates a service spun up solely to set audio device. " +
          "Therefore skipping audio device reset."
      )
    } else {
      Log.i(TAG, "stop: restoring mode to ${getModeName(savedAudioMode)}")
      androidAudioManager.clearCommunicationDevice()
      setSpeakerphoneOn(savedIsSpeakerPhoneOn)
      setMicrophoneMute(savedIsMicrophoneMute)
      androidAudioManager.mode = savedAudioMode
    }
    androidAudioManager.abandonCallAudioFocus()
    Log.d(TAG, "Abandoned audio focus for VOICE_CALL streams")
    state = State.UNINITIALIZED

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
    Log.i(TAG, "startIncomingRinger: uri: ${if (ringtoneUri != null) "present" else "null"} vibrate: $vibrate currentMode: ${getModeName(androidAudioManager.mode)}")
    androidAudioManager.mode = AudioManager.MODE_RINGTONE
    setMicrophoneMute(false)
    setDefaultAudioDevice(recipientId = null, newDefaultDevice = AudioDevice.SPEAKER_PHONE, clearUserEarpieceSelection = false)
    incomingRinger.start(ringtoneUri, vibrate)
  }

  override fun startOutgoingRinger() {
    Log.i(TAG, "startOutgoingRinger: currentDevice: $selectedAudioDevice currentMode: ${getModeName(androidAudioManager.mode)}")
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
    var candidate: AudioDeviceInfo? = userSelectedAudioDevice
    if (candidate != null && candidate.id != 0) {
      val result = androidAudioManager.setCommunicationDevice(candidate)
      if (result) {
        eventListener?.onAudioDeviceChanged(AudioDeviceMapping.fromPlatformType(candidate.type), availableCommunicationDevices.map { AudioDeviceMapping.fromPlatformType(it.type) }.toSet())
      } else {
        Log.w(TAG, "Failed to set ${candidate.id} of type ${getDeviceTypeName(candidate.type)} as communication device.")
      }
    } else {
      val searchOrder: List<AudioDevice> = listOf(AudioDevice.BLUETOOTH, AudioDevice.WIRED_HEADSET, defaultAudioDevice, AudioDevice.EARPIECE, AudioDevice.SPEAKER_PHONE, AudioDevice.NONE).distinct()
      for (deviceType in searchOrder) {
        candidate = availableCommunicationDevices.filterNot { it.productName.contains(" Watch", true) }.find { AudioDeviceMapping.fromPlatformType(it.type) == deviceType }
        if (candidate != null) {
          break
        }
      }

      when (candidate) {
        null -> {
          Log.e(TAG, "Tried to switch audio devices but could not find suitable device in list of types: ${availableCommunicationDevices.map { getDeviceTypeName(it.type) }.joinToString()}")
          androidAudioManager.clearCommunicationDevice()
        }
        else -> {
          Log.d(TAG, "Switching to new device of type ${getDeviceTypeName(candidate.type)} from ${currentAudioDevice?.type?.let { getDeviceTypeName(it) }}")
          val result = androidAudioManager.setCommunicationDevice(candidate)
          if (result) {
            Log.w(TAG, "Succeeded in setting ${candidate.id} (type: ${getDeviceTypeName(candidate.type)}) as communication device.")
            eventListener?.onAudioDeviceChanged(AudioDeviceMapping.fromPlatformType(candidate.type), availableCommunicationDevices.map { AudioDeviceMapping.fromPlatformType(it.type) }.toSet())
          } else {
            Log.w(TAG, "Failed to set ${candidate.id} as communication device.")
          }
        }
      }
    }
  }

  private fun getModeName(mode: Int): String {
    return when (mode) {
      AudioManager.MODE_NORMAL -> "MODE_NORMAL"
      AudioManager.MODE_RINGTONE -> "MODE_RINGTONE"
      AudioManager.MODE_IN_CALL -> "MODE_IN_CALL"
      AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION"
      AudioManager.MODE_CALL_SCREENING -> "MODE_CALL_SCREENING"
      else -> "UNKNOWN($mode)"
    }
  }

  private fun getDeviceTypeName(type: Int): String {
    return when (type) {
      AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
      AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
      AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC"
      AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
      AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
      AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
      AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
      AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
      AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB_ACCESSORY"
      AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
      AudioDeviceInfo.TYPE_TELEPHONY -> "TELEPHONY"
      AudioDeviceInfo.TYPE_HEARING_AID -> "HEARING_AID"
      AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
      AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
      AudioDeviceInfo.TYPE_BLE_BROADCAST -> "BLE_BROADCAST"
      else -> "UNKNOWN($type)"
    }
  }

  private fun getAudioSourceName(source: Int): String {
    return when (source) {
      MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
      MediaRecorder.AudioSource.MIC -> "MIC"
      MediaRecorder.AudioSource.VOICE_UPLINK -> "VOICE_UPLINK"
      MediaRecorder.AudioSource.VOICE_DOWNLINK -> "VOICE_DOWNLINK"
      MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL"
      MediaRecorder.AudioSource.CAMCORDER -> "CAMCORDER"
      MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
      MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
      MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
      MediaRecorder.AudioSource.VOICE_PERFORMANCE -> "VOICE_PERFORMANCE"
      else -> "UNKNOWN($source)"
    }
  }
}
