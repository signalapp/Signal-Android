/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.transfercontrols

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.clickableContainer
import org.signal.core.util.bytes
import org.thoughtcrime.securesms.R
import org.signal.core.ui.R as CoreUiR

private val CENTER_CONTROL_SIZE = 44.dp
private val NO_FONT_PADDING = PlatformTextStyle(includeFontPadding = false)

/**
 * Compose rendering of the attachment transfer controls (start/cancel/progress) that overlay a media thumbnail.
 *
 * This renders a [TransferControlsRenderState] produced by [TransferControls.deriveRenderState]. All state derivation lives in
 * [TransferControls]; this function is purely presentational so the various visual states can be previewed and tested directly.
 */
@Composable
fun TransferControls(
  state: TransferControlsRenderState,
  modifier: Modifier = Modifier,
  onStartClick: () -> Unit = {},
  onCancelClick: () -> Unit = {},
  onPlayClick: () -> Unit = {}
) {
  Box(modifier = modifier.fillMaxSize()) {
    when (state) {
      is TransferControlsRenderState.Gone -> Unit

      is TransferControlsRenderState.Pending -> Content(
        state = TransferProgressState.Ready(
          icon = arrowIcon(state.isUpload),
          startButtonContentDesc = startContentDescription(state.isUpload),
          startButtonOnClickLabel = startContentDescription(state.isUpload),
          onStartClick = onStartClick
        ),
        placement = state.placement,
        showPlayButton = state.showPlayButton,
        centerLabel = state.itemCount?.let { pluralStringResource(R.plurals.TransferControlView_n_items, it, it) },
        cornerText = state.sizeBytes?.toUnitString(),
        onPlayClick = onPlayClick
      )

      is TransferControlsRenderState.Retry -> Content(
        state = TransferProgressState.Ready(
          icon = arrowIcon(state.isUpload),
          startButtonContentDesc = startContentDescription(state.isUpload),
          startButtonOnClickLabel = startContentDescription(state.isUpload),
          onStartClick = onStartClick
        ),
        placement = TransferControls.Placement.CORNER,
        showPlayButton = false,
        centerLabel = null,
        cornerText = stringResource(R.string.NetworkFailure__retry),
        onPlayClick = onPlayClick
      )

      is TransferControlsRenderState.InProgress -> {
        val cancelLabel = stringResource(android.R.string.cancel)
        val label = state.label
        val progressFormat = stringResource(R.string.TransferControlView__download_progress_s_s)
        val cornerTextReserveWidthFor = (label as? TransferControls.ProgressLabel.Bytes)?.let { byteLabel ->
          val unit = byteLabel.total.getLargestNonZeroSize()
          val widestCompleted = byteLabel.total.toUnitString(unit, padDecimals = true, withUnit = false)
          val totalText = byteLabel.total.toUnitString(unit)
          progressFormat.format(widestCompleted, totalText)
        }

        Content(
          state = TransferProgressState.InProgress(
            progress = state.progress,
            cancelAction = if (state.cancelable) {
              TransferProgressState.InProgress.CancelAction(
                contentDesc = cancelLabel,
                onClickLabel = cancelLabel,
                onClick = onCancelClick
              )
            } else {
              null
            }
          ),
          placement = state.placement,
          showPlayButton = state.showPlayButton,
          centerLabel = null,
          cornerText = label?.let { progressLabelText(it) },
          cornerTextReserveWidthFor = cornerTextReserveWidthFor,
          onPlayClick = onPlayClick
        )
      }
    }
  }
}

