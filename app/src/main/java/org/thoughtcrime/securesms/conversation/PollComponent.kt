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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.compose.RoundCheckbox
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.polls.PollOption
import org.thoughtcrime.securesms.polls.PollRecord
import org.thoughtcrime.securesms.polls.VoteState
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
        pollColors = if (isOutgoing) PollColorsType.Outgoing.getColors(chatColor) else PollColorsType.Incoming.getColors(-1),
        fontSize = SignalStore.settings.messageFontSize
      )
    }
  }
}

@Composable
private fun Poll(
  poll: PollRecord,
  onViewVotes: () -> Unit = {},
  onToggleVote: (PollOption, Boolean) -> Unit = { _, _ -> },
  pollColors: PollColors = PollColorsType.Incoming.getColors(-1),
  fontSize: Int = 16
) {
  val totalVoters = remember(poll.pollOptions) { poll.pollOptions.map { it.voters.map { voter -> voter.id } }.flatten().toSet() }.count()
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
      style = MaterialTheme.typography.bodySmall.copy(fontSize = (fontSize * .8).sp),
      modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
    )

    poll.pollOptions.forEach {
      PollOption(it, totalVoters, poll.hasEnded, onToggleVote, pollColors, fontSize)
    }

    Spacer(Modifier.size(16.dp))

    val hasVotes = totalVoters > 0
    Buttons.MediumTonal(
      colors = ButtonDefaults.buttonColors(
        containerColor = pollColors.buttonBackground,
        contentColor = pollColors.button,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = pollColors.text
      ),
      onClick = onViewVotes,
      enabled = hasVotes,
      modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth().padding(horizontal = 16.dp)
    ) {
      Text(
        text = if (!hasVotes) {
          stringResource(R.string.Poll__no_votes)
        } else if (poll.hasEnded) {
          stringResource(R.string.Poll__view_results)
        } else {
          stringResource(R.string.Poll__view_votes)
        },
        style = MaterialTheme.typography.labelLarge.copy(fontSize = (fontSize * .8).sp)
      )
    }
    Spacer(Modifier.size(4.dp))
  }
}

@Composable
private fun PollOption(
  option: PollOption,
  totalVoters: Int,
  hasEnded: Boolean,
  onToggleVote: (PollOption, Boolean) -> Unit = { _, _ -> },
  pollColors: PollColors,
  fontSize: Int
) {
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val progress = remember(option.voters.size, totalVoters) {
    if (totalVoters > 0) (option.voters.size.toFloat() / totalVoters.toFloat()) else 0f
  }
  val progressValue by animateFloatAsState(targetValue = progress, animationSpec = tween(durationMillis = 250))

  Row(
    modifier = Modifier
      .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
      .clickable(
        onClick = {
          if (!hasEnded) {
            val added = option.voteState == VoteState.PENDING_ADD || option.voteState == VoteState.ADDED
            if (VibrateUtil.isHapticFeedbackEnabled(context)) {
              haptics.performHapticFeedback(if (added) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            }
            onToggleVote(option, !added)
          }
        },
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClickLabel = stringResource(R.string.SignalCheckbox_accessibility_on_click_label),
        enabled = true
      )
  ) {
    if (!hasEnded) {
      AnimatedContent(
        targetState = option.voteState,
        transitionSpec = {
          val delayMs = if (option.voteState == VoteState.PENDING_REMOVE || option.voteState == VoteState.PENDING_ADD) 500 else 0
          val enterTransition = fadeIn(tween(delayMillis = delayMs, durationMillis = 500))
          val exitTransition = fadeOut(tween(durationMillis = 500))
          enterTransition.togetherWith(exitTransition)
            .using(SizeTransform(clip = false))
        }
      ) { voteState ->
        when (voteState) {
          VoteState.PENDING_ADD -> {
            Box(modifier = Modifier) {
              CircularProgressIndicator(
                modifier = Modifier.padding(top = 4.dp, end = 8.dp).size(24.dp),
                strokeWidth = 1.5.dp,
                color = pollColors.checkbox
              )
              Icon(
                imageVector = ImageVector.vectorResource(R.drawable.symbol_check_compact_bold_16),
                contentDescription = stringResource(R.string.SignalCheckbox_accessibility_unchecked_description),
                tint = pollColors.checkbox,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp).align(Alignment.Center)
              )
            }
          }

          VoteState.PENDING_REMOVE -> {
            CircularProgressIndicator(
              modifier = Modifier.padding(top = 4.dp, end = 8.dp).size(24.dp),
              strokeWidth = 1.5.dp,
              color = pollColors.checkbox
            )
          }

          VoteState.ADDED,
          VoteState.REMOVED,
          VoteState.NONE -> {
            RoundCheckbox(
              checked = voteState == VoteState.ADDED,
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
    }

    Column {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = option.text,
          style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp),
          modifier = Modifier.padding(end = 24.dp).weight(1f),
          color = pollColors.text
        )

        if (hasEnded && option.voteState == VoteState.ADDED) {
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
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = (fontSize * .8).sp)
          )
        }
      }

      Box(
        modifier = Modifier.padding(top = 4.dp).height(8.dp).fillMaxWidth()
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
              shape = RoundedCornerShape(18.dp)
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
        progressBackground = if (DynamicTheme.isDarkTheme(LocalContext.current)) SignalTheme.colors.colorTransparent2 else SignalTheme.colors.colorTransparentInverse3,
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
          PollOption(1, "yay", listOf(Voter(1, 1)), voteState = VoteState.ADDED),
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
          PollOption(2, "ok", emptyList()),
          PollOption(3, "nay", emptyList())
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
          PollOption(2, "ok", emptyList()),
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
