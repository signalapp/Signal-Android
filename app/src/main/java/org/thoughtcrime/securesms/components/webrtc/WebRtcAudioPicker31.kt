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
import org.signal.core.util.logging.Scrubber
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.service.webrtc.AndroidTelecomUtil
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

    private fun List<AudioOutputOption>.toLogString(): String {
      return joinToString(prefix = "[", postfix = "]") { "${it.deviceType}${Scrubber.hashForLog(it.friendlyName)}" }
    }

    private fun getDeviceList(context: Context, usesTelecom: Boolean): AvailableOutputs? {
      if (usesTelecom) {
        val telecomDevices = AndroidTelecomUtil.getAvailableAudioOutputOptions()
        if (telecomDevices != null) {
          return AvailableOutputs(telecomDevices, AndroidTelecomUtil.getCurrentEndpointDeviceId(), selectById = true)
        }
      }

      val am = AppDependencies.androidCallAudioManager
      val devices = am.availableCommunicationDevices
        .map { AudioOutputOption(it.toFriendlyName(context).toString(), AudioDeviceMapping.fromPlatformType(it.type), it.id) }
        .distinctBy { it.deviceType.name }
        .filterNot { it.deviceType == SignalAudioManager.AudioDevice.NONE }
      if (devices.isEmpty()) return null
      return AvailableOutputs(devices, am.communicationDevice?.id ?: -1, selectById = !usesTelecom)
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
  }

  fun showPicker(fragmentActivity: FragmentActivity, threshold: Int, onDismiss: (DialogInterface) -> Unit): DialogInterface? {
    val (devices, currentDeviceId, selectById) = getDeviceList(fragmentActivity, outputState.usesTelecom) ?: run {
      Toast.makeText(fragmentActivity, R.string.WebRtcAudioOutputToggleButton_no_eligible_audio_i_o_detected, Toast.LENGTH_LONG).show()
      return null
    }

    if (devices.size < threshold) {
      Log.d(TAG, "Only found ${devices.toLogString()} devices, not showing picker.")
      if (devices.isEmpty()) return null

      val index = devices.indexOfFirst { it.deviceId == currentDeviceId }
      if (index == -1) return null

      selectDevice(devices[(index + 1) % devices.size], selectById)
      return null
    } else {
      Log.d(TAG, "Found ${devices.toLogString()} devices, showing picker.")
      return WebRtcAudioOutputBottomSheet.show(fragmentActivity.supportFragmentManager, devices, currentDeviceId, { selectDevice(it, selectById) }, onDismiss)
    }
  }

  @Composable
  fun Picker(threshold: Int) {
    val context = LocalContext.current

    val deviceList = getDeviceList(context, outputState.usesTelecom)
    if (deviceList == null) {
      LaunchedEffect(Unit) {
        Toast.makeText(context, R.string.WebRtcAudioOutputToggleButton_no_eligible_audio_i_o_detected, Toast.LENGTH_LONG).show()
        stateUpdater.hidePicker()
      }
      return
    }

    val (devices, currentDeviceId, selectById) = deviceList
    if (devices.size < threshold) {
      LaunchedEffect(Unit) {
        Log.d(TAG, "Only found ${devices.toLogString()} devices, not showing picker.")
        cycleToNextDevice()
      }
      return
    } else {
      Log.d(TAG, "Found ${devices.toLogString()} devices, showing picker.")
      DeviceList(
        audioOutputOptions = devices.toImmutableList(),
        initialDeviceId = currentDeviceId,
        onDeviceSelected = { selectDevice(it, selectById) },
        modifier = Modifier.padding(
          horizontal = dimensionResource(id = CoreUiR.dimen.gutter)
        )
      )
    }
  }

  private fun selectDevice(option: AudioOutputOption, selectById: Boolean) {
    Log.d(TAG, "User selected audio device of type ${option.deviceType} selectById: $selectById")
    audioOutputChangedListener.audioOutputChanged(WebRtcAudioDevice(option.toWebRtcAudioOutput(), option.deviceId.takeIf { selectById }))

    when (option.deviceType) {
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

  /**
   * Cycles to the next audio device without showing a picker.
   * Uses the system device list to resolve the actual device ID, falling back to
   * type-based lookup from app-tracked state when the current communication device is unknown.
   */
  fun cycleToNextDevice() {
    val (devices, currentDeviceId, selectById) = getDeviceList(AppDependencies.application, outputState.usesTelecom) ?: run {
      Log.w(TAG, "cycleToNextDevice: no available communication devices")
      return
    }

    if (devices.isEmpty()) {
      Log.w(TAG, "cycleToNextDevice: no available communication devices")
      return
    }

    val index = devices.indexOfFirst { it.deviceId == currentDeviceId }

    if (index != -1) {
      selectDevice(devices[(index + 1) % devices.size], selectById)
    } else {
      val nextOutput = outputState.peekNext()
      val targetDeviceType = nextOutput.toSignalAudioDevice()
      val targetDevice = devices.firstOrNull { it.deviceType == targetDeviceType } ?: devices.first()
      Log.d(TAG, "cycleToNextDevice: communicationDevice unknown, selecting ${targetDevice.deviceType} by type")
      selectDevice(targetDevice, selectById)
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

  private data class AvailableOutputs(val devices: List<AudioOutputOption>, val currentDeviceId: Int, val selectById: Boolean)
}
