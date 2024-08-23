package org.thoughtcrime.securesms.components.webrtc

import android.content.Context
import android.content.DialogInterface
import android.media.AudioDeviceInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.fragment.app.FragmentActivity
import kotlinx.collections.immutable.toImmutableList
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.webrtc.audio.AudioDeviceMapping
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager

/**
 * This launches the bottom sheet on Android 12+ devices for selecting which audio device to use during a call.
 * In cases where there are fewer than the provided threshold number of devices, it will cycle through them without presenting a bottom sheet.
 */
@RequiresApi(31)
class WebRtcAudioPicker31(private val audioOutputChangedListener: OnAudioOutputChangedListener, private val outputState: ToggleButtonOutputState, private val stateUpdater: AudioStateUpdater) {

  companion object {
    const val TAG = "WebRtcAudioPicker31"
  }

  fun showPicker(fragmentActivity: FragmentActivity, threshold: Int, onDismiss: (DialogInterface) -> Unit): DialogInterface? {
    val am = AppDependencies.androidCallAudioManager
    if (am.availableCommunicationDevices.isEmpty()) {
      Toast.makeText(fragmentActivity, R.string.WebRtcAudioOutputToggleButton_no_eligible_audio_i_o_detected, Toast.LENGTH_LONG).show()
      return null
    }

    val devices: List<AudioOutputOption> = am.availableCommunicationDevices.map { AudioOutputOption(it.toFriendlyName(fragmentActivity).toString(), AudioDeviceMapping.fromPlatformType(it.type), it.id) }.distinctBy { it.deviceType.name }.filterNot { it.deviceType == SignalAudioManager.AudioDevice.NONE }
    val currentDeviceId = am.communicationDevice?.id ?: -1
    if (devices.size < threshold) {
      Log.d(TAG, "Only found $devices devices, not showing picker.")
      if (devices.isEmpty()) return null

      val index = devices.indexOfFirst { it.deviceId == currentDeviceId }
      if (index == -1) return null

      onAudioDeviceSelected(devices[(index + 1) % devices.size])
      return null
    } else {
      Log.d(TAG, "Found $devices devices, showing picker.")
      return WebRtcAudioOutputBottomSheet.show(fragmentActivity.supportFragmentManager, devices, currentDeviceId, onAudioDeviceSelected, onDismiss)
    }
  }

  @Composable
  fun Picker(threshold: Int) {
    val context = LocalContext.current

    val am = AppDependencies.androidCallAudioManager
    if (am.availableCommunicationDevices.isEmpty()) {
      Toast.makeText(context, R.string.WebRtcAudioOutputToggleButton_no_eligible_audio_i_o_detected, Toast.LENGTH_LONG).show()
      return
    }

    val devices: List<AudioOutputOption> = am.availableCommunicationDevices.map { AudioOutputOption(it.toFriendlyName(context).toString(), AudioDeviceMapping.fromPlatformType(it.type), it.id) }.distinctBy { it.deviceType.name }.filterNot { it.deviceType == SignalAudioManager.AudioDevice.NONE }
    val currentDeviceId = am.communicationDevice?.id ?: -1
    if (devices.size < threshold) {
      Log.d(TAG, "Only found $devices devices, not showing picker.")
      if (devices.isEmpty()) return

      val index = devices.indexOfFirst { it.deviceId == currentDeviceId }
      if (index == -1) return

      onAudioDeviceSelected(devices[(index + 1) % devices.size])
      return
    } else {
      Log.d(TAG, "Found $devices devices, showing picker.")
      DeviceList(
        audioOutputOptions = devices.toImmutableList(),
        initialDeviceId = currentDeviceId,
        onDeviceSelected = onAudioDeviceSelected,
        modifier = Modifier.padding(
          horizontal = dimensionResource(id = R.dimen.core_ui__gutter)
        )
      )
    }
  }

  @RequiresApi(31)
  val onAudioDeviceSelected: (AudioOutputOption) -> Unit = {
    Log.d(TAG, "User selected audio device of type ${it.deviceType}")
    audioOutputChangedListener.audioOutputChanged(WebRtcAudioDevice(it.toWebRtcAudioOutput(), it.deviceId))

    when (it.deviceType) {
      SignalAudioManager.AudioDevice.WIRED_HEADSET -> {
        outputState.isWiredHeadsetAvailable = true
        stateUpdater.updateAudioOutputState(WebRtcAudioOutput.WIRED_HEADSET)
      }

      SignalAudioManager.AudioDevice.EARPIECE -> {
        outputState.isEarpieceAvailable = true
        stateUpdater.updateAudioOutputState(WebRtcAudioOutput.HANDSET)
      }

      SignalAudioManager.AudioDevice.BLUETOOTH -> {
        outputState.isBluetoothHeadsetAvailable = true
        stateUpdater.updateAudioOutputState(WebRtcAudioOutput.BLUETOOTH_HEADSET)
      }

      SignalAudioManager.AudioDevice.SPEAKER_PHONE, SignalAudioManager.AudioDevice.NONE -> stateUpdater.updateAudioOutputState(WebRtcAudioOutput.SPEAKER)
    }
  }

  private fun AudioDeviceInfo.toFriendlyName(context: Context): CharSequence {
    return when (this.type) {
      AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> context.getString(R.string.WebRtcAudioOutputToggle__phone_earpiece)
      AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> context.getString(R.string.WebRtcAudioOutputToggle__speaker)
      AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> context.getString(R.string.WebRtcAudioOutputToggle__wired_headphones)
      AudioDeviceInfo.TYPE_WIRED_HEADSET -> context.getString(R.string.WebRtcAudioOutputToggle__wired_headset)
      AudioDeviceInfo.TYPE_USB_HEADSET -> context.getString(R.string.WebRtcAudioOutputToggle__wired_headset_usb)
      else -> this.productName
    }
  }

  private fun AudioOutputOption.toWebRtcAudioOutput(): WebRtcAudioOutput {
    return when (this.deviceType) {
      SignalAudioManager.AudioDevice.WIRED_HEADSET -> WebRtcAudioOutput.WIRED_HEADSET
      SignalAudioManager.AudioDevice.EARPIECE -> WebRtcAudioOutput.HANDSET
      SignalAudioManager.AudioDevice.BLUETOOTH -> WebRtcAudioOutput.BLUETOOTH_HEADSET
      SignalAudioManager.AudioDevice.SPEAKER_PHONE, SignalAudioManager.AudioDevice.NONE -> WebRtcAudioOutput.SPEAKER
    }
  }
}
