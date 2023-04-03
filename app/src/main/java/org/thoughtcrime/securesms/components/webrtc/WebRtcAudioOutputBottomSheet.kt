package org.thoughtcrime.securesms.components.webrtc

import android.content.DialogInterface
import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager

/**
 * A bottom sheet that allows the user to select what device they want to route audio to. Intended to be used with Android 31+ APIs.
 */
class WebRtcAudioOutputBottomSheet : ComposeBottomSheetDialogFragment(), DialogInterface {
  private val viewModel by viewModels<AudioOutputViewModel>()

  @Composable
  override fun SheetContent() {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .padding(16.dp)
        .wrapContentSize()
    ) {
      Handle()
      DeviceList(audioOutputOptions = viewModel.audioRoutes.toImmutableList(), initialDeviceId = viewModel.defaultDeviceId, modifier = Modifier.fillMaxWidth(), onDeviceSelected = viewModel.onClick)
    }
  }

  override fun cancel() {
    dismiss()
  }

  fun show(fm: FragmentManager, tag: String?, audioRoutes: List<AudioOutputOption>, selectedDeviceId: Int, onClick: (AudioOutputOption) -> Unit) {
    super.showNow(fm, tag)
    viewModel.audioRoutes = audioRoutes
    viewModel.defaultDeviceId = selectedDeviceId
    viewModel.onClick = onClick
  }

  companion object {
    const val TAG = "WebRtcAudioOutputBottomSheet"

    @JvmStatic
    fun show(fragmentManager: FragmentManager, audioRoutes: List<AudioOutputOption>, selectedDeviceId: Int, onClick: (AudioOutputOption) -> Unit): WebRtcAudioOutputBottomSheet {
      val bottomSheet = WebRtcAudioOutputBottomSheet()
      val args = Bundle()
      bottomSheet.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG, audioRoutes, selectedDeviceId, onClick)
      return bottomSheet
    }
  }
}

@Composable
fun DeviceList(audioOutputOptions: ImmutableList<AudioOutputOption>, initialDeviceId: Int, modifier: Modifier = Modifier.fillMaxWidth(), onDeviceSelected: (AudioOutputOption) -> Unit) {
  var selectedDeviceId by rememberSaveable { mutableStateOf(initialDeviceId) }
  Column(
    horizontalAlignment = Alignment.Start,
    modifier = modifier
  ) {
    Text(
      text = stringResource(R.string.WebRtcAudioOutputToggle__audio_output),
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier
        .padding(8.dp)
    )
    Column(Modifier.selectableGroup()) {
      audioOutputOptions.forEach { device: AudioOutputOption ->
        Row(
          Modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(
              selected = (device.deviceId == selectedDeviceId),
              onClick = {
                onDeviceSelected(device)
                selectedDeviceId = device.deviceId
              },
              role = Role.RadioButton
            )
            .padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          RadioButton(
            selected = (device.deviceId == selectedDeviceId),
            onClick = null // null recommended for accessibility with screenreaders
          )
          Icon(
            modifier = Modifier.padding(start = 16.dp),
            painter = painterResource(id = getDrawableResourceForDeviceType(device.deviceType)),
            contentDescription = stringResource(id = getDescriptionStringResourceForDeviceType(device.deviceType)),
            tint = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = device.friendlyName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
          )
        }
      }
    }
  }
}

class AudioOutputViewModel : ViewModel() {
  var audioRoutes: List<AudioOutputOption> = emptyList()
  var defaultDeviceId: Int = -1
  var onClick: (AudioOutputOption) -> Unit = {}
}

private fun getDrawableResourceForDeviceType(deviceType: SignalAudioManager.AudioDevice): Int {
  return when (deviceType) {
    SignalAudioManager.AudioDevice.WIRED_HEADSET -> R.drawable.symbol_headphones_outline_24
    SignalAudioManager.AudioDevice.EARPIECE -> R.drawable.symbol_phone_speaker_outline_24
    SignalAudioManager.AudioDevice.BLUETOOTH -> R.drawable.symbol_speaker_bluetooth_fill_white_24
    SignalAudioManager.AudioDevice.SPEAKER_PHONE, SignalAudioManager.AudioDevice.NONE -> R.drawable.symbol_speaker_outline_24
  }
}

private fun getDescriptionStringResourceForDeviceType(deviceType: SignalAudioManager.AudioDevice): Int {
  return when (deviceType) {
    SignalAudioManager.AudioDevice.WIRED_HEADSET -> R.string.WebRtcAudioOutputBottomSheet__headset_icon_content_description
    SignalAudioManager.AudioDevice.EARPIECE -> R.string.WebRtcAudioOutputBottomSheet__earpiece_icon_content_description
    SignalAudioManager.AudioDevice.BLUETOOTH -> R.string.WebRtcAudioOutputBottomSheet__bluetooth_icon_content_description
    SignalAudioManager.AudioDevice.SPEAKER_PHONE, SignalAudioManager.AudioDevice.NONE -> R.string.WebRtcAudioOutputBottomSheet__speaker_icon_content_description
  }
}

data class AudioOutputOption(val friendlyName: String, val deviceType: SignalAudioManager.AudioDevice, val deviceId: Int)

@Preview
@Composable
private fun SampleOutputBottomSheet() {
  val outputs: ImmutableList<AudioOutputOption> = listOf(
    AudioOutputOption("Earpiece", SignalAudioManager.AudioDevice.EARPIECE, 0),
    AudioOutputOption("Speaker", SignalAudioManager.AudioDevice.SPEAKER_PHONE, 1),
    AudioOutputOption("BT Headset", SignalAudioManager.AudioDevice.BLUETOOTH, 2),
    AudioOutputOption("Wired Headset", SignalAudioManager.AudioDevice.WIRED_HEADSET, 3)
  ).toImmutableList()
  DeviceList(outputs, 0) { }
}
