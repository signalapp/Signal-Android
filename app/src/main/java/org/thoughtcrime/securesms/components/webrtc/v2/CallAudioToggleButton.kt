/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.AudioStateUpdater
import org.thoughtcrime.securesms.components.webrtc.ToggleButtonOutputState
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioDevice
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioOutput
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioPicker31
import org.signal.core.ui.R as CoreUiR

private const val SHOW_PICKER_THRESHOLD = 3

/**
 * Button which allows user to select from different audio devices to play back call audio through.
 */
@Composable
fun CallAudioToggleButton(
  contentDescription: String,
  onSheetDisplayChanged: (Boolean) -> Unit,
  pickerController: AudioOutputPickerController,
  modifier: Modifier = Modifier
) {
  val buttonSize = dimensionResource(id = R.dimen.webrtc_button_size)

  val currentOutput = pickerController.outputState.currentDevice
  val allOutputs = pickerController.outputState.availableDevices

  val containerColor = if (currentOutput == WebRtcAudioOutput.HANDSET || allOutputs.size >= SHOW_PICKER_THRESHOLD) {
    MaterialTheme.colorScheme.secondaryContainer
  } else {
    colorResource(id = R.color.signal_light_colorSecondaryContainer)
  }

  val contentColor = if (currentOutput == WebRtcAudioOutput.HANDSET || allOutputs.size >= SHOW_PICKER_THRESHOLD) {
    colorResource(id = R.color.signal_light_colorOnPrimary)
  } else {
    colorResource(id = R.color.signal_light_colorOnSecondaryContainer)
  }

  IconButtons.IconButton(
    size = buttonSize,
    onClick = {
      pickerController.show()
    },
    colors = IconButtons.iconButtonColors(
      containerColor = containerColor,
      contentColor = contentColor
    ),
    modifier = modifier.size(buttonSize)
  ) {
    val iconRes = remember(currentOutput, pickerController.willDisplayPicker) {
      if (!pickerController.willDisplayPicker && currentOutput == WebRtcAudioOutput.HANDSET) {
        WebRtcAudioOutput.SPEAKER.iconRes
      } else {
        currentOutput.iconRes
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        painter = painterResource(id = iconRes),
        contentDescription = contentDescription,
        modifier = Modifier.size(28.dp)
      )

      if (pickerController.willDisplayPicker) {
        Icon(
          painter = painterResource(id = R.drawable.symbol_dropdown_triangle_compat_bold_16),
          contentDescription = null,
          modifier = Modifier.size(16.dp)
        )
      }
    }
  }

  pickerController.Sheet()

  LaunchedEffect(pickerController.displaySheet) {
    onSheetDisplayChanged(pickerController.displaySheet)
  }
}

@Composable
fun rememberAudioOutputPickerController(
  onSelectedDeviceChanged: (WebRtcAudioDevice) -> Unit,
  outputState: ToggleButtonOutputState
): AudioOutputPickerController {
  return remember(onSelectedDeviceChanged, outputState) {
    AudioOutputPickerController(
      onSelectedDeviceChanged,
      outputState
    )
  }
}

/**
 * Controller for the Audio picker which contains different state variables for choosing whether
 * or not to display the sheet.
 */
