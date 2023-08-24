/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView
import org.thoughtcrime.video.app.ui.theme.SignalTheme

/**
 * Main activity for this sample app.
 */
class MainActivity : ComponentActivity() {
  private val viewModel: MainScreenViewModel by viewModels()
  private lateinit var exoPlayer: ExoPlayer

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    viewModel.initialize(this)
    exoPlayer = ExoPlayer.Builder(this).build()
    setContent {
      SignalTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            val videoUri = viewModel.selectedVideo
            if (videoUri == null) {
              LabeledButton("Select Video") { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
            } else {
              LabeledButton("Play Video") { viewModel.updateMediaSource(this@MainActivity) }
              LabeledButton("Play Video with slow download") { viewModel.updateMediaSourceTrickle(this@MainActivity) }
              ExoVideoView(source = viewModel.mediaSource, exoPlayer = exoPlayer)
            }
          }
        }
      }
    }
  }

  override fun onPause() {
    super.onPause()
    exoPlayer.pause()
  }

  override fun onDestroy() {
    super.onDestroy()
    viewModel.releaseCache()
    exoPlayer.stop()
    exoPlayer.release()
  }

  /**
   * This launches the system media picker and stores the resulting URI.
   */
  private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) {
      Log.d("PhotoPicker", "Selected URI: $uri")
      viewModel.selectedVideo = uri
      viewModel.updateMediaSource(this)
    } else {
      Log.d("PhotoPicker", "No media selected")
    }
  }
}

@Composable
fun LabeledButton(buttonLabel: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Button(onClick = onClick, modifier = modifier) {
    Text(buttonLabel)
  }
}

@OptIn(UnstableApi::class)
@Composable
fun ExoVideoView(source: MediaSource, exoPlayer: ExoPlayer, modifier: Modifier = Modifier) {
  exoPlayer.playWhenReady = false
  exoPlayer.setMediaSource(source)
  exoPlayer.prepare()
  AndroidView(factory = { context ->
    PlayerView(context).apply {
      player = exoPlayer
    }
  }, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  SignalTheme {
    LabeledButton("Preview Render") {}
  }
}
