package org.thoughtcrime.securesms.webrtc.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.AndroidTelecomUtil
import org.thoughtcrime.securesms.util.safeUnregisterReceiver
import org.whispersystems.signalservice.api.util.Preconditions

private val TAG = Log.tag(SignalAudioManager::class.java)

sealed class SignalAudioManager(protected val context: Context, protected val eventListener: EventListener?) {

  private var commandAndControlThread = SignalExecutors.getAndStartHandlerThread("call-audio")
  protected val handler = SignalAudioHandler(commandAndControlThread.looper)

  protected var state: State = State.UNINITIALIZED

  protected val androidAudioManager = ApplicationDependencies.getAndroidCallAudioManager()

  protected var selectedAudioDevice: AudioDevice = AudioDevice.NONE

  protected val soundPool: SoundPool = androidAudioManager.createSoundPool()
  protected val connectedSoundId = soundPool.load(context, R.raw.webrtc_completed, 1)
  protected val disconnectedSoundId = soundPool.load(context, R.raw.webrtc_disconnected, 1)

  protected val incomingRinger = IncomingRinger(context)
  protected val outgoingRinger = OutgoingRinger(context)

  companion object {
    @JvmStatic
    fun create(context: Context, eventListener: EventListener?, isGroup: Boolean): SignalAudioManager {
      return if (AndroidTelecomUtil.telecomSupported && !isGroup) {
        TelecomAwareSignalAudioManager(context, eventListener)
      } else if (Build.VERSION.SDK_INT >= 31) {
        FullSignalAudioManagerApi31(context, eventListener)
      } else {
        FullSignalAudioManager(context, eventListener)
      }
    }
  }

  fun handleCommand(command: AudioManagerCommand) {
    handler.post {
      when (command) {
        is AudioManagerCommand.Initialize -> initialize()
        is AudioManagerCommand.Start -> start()
        is AudioManagerCommand.Stop -> stop(command.playDisconnect)
        is AudioManagerCommand.SetDefaultDevice -> setDefaultAudioDevice(command.recipientId, command.device, command.clearUserEarpieceSelection)
        is AudioManagerCommand.SetUserDevice -> selectAudioDevice(command.recipientId, command.device)
        is AudioManagerCommand.StartIncomingRinger -> startIncomingRinger(command.ringtoneUri, command.vibrate)
        is AudioManagerCommand.SilenceIncomingRinger -> silenceIncomingRinger()
        is AudioManagerCommand.StartOutgoingRinger -> startOutgoingRinger()
      }
    }
  }

  fun shutdown() {
    handler.post {
      stop(false)
      if (commandAndControlThread != null) {
        Log.i(TAG, "Shutting down command and control")
        commandAndControlThread.quitSafely()
        commandAndControlThread = null
      }
    }
  }

  protected abstract fun initialize()
  protected abstract fun start()
  protected abstract fun stop(playDisconnect: Boolean)
  protected abstract fun setDefaultAudioDevice(recipientId: RecipientId?, newDefaultDevice: AudioDevice, clearUserEarpieceSelection: Boolean)
  protected abstract fun selectAudioDevice(recipientId: RecipientId?, device: AudioDevice)
  protected abstract fun startIncomingRinger(ringtoneUri: Uri?, vibrate: Boolean)
  protected abstract fun startOutgoingRinger()

  protected open fun silenceIncomingRinger() {
    Log.i(TAG, "silenceIncomingRinger():")
    incomingRinger.stop()
  }

  enum class AudioDevice {
    SPEAKER_PHONE, WIRED_HEADSET, EARPIECE, BLUETOOTH, NONE
  }

  enum class State {
    UNINITIALIZED, PREINITIALIZED, RUNNING
  }

  interface EventListener {
    @JvmSuppressWildcards
    fun onAudioDeviceChanged(activeDevice: AudioDevice, devices: Set<AudioDevice>)
    fun onBluetoothPermissionDenied()
  }
}

