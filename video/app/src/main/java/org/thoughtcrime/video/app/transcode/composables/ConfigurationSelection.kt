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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.thoughtcrime.video.app.transcode.DEFAULT_VIDEO_MEGABITRATE
import org.thoughtcrime.video.app.transcode.MAX_VIDEO_MEGABITRATE
import org.thoughtcrime.video.app.transcode.MIN_VIDEO_MEGABITRATE
import org.thoughtcrime.video.app.transcode.VideoResolution
import org.thoughtcrime.video.app.ui.composables.LabeledButton

/**
 * A view that shows the queue of video URIs to encode, and allows you to change the encoding options.
 */
@Composable
fun ConfigureEncodingParameters(
  videoUris: List<Uri>,
  onAutoSettingsCheckChanged: (Boolean) -> Unit,
  onRadioButtonSelected: (VideoResolution) -> Unit,
  onSliderValueChanged: (Float) -> Unit,
  onFastStartSettingCheckChanged: (Boolean) -> Unit,
  onSequentialSettingCheckChanged: (Boolean) -> Unit,
  buttonClickListener: () -> Unit,
  modifier: Modifier = Modifier,
  initialSettingsAutoSelected: Boolean = true
) {
  var sliderPosition by remember { mutableFloatStateOf(DEFAULT_VIDEO_MEGABITRATE) }
  var selectedResolution by remember { mutableStateOf(VideoResolution.HD) }
  val autoSettingsChecked = remember { mutableStateOf(initialSettingsAutoSelected) }
  val fastStartChecked = remember { mutableStateOf(true) }
  val sequentialProcessingChecked = remember { mutableStateOf(false) }
  Column(
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier.padding(16.dp)
  ) {
    Text(
      text = "Selected videos:",
      modifier = Modifier
        .align(Alignment.Start)
        .padding(16.dp)
    )
    videoUris.forEach {
      Text(
        text = it.toString(),
        fontSize = 8.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
          .align(Alignment.Start)
          .padding(horizontal = 16.dp)
      )
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .fillMaxWidth()
    ) {
      Checkbox(
        checked = sequentialProcessingChecked.value,
        onCheckedChange = { isChecked ->
          sequentialProcessingChecked.value = isChecked
          onSequentialSettingCheckChanged(isChecked)
        }
      )
      Text(text = "Force Sequential Queue Processing", style = MaterialTheme.typography.bodySmall)
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .padding(vertical = 8.dp, horizontal = 8.dp)
        .fillMaxWidth()
    ) {
      Checkbox(
        checked = autoSettingsChecked.value,
        onCheckedChange = { isChecked ->
          autoSettingsChecked.value = isChecked
          onAutoSettingsCheckChanged(isChecked)
        }
      )
      Text(
        text = "Match Signal App Transcoding Settings",
        style = MaterialTheme.typography.bodySmall
      )
    }
    if (!autoSettingsChecked.value) {
      Row(
        modifier = Modifier
          .padding(vertical = 16.dp)
          .fillMaxWidth()
          .selectableGroup()
      ) {
        VideoResolution.values().forEach {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .selectable(
                selected = selectedResolution == it,
                onClick = {
                  selectedResolution = it
                  onRadioButtonSelected(it)
                },
                role = Role.RadioButton
              )
          ) {
            RadioButton(
              selected = selectedResolution == it,
              onClick = null,
              modifier = Modifier.semantics { contentDescription = it.getContentDescription() }
            )
            Text(
              text = "${it.shortEdge}p",
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(start = 16.dp)
            )
          }
        }
      }
      Slider(
        value = sliderPosition,
        onValueChange = {
          sliderPosition = it
          onSliderValueChanged(it)
        },
        colors = SliderDefaults.colors(
          thumbColor = MaterialTheme.colorScheme.secondary,
          activeTrackColor = MaterialTheme.colorScheme.secondary,
          inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        valueRange = MIN_VIDEO_MEGABITRATE..MAX_VIDEO_MEGABITRATE,
        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
      )
      Text(text = String.format("%.1f Mbit/s", sliderPosition))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .padding(vertical = 8.dp, horizontal = 8.dp)
          .fillMaxWidth()
      ) {
        Checkbox(
          checked = fastStartChecked.value,
          onCheckedChange = { isChecked ->
            fastStartChecked.value = isChecked
            onFastStartSettingCheckChanged(isChecked)
          }
        )
        Text(text = "Enable Mp4San Postprocessing", style = MaterialTheme.typography.bodySmall)
      }
    }
    LabeledButton(buttonLabel = "Transcode", onClick = buttonClickListener, modifier = Modifier.padding(vertical = 8.dp))
  }
}

@Preview
@Composable
private fun ConfigurationScreenPreviewChecked() {
  ConfigureEncodingParameters(
    videoUris = listOf(Uri.parse("content://1"), Uri.parse("content://2")),
    onAutoSettingsCheckChanged = {},
    onRadioButtonSelected = {},
    onSliderValueChanged = {},
    onFastStartSettingCheckChanged = {},
    onSequentialSettingCheckChanged = {},
    buttonClickListener = {}
  )
}

@Preview
@Composable
private fun ConfigurationScreenPreviewUnchecked() {
  ConfigureEncodingParameters(
    videoUris = listOf(Uri.parse("content://1"), Uri.parse("content://2")),
    onAutoSettingsCheckChanged = {},
    onRadioButtonSelected = {},
    onSliderValueChanged = {},
    onFastStartSettingCheckChanged = {},
    onSequentialSettingCheckChanged = {},
    buttonClickListener = {},
    initialSettingsAutoSelected = false
  )
}
