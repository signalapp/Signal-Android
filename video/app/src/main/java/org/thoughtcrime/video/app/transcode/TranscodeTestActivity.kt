/*
 * Copyright 2024 Signal Messenger, LLC
 * 2SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import org.thoughtcrime.video.app.R
import org.thoughtcrime.video.app.transcode.composables.ConfigureEncodingParameters
import org.thoughtcrime.video.app.transcode.composables.SelectInput
import org.thoughtcrime.video.app.transcode.composables.SelectOutput
import org.thoughtcrime.video.app.transcode.composables.TranscodingJobProgress
import org.thoughtcrime.video.app.ui.theme.SignalTheme

/**
 * Visual entry point for testing transcoding in the video sample app.
 */
class TranscodeTestActivity : AppCompatActivity() {
  private val viewModel: TranscodeTestViewModel by viewModels()
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    viewModel.initialize(this)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val name = applicationContext.getString(R.string.channel_name)
      val descriptionText = applicationContext.getString(R.string.channel_description)
      val importance = NotificationManager.IMPORTANCE_DEFAULT
      val mChannel = NotificationChannel(getString(R.string.notification_channel_id), name, importance)
      mChannel.description = descriptionText
      val notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(mChannel)
    }

    setContent {
      SignalTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          val videoUris = viewModel.selectedVideos
          val outputDir = viewModel.outputDirectory
          val transcodingJobs = viewModel.getTranscodingJobsAsState().collectAsState(emptyList())
          if (transcodingJobs.value.isNotEmpty()) {
            TranscodingJobProgress(transcodingJobs = transcodingJobs.value, resetButtonOnClick = { viewModel.reset() })
          } else if (videoUris.isEmpty()) {
            SelectInput { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
          } else if (outputDir == null) {
            SelectOutput { outputDirRequest.launch(null) }
          } else {
            ConfigureEncodingParameters(
              videoUris = videoUris,
              onAutoSettingsCheckChanged = { viewModel.useAutoTranscodingSettings = it },
              onRadioButtonSelected = { viewModel.videoResolution = it },
              onSliderValueChanged = { viewModel.videoMegaBitrate = it },
              onFastStartSettingCheckChanged = { viewModel.enableFastStart = it },
              onSequentialSettingCheckChanged = { viewModel.forceSequentialQueueProcessing = it },
              buttonClickListener = {
                viewModel.transcode()
                viewModel.selectedVideos = emptyList()
                viewModel.resetOutputDirectory()
              }
            )
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
