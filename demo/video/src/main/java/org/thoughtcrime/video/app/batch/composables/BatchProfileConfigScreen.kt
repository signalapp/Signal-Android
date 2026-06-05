/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.batch.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.video.TranscodingPreset
import org.thoughtcrime.securesms.video.videoconverter.utils.DeviceCapabilities
import org.thoughtcrime.video.app.transcode.TranscodeSettings
import org.thoughtcrime.video.app.transcode.calculateAudioKiloBitrateFromPreset
import org.thoughtcrime.video.app.transcode.calculateVideoMegaBitrateFromPreset
import org.thoughtcrime.video.app.transcode.composables.CustomSettings
import org.thoughtcrime.video.app.transcode.composables.PresetPicker
import org.thoughtcrime.video.app.transcode.convertPresetToVideoResolution
import org.thoughtcrime.video.app.ui.composables.LabeledButton
import kotlin.math.roundToInt

@Composable
fun BatchProfileConfigScreen(
  onSaveProfile: (TranscodeSettings) -> Unit,
  hevcCapable: Boolean = DeviceCapabilities.canEncodeHevc(),
  modifier: Modifier = Modifier
) {
  var useAutoTranscodingSettings by remember { mutableStateOf(true) }
  var transcodingPreset by remember { mutableStateOf(TranscodingPreset.LEVEL_2) }
  var videoResolution by remember { mutableStateOf(convertPresetToVideoResolution(TranscodingPreset.LEVEL_2)) }
  var videoMegaBitrate by remember { mutableFloatStateOf(calculateVideoMegaBitrateFromPreset(TranscodingPreset.LEVEL_2)) }
  var audioKiloBitrate by remember { mutableIntStateOf(calculateAudioKiloBitrateFromPreset(TranscodingPreset.LEVEL_2)) }
  var useHevc by remember { mutableStateOf(false) }
  var enableFastStart by remember { mutableStateOf(true) }
  var enableAudioRemux by remember { mutableStateOf(true) }

  Column(
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
  ) {
    Text("Create Transcode Profile", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(16.dp))

    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth()
    ) {
      Checkbox(
        checked = useAutoTranscodingSettings,
        onCheckedChange = { useAutoTranscodingSettings = it }
      )
      Text(
        text = "Match Signal App Transcoding Settings",
        style = MaterialTheme.typography.bodySmall
      )
    }

    if (useAutoTranscodingSettings) {
      PresetPicker(
        selectedTranscodingPreset = transcodingPreset,
        onPresetSelected = {
          transcodingPreset = it
          videoResolution = convertPresetToVideoResolution(it)
          videoMegaBitrate = calculateVideoMegaBitrateFromPreset(it)
          audioKiloBitrate = calculateAudioKiloBitrateFromPreset(it)
        },
        modifier = Modifier.padding(vertical = 16.dp)
      )
    } else {
      CustomSettings(
        selectedResolution = videoResolution,
        onResolutionSelected = { videoResolution = it },
        useHevc = useHevc,
        onUseHevcSettingChanged = { useHevc = it },
        fastStartChecked = enableFastStart,
        onFastStartSettingCheckChanged = { enableFastStart = it },
        audioRemuxChecked = enableAudioRemux,
        onAudioRemuxCheckChanged = { enableAudioRemux = it },
        videoSliderPosition = videoMegaBitrate,
        updateVideoSliderPosition = { videoMegaBitrate = it },
        audioSliderPosition = audioKiloBitrate,
        updateAudioSliderPosition = { audioKiloBitrate = it.roundToInt() },
        hevcCapable = hevcCapable,
        modifier = Modifier.padding(vertical = 16.dp)
      )
    }

    LabeledButton(
      buttonLabel = "Save Profile",
      onClick = {
        onSaveProfile(
          TranscodeSettings(
            isPreset = useAutoTranscodingSettings,
            presetName = if (useAutoTranscodingSettings) transcodingPreset.name else null,
            videoResolution = videoResolution,
            videoMegaBitrate = videoMegaBitrate,
            audioKiloBitrate = audioKiloBitrate,
            useHevc = useHevc,
            enableFastStart = enableFastStart,
            enableAudioRemux = enableAudioRemux
          )
        )
      },
      modifier = Modifier.padding(vertical = 8.dp)
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun BatchProfileConfigScreenPresetPreview() {
  BatchProfileConfigScreen(onSaveProfile = {})
}

@Preview(showBackground = true)
@Composable
private fun BatchProfileConfigScreenCustomPreview() {
  BatchProfileConfigScreen(onSaveProfile = {}, hevcCapable = true)
}
