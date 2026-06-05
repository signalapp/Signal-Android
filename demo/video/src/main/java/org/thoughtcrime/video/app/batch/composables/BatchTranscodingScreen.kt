/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.batch.composables

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.thoughtcrime.video.app.batch.BatchTranscodeResult
import org.thoughtcrime.video.app.batch.BatchTranscodingState
import org.thoughtcrime.video.app.ui.composables.LabeledButton

@Composable
fun BatchTranscodingScreen(
  state: BatchTranscodingState,
  onCancel: () -> Unit,
  onDone: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  if (state is BatchTranscodingState.InProgress) {
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
      is BatchTranscodingState.Idle -> {
        Text("Preparing batch...", style = MaterialTheme.typography.bodyLarge)
      }

      is BatchTranscodingState.InProgress -> {
        Text(
          "Batch Transcoding",
          style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          "${state.completedCount} / ${state.totalCount} complete",
          style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
          progress = { state.completedCount.toFloat() / state.totalCount },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Video: ${state.currentVideoName}", style = MaterialTheme.typography.bodyMedium)
        Text("Profile: ${state.currentProfileName}", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Current transcode: ${state.currentTranscodePercent}%", style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
          progress = { state.currentTranscodePercent / 100f },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        LabeledButton("Cancel", onClick = onCancel)
      }

      is BatchTranscodingState.Completed -> {
        Text("Batch Complete", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          "${state.totalTranscoded} succeeded, ${state.totalFailed} failed",
          style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        val resultsByVideo = state.results.groupBy { it.videoName }

        resultsByVideo.forEach { (videoName, results) ->
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
          val originalSize = results.firstOrNull { it.success }?.originalSize ?: 0L
          val originalFormatted = Formatter.formatFileSize(context, originalSize)
          val originalUri = results.first().originalUri

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = videoName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Original: $originalFormatted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            OutlinedButton(
              onClick = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                  setDataAndType(originalUri, "video/*")
                  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
              }
            ) {
              Text("Play", fontSize = 11.sp)
            }
          }
          Spacer(modifier = Modifier.height(8.dp))

          results.forEach { result ->
            ResultRow(result = result)
            Spacer(modifier = Modifier.height(4.dp))
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
        LabeledButton("Done", onClick = onDone)
      }

      is BatchTranscodingState.Failed -> {
        Text(
          "Batch Failed",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = state.error)
        Spacer(modifier = Modifier.height(24.dp))
        LabeledButton("Done", onClick = onDone)
      }

      is BatchTranscodingState.Cancelled -> {
        Text("Batch Cancelled", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))
        LabeledButton("Done", onClick = onDone)
      }
    }
  }
}

@Composable
private fun ResultRow(result: BatchTranscodeResult) {
  val context = LocalContext.current

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 8.dp)
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = result.profileName,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp
      )
      if (result.success) {
        val outputFormatted = Formatter.formatFileSize(context, result.outputSize)
        val ratio = if (result.originalSize > 0) {
          "%.0f%%".format(result.outputSize.toFloat() / result.originalSize * 100)
        } else {
          ""
        }
        Text(
          text = "$outputFormatted ($ratio of original)",
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        Text(
          text = "Error: ${result.error}",
          style = MaterialTheme.typography.bodySmall,
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.error
        )
      }
    }

    if (result.success && result.outputUri != null) {
      Spacer(modifier = Modifier.width(8.dp))
      OutlinedButton(
        onClick = {
          val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(result.outputUri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
          context.startActivity(intent)
        }
      ) {
        Text("Play", fontSize = 11.sp)
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun BatchTranscodingInProgressPreview() {
  BatchTranscodingScreen(
    state = BatchTranscodingState.InProgress(
      currentVideoName = "vacation_clip.mp4",
      currentProfileName = "Profile 2: LEVEL_3",
      currentTranscodePercent = 63,
      completedCount = 3,
      totalCount = 9
    ),
    onCancel = {},
    onDone = {}
  )
}

@Preview(showBackground = true)
@Composable
private fun BatchTranscodingCompletedPreview() {
  BatchTranscodingScreen(
    state = BatchTranscodingState.Completed(
      totalTranscoded = 5,
      totalFailed = 1,
      results = listOf(
        BatchTranscodeResult(
          videoName = "vacation_clip.mp4",
          originalUri = Uri.parse("content://media/external/video/100"),
          profileName = "Profile 1: LEVEL_1",
          success = true,
          error = null,
          originalSize = 52_428_800L,
          outputSize = 12_582_912L,
          outputUri = Uri.parse("content://downloads/1")
        ),
        BatchTranscodeResult(
          videoName = "vacation_clip.mp4",
          originalUri = Uri.parse("content://media/external/video/100"),
          profileName = "Profile 2: LEVEL_3",
          success = true,
          error = null,
          originalSize = 52_428_800L,
          outputSize = 25_165_824L,
          outputUri = Uri.parse("content://downloads/2")
        ),
        BatchTranscodeResult(
          videoName = "vacation_clip.mp4",
          originalUri = Uri.parse("content://media/external/video/100"),
          profileName = "Profile 3: 720p/H265/2.5Mbps",
          success = false,
          error = "Encoder initialization failed",
          originalSize = 0,
          outputSize = 0
        ),
        BatchTranscodeResult(
          videoName = "birthday_party.mp4",
          originalUri = Uri.parse("content://media/external/video/101"),
          profileName = "Profile 1: LEVEL_1",
          success = true,
          error = null,
          originalSize = 104_857_600L,
          outputSize = 31_457_280L,
          outputUri = Uri.parse("content://downloads/3")
        ),
        BatchTranscodeResult(
          videoName = "birthday_party.mp4",
          originalUri = Uri.parse("content://media/external/video/101"),
          profileName = "Profile 2: LEVEL_3",
          success = true,
          error = null,
          originalSize = 104_857_600L,
          outputSize = 52_428_800L,
          outputUri = Uri.parse("content://downloads/4")
        ),
        BatchTranscodeResult(
          videoName = "birthday_party.mp4",
          originalUri = Uri.parse("content://media/external/video/101"),
          profileName = "Profile 3: 720p/H265/2.5Mbps",
          success = true,
          error = null,
          originalSize = 104_857_600L,
          outputSize = 41_943_040L,
          outputUri = Uri.parse("content://downloads/5")
        )
      )
    ),
    onCancel = {},
    onDone = {}
  )
}

@Preview(showBackground = true)
@Composable
private fun BatchTranscodingFailedPreview() {
  BatchTranscodingScreen(
    state = BatchTranscodingState.Failed("No video files found in directory"),
    onCancel = {},
    onDone = {}
  )
}

@Preview(showBackground = true)
@Composable
private fun BatchTranscodingCancelledPreview() {
  BatchTranscodingScreen(
    state = BatchTranscodingState.Cancelled,
    onCancel = {},
    onDone = {}
  )
}
