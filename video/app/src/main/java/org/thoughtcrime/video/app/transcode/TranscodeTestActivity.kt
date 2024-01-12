/*
 * Copyright 2024 Signal Messenger, LLC
 * 2SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.thoughtcrime.video.app.ui.composables.LabeledButton
import org.thoughtcrime.video.app.ui.theme.SignalTheme

class TranscodeTestActivity : AppCompatActivity() {
  private val viewModel: TranscodeTestViewModel by viewModels()
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    viewModel.initialize(this)
    setContent {
      SignalTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            val videoUris = viewModel.selectedVideos
            val outputDir = viewModel.outputDirectory
            val transcodingJobs = viewModel.getTranscodingJobsAsState().collectAsState(emptyList())
            if (transcodingJobs.value.isNotEmpty()) {
              transcodingJobs.value.forEach { workInfo ->
                val currentProgress = workInfo.progress.getInt(TranscodeWorker.KEY_PROGRESS, -1)
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                  Text(text = "...${workInfo.id.toString().takeLast(4)}", modifier = Modifier.padding(end = 16.dp).weight(1f))
                  if (workInfo.state.isFinished) {
                    LinearProgressIndicator(progress = 1f, trackColor = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(3f))
                  } else if (currentProgress >= 0) {
                    LinearProgressIndicator(progress = currentProgress / 100f, modifier = Modifier.weight(3f))
                  } else {
                    LinearProgressIndicator(modifier = Modifier.weight(3f))
                  }
                }
              }
              LabeledButton("Reset/Cancel") { viewModel.reset() }
            } else if (videoUris.isEmpty()) {
              LabeledButton("Select Videos") { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
            } else if (outputDir == null) {
              LabeledButton("Select Output Directory") { outputDirRequest.launch(null) }
            } else {
              Text(text = "Selected videos:", modifier = Modifier.align(Alignment.Start).padding(16.dp))
              videoUris.forEach {
                Text(text = it.toString(), fontSize = 8.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.Start).padding(horizontal = 16.dp))
              }
              LabeledButton(buttonLabel = "Transcode") {
                viewModel.transcode()
                viewModel.selectedVideos = emptyList()
                viewModel.resetOutputDirectory()
              }
            }
          }
        }
      }
    }
    getComposeView()?.keepScreenOn = true
  }

  /**
   * This launches the system media picker and stores the resulting URI.
   */
  private val pickMedia = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      Log.d("VideoPicker", "Selected URI: $uris")
      viewModel.selectedVideos = uris
      viewModel.resetOutputDirectory()
    } else {
      Log.d("VideoPicker", "No media selected")
    }
  }

  private val outputDirRequest = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri?.let {
      contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      viewModel.setOutputDirectoryAndCleanFailedTranscodes(this, it)
    }
  }

  private fun getComposeView(): ComposeView? {
    return window.decorView
      .findViewById<ViewGroup>(android.R.id.content)
      .getChildAt(0) as? ComposeView
  }
}
