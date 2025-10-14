/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.compose.RoundCheckbox
import org.thoughtcrime.securesms.polls.PollOption
import org.thoughtcrime.securesms.polls.PollRecord
import org.thoughtcrime.securesms.polls.Voter
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.VibrateUtil

/**
 * Allows us to utilize our composeView from Java code.
 */
fun setContent(
  composeView: ComposeView,
  poll: PollRecord,
  isOutgoing: Boolean,
  chatColor: Int,
  onViewVotes: () -> Unit,
  onToggleVote: (PollOption, Boolean) -> Unit = { _, _ -> }
) {
  composeView.setContent {
    SignalTheme(
      isDarkMode = DynamicTheme.isDarkTheme(LocalContext.current)
    ) {
      Poll(
        poll = poll,
        onViewVotes = onViewVotes,
        onToggleVote = onToggleVote,
        pollColors = if (isOutgoing) PollColorsType.Outgoing.getColors(chatColor) else PollColorsType.Incoming.getColors(-1)
      )
    }
  }
}

@Composable
private fun Poll(
  poll: PollRecord,
  onViewVotes: () -> Unit = {},
  onToggleVote: (PollOption, Boolean) -> Unit = { _, _ -> },
  pollColors: PollColors = PollColorsType.Incoming.getColors(-1)
) {
  val totalVotes = remember(poll.pollOptions) { poll.pollOptions.sumOf { it.voters.size } }
  val caption = when {
    poll.hasEnded -> R.string.Poll__final_results
    poll.allowMultipleVotes -> R.string.Poll__select_multiple
    else -> R.string.Poll__select_one
  }

  Column(
    modifier = Modifier.fillMaxWidth()
  ) {
    Text(
      text = stringResource(caption),
      color = pollColors.caption,
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
    )

    poll.pollOptions.forEach {
      PollOption(it, totalVotes, poll.hasEnded, onToggleVote, pollColors)
    }

    Spacer(Modifier.size(16.dp))

    if (totalVotes == 0) {
      Text(
        text = stringResource(R.string.Poll__no_votes),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.align(Alignment.CenterHorizontally).height(40.dp).wrapContentHeight(align = Alignment.CenterVertically),
        textAlign = TextAlign.Center,
        color = pollColors.text
      )
    } else {
      Buttons.MediumTonal(
        colors = ButtonDefaults.buttonColors(containerColor = pollColors.buttonBackground, contentColor = pollColors.button),
        onClick = onViewVotes,
        modifier = Modifier.align(Alignment.CenterHorizontally).height(40.dp)
      ) {
        Text(stringResource(if (poll.hasEnded) R.string.Poll__view_results else R.string.Poll__view_votes))
      }
    }
    Spacer(Modifier.size(4.dp))
  }
}

