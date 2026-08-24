/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.webrtc.audio

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import androidx.annotation.VisibleForTesting
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.core.util.safeUnregisterReceiver
import org.signal.network.util.Preconditions
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.audio.AudioDeviceUpdatedListener
import org.thoughtcrime.securesms.audio.SignalBluetoothManager
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.AndroidTelecomUtil

private val TAG = Log.tag(SignalAudioManager::class.java)

sealed class SignalAudioManager(protected val context: Context, protected val eventListener: EventListener?) {

  private var commandAndControlThread = SignalExecutors.getAndStartHandlerThread("call-audio", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD)
  protected val handler = SignalAudioHandler(commandAndControlThread.looper)

  protected var state: State = State.UNINITIALIZED

  protected val androidAudioManager = AppDependencies.androidCallAudioManager

  protected var selectedAudioDevice: AudioDevice = AudioDevice.NONE

  protected val soundPool: SoundPool = androidAudioManager.createSoundPool()
  protected val connectedSoundId = soundPool.load(context, R.raw.webrtc_completed, 1)
  protected val disconnectedSoundId = soundPool.load(context, R.raw.webrtc_disconnected, 1)

  protected val incomingRinger = IncomingRinger(context)
  protected val outgoingRinger = OutgoingRinger(context)

  private val stateChangeUpSoundId = soundPool.load(context, R.raw.notification_simple_01, 1)

  protected var savedAudioMode = AudioManager.MODE_INVALID
  protected var savedIsMicrophoneMute = false

  /** What we last asked for, which may not be in effect yet. Logged in place of the blocking getMode(). */
  protected var requestedMode = AudioManager.MODE_INVALID

  /** What the platform last reported as in effect. Branch on this rather than [requestedMode]. */
  protected var appliedMode = AudioManager.MODE_INVALID

  protected var preparedForAccept = false

  private var awaitingAudioForAccept = false
  private var prepareForAcceptStartedAt = 0L

  private val acceptGateTimeout = Runnable {
    Log.w(TAG, "Audio not reported ready within ${ACCEPT_GATE_TIMEOUT_MS}ms, accepting anyway")
    notifyReadyForAccept("timed out")
  }

  companion object {
    private const val FOCUS_RETRY_DELAY_MS = 500L

    @VisibleForTesting
    const val ACCEPT_GATE_TIMEOUT_MS = 1500L

    @SuppressLint("NewApi")
    @JvmStatic
    fun create(context: Context, eventListener: EventListener?, canUseTelecom: Boolean): SignalAudioManager {
      return if (canUseTelecom && AndroidTelecomUtil.telecomSupported) {
        TelecomAudioManager(context, eventListener)
      } else if (Build.VERSION.SDK_INT >= 31) {
        FullSignalAudioManagerApi31(context, eventListener)
      } else {
        FullSignalAudioManager(context, eventListener)
      }
    }
  }

  fun handleCommand(command: AudioManagerCommand) {
    val posted = handler.post {
      Log.i(TAG, "handleCommand(): ${command.javaClass.simpleName} state: $state requestedMode: ${getModeName(requestedMode)}")
      when (command) {
        is AudioManagerCommand.Initialize -> initialize()
        is AudioManagerCommand.PrepareForAccept -> prepareForAccept()
        is AudioManagerCommand.Start -> start()
        is AudioManagerCommand.Stop -> stop(command.playDisconnect)
        is AudioManagerCommand.SetDefaultDevice -> setDefaultAudioDevice(command.recipientId, command.device, command.clearUserEarpieceSelection)
        is AudioManagerCommand.SetUserDevice -> selectAudioDevice(command.recipientId, command.device, command.isId)
        is AudioManagerCommand.StartIncomingRinger -> startIncomingRinger(command.ringtoneUri, command.vibrate)
        is AudioManagerCommand.SilenceIncomingRinger -> silenceIncomingRinger()
        is AudioManagerCommand.StartOutgoingRinger -> startOutgoingRinger()
        is AudioManagerCommand.PlayStateChangeUp -> playStateChangeUp()
      }
    }

    if (!posted) {
      Log.w(TAG, "handleCommand(): could not enqueue ${command.javaClass.simpleName}, handler is shut down")
      if (command is AudioManagerCommand.PrepareForAccept) {
        eventListener?.onAudioReadyForAccept()
      }
    }
  }

