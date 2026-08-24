package org.thoughtcrime.securesms.webrtc.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
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
  private var savedIsSpeakerPhoneOn = false
  private var hasWiredHeadset = false

  private var appliedCommunicationDeviceId: Int? = null

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
      Log.i(TAG, "OnCommunicationDeviceChangedListener: id: ${device.id} type: ${getDeviceTypeName(device.type)} mode: ${getModeName(requestedMode)} state: $state")
      if (state == State.RUNNING && userSelectedAudioDevice != null && device.id != userSelectedAudioDevice?.id) {
        Log.w(TAG, "OnCommunicationDeviceChangedListener: Device changed to ${device.id} but user selected ${userSelectedAudioDevice?.id}. Re-asserting user selection.")
        logRoutingContext("OnCommunicationDeviceChangedListener", device)
        updateAudioDeviceState()
      }
    } else {
      Log.w(TAG, "OnCommunicationDeviceChangedListener: null")
    }
  }

  private val modeChangedListener = AudioManager.OnModeChangedListener { mode ->
    Log.i(TAG, "OnModeChangedListener: applied ${getModeName(mode)} (requested ${getModeName(requestedMode)}) state: $state")

    appliedMode = mode

    if (mode == AudioManager.MODE_IN_COMMUNICATION) {
      Log.i(TAG, "OnModeChangedListener: commDevice: ${describeDevice(androidAudioManager.communicationDevice)} micMute: ${androidAudioManager.isMicrophoneMute}")
      notifyReadyForAccept("mode applied")
    } else if (state != State.UNINITIALIZED) {
      if (state == State.RUNNING) {
        Log.w(TAG, "OnModeChangedListener: Not MODE_IN_COMMUNICATION during a call. state: $state")
      }
      logRoutingContext("OnModeChangedListener", mode = mode)
    }
  }

  private val audioRecordingCallback = object : AudioManager.AudioRecordingCallback() {
    override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
      if (configs.isEmpty()) {
        Log.i(TAG, "AudioRecordingCallback: no active recordings state: $state mode: ${getModeName(requestedMode)}")
      } else {
        for (config in configs) {
          val deviceName = config.audioDevice?.let { getDeviceTypeName(it.type) } ?: "null"
          val description = "AudioRecordingCallback: silenced: ${config.isClientSilenced} source: ${getAudioSourceName(config.audioSource)} device: $deviceName state: $state mode: ${getModeName(requestedMode)}"
          if (config.isClientSilenced) {
            Log.w(TAG, description)
          } else {
            Log.i(TAG, description)
          }
        }
      }
    }
  }

  override fun setDefaultAudioDevice(recipientId: RecipientId?, newDefaultDevice: AudioDevice, clearUserEarpieceSelection: Boolean) {
    Log.i(TAG, "setDefaultAudioDevice(): currentDefault: $defaultAudioDevice device: $newDefaultDevice clearUser: $clearUserEarpieceSelection state: $state mode: ${getModeName(requestedMode)}")
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
      Log.i(TAG, "Clearing user setting of earpiece")
      userSelectedAudioDevice = null
    }

    Log.i(TAG, "New default: $defaultAudioDevice userSelected: ${userSelectedAudioDevice?.id} of type ${userSelectedAudioDevice?.type}")
    updateAudioDeviceState()
  }

  override fun initialize() {
    if (state == State.UNINITIALIZED) {
      savedAudioMode = androidAudioManager.mode
      requestedMode = savedAudioMode
      appliedMode = savedAudioMode
      savedIsSpeakerPhoneOn = androidAudioManager.isSpeakerphoneOn
      savedIsMicrophoneMute = androidAudioManager.isMicrophoneMute
      hasWiredHeadset = androidAudioManager.isWiredHeadsetOn

      Log.i(TAG, "initialize: savedMode: ${getModeName(savedAudioMode)} savedSpeaker: $savedIsSpeakerPhoneOn savedMicMute: $savedIsMicrophoneMute wiredHeadset: $hasWiredHeadset")

      requestCallAudioFocus("initialize")

      setMicrophoneMute(false)

      updateAudioDeviceState()

      androidAudioManager.registerAudioDeviceCallback(deviceCallback, handler)
      androidAudioManager.registerAudioRecordingCallback(audioRecordingCallback, handler)
      val api31AudioManager = androidAudioManager as AudioManagerCompat.Api31AudioManagerCompat
      api31AudioManager.addOnModeChangedListener(handler::post, modeChangedListener)
      api31AudioManager.addOnCommunicationDeviceChangedListener(handler::post, communicationDeviceChangedListener)

      state = State.PREINITIALIZED

      Log.i(TAG, "initialize: complete. mode: ${getModeName(requestedMode)}")
    } else {
      Log.i(TAG, "initialize: skipping, state: $state")
    }
  }

  override fun onPrepareForAccept() {
    Log.i(TAG, "onPrepareForAccept: state: $state previousMode: ${getModeName(requestedMode)} appliedMode: ${getModeName(appliedMode)}")

    incomingRinger.stop()
    requestCallAudioFocus("onPrepareForAccept")
    setMicrophoneMute(false)

    // The mode is global but owned per app, so always claim it even when another app holds it,
    // but only expect a callback when the global mode actually moves.
    val expectModeChange = requestedMode != AudioManager.MODE_IN_COMMUNICATION || appliedMode != AudioManager.MODE_IN_COMMUNICATION
    setMode(AudioManager.MODE_IN_COMMUNICATION, "onPrepareForAccept")

    if (!expectModeChange) {
      notifyReadyForAccept("no mode change expected")
    }
  }

  override fun start() {
    Log.i(TAG, "start: currentState: $state previousMode: ${getModeName(requestedMode)} appliedMode: ${getModeName(appliedMode)} prepared: $preparedForAccept")

    incomingRinger.stop()
    outgoingRinger.stop()
    requestCallAudioFocus("start")

    // Only the accept path can skip this, and only because the listener proves the mode is in effect.
    if (!preparedForAccept || appliedMode != AudioManager.MODE_IN_COMMUNICATION) {
      setMode(AudioManager.MODE_IN_COMMUNICATION, "start")
    }

    state = State.RUNNING
    logActiveRecordingConfigurations("start")

    val volume: Float = androidAudioManager.ringVolumeWithMinimum()
    soundPool.play(connectedSoundId, volume, volume, 0, 0, 1.0f)
  }

  override fun stop(playDisconnect: Boolean) {
    Log.i(TAG, "stop: playDisconnect: $playDisconnect currentState: $state currentMode: ${getModeName(requestedMode)}")

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
      Log.i(TAG, "stop: restoring mode to ${getModeName(savedAudioMode)} speaker: $savedIsSpeakerPhoneOn micMute: $savedIsMicrophoneMute")
      androidAudioManager.clearCommunicationDevice()
      setSpeakerphoneOn(savedIsSpeakerPhoneOn)
      setMicrophoneMute(savedIsMicrophoneMute)
      setMode(savedAudioMode, "stop")
    }
    androidAudioManager.abandonCallAudioFocus()
    state = State.UNINITIALIZED
    resetAcceptState()
    appliedMode = AudioManager.MODE_INVALID
    appliedCommunicationDeviceId = null

    Log.i(TAG, "stop: complete. mode: ${getModeName(requestedMode)}")
  }

  override fun selectAudioDevice(recipientId: RecipientId?, device: Int, isId: Boolean) {
    if (!isId) {
      throw IllegalArgumentException("Must supply a device address for API 31+.")
    }

    userSelectedAudioDevice = androidAudioManager.availableCommunicationDevices.find { it.id == device }

    Log.i(TAG, "selectAudioDevice(): requested: $device resolved: ${describeDevice(userSelectedAudioDevice)} state: $state mode: ${getModeName(requestedMode)}")

    updateAudioDeviceState()
  }

  private fun setSpeakerphoneOn(on: Boolean) {
    if (androidAudioManager.isSpeakerphoneOn != on) {
      androidAudioManager.isSpeakerphoneOn = on
    }
  }

  private fun updateAudioDeviceState() {
    handler.assertHandlerThread()

    val currentAudioDevice: AudioDeviceInfo? = androidAudioManager.communicationDevice

    val availableCommunicationDevices: List<AudioDeviceInfo> = androidAudioManager.availableCommunicationDevices

    Log.i(
      TAG,
      "updateAudioDeviceState(): state: $state mode: ${getModeName(requestedMode)}\n" +
        "    default: $defaultAudioDevice userSelected: ${describeDevice(userSelectedAudioDevice)}\n" +
        "    current: ${describeDevice(currentAudioDevice)}\n" +
        "    available: ${describeDevices(availableCommunicationDevices)}"
    )

    if (userSelectedAudioDevice != null && availableCommunicationDevices.none { it.id == userSelectedAudioDevice?.id }) {
      Log.w(TAG, "User selected device ${userSelectedAudioDevice?.id} of type ${userSelectedAudioDevice?.type?.let { getDeviceTypeName(it) }} is no longer available. Clearing user selection.")
      userSelectedAudioDevice = null
    }

    var candidate: AudioDeviceInfo? = userSelectedAudioDevice
    if (candidate != null && candidate.id != 0) {
      val result = setCommunicationDeviceIfNeeded(candidate, currentAudioDevice)
      if (result) {
        eventListener?.onAudioDeviceChanged(AudioDeviceMapping.fromPlatformType(candidate.type), availableCommunicationDevices.map { AudioDeviceMapping.fromPlatformType(it.type) }.toSet())
      } else {
        Log.w(TAG, "Failed to set ${candidate.id} of type ${getDeviceTypeName(candidate.type)} as communication device. Clearing user selection.")
        userSelectedAudioDevice = null
        candidate = null
      }
    }

    if (candidate == null) {
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
          appliedCommunicationDeviceId = null
          eventListener?.onAudioDeviceChangeFailed()
        }
        else -> {
          if (setCommunicationDeviceIfNeeded(candidate, currentAudioDevice)) {
            eventListener?.onAudioDeviceChanged(AudioDeviceMapping.fromPlatformType(candidate.type), availableCommunicationDevices.map { AudioDeviceMapping.fromPlatformType(it.type) }.toSet())
          } else {
            eventListener?.onAudioDeviceChangeFailed()
          }
        }
      }
    }
  }

  private fun setCommunicationDeviceIfNeeded(device: AudioDeviceInfo, currentDevice: AudioDeviceInfo?): Boolean {
    if (device.id == appliedCommunicationDeviceId && device.id == currentDevice?.id) {
      Log.i(TAG, "setCommunicationDevice(${describeDevice(device)}) skipped, already routed")
      return true
    }

    val result = androidAudioManager.setCommunicationDevice(device)
    val description = "setCommunicationDevice(${describeDevice(device)}) returned $result in ${getModeName(requestedMode)}, previous: ${describeDevice(currentDevice)}"
    if (result) {
      appliedCommunicationDeviceId = device.id
      Log.i(TAG, description)
    } else {
      Log.w(TAG, description)
    }
    return result
  }

  private fun logActiveRecordingConfigurations(event: String) {
    val configs = androidAudioManager.activeRecordingConfigurations
    if (configs.isEmpty()) {
      Log.w(TAG, "$event: activeRecordingConfigurations: none mode: ${getModeName(requestedMode)}")
      return
    }
    for (config in configs) {
      val deviceName = config.audioDevice?.let { getDeviceTypeName(it.type) } ?: "null"
      Log.i(TAG, "$event: activeRecordingConfiguration: silenced: ${config.isClientSilenced} source: ${getAudioSourceName(config.audioSource)} device: $deviceName mode: ${getModeName(requestedMode)}")
    }
  }

  private fun logRoutingContext(event: String, callbackDevice: AudioDeviceInfo? = null, mode: Int = appliedMode) {
    val currentDevice: AudioDeviceInfo? = androidAudioManager.communicationDevice
    val availableDevices: List<AudioDeviceInfo> = androidAudioManager.availableCommunicationDevices
    val selectedStillAvailable = userSelectedAudioDevice?.let { selected ->
      availableDevices.any { it.id == selected.id }
    } ?: false
    val probableCause = when {
      mode != AudioManager.MODE_IN_COMMUNICATION -> "mode_not_in_communication"
      userSelectedAudioDevice != null && !selectedStillAvailable -> "user_selected_device_disconnected"
      state != State.RUNNING -> "expected_before_accept"
      else -> "platform_or_competing_app_reroute"
    }
    val description = "$event: probableCause: $probableCause state: $state mode: ${getModeName(mode)}\n" +
      "    defaultDevice: $defaultAudioDevice callbackDevice: ${describeDevice(callbackDevice)}\n" +
      "    userSelected: ${describeDevice(userSelectedAudioDevice)}\n" +
      "    currentDevice: ${describeDevice(currentDevice)}\n" +
      "    availableDevices: ${describeDevices(availableDevices)}"
    if (state == State.RUNNING) {
      Log.w(TAG, description)
    } else {
      Log.i(TAG, description)
    }
  }

  private fun describeDevices(devices: List<AudioDeviceInfo>): String = devices.joinToString(prefix = "[", postfix = "]") { describeDevice(it) }

  private fun describeDevice(device: AudioDeviceInfo?): String {
    if (device == null) {
      return "null"
    }
    val productName = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "unknown"
    return "${device.id}:${getDeviceTypeName(device.type)}:$productName"
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
