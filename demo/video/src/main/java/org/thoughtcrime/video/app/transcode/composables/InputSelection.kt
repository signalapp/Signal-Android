/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.video.app.transcode.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.thoughtcrime.video.app.ui.composables.LabeledButton

/**
 * A view that prompts you to select input videos for transcoding.
 */
@Composable
fun SelectInput(modifier: Modifier = Modifier, onClick: () -> Unit) {
  Column(
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    LabeledButton("Select Videos", onClick = onClick, modifier = modifier)
  }
}

@Preview
@Composable
private fun InputSelectionPreview() {
  SelectInput { }
}
