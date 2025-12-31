/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import org.thoughtcrime.video.app.transcode.TranscodeWorker
import org.thoughtcrime.video.app.ui.composables.LabeledButton

/**
 * A view that shows the current encodes in progress.
 */
@Composable
fun TranscodingJobProgress(transcodingJobs: List<WorkState>, resetButtonOnClick: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    transcodingJobs.forEach { workInfo ->
      val currentProgress = workInfo.progress
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 16.dp)
      ) {
        val progressIndicatorModifier = Modifier.weight(3f)
        Text(
          text = "Job ${workInfo.id.takeLast(4)}",
          modifier = Modifier
            .padding(end = 16.dp)
            .weight(1f)
        )
        if (workInfo.state.isFinished) {
          Text(text = workInfo.state.toString(), textAlign = TextAlign.Center, modifier = progressIndicatorModifier)
        } else if (currentProgress >= 0) {
          LinearProgressIndicator(progress = currentProgress / 100f, modifier = progressIndicatorModifier)
        } else {
          LinearProgressIndicator(modifier = progressIndicatorModifier)
        }
      }
    }
    LabeledButton("Reset/Cancel", onClick = resetButtonOnClick)
  }
}

data class WorkState(val id: String, val state: WorkInfo.State, val progress: Int) {
  companion object {
    fun fromInfo(info: WorkInfo): WorkState {
      return WorkState(info.id.toString(), info.state, info.progress.getInt(TranscodeWorker.KEY_PROGRESS, -1))
    }
  }
}

@Preview
@Composable
private fun ProgressScreenPreview() {
  TranscodingJobProgress(
    listOf(
      WorkState("abcde", WorkInfo.State.RUNNING, 47),
      WorkState("fghij", WorkInfo.State.ENQUEUED, -1),
      WorkState("klmnop", WorkInfo.State.FAILED, -1)
    ),
    resetButtonOnClick = {}
  )
}
