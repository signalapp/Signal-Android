/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.signal.core.util.logging.AndroidLogger
import org.signal.core.util.logging.Log
import org.thoughtcrime.video.app.playback.PlaybackTestActivity
import org.thoughtcrime.video.app.transcode.TranscodeTestActivity
import org.thoughtcrime.video.app.ui.composables.LabeledButton
import org.thoughtcrime.video.app.ui.theme.SignalTheme

/**
 * Main activity for this sample app.
 */
class MainActivity : AppCompatActivity() {
  companion object {
    private val TAG = Log.tag(MainActivity::class.java)
    private var appLaunch = true
  }

  private val sharedPref: SharedPreferences by lazy {
    getSharedPreferences(
      getString(R.string.preference_file_key),
      Context.MODE_PRIVATE
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.initialize(AndroidLogger)

    val startPlaybackScreen = { saveChoice: Boolean -> proceed(Screen.TEST_PLAYBACK, saveChoice) }
    val startTranscodeScreen = { saveChoice: Boolean -> proceed(Screen.TEST_TRANSCODE, saveChoice) }
    setContent {
      Body(startPlaybackScreen, startTranscodeScreen)
    }
    refreshMediaProviderForExternalStorage(this, arrayOf("video/*"))
    if (appLaunch) {
      appLaunch = false
      getLaunchChoice()?.let {
        proceed(it, false)
      }
    }
  }

  @Composable
  private fun Body(startPlaybackScreen: (Boolean) -> Unit, startTranscodeScreen: (Boolean) -> Unit) {
    var rememberChoice by remember { mutableStateOf(getLaunchChoice() != null) }
    SignalTheme {
      Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          LabeledButton("Test Playback") {
            startPlaybackScreen(rememberChoice)
          }
          LabeledButton("Test Transcode") {
            startTranscodeScreen(rememberChoice)
          }
          Row(
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = rememberChoice,
              onCheckedChange = { isChecked ->
                rememberChoice = isChecked
                if (!isChecked) {
                  clearLaunchChoice()
                }
              }
            )
            Text(text = "Remember & Skip This Screen", style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    }
  }

  private fun getLaunchChoice(): Screen? {
    val screenName = sharedPref.getString(getString(R.string.preference_activity_shortcut_key), null) ?: return null
    return Screen.valueOf(screenName)
  }

  private fun clearLaunchChoice() {
    with(sharedPref.edit()) {
      remove(getString(R.string.preference_activity_shortcut_key))
      apply()
    }
  }

  private fun saveLaunchChoice(choice: Screen) {
    with(sharedPref.edit()) {
      putString(getString(R.string.preference_activity_shortcut_key), choice.name)
      apply()
    }
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

  private fun proceed(screen: Screen, saveChoice: Boolean) {
    if (saveChoice) {
      saveLaunchChoice(screen)
    }
    when (screen) {
      Screen.TEST_PLAYBACK -> startActivity(Intent(this, PlaybackTestActivity::class.java))
      Screen.TEST_TRANSCODE -> startActivity(Intent(this, TranscodeTestActivity::class.java))
    }
  }

  private enum class Screen {
    TEST_PLAYBACK,
    TEST_TRANSCODE
  }

  @Preview
  @Composable
  private fun PreviewBody() {
    Body({}, {})
  }
}
