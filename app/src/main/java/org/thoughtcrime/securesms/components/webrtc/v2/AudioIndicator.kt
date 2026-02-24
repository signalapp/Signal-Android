/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.CallParticipantId
import org.thoughtcrime.securesms.recipients.RecipientId

private val BAR_WIDTH = 3.dp
private val BAR_PADDING = 3.dp
private const val AUDIO_LEVELS_INTERVAL_MS = 200
private val ANIMATION_SPEC = tween<Dp>(durationMillis = AUDIO_LEVELS_INTERVAL_MS, easing = FastOutSlowInEasing)

/**
 * Displays the audio state of a call participant. Shows a muted mic icon when the participant's
 * microphone is disabled, animated audio level bars when speaking, or nothing when mic is on but silent.
 */
@Composable
fun AudioIndicator(
  participant: CallParticipant,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(!participant.isMicrophoneEnabled || participant.audioLevel != null, modifier = modifier) {
    AnimatedContent(
      targetState = participant.isMicrophoneEnabled && participant.audioLevel != null
    ) { showAudioLevel ->
      if (showAudioLevel) {
        AudioLevelBars(participant.audioLevel ?: CallParticipant.AudioLevel.LOWEST)
      } else {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.symbol_mic_slash_24),
          tint = MaterialTheme.colorScheme.onSurface,
          contentDescription = null,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }
}

@Composable
private fun AudioLevelBars(
  audioLevel: CallParticipant.AudioLevel
) {
  val scaleFactor = when (audioLevel) {
    CallParticipant.AudioLevel.LOWEST -> 0.1f
    CallParticipant.AudioLevel.LOW -> 0.3f
    CallParticipant.AudioLevel.MEDIUM -> 0.5f
    CallParticipant.AudioLevel.HIGH -> 0.65f
    CallParticipant.AudioLevel.HIGHEST -> 0.8f
  }

  BoxWithConstraints {
    val maxHeight = maxHeight
    val maxWidth = maxWidth

    val sideBarShrinkFactor = if (audioLevel != CallParticipant.AudioLevel.LOWEST) 0.75f else 1f
    val targetSideBarHeight = maxHeight * scaleFactor * sideBarShrinkFactor
    val targetMiddleBarHeight = maxHeight * scaleFactor

    val sideBarHeight by animateDpAsState(targetSideBarHeight, ANIMATION_SPEC)
    val middleBaHeight by animateDpAsState(targetMiddleBarHeight, ANIMATION_SPEC)
    val barColor = MaterialTheme.colorScheme.onSurface

    Box(
      modifier = Modifier.fillMaxSize().drawWithContent {
        val sideBarHeightPx = sideBarHeight.toPx()
        val middleBarHeightPx = middleBaHeight.toPx()
        val audioLevelWidth = BAR_WIDTH * 3 + BAR_PADDING * 2
        val xOffsetBase = (maxWidth - audioLevelWidth) / 2 + BAR_PADDING / 2

        drawBar(barColor, xOffsetBase.toPx(), sideBarHeightPx)
        drawBar(barColor, (xOffsetBase + BAR_WIDTH + BAR_PADDING).toPx(), middleBarHeightPx)
        drawBar(barColor, (xOffsetBase + (BAR_WIDTH + BAR_PADDING) * 2).toPx(), sideBarHeightPx)
      }
    )
  }
}

private fun ContentDrawScope.drawBar(barColor: Color, xOffset: Float, size: Float) {
  val yOffset = (drawContext.size.height - size) / 2
  drawLine(
    color = barColor,
    cap = StrokeCap.Round,
    strokeWidth = BAR_WIDTH.toPx(),
    start = Offset(x = xOffset, y = yOffset),
    end = Offset(x = xOffset, y = drawContext.size.height - yOffset)
  )
}

@NightPreview
@Composable
fun AudioIndicatorPreview() {
  Previews.Preview {
    AudioIndicator(
      participant = CallParticipant(
        callParticipantId = CallParticipantId(
          1L,
          RecipientId.from(1L)
        ),
        isMicrophoneEnabled = false,
        audioLevel = null
      ),
      modifier = Modifier
        .size(28.dp)
        .background(
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
          shape = CircleShape
        )
        .padding(6.dp)
    )
  }
}

@NightPreview
@Composable
fun AudioIndicatorGonePreview() {
  Previews.Preview {
    AudioIndicator(
      participant = CallParticipant(
        callParticipantId = CallParticipantId(
          1L,
          RecipientId.from(1L)
        ),
        isMicrophoneEnabled = true,
        audioLevel = null
      ),
      modifier = Modifier
        .size(28.dp)
        .background(
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
          shape = CircleShape
        )
        .padding(6.dp)
    )
  }
}

@NightPreview
@Composable
fun AudioLevelBarsLowestPreview() {
  Previews.Preview {
    Box(
      modifier = Modifier
        .size(28.dp)
        .background(
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
          shape = CircleShape
        )
        .padding(6.dp)
    ) {
      AudioLevelBars(
        audioLevel = CallParticipant.AudioLevel.LOWEST
      )
    }
  }
}

@NightPreview
@Composable
fun AudioLevelBarsLowPreview() {
  Previews.Preview {
    Box(
      modifier = Modifier
        .size(28.dp)
        .background(
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
          shape = CircleShape
        )
        .padding(6.dp)
    ) {
      AudioLevelBars(
        audioLevel = CallParticipant.AudioLevel.LOW
      )
    }
  }
}

@NightPreview
@Composable
fun AudioLevelBarsMediumPreview() {
  Previews.Preview {
    Box(
      modifier = Modifier
        .size(28.dp)
        .background(
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
          shape = CircleShape
        )
        .padding(6.dp)
    ) {
      AudioLevelBars(
        audioLevel = CallParticipant.AudioLevel.MEDIUM
      )
    }
  }
}

@NightPreview
@Composable
fun AudioLevelBarsHighPreview() {
  Previews.Preview {
    Box(
      modifier = Modifier
        .size(28.dp)
        .background(
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
          shape = CircleShape
        )
        .padding(6.dp)
    ) {
      AudioLevelBars(
        audioLevel = CallParticipant.AudioLevel.HIGH
      )
    }
  }
}

@NightPreview
@Composable
fun AudioLevelBarsHighestPreview() {
  Previews.Preview {
    Box(
      modifier = Modifier
        .size(28.dp)
        .background(
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
          shape = CircleShape
        )
        .padding(6.dp)
    ) {
      AudioLevelBars(
        audioLevel = CallParticipant.AudioLevel.HIGHEST
      )
    }
  }
}
