/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediasend.R

@Composable
fun TextStoryHorizontalBar(
  background: Brush,
  onEvent: (TextStoryBarEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier.background(
      color = colorResource(R.color.MediaSend_controls_color),
      shape = RoundedCornerShape(50)
    )
  ) {
    ColorButton(background = background, onEvent = onEvent)
    LinkButton(onEvent = onEvent)
  }
}

@Composable
fun TextStoryVerticalBar(
  background: Brush,
  onEvent: (TextStoryBarEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.background(
      color = colorResource(R.color.MediaSend_controls_color),
      shape = RoundedCornerShape(50)
    )
  ) {
    ColorButton(background = background, onEvent = onEvent)
    LinkButton(onEvent = onEvent)
  }
}

@Composable
private fun ColorButton(
  background: Brush,
  onEvent: (TextStoryBarEvents) -> Unit
) {
  IconButtons.IconButton(
    size = 48.dp,
    onClick = {
      onEvent(TextStoryBarEvents.CycleBackgroundColor)
    }
  ) {
    Box(
      modifier = Modifier
        .size(48.dp)
        .padding(12.dp)
        .background(brush = background, shape = CircleShape)
        .border(2.dp, color = SignalTheme.colors.colorOnCustom, shape = CircleShape)
    )
  }
}

@Composable
private fun LinkButton(
  onEvent: (TextStoryBarEvents) -> Unit
) {
  IconButtons.IconButton(
    size = 48.dp,
    onClick = {
      onEvent(TextStoryBarEvents.AddLink)
    }
  ) {
    Icon(
      imageVector = SignalIcons.Link.imageVector,
      tint = SignalTheme.colors.colorOnCustom,
      contentDescription = stringResource(R.string.TextStoryBar__add_link)
    )
  }
}

@NightPreview
@Composable
private fun TextStoryHorizontalBarPreview() {
  Previews.Preview {
    TextStoryHorizontalBar(
      background = Brush.linearGradient(
        colors = listOf(Color.Red, Color.Green)
      ),
      onEvent = {}
    )
  }
}

@NightPreview
@Composable
private fun TextStoryVerticalBarPreview() {
  Previews.Preview {
    TextStoryVerticalBar(
      background = Brush.linearGradient(
        colors = listOf(Color.Red, Color.Green)
      ),
      onEvent = {}
    )
  }
}
