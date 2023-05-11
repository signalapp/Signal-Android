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
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioHandler

internal const val TAG = "BluetoothVoiceNoteUtil"

sealed interface BluetoothVoiceNoteUtil {
  fun connectBluetoothScoConnection()
  fun disconnectBluetoothScoConnection()
  fun destroy()

  companion object {
    fun create(context: Context, listener: () -> Unit, bluetoothPermissionDeniedHandler: () -> Unit): BluetoothVoiceNoteUtil {
      return if (Build.VERSION.SDK_INT >= 31) BluetoothVoiceNoteUtil31(listener) else BluetoothVoiceNoteUtilLegacy(context, listener, bluetoothPermissionDeniedHandler)
    }
  }
}

@RequiresApi(31)
private class BluetoothVoiceNoteUtil31(val listener: () -> Unit) : BluetoothVoiceNoteUtil {
  override fun connectBluetoothScoConnection() {
    val audioManager = ApplicationDependencies.getAndroidCallAudioManager()
    val device: AudioDeviceInfo? = audioManager.connectedBluetoothDevice
    if (device != null) {
      val result: Boolean = audioManager.setCommunicationDevice(device)
      if (result) {
        Log.d(TAG, "Successfully set Bluetooth device as active communication device.")
      } else {
        Log.d(TAG, "Found Bluetooth device but failed to set it as active communication device.")
      }
    } else {
      Log.d(TAG, "Could not find Bluetooth device in list of communications devices, falling back to current input.")
    }
    listener()
  }

  override fun disconnectBluetoothScoConnection() = Unit

  override fun destroy() = Unit
}

/**
 * Encapsulated logic for managing a Bluetooth connection withing the Fragment lifecycle for voice notes.
 *
 * @param context Context with reference to the main thread.
 * @param listener This will be executed on the main thread after the Bluetooth connection connects, or if it doesn't.
 * @param bluetoothPermissionDeniedHandler called when we detect the Bluetooth permission has been denied to our app.
 */
private class BluetoothVoiceNoteUtilLegacy(val context: Context, val listener: () -> Unit, val bluetoothPermissionDeniedHandler: () -> Unit) : BluetoothVoiceNoteUtil {
  private val commandAndControlThread: HandlerThread = SignalExecutors.getAndStartHandlerThread("voice-note-audio", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD)
  private val uiThreadHandler = Handler(context.mainLooper)
  private val audioHandler: SignalAudioHandler = SignalAudioHandler(commandAndControlThread.looper)
  private val deviceUpdatedListener: AudioDeviceUpdatedListener = object : AudioDeviceUpdatedListener {
    override fun onAudioDeviceUpdated() {
      if (signalBluetoothManager.state == SignalBluetoothManager.State.CONNECTED) {
        Log.d(TAG, "Bluetooth SCO connected. Starting voice note recording on UI thread.")
        uiThreadHandler.post { listener() }
      }
    }
  }
  private val signalBluetoothManager: SignalBluetoothManager = SignalBluetoothManager(context, deviceUpdatedListener, audioHandler)

  private var hasWarnedAboutBluetooth = false

  init {
    if (Build.VERSION.SDK_INT < 31) {
      audioHandler.post {
        signalBluetoothManager.start()
        Log.d(TAG, "Bluetooth manager started.")
      }
    }
  }

  override fun connectBluetoothScoConnection() {
    if (Build.VERSION.SDK_INT >= 31) {
      val audioManager = ApplicationDependencies.getAndroidCallAudioManager()
      val device: AudioDeviceInfo? = audioManager.connectedBluetoothDevice
      if (device != null) {
        val result: Boolean = audioManager.setCommunicationDevice(device)
        if (result) {
          Log.d(TAG, "Successfully set Bluetooth device as active communication device.")
        } else {
          Log.d(TAG, "Found Bluetooth device but failed to set it as active communication device.")
        }
      } else {
        Log.d(TAG, "Could not find Bluetooth device in list of communications devices, falling back to current input.")
      }
      listener()
    } else {
      audioHandler.post {
        if (signalBluetoothManager.state.shouldUpdate()) {
          signalBluetoothManager.updateDevice()
        }
        val currentState = signalBluetoothManager.state
        if (currentState == SignalBluetoothManager.State.AVAILABLE) {
          signalBluetoothManager.startScoAudio()
        } else {
          Log.d(TAG, "Recording from phone mic because bluetooth state was " + currentState + ", not " + SignalBluetoothManager.State.AVAILABLE)
          uiThreadHandler.post {
            if (currentState == SignalBluetoothManager.State.PERMISSION_DENIED && !hasWarnedAboutBluetooth) {
              bluetoothPermissionDeniedHandler()
              hasWarnedAboutBluetooth = true
            }
            listener()
          }
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