@Composable
private fun PollOption(
  option: PollOption,
  totalVotes: Int,
  hasEnded: Boolean,
  onToggleVote: (PollOption, Boolean) -> Unit = { _, _ -> },
  pollColors: PollColors
) {
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val progress = remember(option.voters.size, totalVotes) {
    if (totalVotes > 0) (option.voters.size.toFloat() / totalVotes.toFloat()) else 0f
  }
  val progressValue by animateFloatAsState(targetValue = progress, animationSpec = tween(durationMillis = 250))

  Row(
    modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
  ) {
    if (!hasEnded) {
      AnimatedContent(
        targetState = option.isPending,
        transitionSpec = {
          val enterTransition = fadeIn(tween(delayMillis = 500, durationMillis = 500))
          val exitTransition = fadeOut(tween(durationMillis = 500))
          enterTransition.togetherWith(exitTransition)
            .using(SizeTransform(clip = false))
        }
      ) { inProgress ->
        if (inProgress) {
          CircularProgressIndicator(
            modifier = Modifier.padding(top = 4.dp, end = 8.dp).size(24.dp),
            strokeWidth = 1.5.dp,
            color = pollColors.checkbox
          )
        } else {
          RoundCheckbox(
            checked = option.isSelected,
            onCheckedChange = { checked ->
              if (VibrateUtil.isHapticFeedbackEnabled(context)) {
                haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
              }
              onToggleVote(option, checked)
            },
            modifier = Modifier.padding(top = 4.dp, end = 8.dp).height(24.dp),
            outlineColor = pollColors.checkbox,
            checkedColor = pollColors.checkboxBackground
          )
        }
      }
    }

    Column {
      Row(verticalAlignment = Alignment.Bottom) {
        Text(
          text = option.text,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(end = 24.dp).weight(1f),
          color = pollColors.text
        )

        if (hasEnded && option.isSelected) {
          RoundCheckbox(
            checked = true,
            onCheckedChange = {},
            modifier = Modifier.padding(end = 4.dp),
            size = 16.dp,
            enabled = false,
            checkedColor = pollColors.checkboxBackground
          )
        }

        AnimatedContent(
          targetState = option.voters.size
        ) { size ->
          Text(
            text = size.toString(),
            color = pollColors.text,
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }

      Box(
        modifier = Modifier.height(8.dp).padding(top = 4.dp).fillMaxWidth()
          .background(
            color = pollColors.progressBackground,
            shape = RoundedCornerShape(18.dp)
          )
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth(progressValue)
            .fillMaxHeight()
            .background(
              color = pollColors.progress,
              shape = if (progress == 1f) RoundedCornerShape(18.dp) else RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
            )
        )
      }
    }
  }
}

class PollColors(
  val text: Color,
  val caption: Color,
  val progress: Color,
  val progressBackground: Color,
  val checkbox: Color,
  val checkboxBackground: Color,
  val button: Color,
  val buttonBackground: Color
)

private sealed interface PollColorsType {
  @Composable
  fun getColors(chatColor: Int): PollColors

  data object Outgoing : PollColorsType {
    @Composable
    override fun getColors(chatColor: Int): PollColors {
      return PollColors(
        text = colorResource(R.color.conversation_item_sent_text_primary_color),
        caption = colorResource(R.color.conversation_item_sent_text_secondary_color),
        progress = colorResource(R.color.conversation_item_sent_text_primary_color),
        progressBackground = SignalTheme.colors.colorTransparent3,
        checkbox = colorResource(R.color.conversation_item_sent_text_secondary_color),
        checkboxBackground = colorResource(R.color.conversation_item_sent_text_primary_color),
        button = Color(chatColor),
        buttonBackground = colorResource(R.color.conversation_item_sent_text_primary_color)
      )
    }
  }

  data object Incoming : PollColorsType {
    @Composable
    override fun getColors(chatColor: Int): PollColors {
      return PollColors(
        text = MaterialTheme.colorScheme.onSurface,
        caption = MaterialTheme.colorScheme.onSurfaceVariant,
        progress = MaterialTheme.colorScheme.primary,
        progressBackground = SignalTheme.colors.colorTransparentInverse3,
        checkbox = MaterialTheme.colorScheme.outline,
        checkboxBackground = MaterialTheme.colorScheme.primary,
        button = MaterialTheme.colorScheme.onPrimaryContainer,
        buttonBackground = MaterialTheme.colorScheme.primaryContainer
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun PollPreview() {
  Previews.Preview {
    Poll(
      PollRecord(
        id = 1,
        question = "How do you feel about compose previews?",
        pollOptions = listOf(
          PollOption(1, "yay", listOf(Voter(1, 1)), isSelected = true),
          PollOption(2, "ok", listOf(Voter(1, 1), Voter(2, 1))),
          PollOption(3, "nay", listOf(Voter(1, 1), Voter(2, 1), Voter(3, 1)))
        ),
        allowMultipleVotes = false,
        hasEnded = false,
        authorId = 1,
        messageId = 1
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun EmptyPollPreview() {
  Previews.Preview {
    Poll(
      PollRecord(
        id = 1,
        question = "How do you feel about multiple compose previews?",
        pollOptions = listOf(
          PollOption(1, "yay", emptyList()),
          PollOption(2, "ok", emptyList(), isSelected = true),
          PollOption(3, "nay", emptyList(), isSelected = true)
        ),
        allowMultipleVotes = true,
        hasEnded = false,
        authorId = 1,
        messageId = 1
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun FinishedPollPreview() {
  Previews.Preview {
    Poll(
      PollRecord(
        id = 1,
        question = "How do you feel about finished compose previews?",
        pollOptions = listOf(
          PollOption(1, "yay", listOf(Voter(1, 1))),
          PollOption(2, "ok", emptyList(), isSelected = true),
          PollOption(3, "nay", emptyList())
        ),
        allowMultipleVotes = false,
        hasEnded = true,
        authorId = 1,
        messageId = 1
      )
    )
  }
}
