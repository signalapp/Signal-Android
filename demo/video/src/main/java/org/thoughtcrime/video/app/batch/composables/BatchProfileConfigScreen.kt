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
import org.thoughtcrime.video.app.transcode.TranscodeQuality
import org.thoughtcrime.video.app.transcode.TranscodeSettings
import org.thoughtcrime.video.app.transcode.calculateAudioKiloBitrateFromQuality
import org.thoughtcrime.video.app.transcode.calculateVideoMegaBitrateFromQuality
import org.thoughtcrime.video.app.transcode.composables.CustomSettings
import org.thoughtcrime.video.app.transcode.composables.QualityPicker
import org.thoughtcrime.video.app.transcode.convertQualityToVideoResolution
import org.thoughtcrime.video.app.ui.composables.LabeledButton
import kotlin.math.roundToInt

@Composable
fun BatchProfileConfigScreen(
  onSaveProfile: (TranscodeSettings) -> Unit,
  modifier: Modifier = Modifier
) {
  var useAutoTranscodingSettings by remember { mutableStateOf(true) }
  var transcodeQuality by remember { mutableStateOf(TranscodeQuality.STANDARD) }
  var videoResolution by remember { mutableStateOf(convertQualityToVideoResolution(TranscodeQuality.STANDARD)) }
  var videoMegaBitrate by remember { mutableFloatStateOf(calculateVideoMegaBitrateFromQuality(TranscodeQuality.STANDARD)) }
  var audioKiloBitrate by remember { mutableIntStateOf(calculateAudioKiloBitrateFromQuality(TranscodeQuality.STANDARD)) }
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
      QualityPicker(
        selectedTranscodeQuality = transcodeQuality,
        onQualitySelected = {
          transcodeQuality = it
          videoResolution = convertQualityToVideoResolution(it)
          videoMegaBitrate = calculateVideoMegaBitrateFromQuality(it)
          audioKiloBitrate = calculateAudioKiloBitrateFromQuality(it)
        },
        modifier = Modifier.padding(vertical = 16.dp)
      )
    } else {
      CustomSettings(
        selectedResolution = videoResolution,
        onResolutionSelected = { videoResolution = it },
        fastStartChecked = enableFastStart,
        onFastStartSettingCheckChanged = { enableFastStart = it },
        audioRemuxChecked = enableAudioRemux,
        onAudioRemuxCheckChanged = { enableAudioRemux = it },
        videoSliderPosition = videoMegaBitrate,
        updateVideoSliderPosition = { videoMegaBitrate = it },
        audioSliderPosition = audioKiloBitrate,
        updateAudioSliderPosition = { audioKiloBitrate = it.roundToInt() },
        modifier = Modifier.padding(vertical = 16.dp)
      )
    }

    LabeledButton(
      buttonLabel = "Save Profile",
      onClick = {
        onSaveProfile(
          TranscodeSettings(
            quality = if (useAutoTranscodingSettings) transcodeQuality else null,
            videoResolution = videoResolution,
            videoMegaBitrate = videoMegaBitrate,
            audioKiloBitrate = audioKiloBitrate,
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
  BatchProfileConfigScreen(onSaveProfile = {})
}