@Composable
private fun BoxScope.Content(
  state: TransferProgressState,
  placement: TransferControls.Placement,
  showPlayButton: Boolean,
  centerLabel: String?,
  cornerText: String?,
  onPlayClick: () -> Unit,
  cornerTextReserveWidthFor: String? = null
) {
  val controlInCenter = placement == TransferControls.Placement.CENTER
  val controlInCorner = placement == TransferControls.Placement.CORNER

  if (controlInCenter || showPlayButton || centerLabel != null) {
    val centerStartReadyState = if (controlInCenter) state as? TransferProgressState.Ready else null
    Pill(
      modifier = Modifier.align(Alignment.Center),
      cornerRadius = 24.dp,
      onClick = centerStartReadyState?.onStartClick,
      onClickContentDescription = centerStartReadyState?.startButtonContentDesc,
      onClickLabel = centerStartReadyState?.startButtonOnClickLabel
    ) {
      if (controlInCenter) {
        OnMediaIndicator(centerStartReadyState?.copy(onStartClick = null) ?: state, CENTER_CONTROL_SIZE)
      }

      if (showPlayButton) {
        PlayButton(onPlayClick)
      }

      if (centerLabel != null) {
        Text(
          text = centerLabel,
          style = MaterialTheme.typography.bodyLarge.copy(platformStyle = NO_FONT_PADDING),
          color = colorResource(CoreUiR.color.signal_colorOnCustom),
          maxLines = 1,
          modifier = Modifier.padding(end = 12.dp)
        )
      }
    }
  }

  if (controlInCorner || cornerText != null) {
    val cornerStartReadyState = if (controlInCorner) state as? TransferProgressState.Ready else null
    Pill(
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(4.dp),
      cornerRadius = 16.dp,
      onClick = cornerStartReadyState?.onStartClick,
      onClickContentDescription = cornerStartReadyState?.startButtonContentDesc,
      onClickLabel = cornerStartReadyState?.startButtonOnClickLabel
    ) {
      if (controlInCorner) {
        OnMediaIndicator(cornerStartReadyState?.copy(onStartClick = null) ?: state, 32.dp)
      }

      if (cornerText != null) {
        if (!controlInCorner) {
          Spacer(modifier = Modifier.width(8.dp))
        }

        CornerText(
          text = cornerText,
          reserveWidthFor = cornerTextReserveWidthFor
        )
      }
    }
  }
}

@Composable
private fun Pill(
  modifier: Modifier = Modifier,
  cornerRadius: Dp,
  onClick: (() -> Unit)? = null,
  onClickContentDescription: String? = null,
  onClickLabel: String? = null,
  content: @Composable RowScope.() -> Unit
) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(cornerRadius))
      .background(colorResource(CoreUiR.color.signal_colorTransparentInverse4))
      .then(
        if (onClick != null) {
          Modifier.clickableContainer(
            contentDescription = onClickContentDescription,
            onClickLabel = onClickLabel ?: "",
            onClick = onClick
          )
        } else {
          Modifier
        }
      ),
    verticalAlignment = Alignment.CenterVertically
  ) {
    content()
  }
}

/**
 * Wraps [TransferProgressIndicator] in a color scheme override so it adopts the on-media ("OnCustom") palette rather than the
 * default surface palette, matching the legacy view's appearance over thumbnails.
 */
@Composable
private fun OnMediaIndicator(state: TransferProgressState, size: Dp) {
  MaterialTheme(
    colorScheme = MaterialTheme.colorScheme.copy(
      onSurface = colorResource(CoreUiR.color.signal_colorOnCustom),
      surfaceContainerHighest = colorResource(CoreUiR.color.signal_colorTransparent2)
    )
  ) {
    TransferProgressIndicator(state = state, size = size)
  }
}

@Composable
private fun PlayButton(onPlayClick: () -> Unit) {
  val description = stringResource(R.string.ThumbnailView_Play_video_description)
  Icon(
    imageVector = ImageVector.vectorResource(R.drawable.triangle_right),
    contentDescription = description,
    tint = colorResource(CoreUiR.color.signal_colorOnCustom),
    modifier = Modifier
      .size(CENTER_CONTROL_SIZE)
      .clickableContainer(
        contentDescription = description,
        onClickLabel = description,
        onClick = onPlayClick
      )
      .padding(10.dp)
  )
}