  /** Starts the backstop first, so an implementation that never reports back only delays the accept. */
  private fun prepareForAccept() {
    preparedForAccept = true
    awaitingAudioForAccept = true
    prepareForAcceptStartedAt = System.currentTimeMillis()
    handler.postDelayed(acceptGateTimeout, ACCEPT_GATE_TIMEOUT_MS)
    onPrepareForAccept()
  }

  protected fun notifyReadyForAccept(reason: String) {
    if (!awaitingAudioForAccept) {
      return
    }
    awaitingAudioForAccept = false
    handler.removeCallbacks(acceptGateTimeout)
    Log.i(TAG, "notifyReadyForAccept(): $reason after ${System.currentTimeMillis() - prepareForAcceptStartedAt}ms")
    eventListener?.onAudioReadyForAccept()
  }

  protected fun resetAcceptState() {
    preparedForAccept = false
    awaitingAudioForAccept = false
    handler.removeCallbacks(acceptGateTimeout)
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

  private fun playStateChangeUp() {
    val volume: Float = androidAudioManager.voiceCallVolume
    soundPool.play(stateChangeUpSoundId, volume, volume, 0, 0, 1f)
  }

  protected abstract fun initialize()
  protected abstract fun start()
  protected abstract fun stop(playDisconnect: Boolean)
  protected abstract fun setDefaultAudioDevice(recipientId: RecipientId?, newDefaultDevice: AudioDevice, clearUserEarpieceSelection: Boolean)
  protected abstract fun selectAudioDevice(recipientId: RecipientId?, device: Int, isId: Boolean)

  /** Must end in [notifyReadyForAccept]; otherwise the accept waits for [ACCEPT_GATE_TIMEOUT_MS]. */
  protected open fun onPrepareForAccept() {
    Log.i(TAG, "onPrepareForAccept(): state: $state previousMode: ${getModeName(requestedMode)}")

    incomingRinger.stop()
    requestCallAudioFocus("onPrepareForAccept")
    setMicrophoneMute(false)
    setMode(AudioManager.MODE_IN_COMMUNICATION, "onPrepareForAccept")

    // Nothing reports mode changes before API 31, but the setter blocks there, so a read back
    // confirms the mode is applied.
    val readBackStart = System.currentTimeMillis()
    appliedMode = androidAudioManager.mode
    Log.i(TAG, "onPrepareForAccept(): mode read back as ${getModeName(appliedMode)} after ${System.currentTimeMillis() - readBackStart}ms")

    notifyReadyForAccept("mode applied per read back")
  }

  protected open fun startIncomingRinger(ringtoneUri: Uri?, vibrate: Boolean) {
    Log.i(TAG, "startIncomingRinger(): uri: ${if (ringtoneUri != null) "present" else "null"} vibrate: $vibrate previousMode: ${getModeName(requestedMode)}")
    setMode(AudioManager.MODE_RINGTONE, "startIncomingRinger")
    setMicrophoneMute(false)
    incomingRinger.start(ringtoneUri, vibrate)
  }

  protected open fun startOutgoingRinger() {
    Log.i(TAG, "startOutgoingRinger(): currentDevice: $selectedAudioDevice previousMode: ${getModeName(requestedMode)}")
    setMode(AudioManager.MODE_IN_COMMUNICATION, "startOutgoingRinger")
    setMicrophoneMute(false)
    outgoingRinger.start(OutgoingRinger.Type.RINGING)
  }

  protected fun requestCallAudioFocus(caller: String) {
    val gained = androidAudioManager.requestCallAudioFocus()
    Log.i(TAG, "$caller: audio focus gained: $gained")

    if (!gained) {
      Log.w(TAG, "$caller: audio focus request failed, scheduling retry")
      handler.postDelayed({
        Log.i(TAG, "$caller: audio focus retry result: ${androidAudioManager.requestCallAudioFocus()}")
      }, FOCUS_RETRY_DELAY_MS)
    }
  }

  /** The setter returns long before the platform applies the change. */
  protected fun setMode(mode: Int, caller: String) {
    val start = System.currentTimeMillis()
    requestedMode = mode
    androidAudioManager.mode = mode
    Log.i(TAG, "$caller: requested ${getModeName(mode)}, setter returned in ${System.currentTimeMillis() - start}ms")
  }

  protected fun getModeName(mode: Int): String {
    return when (mode) {
      AudioManager.MODE_NORMAL -> "MODE_NORMAL"
      AudioManager.MODE_RINGTONE -> "MODE_RINGTONE"
      AudioManager.MODE_IN_CALL -> "MODE_IN_CALL"
      AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION"
      AudioManager.MODE_CALL_SCREENING -> "MODE_CALL_SCREENING"
      else -> "UNKNOWN($mode)"
    }
  }

  protected open fun silenceIncomingRinger() {
    Log.i(TAG, "silenceIncomingRinger():")
    incomingRinger.stop()
  }

  protected fun setMicrophoneMute(on: Boolean) {
    if (androidAudioManager.isMicrophoneMute != on) {
      Log.i(TAG, "setMicrophoneMute(): changing system microphone mute to $on")
      androidAudioManager.isMicrophoneMute = on
    }
  }

  enum class AudioDevice {
    SPEAKER_PHONE,
    WIRED_HEADSET,
    EARPIECE,
    BLUETOOTH,
    NONE
  }

  enum class State {
    UNINITIALIZED,
    PREINITIALIZED,
    RUNNING
  }

  /**
   * This encapsulates the two ways to represent a chosen audio device.
   * Use [desiredAudioDeviceLegacy] for API < 31
   * Use [desiredAudioDevice31] for API 31+
   */
  class ChosenAudioDeviceIdentifier {
    var desiredAudioDeviceLegacy: AudioDevice? = null
    var desiredAudioDevice31: Int? = null

    fun isLegacy(): Boolean {
      return desiredAudioDeviceLegacy != null
    }

    constructor(device: AudioDevice) {
      desiredAudioDeviceLegacy = device
    }

    constructor(device: Int) {
      desiredAudioDevice31 = device
    }
  }

  interface EventListener {
    @JvmSuppressWildcards
    fun onAudioDeviceChanged(activeDevice: AudioDevice, devices: Set<AudioDevice>)
    fun onAudioDeviceChangeFailed()
    fun onBluetoothPermissionDenied()

    /** The device is in MODE_IN_COMMUNICATION and the incoming call may now be accepted. */
    fun onAudioReadyForAccept()
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
class FullSignalAudioManager(context: Context, eventListener: EventListener?) : SignalAudioManager(context, eventListener), AudioDeviceUpdatedListener {
  private val signalBluetoothManager = SignalBluetoothManager(context, this, handler)

  private var audioDevices: MutableSet<AudioDevice> = mutableSetOf()
  private var defaultAudioDevice: AudioDevice = AudioDevice.EARPIECE
  private var userSelectedAudioDevice: AudioDevice = AudioDevice.NONE
  private var previousBluetoothState: SignalBluetoothManager.State? = null

  private var savedIsSpeakerPhoneOn = false
  private var hasWiredHeadset = false
  private var autoSwitchToWiredHeadset = true
  private var autoSwitchToBluetooth = true

  private var wiredHeadsetReceiver: WiredHeadsetReceiver? = null

  override fun initialize() {
    Log.i(TAG, "initialize(): state: $state")

    if (state == State.UNINITIALIZED) {
      savedAudioMode = androidAudioManager.mode
      requestedMode = savedAudioMode
      appliedMode = savedAudioMode
      savedIsSpeakerPhoneOn = androidAudioManager.isSpeakerphoneOn
      savedIsMicrophoneMute = androidAudioManager.isMicrophoneMute
      hasWiredHeadset = androidAudioManager.isWiredHeadsetOn

      requestCallAudioFocus("initialize")

      setMicrophoneMute(false)

      audioDevices.clear()

      signalBluetoothManager.start()

      onAudioDeviceUpdated()

      wiredHeadsetReceiver = WiredHeadsetReceiver()
      context.registerReceiver(wiredHeadsetReceiver, IntentFilter(AudioManager.ACTION_HEADSET_PLUG))

      state = State.PREINITIALIZED
    }
  }

  override fun start() {
    Log.i(TAG, "start(): state: $state previousMode: ${getModeName(requestedMode)} appliedMode: ${getModeName(appliedMode)} prepared: $preparedForAccept")
    if (state == State.RUNNING) {
      Log.w(TAG, "start(): skipping, already active")
      return
    }

    incomingRinger.stop()
    outgoingRinger.stop()
    requestCallAudioFocus("start")

    setMode(AudioManager.MODE_IN_COMMUNICATION, "start")

    state = State.RUNNING

    val volume: Float = androidAudioManager.ringVolumeWithMinimum()
    soundPool.play(connectedSoundId, volume, volume, 0, 0, 1.0f)
  }

  override fun stop(playDisconnect: Boolean) {
    Log.i(TAG, "stop(): playDisconnect: $playDisconnect state: $state currentMode: ${getModeName(requestedMode)}")

    incomingRinger.stop()
    outgoingRinger.stop()

    if (playDisconnect && state != State.UNINITIALIZED) {
      val volume: Float = androidAudioManager.ringVolumeWithMinimum()
      soundPool.play(disconnectedSoundId, volume, volume, 0, 0, 1.0f)
    }

    state = State.UNINITIALIZED
    resetAcceptState()
    appliedMode = AudioManager.MODE_INVALID

    context.safeUnregisterReceiver(wiredHeadsetReceiver)
    wiredHeadsetReceiver = null

    signalBluetoothManager.stop()

    setSpeakerphoneOn(savedIsSpeakerPhoneOn)
    setMicrophoneMute(savedIsMicrophoneMute)
    setMode(savedAudioMode, "stop")

    androidAudioManager.abandonCallAudioFocus()

    Log.i(TAG, "stop(): complete")
  }

  override fun onAudioDeviceUpdated() {
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
      (userSelectedAudioDevice == AudioDevice.NONE || userSelectedAudioDevice == AudioDevice.BLUETOOTH || autoSwitchToBluetooth) &&
      !androidAudioManager.isBluetoothScoOn

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
    }

    // Always notify listener to clear any pending audio device change state,
    // even if the device didn't actually change
    eventListener?.onAudioDeviceChanged(selectedAudioDevice, audioDevices)
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
    onAudioDeviceUpdated()
  }

  override fun selectAudioDevice(recipientId: RecipientId?, device: Int, isId: Boolean) {
    if (isId) {
      throw IllegalArgumentException("Passing audio device address $device to legacy audio manager")
    }
    val mappedDevice = AudioDevice.entries[device]
    val actualDevice: AudioDevice = if (mappedDevice == AudioDevice.EARPIECE && audioDevices.contains(AudioDevice.WIRED_HEADSET)) AudioDevice.WIRED_HEADSET else mappedDevice

    Log.d(TAG, "selectAudioDevice(): device: $device actualDevice: $actualDevice")
    if (!audioDevices.contains(actualDevice)) {
      Log.w(TAG, "Can not select $actualDevice from available $audioDevices")
    }
    userSelectedAudioDevice = actualDevice
    onAudioDeviceUpdated()
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

  private fun onWiredHeadsetChange(pluggedIn: Boolean, hasMic: Boolean) {
    Log.i(TAG, "onWiredHeadsetChange state: $state plug: $pluggedIn mic: $hasMic")
    hasWiredHeadset = pluggedIn
    onAudioDeviceUpdated()
  }

  private inner class WiredHeadsetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val pluggedIn = intent.getIntExtra("state", 0) == 1
      val hasMic = intent.getIntExtra("microphone", 0) == 1

      handler.post { onWiredHeadsetChange(pluggedIn, hasMic) }
    }
  }
}