/**
 * Manage all audio and bluetooth routing for calling. Primarily, operates by maintaining a list
 * of available devices (wired, speaker, bluetooth, earpiece) and then using a state machine to determine
 * which device to use. Inputs into the decision include the [defaultAudioDevice] (set based on if audio
 * only or video call) and [userSelectedAudioDevice] (set by user interaction with UI). [autoSwitchToWiredHeadset]
 * and [autoSwitchToBluetooth] also impact the decision by forcing the user selection to the respective device
 * when initially discovered. If the user switches to another device while bluetooth or wired headset are
 * connected, the system will not auto switch back until the audio device is disconnected and reconnected.
 *
 * For example, call starts with speaker, then a bluetooth headset is connected. The audio will automatically
 * switch to the headset. The user can then switch back to speaker through a manual interaction. If the
 * bluetooth headset is then disconnected, and reconnected, the audio will again automatically switch to
 * the bluetooth headset.
 */
class FullSignalAudioManager(context: Context, eventListener: EventListener?) : SignalAudioManager(context, eventListener) {
  private val signalBluetoothManager = SignalBluetoothManager(context, this, handler)

  private var audioDevices: MutableSet<AudioDevice> = mutableSetOf()
  private var defaultAudioDevice: AudioDevice = AudioDevice.EARPIECE
  private var userSelectedAudioDevice: AudioDevice = AudioDevice.NONE
  private var previousBluetoothState: SignalBluetoothManager.State? = null

  private var savedAudioMode = AudioManager.MODE_INVALID
  private var savedIsSpeakerPhoneOn = false
  private var savedIsMicrophoneMute = false
  private var hasWiredHeadset = false
  private var autoSwitchToWiredHeadset = true
  private var autoSwitchToBluetooth = true

  private var wiredHeadsetReceiver: WiredHeadsetReceiver? = null

