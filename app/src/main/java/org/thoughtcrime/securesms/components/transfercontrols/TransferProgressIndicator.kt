/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.transfercontrols

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.clickableContainer
import org.thoughtcrime.securesms.R

/**
 * A button that can be used to start, cancel, show progress, and show completion of a data transfer.
 */
@Composable
fun TransferProgressIndicator(
  state: TransferProgressState,
  modifier: Modifier = Modifier.size(48.dp)
) {
  when (state) {
    is TransferProgressState.Ready -> StartTransferButton(state, modifier)
    is TransferProgressState.InProgress -> ProgressIndicator(state, modifier)
    is TransferProgressState.Complete -> CompleteIcon(state, modifier)
  }
}

@Composable
private fun StartTransferButton(
  state: TransferProgressState.Ready,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clickableContainer(
        contentDescription = state.startButtonContentDesc,
        onClickLabel = state.startButtonOnClickLabel,
        onClick = state.onStartClick
      )
  ) {
    Icon(
      painter = state.iconPainter,
      tint = MaterialTheme.colorScheme.onSurface,
      contentDescription = null,
      modifier = Modifier
        .matchParentSize()
        .padding(12.dp)
    )
  }
}

@Composable
private fun ProgressIndicator(
  state: TransferProgressState.InProgress,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clickableContainer(
        contentDescription = null,
        onClickLabel = state.cancelButtonOnClickLabel,
        onClick = state.onCancelClick
      )
      .padding(8.dp)
  ) {
    Icon(
      painter = painterResource(R.drawable.symbol_stop_24),
      tint = MaterialTheme.colorScheme.onSurface,
      contentDescription = null,
      modifier = Modifier
        .matchParentSize()
        .padding(6.dp)
    )

    CircularProgressIndicator(
      progress = { state.progress },
      strokeWidth = 2.dp,
      strokeCap = StrokeCap.Round,
      trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .matchParentSize()
        .clearAndSetSemantics {
          contentDescription = state.cancelButtonContentDesc
        }
    )
  }
}

@Composable
private fun CompleteIcon(
  state: TransferProgressState.Complete,
  modifier: Modifier = Modifier
) {
  Icon(
    painter = state.iconPainter,
    tint = MaterialTheme.colorScheme.onSurface,
    contentDescription = state.iconContentDesc,
    modifier = modifier.padding(12.dp)
  )
}

sealed interface TransferProgressState {
  data class Ready(
    val iconPainter: Painter,
    val startButtonContentDesc: String,
    val startButtonOnClickLabel: String,
    val onStartClick: () -> Unit
  ) : TransferProgressState

  data class InProgress(
    val progress: Float,
    val cancelButtonContentDesc: String,
    val cancelButtonOnClickLabel: String,
    val onCancelClick: () -> Unit = {}
  ) : TransferProgressState

  data class Complete(
    val iconPainter: Painter,
    val iconContentDesc: String
  ) : TransferProgressState
}
