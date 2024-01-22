/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.signal.core.util.logging.Log
import org.thoughtcrime.video.app.playback.PlaybackTestActivity
import org.thoughtcrime.video.app.transcode.TranscodeTestActivity
import org.thoughtcrime.video.app.ui.composables.LabeledButton
import org.thoughtcrime.video.app.ui.theme.SignalTheme

/**
 * Main activity for this sample app.
 */
class MainActivity : AppCompatActivity() {
  private val TAG = Log.tag(MainActivity::class.java)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val startPlaybackScreen = Intent(this, PlaybackTestActivity::class.java)
    val startTranscodeScreen = Intent(this, TranscodeTestActivity::class.java)
    setContent {
      SignalTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            LabeledButton("Test Playback") { startActivity(startPlaybackScreen) }
            LabeledButton("Test Transcode") { startActivity(startTranscodeScreen) }
          }
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    refreshMediaProviderForExternalStorage(this, arrayOf("video/*"))
  }

  private fun refreshMediaProviderForExternalStorage(context: Context, mimeTypes: Array<String>) {
    val rootPath = Environment.getExternalStorageDirectory().absolutePath
    MediaScannerConnection.scanFile(
      context,
      arrayOf<String>(rootPath),
      mimeTypes
    ) { _, _ ->
      Log.i(TAG, "Re-scan of external storage for media completed.")
    }
  }
}