  override fun initialize() {
    Log.i(TAG, "Initializing audio manager state: $state")

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

      audioDevices.clear()

      signalBluetoothManager.start()

      updateAudioDeviceState()

      wiredHeadsetReceiver = WiredHeadsetReceiver()
      context.registerReceiver(wiredHeadsetReceiver, IntentFilter(AudioManager.ACTION_HEADSET_PLUG))

      state = State.PREINITIALIZED

      Log.d(TAG, "Initialized")
    }
  }

  override fun start() {
    Log.d(TAG, "Starting. state: $state")
    if (state == State.RUNNING) {
      Log.w(TAG, "Skipping, already active")
      return
    }

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
    Log.d(TAG, "Stopping. state: $state")

    incomingRinger.stop()
    outgoingRinger.stop()

    if (playDisconnect && state != State.UNINITIALIZED) {
      val volume: Float = androidAudioManager.ringVolumeWithMinimum()
      soundPool.play(disconnectedSoundId, volume, volume, 0, 0, 1.0f)
    }

    state = State.UNINITIALIZED

    context.safeUnregisterReceiver(wiredHeadsetReceiver)
    wiredHeadsetReceiver = null

    signalBluetoothManager.stop()

    setSpeakerphoneOn(savedIsSpeakerPhoneOn)
    setMicrophoneMute(savedIsMicrophoneMute)
    androidAudioManager.mode = savedAudioMode

    androidAudioManager.abandonCallAudioFocus()
    Log.d(TAG, "Abandoned audio focus for VOICE_CALL streams")

    Log.d(TAG, "Stopped")
  }

  fun updateAudioDeviceState() {
    handler.assertHandlerThread()

    Log.i(
      TAG,
      "updateAudioDeviceState(): " +
        "wired: $hasWiredHeadset " +
        "bt: ${signalBluetoothManager.state} " +
        "available: $audioDevices " +
        "selected: $selectedAudioDevice " +
        "userSelected: $userSelectedAudioDevice"
    )

    if (signalBluetoothManager.state.shouldUpdate()) {
      signalBluetoothManager.updateDevice()
    }

    val newAudioDevices = mutableSetOf(AudioDevice.SPEAKER_PHONE)

    if (signalBluetoothManager.state.hasDevice()) {
      newAudioDevices += AudioDevice.BLUETOOTH
    }

    if (hasWiredHeadset) {
      newAudioDevices += AudioDevice.WIRED_HEADSET
    } else {
      autoSwitchToWiredHeadset = true
      if (androidAudioManager.hasEarpiece(context)) {
        newAudioDevices += AudioDevice.EARPIECE
      }
    }

    var audioDeviceSetUpdated = audioDevices != newAudioDevices
    audioDevices = newAudioDevices

    if (signalBluetoothManager.state == SignalBluetoothManager.State.UNAVAILABLE && userSelectedAudioDevice == AudioDevice.BLUETOOTH) {
      userSelectedAudioDevice = AudioDevice.NONE
    }

    if (hasWiredHeadset && autoSwitchToWiredHeadset) {
      userSelectedAudioDevice = AudioDevice.WIRED_HEADSET
      autoSwitchToWiredHeadset = false
    }

    if (!hasWiredHeadset && userSelectedAudioDevice == AudioDevice.WIRED_HEADSET) {
      userSelectedAudioDevice = AudioDevice.NONE
    }

    val needBluetoothAudioStart = signalBluetoothManager.state == SignalBluetoothManager.State.AVAILABLE &&
      (userSelectedAudioDevice == AudioDevice.NONE || userSelectedAudioDevice == AudioDevice.BLUETOOTH || autoSwitchToBluetooth)

    val needBluetoothAudioStop = (signalBluetoothManager.state == SignalBluetoothManager.State.CONNECTED || signalBluetoothManager.state == SignalBluetoothManager.State.CONNECTING) &&
      (userSelectedAudioDevice != AudioDevice.NONE && userSelectedAudioDevice != AudioDevice.BLUETOOTH)

    if (signalBluetoothManager.state.hasDevice()) {
      Log.i(TAG, "Need bluetooth audio: state: ${signalBluetoothManager.state} start: $needBluetoothAudioStart stop: $needBluetoothAudioStop")
    }

    if (needBluetoothAudioStop) {
      signalBluetoothManager.stopScoAudio()
      signalBluetoothManager.updateDevice()
    }

    if (!autoSwitchToBluetooth && signalBluetoothManager.state == SignalBluetoothManager.State.UNAVAILABLE) {
      autoSwitchToBluetooth = true
    }

    if (needBluetoothAudioStart && !needBluetoothAudioStop) {
      if (!signalBluetoothManager.startScoAudio()) {
        audioDevices.remove(AudioDevice.BLUETOOTH)
        audioDeviceSetUpdated = true
      }
    }

    if (autoSwitchToBluetooth && signalBluetoothManager.state == SignalBluetoothManager.State.CONNECTED) {
      userSelectedAudioDevice = AudioDevice.BLUETOOTH
      autoSwitchToBluetooth = false
    }

    if (previousBluetoothState != null && previousBluetoothState != SignalBluetoothManager.State.PERMISSION_DENIED && signalBluetoothManager.state == SignalBluetoothManager.State.PERMISSION_DENIED) {
      eventListener?.onBluetoothPermissionDenied()
    }
    previousBluetoothState = signalBluetoothManager.state

    val newAudioDevice: AudioDevice = when {
      audioDevices.contains(userSelectedAudioDevice) -> userSelectedAudioDevice
      audioDevices.contains(defaultAudioDevice) -> defaultAudioDevice
      else -> AudioDevice.SPEAKER_PHONE
    }

    if (newAudioDevice != selectedAudioDevice || audioDeviceSetUpdated) {
      setAudioDevice(newAudioDevice)
      Log.i(TAG, "New device status: available: $audioDevices, selected: $newAudioDevice")
      eventListener?.onAudioDeviceChanged(selectedAudioDevice, audioDevices)
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

  override fun selectAudioDevice(recipientId: RecipientId?, device: AudioDevice) {
    val actualDevice = if (device == AudioDevice.EARPIECE && audioDevices.contains(AudioDevice.WIRED_HEADSET)) AudioDevice.WIRED_HEADSET else device

    Log.d(TAG, "selectAudioDevice(): device: $device actualDevice: $actualDevice")
    if (!audioDevices.contains(actualDevice)) {
      Log.w(TAG, "Can not select $actualDevice from available $audioDevices")
    }
    userSelectedAudioDevice = actualDevice
    updateAudioDeviceState()
  }

  private fun setAudioDevice(device: AudioDevice) {
    Log.d(TAG, "setAudioDevice(): device: $device")
    Preconditions.checkArgument(audioDevices.contains(device))
    when (device) {
      AudioDevice.SPEAKER_PHONE -> setSpeakerphoneOn(true)
      AudioDevice.EARPIECE -> setSpeakerphoneOn(false)
      AudioDevice.WIRED_HEADSET -> setSpeakerphoneOn(false)
      AudioDevice.BLUETOOTH -> setSpeakerphoneOn(false)
      else -> throw AssertionError("Invalid audio device selection")
    }
    selectedAudioDevice = device
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

  private fun onWiredHeadsetChange(pluggedIn: Boolean, hasMic: Boolean) {
    Log.i(TAG, "onWiredHeadsetChange state: $state plug: $pluggedIn mic: $hasMic")
    hasWiredHeadset = pluggedIn
    updateAudioDeviceState()
  }

  private inner class WiredHeadsetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val pluggedIn = intent.getIntExtra("state", 0) == 1
      val hasMic = intent.getIntExtra("microphone", 0) == 1

      handler.post { onWiredHeadsetChange(pluggedIn, hasMic) }
    }
  }
}

