package org.thoughtcrime.securesms.components.webrtc

import android.content.Context
import android.content.DialogInterface
import android.media.AudioDeviceInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.signal.core.ui.R as CoreUiR

/**
 * This launches the bottom sheet on Android 12+ devices for selecting which audio device to use during a call.
 * In cases where there are fewer than the provided threshold number of devices, it will cycle through them without presenting a bottom sheet.
 */
@RequiresApi(31)
class WebRtcAudioPicker31(private val audioOutputChangedListener: OnAudioOutputChangedListener, private val outputState: ToggleButtonOutputState, private val stateUpdater: AudioStateUpdater) {

  companion object {
    const val TAG = "WebRtcAudioPicker31"

    private fun WebRtcAudioOutput.toSignalAudioDevice(): SignalAudioManager.AudioDevice {
      return when (this) {
        WebRtcAudioOutput.HANDSET -> SignalAudioManager.AudioDevice.EARPIECE
        WebRtcAudioOutput.SPEAKER -> SignalAudioManager.AudioDevice.SPEAKER_PHONE
        WebRtcAudioOutput.BLUETOOTH_HEADSET -> SignalAudioManager.AudioDevice.BLUETOOTH
        WebRtcAudioOutput.WIRED_HEADSET -> SignalAudioManager.AudioDevice.WIRED_HEADSET
      }
    }
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
      LaunchedEffect(Unit) {
        Toast.makeText(context, R.string.WebRtcAudioOutputToggleButton_no_eligible_audio_i_o_detected, Toast.LENGTH_LONG).show()
        stateUpdater.hidePicker()
      }
      return
    }

    val devices: List<AudioOutputOption> = am.availableCommunicationDevices.map { AudioOutputOption(it.toFriendlyName(context).toString(), AudioDeviceMapping.fromPlatformType(it.type), it.id) }.distinctBy { it.deviceType.name }.filterNot { it.deviceType == SignalAudioManager.AudioDevice.NONE }
    val currentDeviceId = am.communicationDevice?.id ?: -1
    if (devices.size < threshold) {
      LaunchedEffect(Unit) {
        Log.d(TAG, "Only found $devices devices, not showing picker.")
        cycleToNextDevice()
      }
      return
    } else {
      Log.d(TAG, "Found $devices devices, showing picker.")
      DeviceList(
        audioOutputOptions = devices.toImmutableList(),
        initialDeviceId = currentDeviceId,
        onDeviceSelected = onAudioDeviceSelected,
        modifier = Modifier.padding(
          horizontal = dimensionResource(id = CoreUiR.dimen.gutter)
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

  /**
   * Cycles to the next audio device without showing a picker.
   * Uses the system device list to resolve the actual device ID, falling back to
   * type-based lookup from app-tracked state when the current communication device is unknown.
   */
  fun cycleToNextDevice() {
    val am = AppDependencies.androidCallAudioManager
    val devices: List<AudioOutputOption> = am.availableCommunicationDevices
      .map { AudioOutputOption("", AudioDeviceMapping.fromPlatformType(it.type), it.id) }
      .distinctBy { it.deviceType.name }
      .filterNot { it.deviceType == SignalAudioManager.AudioDevice.NONE }

    if (devices.isEmpty()) {
      Log.w(TAG, "cycleToNextDevice: no available communication devices")
      return
    }

    val currentDeviceId = am.communicationDevice?.id ?: -1
    val index = devices.indexOfFirst { it.deviceId == currentDeviceId }

    if (index != -1) {
      onAudioDeviceSelected(devices[(index + 1) % devices.size])
    } else {
      val nextOutput = outputState.peekNext()
      val targetDeviceType = nextOutput.toSignalAudioDevice()
      val targetDevice = devices.firstOrNull { it.deviceType == targetDeviceType } ?: devices.first()
      Log.d(TAG, "cycleToNextDevice: communicationDevice unknown, selecting ${targetDevice.deviceType} by type")
      onAudioDeviceSelected(targetDevice)
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
