/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode.composables

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import org.thoughtcrime.video.app.transcode.TranscodeSettings
import org.thoughtcrime.video.app.transcode.TranscodingState
import org.thoughtcrime.video.app.transcode.VideoResolution
import org.thoughtcrime.video.app.ui.composables.LabeledButton

@Composable
fun TranscodingScreen(
  state: TranscodingState,
  onCancel: () -> Unit,
  onReset: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  if (state is TranscodingState.InProgress) {
    DisposableEffect(Unit) {
      val window = (context as? android.app.Activity)?.window
      window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      onDispose {
        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }
  }

  Column(
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp)
  ) {
    when (state) {
      is TranscodingState.Idle -> {
        Text("Preparing...", style = MaterialTheme.typography.bodyLarge)
      }

      is TranscodingState.InProgress -> {
        Text("Transcoding: ${state.percent}%", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
          progress = { state.percent / 100f },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        LabeledButton("Cancel", onClick = onCancel)
      }

      is TranscodingState.Completed -> {
        Text("Transcoding Complete", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        val originalFormatted = Formatter.formatFileSize(context, state.originalSize)
        val outputFormatted = Formatter.formatFileSize(context, state.outputSize)
        val ratio = if (state.originalSize > 0) {
          "%.1f%%".format(state.outputSize.toFloat() / state.originalSize * 100)
        } else {
          "N/A"
        }

        Text("File Sizes", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        StatsText("Original: $originalFormatted")
        StatsText("Output: $outputFormatted ($ratio of original)")
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(12.dp))

        Text("Transcode Settings", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        if (state.settings.isPreset) {
          StatsText("Mode: Preset (${state.settings.presetName})")
        } else {
          StatsText("Mode: Custom")
        }
        StatsText("Resolution: ${state.settings.videoResolution.name} (${state.settings.videoResolution.shortEdge}p)")
        StatsText("Video bitrate: ${"%.2f".format(state.settings.videoMegaBitrate)} Mbps")
        StatsText("Audio bitrate: ${state.settings.audioKiloBitrate} kbps")
        StatsText("Codec: ${if (state.settings.useHevc) "HEVC (H.265)" else "AVC (H.264)"}")
        StatsText("Fast start: ${if (state.settings.enableFastStart) "Yes" else "No"}")
        StatsText("Audio remux: ${if (state.settings.enableAudioRemux) "Yes" else "No"}")
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = "Saved to Downloads:\n${state.outputUri}",
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        LabeledButton("Play Original", onClick = {
          val originalUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", state.originalFile)
          val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(originalUri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
          context.startActivity(intent)
        })
        Spacer(modifier = Modifier.height(8.dp))
        LabeledButton("Play Transcoded", onClick = {
          val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(state.outputUri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
          context.startActivity(intent)
        })
        Spacer(modifier = Modifier.height(8.dp))
        LabeledButton("Start Over", onClick = onReset)
      }

      is TranscodingState.Failed -> {
        Text(
          "Transcoding Failed",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = state.error)
        Spacer(modifier = Modifier.height(24.dp))
        LabeledButton("Start Over", onClick = onReset)
      }

      is TranscodingState.Cancelled -> {
        Text("Transcoding Cancelled", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))
        LabeledButton("Start Over", onClick = onReset)
      }
    }
  }
}

@Composable
private fun StatsText(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp
  )
}

@Preview(showBackground = true)
@Composable
private fun TranscodingScreenInProgressPreview() {
  TranscodingScreen(
    state = TranscodingState.InProgress(42),
    onCancel = {},
    onReset = {}
  )
}

@Preview(showBackground = true)
@Composable
private fun TranscodingScreenCompletedPreview() {
  TranscodingScreen(
    state = TranscodingState.Completed(
      outputUri = Uri.parse("content://downloads/123"),
      originalFile = java.io.File("/tmp/original.mp4"),
      originalSize = 52_428_800L,
      outputSize = 12_582_912L,
      settings = TranscodeSettings(
        isPreset = true,
        presetName = "LEVEL_2",
        videoResolution = VideoResolution.SD,
        videoMegaBitrate = 2.0f,
        audioKiloBitrate = 192,
        useHevc = false,
        enableFastStart = true,
        enableAudioRemux = true
      )
    ),
    onCancel = {},
    onReset = {}
  )
}

@Preview(showBackground = true)
@Composable
private fun TranscodingScreenFailedPreview() {
  TranscodingScreen(
    state = TranscodingState.Failed("Encoder initialization failed"),
    onCancel = {},
    onReset = {}
  )
}
