/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode.composables

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.thoughtcrime.securesms.video.TranscodingPreset
import org.thoughtcrime.securesms.video.videoconverter.utils.DeviceCapabilities
import org.thoughtcrime.video.app.transcode.MAX_VIDEO_MEGABITRATE
import org.thoughtcrime.video.app.transcode.MIN_VIDEO_MEGABITRATE
import org.thoughtcrime.video.app.transcode.OPTIONS_AUDIO_KILOBITRATES
import org.thoughtcrime.video.app.transcode.TranscodeTestViewModel
import org.thoughtcrime.video.app.transcode.VideoResolution
import org.thoughtcrime.video.app.ui.composables.LabeledButton
import kotlin.math.roundToInt

/**
 * A view that shows the queue of video URIs to encode, and allows you to change the encoding options.
 */
@Composable
fun ConfigureEncodingParameters(
  hevcCapable: Boolean = DeviceCapabilities.canEncodeHevc(),
  modifier: Modifier = Modifier,
  viewModel: TranscodeTestViewModel = viewModel()
) {
  Column(
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
  ) {
    Text(
      text = "Selected videos:",
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .align(Alignment.Start)
    )
    viewModel.selectedVideos.forEach {
      Text(
        text = it.toString(),
        fontSize = 8.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
          .padding(horizontal = 8.dp)
          .align(Alignment.Start)
      )
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
    ) {
      Checkbox(
        checked = viewModel.forceSequentialQueueProcessing,
        onCheckedChange = { viewModel.forceSequentialQueueProcessing = it }
      )
      Text(text = "Force Sequential Queue Processing", style = MaterialTheme.typography.bodySmall)
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
    ) {
      Checkbox(
        checked = viewModel.useAutoTranscodingSettings,
        onCheckedChange = { viewModel.useAutoTranscodingSettings = it }
      )
      Text(
        text = "Match Signal App Transcoding Settings",
        style = MaterialTheme.typography.bodySmall
      )
    }
    if (viewModel.useAutoTranscodingSettings) {
      PresetPicker(
        viewModel.transcodingPreset,
        viewModel::updateTranscodingPreset,
        modifier = Modifier.padding(vertical = 16.dp)
      )
    } else {
      CustomSettings(
        selectedResolution = viewModel.videoResolution,
        onResolutionSelected = { viewModel.videoResolution = it },
        useHevc = viewModel.useHevc,
        onUseHevcSettingChanged = { viewModel.useHevc = it },
        fastStartChecked = viewModel.enableFastStart,
        onFastStartSettingCheckChanged = { viewModel.enableFastStart = it },
        audioRemuxChecked = viewModel.enableAudioRemux,
        onAudioRemuxCheckChanged = { viewModel.enableAudioRemux = it },
        videoSliderPosition = viewModel.videoMegaBitrate,
        updateVideoSliderPosition = { viewModel.videoMegaBitrate = it },
        audioSliderPosition = viewModel.audioKiloBitrate,
        updateAudioSliderPosition = { viewModel.audioKiloBitrate = it.roundToInt() },
        hevcCapable = hevcCapable,
        modifier = Modifier.padding(vertical = 16.dp)
      )
    }
    LabeledButton(
      buttonLabel = "Transcode",
      onClick = {
        viewModel.transcode()
        viewModel.selectedVideos = emptyList()
        viewModel.resetOutputDirectory()
      },
      modifier = Modifier.padding(vertical = 8.dp)
    )
  }
}

@Composable
private fun PresetPicker(
  selectedTranscodingPreset: TranscodingPreset,
  onPresetSelected: (TranscodingPreset) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    horizontalArrangement = Arrangement.SpaceEvenly,
    modifier = modifier
      .fillMaxWidth()
      .selectableGroup()
  ) {
    TranscodingPreset.entries.forEach {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .selectable(
            selected = selectedTranscodingPreset == it,
            onClick = {
              onPresetSelected(it)
            },
            role = Role.RadioButton
          )
      ) {
        RadioButton(
          selected = selectedTranscodingPreset == it,
          onClick = null,
          modifier = Modifier.semantics { contentDescription = it.name }
        )
        Text(
          text = it.name,
          textAlign = TextAlign.Center
        )
      }
    }
  }
}