@Composable
private fun CornerText(
  text: String,
  reserveWidthFor: String?
) {
  val reserving = reserveWidthFor != null
  val style = MaterialTheme.typography.labelSmall.copy(
    fontWeight = FontWeight.Light,
    platformStyle = NO_FONT_PADDING
  )
  val effectiveStyle = if (reserving) style.copy(fontFeatureSettings = "tnum") else style

  val widthModifier = if (reserving) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val reservedWidth = remember(reserveWidthFor, effectiveStyle, density) {
      with(density) { measurer.measure(reserveWidthFor, effectiveStyle).size.width.toDp() }
    }
    Modifier.width(reservedWidth)
  } else {
    Modifier
  }

  Text(
    text = text,
    style = effectiveStyle,
    color = colorResource(CoreUiR.color.signal_colorOnCustom),
    maxLines = 1,
    textAlign = if (reserving) TextAlign.End else null,
    modifier = Modifier
      .padding(end = 8.dp, top = 8.dp, bottom = 8.dp)
      .then(widthModifier)
  )
}

@Composable
private fun arrowIcon(isUpload: Boolean): ImageVector {
  return ImageVector.vectorResource(if (isUpload) R.drawable.symbol_arrow_up_24 else R.drawable.symbol_arrow_down_24)
}

@Composable
private fun startContentDescription(isUpload: Boolean): String {
  return stringResource(if (isUpload) R.string.TransferControlView__upload else R.string.TransferControlView__download)
}

@Composable
private fun progressLabelText(label: TransferControls.ProgressLabel): String {
  return when (label) {
    is TransferControls.ProgressLabel.Processing -> stringResource(R.string.TransferControlView__processing)
    is TransferControls.ProgressLabel.Bytes -> {
      val unit = label.total.getLargestNonZeroSize()
      stringResource(
        R.string.TransferControlView__download_progress_s_s,
        label.completed.toUnitString(unit, padDecimals = true, withUnit = false),
        label.total.toUnitString(unit)
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun TransferControlsPendingSinglePreview() {
  Previews.Preview {
    PreviewSurface {
      TransferControls(
        state = TransferControlsRenderState.Pending(
          isUpload = false,
          placement = TransferControls.Placement.CENTER,
          showPlayButton = false,
          sizeBytes = (2 * 1024 * 1024L).bytes
        )
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun TransferControlsPendingGalleryPreview() {
  Previews.Preview {
    PreviewSurface {
      TransferControls(
        state = TransferControlsRenderState.Pending(
          isUpload = false,
          placement = TransferControls.Placement.CENTER,
          showPlayButton = false,
          itemCount = 3,
          sizeBytes = (6 * 1024 * 1024L).bytes
        )
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun TransferControlsPendingPlayableVideoPreview() {
  Previews.Preview {
    PreviewSurface {
      TransferControls(
        state = TransferControlsRenderState.Pending(
          isUpload = false,
          placement = TransferControls.Placement.CORNER,
          showPlayButton = true,
          sizeBytes = (12 * 1024 * 1024L).bytes
        )
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun TransferControlsDownloadingSinglePreview() {
  Previews.Preview {
    PreviewSurface {
      TransferControls(
        state = TransferControlsRenderState.InProgress(
          isUpload = false,
          placement = TransferControls.Placement.CORNER,
          progress = 0.45f,
          showPlayButton = false,
          cancelable = true,
          label = TransferControls.ProgressLabel.Bytes((1024 * 1024L).bytes, (2 * 1024 * 1024L).bytes)
        )
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun TransferControlsAwaitingPrimaryPreview() {
  Previews.Preview {
    PreviewSurface {
      TransferControls(
        state = TransferControlsRenderState.InProgress(
          isUpload = false,
          placement = TransferControls.Placement.CENTER,
          progress = null,
          showPlayButton = false,
          cancelable = false,
          label = null
        )
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun TransferControlsRetryPreview() {
  Previews.Preview {
    PreviewSurface {
      TransferControls(
        state = TransferControlsRenderState.Retry(isUpload = false)
      )
    }
  }
}

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
  Box(
    modifier = Modifier
      .size(150.dp)
      .background(colorResource(CoreUiR.color.signal_colorTransparent2))
  ) {
    content()
  }
}
