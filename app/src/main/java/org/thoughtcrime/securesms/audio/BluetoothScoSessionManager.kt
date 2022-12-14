package org.thoughtcrime.securesms.audio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.ParcelFileDescriptor
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.ServiceUtil
import java.io.IOException

/**
 * This class manages the SCO (Synchronous Connection Oriented) Bluetooth link for voice memos.
 * A consumer of this class should first check if the hardware is prepared to receive input from a bluetooth device using [isBluetoothScoCapable]
 * Then they can use [startBluetooth] to initiate a session.
 * We send initialize an SCO link and receive its state updates as a system Broadcast.
 * Once the connection is established, we start storing audio via the provided [Recorder]
 * It is the responsibility of the owner of this object to close the Bluetooth link when recording is finished.
 *
 * Note: in testing, closing the SCO link does not interrupt an in-progress recording, and a user is free to continue recording on the device's mic.
 */
class BluetoothScoSessionManager(val context: Context) : BroadcastReceiver() {
  private val audioManager: AudioManager = ServiceUtil.getAudioManager(context)
  private var bluetoothSessionAlive: Boolean = false
  private var callback: Recorder? = null
  private var fileDescriptor: ParcelFileDescriptor? = null

  private fun register() {
    Log.d(TAG, "Registering Bluetooth SCO broadcast receiver.")
    val filter = IntentFilter()
    filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
    context.registerReceiver(this, filter)
  }

  private fun unregister() {
    Log.d(TAG, "Unregistering Bluetooth SCO broadcast receiver.")
    context.unregisterReceiver(this)
  }

  fun isBluetoothScoCapable(): Boolean {
    if (!audioManager.isBluetoothScoAvailableOffCall) {
      return false
    }

    return if (Build.VERSION.SDK_INT >= 31) {
      audioManager.availableCommunicationDevices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    } else if (Build.VERSION.SDK_INT >= 23) {
      audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    } else {
      hasBluetoothMicConnectedLegacy()
    }
  }

  @SuppressLint("MissingPermission")
  private fun hasBluetoothMicConnectedLegacy(): Boolean {
    val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled &&
      mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothHeadset.STATE_CONNECTED
  }

  fun startBluetooth(callback: Recorder, fileDescriptor: ParcelFileDescriptor) {
    Log.d(TAG, "Starting Bluetooth SCO for voice memo.")
    this.callback = callback
    this.fileDescriptor = fileDescriptor
    register()
    audioManager.startBluetoothSco()
  }

  fun stopBluetooth() {
    if (bluetoothSessionAlive) {
      Log.d(TAG, "Stopping Bluetooth SCO for voice memo.")
      bluetoothSessionAlive = false
      unregister()
      if (audioManager.isBluetoothScoOn) {
        audioManager.stopBluetoothSco()
      }
    }
  }

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    if (action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
      val state: Int = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
      when (state) {
        AudioManager.SCO_AUDIO_STATE_CONNECTED -> try {
          bluetoothSessionAlive = true
          callback?.start(fileDescriptor)
          Log.d(TAG, "Bluetooth SCO connected.")
        } catch (e: IOException) {
          Log.w(TAG, e)
        }
        AudioManager.SCO_AUDIO_STATE_CONNECTING -> Log.d(TAG, "Bluetooth SCO in connecting state.")
        AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
          Log.d(TAG, "Bluetooth SCO disconnected.")
          stopBluetooth()
        }
      }
    }
  }

  companion object {
    const val TAG = "BluetoothMicManager"
  }
}