@Composable
private fun CustomSettings(
  selectedResolution: VideoResolution,
  onResolutionSelected: (VideoResolution) -> Unit,
  useHevc: Boolean,
  onUseHevcSettingChanged: (Boolean) -> Unit,
  fastStartChecked: Boolean,
  onFastStartSettingCheckChanged: (Boolean) -> Unit,
  audioRemuxChecked: Boolean,
  onAudioRemuxCheckChanged: (Boolean) -> Unit,
  videoSliderPosition: Float,
  updateVideoSliderPosition: (Float) -> Unit,
  audioSliderPosition: Int,
  updateAudioSliderPosition: (Float) -> Unit,
  hevcCapable: Boolean,
  modifier: Modifier = Modifier
) {
  Row(
    horizontalArrangement = Arrangement.SpaceEvenly,
    modifier = modifier
      .fillMaxWidth()
      .selectableGroup()
  ) {
    VideoResolution.entries.forEach {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .selectable(
            selected = selectedResolution == it,
            onClick = { onResolutionSelected(it) },
            role = Role.RadioButton
          )
          .padding(start = 16.dp)
      ) {
        RadioButton(
          selected = selectedResolution == it,
          onClick = null,
          modifier = Modifier.semantics { contentDescription = it.getContentDescription() }
        )
        Text(
          text = "${it.shortEdge}p",
          textAlign = TextAlign.Center
        )
      }
    }
  }
  VideoBitrateSlider(videoSliderPosition, updateVideoSliderPosition)
  AudioBitrateSlider(audioSliderPosition, updateAudioSliderPosition)

  if (hevcCapable) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .padding(vertical = 8.dp, horizontal = 8.dp)
        .fillMaxWidth()
    ) {
      Checkbox(
        checked = useHevc,
        onCheckedChange = { onUseHevcSettingChanged(it) }
      )
      Text(text = "Use HEVC encoder", style = MaterialTheme.typography.bodySmall)
    }
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .padding(vertical = 8.dp, horizontal = 8.dp)
      .fillMaxWidth()
  ) {
    Checkbox(
      checked = audioRemuxChecked,
      onCheckedChange = { onAudioRemuxCheckChanged(it) }
    )
    Text(text = "Allow audio remuxing", style = MaterialTheme.typography.bodySmall)
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .padding(vertical = 8.dp, horizontal = 8.dp)
      .fillMaxWidth()
  ) {
    Checkbox(
      checked = fastStartChecked,
      onCheckedChange = { onFastStartSettingCheckChanged(it) }
    )
    Text(text = "Perform Mp4San Postprocessing", style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun VideoBitrateSlider(
  videoSliderPosition: Float,
  updateSliderPosition: (Float) -> Unit,
  modifier: Modifier = Modifier
) {
  Slider(
    value = videoSliderPosition,
    onValueChange = updateSliderPosition,
    colors = SliderDefaults.colors(
      thumbColor = MaterialTheme.colorScheme.secondary,
      activeTrackColor = MaterialTheme.colorScheme.secondary,
      inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer
    ),
    valueRange = MIN_VIDEO_MEGABITRATE..MAX_VIDEO_MEGABITRATE,
    modifier = modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
  )
  Text(text = String.format("Video: %.2f Mbit/s", videoSliderPosition))
}

@Composable
private fun AudioBitrateSlider(
  audioSliderPosition: Int,
  updateSliderPosition: (Float) -> Unit,
  modifier: Modifier = Modifier
) {
  val minValue = OPTIONS_AUDIO_KILOBITRATES.first().toFloat()
  val maxValue = OPTIONS_AUDIO_KILOBITRATES.last().toFloat()
  val steps = OPTIONS_AUDIO_KILOBITRATES.size - 2

  Slider(
    value = audioSliderPosition.toFloat(),
    onValueChange = updateSliderPosition,
    colors = SliderDefaults.colors(
      thumbColor = MaterialTheme.colorScheme.secondary,
      activeTrackColor = MaterialTheme.colorScheme.secondary,
      inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer
    ),
    valueRange = minValue..maxValue,
    steps = steps,
    modifier = modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
  )
  Text(text = String.format("Audio: %d Kbit/s", audioSliderPosition))
}

@Preview(showBackground = true)
@Composable
private fun ConfigurationScreenPreviewChecked() {
  val vm: TranscodeTestViewModel = viewModel()
  vm.selectedVideos = listOf(Uri.parse("content://1"), Uri.parse("content://2"))
  vm.forceSequentialQueueProcessing = true
  ConfigureEncodingParameters()
}

@Preview(showBackground = true)
@Composable
private fun ConfigurationScreenPreviewUnchecked() {
  val vm: TranscodeTestViewModel = viewModel()
  vm.selectedVideos = listOf(Uri.parse("content://1"), Uri.parse("content://2"))
  vm.useAutoTranscodingSettings = false
  ConfigureEncodingParameters(hevcCapable = true)
}
