/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.thoughtcrime.video.app.ui.composables.LabeledButton

@Composable
fun VideoSelectionScreen(
  onSelectVideo: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier.fillMaxSize()
  ) {
    Text(
      text = "Video Transcode Demo",
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.padding(bottom = 24.dp)
    )
    LabeledButton("Select Video", onClick = onSelectVideo)
  }
}

@Preview(showBackground = true)
@Composable
private fun VideoSelectionScreenPreview() {
  VideoSelectionScreen(onSelectVideo = {})
}
