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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.clickableContainer
import org.thoughtcrime.securesms.R
import org.signal.core.ui.R as CoreUiR

/**
 * A button that can be used to start, cancel, show progress, and show completion of a data transfer.
 */
@Composable
fun TransferProgressIndicator(
  state: TransferProgressState,
  modifier: Modifier = Modifier,
  size: Dp = 48.dp
) {
  // Internal paddings are tuned for a 48dp control; scale them with [size] so the icon/ring proportions are preserved at
  // other sizes. At 48dp this is a no-op, so existing callers are unaffected.
  val scale = size / 48.dp
  val sizedModifier = modifier.size(size)

  AnimatedContent(
    targetState = state,
    // Key on the state type, not the value, so that progress updates within InProgress recompose in place instead of
    // re-triggering the enter/exit transition on every tick (which would prevent the determinate fill from ever settling).
    contentKey = { it::class },
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
      is TransferProgressState.Ready -> StartTransferButton(targetState, sizedModifier, scale)
      is TransferProgressState.InProgress -> ProgressIndicator(targetState, sizedModifier, scale)
      is TransferProgressState.Complete -> CompleteIcon(targetState, sizedModifier, scale)
    }
  }
}

@Composable
private fun StartTransferButton(
  state: TransferProgressState.Ready,
  modifier: Modifier = Modifier,
  scale: Float = 1f
) {
  Box(
    modifier = modifier
      .then(
        if (state.onStartClick != null) {
          Modifier.clickableContainer(
            contentDescription = state.startButtonContentDesc,
            onClickLabel = state.startButtonOnClickLabel,
            onClick = state.onStartClick
          )
        } else {
          Modifier
        }
      )
  ) {
    Icon(
      imageVector = state.icon,
      tint = MaterialTheme.colorScheme.onSurface,
      contentDescription = null,
      modifier = Modifier
        .matchParentSize()
        .padding(12.dp * scale)
    )
  }
}

@Composable
private fun ProgressIndicator(
  state: TransferProgressState.InProgress,
  modifier: Modifier = Modifier,
  scale: Float = 1f
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
      .padding(10.dp * scale)
  ) {
    state.icon?.let { icon ->
      Icon(
        imageVector = icon,
        tint = MaterialTheme.colorScheme.onSurface,
        contentDescription = null,
        modifier = Modifier
          .matchParentSize()
          .padding(6.dp * scale)
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

    if (state.cancelAction != null) {
      Box(
        modifier = Modifier
          .align(Alignment.Center)
          .fillMaxSize(0.3f)
          .clip(RoundedCornerShape(percent = 15))
          .background(MaterialTheme.colorScheme.onSurface)
      )
    }
  }
}

@Composable
private fun CompleteIcon(
  state: TransferProgressState.Complete,
  modifier: Modifier = Modifier,
  scale: Float = 1f
) {
  Icon(
    imageVector = state.icon,
    tint = MaterialTheme.colorScheme.onSurface,
    contentDescription = state.iconContentDesc,
    modifier = modifier.padding(12.dp * scale)
  )
}

sealed interface TransferProgressState {
  data class Ready(
    val icon: ImageVector,
    val startButtonContentDesc: String,
    val startButtonOnClickLabel: String,
    val onStartClick: (() -> Unit)?
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

@DayNightPreviews
@Composable
private fun TransferProgressIndicatorReadyPreview() {
  Previews.Preview {
    PreviewBackdrop {
      TransferProgressIndicator(
        state = TransferProgressState.Ready(
          icon = ImageVector.vectorResource(R.drawable.symbol_arrow_down_24),
          startButtonContentDesc = "",
          startButtonOnClickLabel = "",
          onStartClick = {}
        )
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun TransferProgressIndicatorIndeterminatePreview() {
  Previews.Preview {
    PreviewBackdrop {
      TransferProgressIndicator(
        state = TransferProgressState.InProgress(
          icon = ImageVector.vectorResource(R.drawable.symbol_arrow_down_24),
          progress = null,
          cancelAction = null
        )
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun TransferProgressIndicatorDeterminatePreview() {
  Previews.Preview {
    PreviewBackdrop {
      TransferProgressIndicator(
        state = TransferProgressState.InProgress(
          icon = ImageVector.vectorResource(R.drawable.symbol_arrow_down_24),
          progress = 0.4f,
          cancelAction = null
        )
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun TransferProgressIndicatorCancelablePreview() {
  Previews.Preview {
    PreviewBackdrop {
      TransferProgressIndicator(
        state = TransferProgressState.InProgress(
          progress = 0.4f,
          cancelAction = TransferProgressState.InProgress.CancelAction(
            contentDesc = "",
            onClickLabel = "",
            onClick = {}
          )
        )
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun TransferProgressIndicatorCompletePreview() {
  Previews.Preview {
    PreviewBackdrop {
      TransferProgressIndicator(
        state = TransferProgressState.Complete(
          icon = ImageVector.vectorResource(R.drawable.symbol_check_white_24),
          iconContentDesc = ""
        )
      )
    }
  }
}

@Composable
private fun PreviewBackdrop(content: @Composable () -> Unit) {
  Box(
    modifier = Modifier
      .size(96.dp)
      .background(colorResource(CoreUiR.color.signal_colorTransparent2))
  ) {
    content()
  }
}
