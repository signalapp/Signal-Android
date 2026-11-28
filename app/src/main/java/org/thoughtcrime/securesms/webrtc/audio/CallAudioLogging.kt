/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.webrtc.audio

import android.media.AudioDeviceInfo
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import androidx.annotation.RequiresApi
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Scrubber

internal object CallAudioLogging {

  @JvmStatic
  fun describeDevice(device: AudioDeviceInfo?): String {
    if (device == null) {
      return "null"
    }
    val productName = device.productName?.toString()?.takeIf { it.isNotBlank() }?.let { Scrubber.hashForLog(it) } ?: "unknown"
    return "${device.id}:${getDeviceTypeName(device.type)}:$productName"
  }

  fun getDeviceTypeName(type: Int): String {
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

  fun getAudioSourceName(source: Int): String {
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

  @RequiresApi(31)
  fun logActiveRecordingConfigurations(tag: String, event: String, configs: List<AudioRecordingConfiguration>, modeName: String) {
    if (configs.isEmpty()) {
      Log.w(tag, "$event: activeRecordingConfigurations: none mode: $modeName")
      return
    }
    for (config in configs) {
      Log.i(tag, "$event: activeRecordingConfiguration: silenced: ${config.isClientSilenced} source: ${getAudioSourceName(config.audioSource)} device: ${describeRecordingDevice(config)} mode: $modeName")
    }
  }

  @RequiresApi(31)
  fun describeRecordingDevice(config: AudioRecordingConfiguration): String {
    return config.audioDevice?.let { getDeviceTypeName(it.type) } ?: "null"
  }
}
