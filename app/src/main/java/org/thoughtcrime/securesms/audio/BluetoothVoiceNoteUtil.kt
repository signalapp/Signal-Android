/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioHandler

internal const val TAG = "BluetoothVoiceNoteUtil"

sealed interface BluetoothVoiceNoteUtil {
  fun connectBluetoothScoConnection()
  fun disconnectBluetoothScoConnection()
  fun destroy()

  companion object {
    fun create(context: Context, listener: (Boolean) -> Unit, bluetoothPermissionDeniedHandler: () -> Unit): BluetoothVoiceNoteUtil {
      return if (Build.VERSION.SDK_INT >= 31) BluetoothVoiceNoteUtil31(listener) else BluetoothVoiceNoteUtilLegacy(context, listener, bluetoothPermissionDeniedHandler)
    }
  }
}

@RequiresApi(31)
private class BluetoothVoiceNoteUtil31(val listener: (Boolean) -> Unit) : BluetoothVoiceNoteUtil {
  override fun connectBluetoothScoConnection() {
    val audioManager = AppDependencies.androidCallAudioManager
    val device: AudioDeviceInfo? = audioManager.connectedBluetoothDevice
    if (device != null) {
      val result: Boolean = audioManager.setCommunicationDevice(device)
      if (result) {
        Log.d(TAG, "Successfully set Bluetooth device as active communication device.")
        listener(true)
      } else {
        Log.d(TAG, "Found Bluetooth device but failed to set it as active communication device.")
        listener(false)
      }
    } else {
      Log.d(TAG, "Could not find Bluetooth device in list of communications devices, falling back to current input.")
      listener(false)
    }
  }

  override fun disconnectBluetoothScoConnection() {
    Log.d(TAG, "Clearing call manager communication device.")
    AppDependencies.androidCallAudioManager.clearCommunicationDevice()
  }

  override fun destroy() = Unit
}

/**
 * Encapsulated logic for managing a Bluetooth connection withing the Fragment lifecycle for voice notes.
 *
 * @param context Context with reference to the main thread.
 * @param listener This will be executed on the main thread after the Bluetooth connection connects, or if it doesn't.
 * @param bluetoothPermissionDeniedHandler called when we detect the Bluetooth permission has been denied to our app.
 */
private class BluetoothVoiceNoteUtilLegacy(val context: Context, val listener: (Boolean) -> Unit, val bluetoothPermissionDeniedHandler: () -> Unit) : BluetoothVoiceNoteUtil {
  private val commandAndControlThread: HandlerThread = SignalExecutors.getAndStartHandlerThread("voice-note-audio", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD)
  private val uiThreadHandler = Handler(context.mainLooper)
  private val audioHandler: SignalAudioHandler = SignalAudioHandler(commandAndControlThread.looper)
  private val deviceUpdatedListener: AudioDeviceUpdatedListener = object : AudioDeviceUpdatedListener {
    override fun onAudioDeviceUpdated() {
      when (signalBluetoothManager.state) {
        SignalBluetoothManager.State.CONNECTED -> {
          Log.d(TAG, "Bluetooth SCO connected. Starting voice note recording on UI thread.")
          uiThreadHandler.post { listener(true) }
        }
        SignalBluetoothManager.State.ERROR,
        SignalBluetoothManager.State.PERMISSION_DENIED -> {
          Log.w(TAG, "Unable to complete Bluetooth connection due to ${signalBluetoothManager.state}. Starting voice note recording anyway on UI thread.")
          uiThreadHandler.post { listener(false) }
        }
        else -> Log.d(TAG, "Current Bluetooth connection state: ${signalBluetoothManager.state}.")
      }
    }
  }
  private val signalBluetoothManager: SignalBluetoothManager = SignalBluetoothManager(context, deviceUpdatedListener, audioHandler)

  private var hasWarnedAboutBluetooth = false

  init {
    audioHandler.post {
      signalBluetoothManager.start()
      Log.d(TAG, "Bluetooth manager started.")
    }
  }

  override fun connectBluetoothScoConnection() {
    audioHandler.post {
      if (signalBluetoothManager.state.shouldUpdate()) {
        Log.d(TAG, "Bluetooth manager updating devices.")
        signalBluetoothManager.updateDevice()
      }
      val currentState = signalBluetoothManager.state
      if (currentState == SignalBluetoothManager.State.AVAILABLE) {
        Log.d(TAG, "Bluetooth manager state is AVAILABLE. Starting SCO connection.")
        signalBluetoothManager.startScoAudio()
      } else {
        Log.d(TAG, "Recording from phone mic because bluetooth state was " + currentState + ", not " + SignalBluetoothManager.State.AVAILABLE)
        uiThreadHandler.post {
          if (currentState == SignalBluetoothManager.State.PERMISSION_DENIED && !hasWarnedAboutBluetooth) {
            Log.d(TAG, "Warning about Bluetooth permissions.")
            bluetoothPermissionDeniedHandler()
            hasWarnedAboutBluetooth = true
          }
          listener(false)
        }
      }
    }
  }

  override fun disconnectBluetoothScoConnection() {
    audioHandler.post {
      if (signalBluetoothManager.state == SignalBluetoothManager.State.CONNECTED) {
        signalBluetoothManager.stopScoAudio()
      }
    }
  }

  override fun destroy() {
    audioHandler.post {
      signalBluetoothManager.stop()
    }
  }
}