class TelecomAwareSignalAudioManager(context: Context, eventListener: EventListener?) : SignalAudioManager(context, eventListener) {

  override fun setDefaultAudioDevice(recipientId: RecipientId?, newDefaultDevice: AudioDevice, clearUserEarpieceSelection: Boolean) {
    if (recipientId != null && AndroidTelecomUtil.getSelectedAudioDevice(recipientId) == AudioDevice.EARPIECE) {
      selectAudioDevice(recipientId, newDefaultDevice)
    }
  }

  override fun initialize() {
    val focusedGained = androidAudioManager.requestCallAudioFocus()
    if (!focusedGained) {
      handler.postDelayed({ androidAudioManager.requestCallAudioFocus() }, 500)
    }

    state = State.PREINITIALIZED
  }

  override fun start() {
    incomingRinger.stop()
    outgoingRinger.stop()

    val focusedGained = androidAudioManager.requestCallAudioFocus()
    if (!focusedGained) {
      handler.postDelayed({ androidAudioManager.requestCallAudioFocus() }, 500)
    }

    state = State.RUNNING
  }

  override fun stop(playDisconnect: Boolean) {
    incomingRinger.stop()
    outgoingRinger.stop()

    if (playDisconnect && state != State.UNINITIALIZED) {
      val volume: Float = androidAudioManager.ringVolumeWithMinimum()
      soundPool.play(disconnectedSoundId, volume, volume, 0, 0, 1.0f)
    }

    state = State.UNINITIALIZED

    androidAudioManager.abandonCallAudioFocus()
  }

  override fun selectAudioDevice(recipientId: RecipientId?, device: AudioDevice) {
    if (recipientId != null) {
      selectedAudioDevice = device
      AndroidTelecomUtil.selectAudioDevice(recipientId, device)
      handler.postDelayed({ AndroidTelecomUtil.selectAudioDevice(recipientId, selectedAudioDevice) }, 1000)
    }
  }

  override fun startIncomingRinger(ringtoneUri: Uri?, vibrate: Boolean) {
    incomingRinger.start(ringtoneUri, vibrate)
  }

  override fun startOutgoingRinger() {
    outgoingRinger.start(OutgoingRinger.Type.RINGING)
  }
}
