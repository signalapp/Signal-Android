/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.transfercontrols

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.clickableContainer

/**
 * A button that can be used to start, cancel, show progress, and show completion of a data transfer.
 */
@Composable
fun TransferProgressIndicator(
  state: TransferProgressState,
  modifier: Modifier = Modifier.size(48.dp)
) {
  AnimatedContent(
    targetState = state,
    transitionSpec = {
      val startDelay = 200
      val enterTransition = fadeIn(tween(delayMillis = startDelay, durationMillis = 500)) + scaleIn(tween(delayMillis = startDelay, durationMillis = 400))
      val exitTransition = fadeOut(tween(delayMillis = startDelay, durationMillis = 600)) + scaleOut(tween(delayMillis = startDelay, durationMillis = 800))
      enterTransition
        .togetherWith(exitTransition)
        .using(SizeTransform(clip = false))
    }
  ) { targetState ->
    when (targetState) {
      is TransferProgressState.Ready -> StartTransferButton(targetState, modifier)
      is TransferProgressState.InProgress -> ProgressIndicator(targetState, modifier)
      is TransferProgressState.Complete -> CompleteIcon(targetState, modifier)
    }
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
      imageVector = state.icon,
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
      .then(
        if (state.cancelAction != null) {
          Modifier.clickableContainer(
            contentDescription = null,
            onClickLabel = state.cancelAction.onClickLabel,
            onClick = state.cancelAction.onClick
          )
        } else {
          Modifier
        }
      )
      .padding(10.dp)
  ) {
    state.icon?.let { icon ->
      Icon(
        imageVector = icon,
        tint = MaterialTheme.colorScheme.onSurface,
        contentDescription = null,
        modifier = Modifier
          .matchParentSize()
          .padding(6.dp)
      )
    }

    val indicatorModifier = Modifier
      .matchParentSize()
      .then(
        if (state.cancelAction != null) {
          Modifier.clearAndSetSemantics {
            contentDescription = state.cancelAction.contentDesc
          }
        } else {
          Modifier
        }
      )

    val progress = state.progress
    if (progress == null) {
      CircularProgressIndicator(
        strokeWidth = 2.dp,
        strokeCap = StrokeCap.Round,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = indicatorModifier
      )
    } else {
      CircularProgressIndicator(
        progress = { progress },
        strokeWidth = 2.dp,
        strokeCap = StrokeCap.Round,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = indicatorModifier
      )
    }
  }
}

@Composable
private fun CompleteIcon(
  state: TransferProgressState.Complete,
  modifier: Modifier = Modifier
) {
  Icon(
    imageVector = state.icon,
    tint = MaterialTheme.colorScheme.onSurface,
    contentDescription = state.iconContentDesc,
    modifier = modifier.padding(12.dp)
  )
}

sealed interface TransferProgressState {
  data class Ready(
    val icon: ImageVector,
    val startButtonContentDesc: String,
    val startButtonOnClickLabel: String,
    val onStartClick: () -> Unit
  ) : TransferProgressState

  data class InProgress(
    val icon: ImageVector? = null,
    val progress: Float? = null,
    val cancelAction: CancelAction? = null
  ) : TransferProgressState {

    data class CancelAction(
      val contentDesc: String,
      val onClickLabel: String,
      val onClick: () -> Unit
    )
  }

  data class Complete(
    val icon: ImageVector,
    val iconContentDesc: String
  ) : TransferProgressState
}
