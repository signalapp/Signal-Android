package org.thoughtcrime.securesms.webrtc.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.HandlerThread
import network.loki.messenger.R
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.webrtc.AudioManagerCommand
import org.thoughtcrime.securesms.webrtc.audio.SignalBluetoothManager.State as BState

private val TAG = Log.tag(SignalAudioManager::class.java)

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
class SignalAudioManager(private val context: Context,
                         private val eventListener: EventListener?,
                         private val androidAudioManager: AudioManagerCompat) {

    private var commandAndControlThread: HandlerThread? = HandlerThread("call-audio").apply { start() }
    private var handler: SignalAudioHandler? = null

    private var signalBluetoothManager: SignalBluetoothManager? = null

    private var state: State = State.UNINITIALIZED

    private var savedAudioMode = AudioManager.MODE_INVALID
    private var savedIsSpeakerPhoneOn = false
    private var savedIsMicrophoneMute = false
    private var hasWiredHeadset = false
    private var autoSwitchToWiredHeadset = true
    private var autoSwitchToBluetooth = true

    private var defaultAudioDevice: AudioDevice = AudioDevice.EARPIECE
    private var selectedAudioDevice: AudioDevice = AudioDevice.NONE
    private var userSelectedAudioDevice: AudioDevice = AudioDevice.NONE

    private var audioDevices: MutableSet<AudioDevice> = mutableSetOf()

    private val soundPool: SoundPool = androidAudioManager.createSoundPool()
    private val connectedSoundId = soundPool.load(context, R.raw.webrtc_completed, 1)
    private val disconnectedSoundId = soundPool.load(context, R.raw.webrtc_disconnected, 1)

    private val incomingRinger = IncomingRinger(context)
    private val outgoingRinger = OutgoingRinger(context)

    private var wiredHeadsetReceiver: WiredHeadsetReceiver? = null

    fun handleCommand(command: AudioManagerCommand) {
        if (command == AudioManagerCommand.Initialize) {
            initialize()
            return
        }
        handler?.post {
            when (command) {
                is AudioManagerCommand.UpdateAudioDeviceState -> updateAudioDeviceState()
                is AudioManagerCommand.Start -> start()
                is AudioManagerCommand.Stop -> stop(command.playDisconnect)
                is AudioManagerCommand.SetDefaultDevice -> setDefaultAudioDevice(command.device, command.clearUserEarpieceSelection)
                is AudioManagerCommand.SetUserDevice -> selectAudioDevice(command.device)
                is AudioManagerCommand.StartIncomingRinger -> startIncomingRinger(command.vibrate)
                is AudioManagerCommand.SilenceIncomingRinger -> silenceIncomingRinger()
                is AudioManagerCommand.StartOutgoingRinger -> startOutgoingRinger(command.type)
            }
        }
    }

    private fun initialize() {
        Log.i(TAG, "Initializing audio manager state: $state")

        if (state == State.UNINITIALIZED) {
            commandAndControlThread = HandlerThread("call-audio").apply { start() }
            handler = SignalAudioHandler(commandAndControlThread!!.looper)

            signalBluetoothManager = SignalBluetoothManager(context, this, androidAudioManager, handler!!)

            handler!!.post {

                savedAudioMode = androidAudioManager.mode
                savedIsSpeakerPhoneOn = androidAudioManager.isSpeakerphoneOn
                savedIsMicrophoneMute = androidAudioManager.isMicrophoneMute
                hasWiredHeadset = androidAudioManager.isWiredHeadsetOn

                androidAudioManager.requestCallAudioFocus()

                setMicrophoneMute(false)

                audioDevices.clear()

                signalBluetoothManager!!.start()

                updateAudioDeviceState()

                wiredHeadsetReceiver = WiredHeadsetReceiver()
                context.registerReceiver(wiredHeadsetReceiver, IntentFilter(if (Build.VERSION.SDK_INT >= 21) AudioManager.ACTION_HEADSET_PLUG else Intent.ACTION_HEADSET_PLUG))

                state = State.PREINITIALIZED

                Log.d(TAG, "Initialized")
            }
        }
    }

    private fun start() {
        Log.d(TAG, "Starting. state: $state")
        if (state == State.RUNNING) {
            Log.w(TAG, "Skipping, already active")
            return
        }

        incomingRinger.stop()
        outgoingRinger.stop()

        state = State.RUNNING

        androidAudioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        val volume: Float = androidAudioManager.ringVolumeWithMinimum()
        soundPool.play(connectedSoundId, volume, volume, 0, 0, 1.0f)

        Log.d(TAG, "Started")
    }

    private fun stop(playDisconnect: Boolean) {
        Log.d(TAG, "Stopping. state: $state")
        if (state == State.UNINITIALIZED) {
            Log.i(TAG, "Trying to stop AudioManager in incorrect state: $state")
            return
        }

        handler?.post {
            incomingRinger.stop()
            outgoingRinger.stop()
            stop(false)
            if (commandAndControlThread != null) {
                Log.i(TAG, "Shutting down command and control")
                commandAndControlThread?.quitSafely()
                commandAndControlThread = null
            }
        }

        if (playDisconnect) {
            val volume: Float = androidAudioManager.ringVolumeWithMinimum()
            soundPool.play(disconnectedSoundId, volume, volume, 0, 0, 1.0f)
        }

        state = State.UNINITIALIZED

        wiredHeadsetReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e(TAG, "error unregistering wiredHeadsetReceiver", e)
            }
        }
        wiredHeadsetReceiver = null

        signalBluetoothManager?.stop()

        setSpeakerphoneOn(savedIsSpeakerPhoneOn)
        setMicrophoneMute(savedIsMicrophoneMute)
        androidAudioManager.mode = savedAudioMode

        androidAudioManager.abandonCallAudioFocus()
        Log.d(TAG, "Abandoned audio focus for VOICE_CALL streams")

        Log.d(TAG, "Stopped")
    }

    private fun updateAudioDeviceState() {
        handler!!.assertHandlerThread()

        Log.i(
                TAG,
                "updateAudioDeviceState(): " +
                        "wired: $hasWiredHeadset " +
                        "bt: ${signalBluetoothManager!!.state} " +
                        "available: $audioDevices " +
                        "selected: $selectedAudioDevice " +
                        "userSelected: $userSelectedAudioDevice"
        )

        if (signalBluetoothManager!!.state.shouldUpdate()) {
            signalBluetoothManager!!.updateDevice()
        }

        val newAudioDevices = mutableSetOf(AudioDevice.SPEAKER_PHONE)

        if (signalBluetoothManager!!.state.hasDevice()) {
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

        if (signalBluetoothManager!!.state == BState.UNAVAILABLE && userSelectedAudioDevice == AudioDevice.BLUETOOTH) {
            userSelectedAudioDevice = AudioDevice.NONE
        }

        if (hasWiredHeadset && autoSwitchToWiredHeadset) {
            userSelectedAudioDevice = AudioDevice.WIRED_HEADSET
            autoSwitchToWiredHeadset = false
        }

        if (!hasWiredHeadset && userSelectedAudioDevice == AudioDevice.WIRED_HEADSET) {
            userSelectedAudioDevice = AudioDevice.NONE
        }

        val btState = signalBluetoothManager!!.state
        val needBluetoothAudioStart = btState == BState.AVAILABLE &&
                (userSelectedAudioDevice == AudioDevice.NONE || userSelectedAudioDevice == AudioDevice.BLUETOOTH || autoSwitchToBluetooth)

        val needBluetoothAudioStop = (btState == BState.CONNECTED || btState == BState.CONNECTING) &&
                (userSelectedAudioDevice != AudioDevice.NONE && userSelectedAudioDevice != AudioDevice.BLUETOOTH)

        if (btState.hasDevice()) {
            Log.i(TAG, "Need bluetooth audio: state: ${signalBluetoothManager!!.state} start: $needBluetoothAudioStart stop: $needBluetoothAudioStop")
        }

        if (needBluetoothAudioStop) {
            signalBluetoothManager!!.stopScoAudio()
            signalBluetoothManager!!.updateDevice()
        }

        if (!autoSwitchToBluetooth && signalBluetoothManager!!.state == BState.UNAVAILABLE) {
            autoSwitchToBluetooth = true
        }

        if (needBluetoothAudioStart && !needBluetoothAudioStop) {
            if (!signalBluetoothManager!!.startScoAudio()) {
                Log.e(TAG,"Failed to start sco audio")
                audioDevices.remove(AudioDevice.BLUETOOTH)
                audioDeviceSetUpdated = true
            }
        }

        if (autoSwitchToBluetooth && signalBluetoothManager!!.state == BState.CONNECTED) {
            userSelectedAudioDevice = AudioDevice.BLUETOOTH
            autoSwitchToBluetooth = false
        }

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

    private fun setDefaultAudioDevice(newDefaultDevice: AudioDevice, clearUserEarpieceSelection: Boolean) {
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

    private fun selectAudioDevice(device: AudioDevice) {
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
        if (!audioDevices.contains(device)) return
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

    private fun startIncomingRinger(vibrate: Boolean) {
        Log.i(TAG, "startIncomingRinger(): vibrate: $vibrate")
        androidAudioManager.mode = AudioManager.MODE_RINGTONE

        incomingRinger.start(vibrate)
    }

    private fun silenceIncomingRinger() {
        Log.i(TAG, "silenceIncomingRinger():")
        incomingRinger.stop()
    }

    private fun startOutgoingRinger(type: OutgoingRinger.Type) {
        Log.i(TAG, "startOutgoingRinger(): currentDevice: $selectedAudioDevice")

        androidAudioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setMicrophoneMute(false)

        outgoingRinger.start(type)
    }

    private fun onWiredHeadsetChange(pluggedIn: Boolean, hasMic: Boolean) {
        Log.i(TAG, "onWiredHeadsetChange state: $state plug: $pluggedIn mic: $hasMic")
        hasWiredHeadset = pluggedIn
        updateAudioDeviceState()
    }

    fun isSpeakerphoneOn(): Boolean = androidAudioManager.isSpeakerphoneOn

    fun isBluetoothScoOn(): Boolean = androidAudioManager.isBluetoothScoOn

    fun isWiredHeadsetOn(): Boolean = androidAudioManager.isWiredHeadsetOn

    private inner class WiredHeadsetReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pluggedIn = intent.getIntExtra("state", 0) == 1
            val hasMic = intent.getIntExtra("microphone", 0) == 1

            handler?.post { onWiredHeadsetChange(pluggedIn, hasMic) }
        }
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
    }
}
