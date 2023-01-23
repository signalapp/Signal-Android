package org.thoughtcrime.securesms.webrtc.audio

import android.media.AudioDeviceInfo
import androidx.annotation.RequiresApi

@RequiresApi(31)
object AudioDeviceMapping {

  private val systemDeviceTypeMap: Map<SignalAudioManager.AudioDevice, List<Int>> = mapOf(
    SignalAudioManager.AudioDevice.BLUETOOTH to listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_HEARING_AID),
    SignalAudioManager.AudioDevice.EARPIECE to listOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE),
    SignalAudioManager.AudioDevice.SPEAKER_PHONE to listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE),
    SignalAudioManager.AudioDevice.WIRED_HEADSET to listOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET),
    SignalAudioManager.AudioDevice.NONE to emptyList()
  )

  @JvmStatic
  fun getEquivalentPlatformTypes(audioDevice: SignalAudioManager.AudioDevice): List<Int> {
    return systemDeviceTypeMap[audioDevice]!!
  }

  @JvmStatic
  fun fromPlatformType(type: Int): SignalAudioManager.AudioDevice {
    for (kind in SignalAudioManager.AudioDevice.values()) {
      if (getEquivalentPlatformTypes(kind).contains(type)) return kind
    }
    return SignalAudioManager.AudioDevice.NONE
  }
}
