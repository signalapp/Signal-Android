/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.batch.composables

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.thoughtcrime.video.app.batch.BatchTranscodeViewModel
import org.thoughtcrime.video.app.transcode.TranscodeSettings
import org.thoughtcrime.video.app.transcode.VideoResolution
import org.thoughtcrime.video.app.ui.composables.LabeledButton

@Composable
fun BatchConfigScreen(
  viewModel: BatchTranscodeViewModel,
  onCreateProfile: () -> Unit,
  onStartBatch: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  val pickVideos = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      viewModel.setVideos(context, uris)
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text("Batch Transcode", style = MaterialTheme.typography.headlineMedium)

    Spacer(modifier = Modifier.height(4.dp))

    Text("Source Videos (${viewModel.selectedVideos.size})", style = MaterialTheme.typography.titleMedium)
    if (viewModel.selectedVideos.isEmpty()) {
      Text("No videos selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
      viewModel.selectedVideos.forEachIndexed { index, video ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = video.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
          )
          IconButton(onClick = { viewModel.removeVideo(index) }) {
            Text("X", style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    }
    LabeledButton(
      "Select Videos",
      onClick = {
        pickVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
      }
    )

    HorizontalDivider()

    Text("Transcode Profiles (${viewModel.profiles.size})", style = MaterialTheme.typography.titleMedium)

    viewModel.profiles.forEachIndexed { index, profile ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.weight(1f)) {
          if (profile.isPreset) {
            Text("Preset: ${profile.presetName}", style = MaterialTheme.typography.bodyMedium)
          } else {
            Text(
              "${profile.videoResolution.shortEdge}p / ${if (profile.useHevc) "H.265" else "H.264"} / ${"%.1f".format(profile.videoMegaBitrate)} Mbps",
              style = MaterialTheme.typography.bodyMedium
            )
          }
          Text(
            "Audio: ${profile.audioKiloBitrate} kbps | Fast start: ${if (profile.enableFastStart) "Yes" else "No"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(onClick = { viewModel.removeProfile(index) }) {
          Text("X", style = MaterialTheme.typography.labelLarge)
        }
      }
    }

    LabeledButton("Create Profile", onClick = onCreateProfile)

    HorizontalDivider()

    val videoCount = viewModel.selectedVideos.size
    val canStart = videoCount > 0 && viewModel.profiles.isNotEmpty()
    Button(
      onClick = onStartBatch,
      enabled = canStart,
      modifier = Modifier.fillMaxWidth()
    ) {
      if (canStart) {
        Text("Start Batch ($videoCount videos x ${viewModel.profiles.size} profiles = ${videoCount * viewModel.profiles.size} transcodes)")
      } else {
        Text("Start Batch Transcode")
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun BatchConfigScreenEmptyPreview() {
  val vm: BatchTranscodeViewModel = viewModel()
  BatchConfigScreen(viewModel = vm, onCreateProfile = {}, onStartBatch = {})
}

@Preview(showBackground = true)
@Composable
private fun BatchConfigScreenWithProfilesPreview() {
  val vm: BatchTranscodeViewModel = viewModel()
  vm.addProfile(
    TranscodeSettings(
      isPreset = true,
      presetName = "LEVEL_2",
      videoResolution = VideoResolution.SD,
      videoMegaBitrate = 1.25f,
      audioKiloBitrate = 128,
      useHevc = false,
      enableFastStart = true,
      enableAudioRemux = true
    )
  )
  vm.addProfile(
    TranscodeSettings(
      isPreset = false,
      presetName = null,
      videoResolution = VideoResolution.HD,
      videoMegaBitrate = 2.5f,
      audioKiloBitrate = 192,
      useHevc = true,
      enableFastStart = true,
      enableAudioRemux = false
    )
  )
  BatchConfigScreen(viewModel = vm, onCreateProfile = {}, onStartBatch = {})
}