@Stable
class AudioOutputPickerController(
  private val onSelectedDeviceChanged: (WebRtcAudioDevice) -> Unit,
  val outputState: ToggleButtonOutputState
) {

  var displaySheet: Boolean by mutableStateOf(false)
    private set

  val willDisplayPicker: Boolean by derivedStateOf {
    outputState.availableDevices.size >= SHOW_PICKER_THRESHOLD || !outputState.availableDevices.contains(WebRtcAudioOutput.HANDSET)
  }

  private val newApiController = if (Build.VERSION.SDK_INT >= 31) WebRtcAudioPicker31(
    audioOutputChangedListener = onSelectedDeviceChanged,
    outputState = outputState,
    stateUpdater = object : AudioStateUpdater {
      override fun updateAudioOutputState(audioOutput: WebRtcAudioOutput) {
        outputState.setCurrentOutput(audioOutput)
        displaySheet = false
      }

      override fun hidePicker() {
        displaySheet = false
      }
    }
  ) else null

  fun show() {
    displaySheet = true
  }

  fun hide() {
    displaySheet = false
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun Sheet() {
    if (!displaySheet) {
      return
    }

    val isLegacy = Build.VERSION.SDK_INT < 31
    if (!willDisplayPicker) {
      if (isLegacy) {
        onSelectedDeviceChanged(WebRtcAudioDevice(outputState.peekNext(), null))
      } else {
        newApiController!!.Picker(threshold = SHOW_PICKER_THRESHOLD)
      }
      return
    }

    ModalBottomSheet(
      onDismissRequest = { displaySheet = false }
    ) {
      if (isLegacy) {
        LegacyAudioPickerContent(
          toggleButtonOutputState = outputState,
          onSelectedDeviceChanged = {
            displaySheet = false
            onSelectedDeviceChanged(WebRtcAudioDevice(it, null))
          }
        )
      } else {
        newApiController!!.Picker(threshold = SHOW_PICKER_THRESHOLD)
      }
    }
  }
}

/**
 * Picker for pre-api-31 devices.
 */
@Composable
private fun LegacyAudioPickerContent(
  toggleButtonOutputState: ToggleButtonOutputState,
  onSelectedDeviceChanged: (WebRtcAudioOutput) -> Unit
) {
  Text(
    text = stringResource(R.string.WebRtcAudioOutputToggle__audio_output),
    style = MaterialTheme.typography.headlineMedium,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier
      .padding(8.dp)
      .padding(
        horizontal = dimensionResource(id = CoreUiR.dimen.gutter)
      )
  )

  LazyColumn(
    modifier = Modifier.padding(
      horizontal = dimensionResource(id = CoreUiR.dimen.gutter)
    )
  ) {
    items(
      items = toggleButtonOutputState.availableDevices
    ) { item ->
      val icon = when (item) {
        WebRtcAudioOutput.HANDSET -> R.drawable.symbol_phone_speaker_outline_24
        WebRtcAudioOutput.SPEAKER -> R.drawable.symbol_speaker_outline_24
        WebRtcAudioOutput.BLUETOOTH_HEADSET -> R.drawable.symbol_speaker_bluetooth_outline_24
        WebRtcAudioOutput.WIRED_HEADSET -> R.drawable.symbol_headphones_outline_24
      }

      Rows.RadioRow(
        selected = item == toggleButtonOutputState.currentDevice,
        content = {
          Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.padding(end = 16.dp)
          )

          Text(
            text = stringResource(id = item.labelRes),
            style = MaterialTheme.typography.bodyLarge
          )
        },
        modifier = Modifier.clickable {
          onSelectedDeviceChanged(item)
        }
      )
    }
  }
}

@NightPreview
@Composable
private fun CallAudioPickerSheetContentPreview() {
  Previews.BottomSheetPreview {
    Column {
      LegacyAudioPickerContent(
        toggleButtonOutputState = ToggleButtonOutputState().apply {
          isEarpieceAvailable = true
          isBluetoothHeadsetAvailable = true
          isWiredHeadsetAvailable = true
        },
        onSelectedDeviceChanged = {}
      )
    }
  }
}

@NightPreview
@Composable
private fun TwoDeviceCallAudioToggleButtonPreview() {
  val outputState = remember {
    ToggleButtonOutputState().apply {
      isEarpieceAvailable = true
    }
  }

  Previews.Preview {
    CallAudioToggleButton(
      contentDescription = "",
      onSheetDisplayChanged = {},
      pickerController = rememberAudioOutputPickerController(
        outputState = outputState,
        onSelectedDeviceChanged = {
          outputState.setCurrentOutput(it.webRtcAudioOutput)
        }
      )
    )
  }
}

@NightPreview
@Composable
private fun ThreeDeviceCallAudioToggleButtonPreview() {
  val outputState = remember {
    ToggleButtonOutputState().apply {
      isEarpieceAvailable = true
      isBluetoothHeadsetAvailable = true
    }
  }

  Previews.Preview {
    CallAudioToggleButton(
      contentDescription = "",
      onSheetDisplayChanged = {},
      pickerController = rememberAudioOutputPickerController(
        outputState = outputState,
        onSelectedDeviceChanged = {
          outputState.setCurrentOutput(it.webRtcAudioOutput)
        }
      )
    )
  }
}
