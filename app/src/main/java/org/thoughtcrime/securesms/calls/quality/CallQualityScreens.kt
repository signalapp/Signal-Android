/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.quality

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.AnimatedFlowRow
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallQualitySheet(
  state: CallQualitySheetState = remember { CallQualitySheetState() },
  callback: CallQualitySheetCallback = CallQualitySheetCallback.Empty
) {
  var navEntry: CallQualitySheetNavEntry by remember { mutableStateOf(CallQualitySheetNavEntry.HowWasYourCall) }

  if (state.failedDueToNetworkAvailability) {
    Dialogs.SimpleAlertDialog(
      title = "",
      body = stringResource(R.string.CallQualitySheet__your_feedback_failed_to_send),
      confirm = stringResource(R.string.CallQualitySheet__try_again),
      onConfirm = callback::tryAgain,
      dismiss = stringResource(android.R.string.cancel),
      onDeny = callback::dismiss
    )

    return
  }

  Sheet(onDismissRequest = callback::dismiss) {
    AnimatedContent(
      targetState = navEntry,
      transitionSpec = {
        fadeIn(
          animationSpec = tween(
            durationMillis = 300,
            delayMillis = 300,
            easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
          )
        ) togetherWith fadeOut(
          animationSpec = tween(
            durationMillis = 300,
            easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
          )
        )
      }
    ) { target ->
      Column {
        when (target) {
          CallQualitySheetNavEntry.HowWasYourCall -> HowWasYourCall(
            onGreatClick = {
              callback.onUserSatisfiedWithCall(true)
              navEntry = CallQualitySheetNavEntry.HelpUsImprove
            },
            onHadIssuesClick = {
              callback.onUserSatisfiedWithCall(false)
              navEntry = CallQualitySheetNavEntry.WhatIssuesDidYouHave
            },
            onCancelClick = callback::dismiss
          )

          CallQualitySheetNavEntry.WhatIssuesDidYouHave -> WhatIssuesDidYouHave(
            selectedQualityIssues = state.selectedQualityIssues,
            somethingElseDescription = state.somethingElseDescription,
            isContinueEnabled = state.canContinueAfterIssueSelection(),
            onCallQualityIssueSelectionChanged = callback::onCallQualityIssueSelectionChanged,
            onContinueClick = {
              navEntry = CallQualitySheetNavEntry.HelpUsImprove
            },
            onDescribeYourIssueClick = callback::describeYourIssue,
            onCancelClick = callback::dismiss
          )

          CallQualitySheetNavEntry.HelpUsImprove -> HelpUsImprove(
            isShareDebugLogSelected = state.isShareDebugLogSelected,
            onViewDebugLogClick = callback::viewDebugLog,
            onCancelClick = callback::dismiss,
            onShareDebugLogChanged = callback::onShareDebugLogChanged,
            onSubmitClick = callback::submit
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.HowWasYourCall(
  onHadIssuesClick: () -> Unit,
  onGreatClick: () -> Unit,
  onCancelClick: () -> Unit
) {
  SheetTitle(text = stringResource(R.string.CallQualitySheet__how_was_your_call))
  SheetSubtitle(text = stringResource(R.string.CallQualitySheet__how_was_your_call_subtitle))

  Row(
    horizontalArrangement = Arrangement.SpaceEvenly,
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 24.dp)
  ) {
    HadIssuesButton(onClick = onHadIssuesClick)
    GreatButton(onClick = onGreatClick)
  }

  CancelButton(
    onClick = onCancelClick,
    modifier = Modifier
      .align(Alignment.CenterHorizontally)
      .padding(top = 32.dp, bottom = 24.dp)
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatIssuesDidYouHave(
  selectedQualityIssues: Set<CallQualityIssue>,
  somethingElseDescription: String,
  isContinueEnabled: Boolean,
  onCallQualityIssueSelectionChanged: (Set<CallQualityIssue>) -> Unit,
  onCancelClick: () -> Unit,
  onContinueClick: () -> Unit,
  onDescribeYourIssueClick: () -> Unit
) {
  SheetTitle(text = stringResource(R.string.CallQualitySheet__what_issues_did_you_have))
  SheetSubtitle(text = stringResource(R.string.CallQualitySheet__select_all_that_apply))

  val onCallQualityIssueClick: (CallQualityIssue) -> Unit = remember(selectedQualityIssues, onCallQualityIssueSelectionChanged) {
    { issue ->
      val isRemoving = issue in selectedQualityIssues
      val selection = when {
        isRemoving && issue == CallQualityIssue.AUDIO_ISSUE -> {
          selectedQualityIssues.filterNot { it.category == CallQualityIssueCategory.AUDIO }.toSet()
        }

        isRemoving && issue == CallQualityIssue.VIDEO_ISSUE -> {
          selectedQualityIssues.filterNot { it.category == CallQualityIssueCategory.VIDEO }.toSet()
        }

        isRemoving -> {
          selectedQualityIssues - issue
        }

        else -> {
          selectedQualityIssues + issue
        }
      }

      onCallQualityIssueSelectionChanged(selection)
    }
  }

  val isAudioExpanded = CallQualityIssue.AUDIO_ISSUE in selectedQualityIssues
  val isVideoExpanded = CallQualityIssue.VIDEO_ISSUE in selectedQualityIssues

  AnimatedFlowRow(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 24.dp)
      .horizontalGutters()
  ) {
    item(CallQualityIssue.AUDIO_ISSUE) {
      IssueChip(
        issue = CallQualityIssue.AUDIO_ISSUE,
        isSelected = isAudioExpanded,
        onClick = { onCallQualityIssueClick(CallQualityIssue.AUDIO_ISSUE) }
      )
    }

    if (isAudioExpanded) {
      item(CallQualityIssue.AUDIO_STUTTERING) {
        IssueChip(
          issue = CallQualityIssue.AUDIO_STUTTERING,
          isSelected = CallQualityIssue.AUDIO_STUTTERING in selectedQualityIssues,
          onClick = { onCallQualityIssueClick(CallQualityIssue.AUDIO_STUTTERING) }
        )
      }

      item(CallQualityIssue.AUDIO_CUT_OUT) {
        IssueChip(
          issue = CallQualityIssue.AUDIO_CUT_OUT,
          isSelected = CallQualityIssue.AUDIO_CUT_OUT in selectedQualityIssues,
          onClick = { onCallQualityIssueClick(CallQualityIssue.AUDIO_CUT_OUT) }
        )
      }

      item(CallQualityIssue.AUDIO_I_HEARD_ECHO) {
        IssueChip(
          issue = CallQualityIssue.AUDIO_I_HEARD_ECHO,
          isSelected = CallQualityIssue.AUDIO_I_HEARD_ECHO in selectedQualityIssues,
          onClick = { onCallQualityIssueClick(CallQualityIssue.AUDIO_I_HEARD_ECHO) }
        )
      }

      item(CallQualityIssue.AUDIO_OTHERS_HEARD_ECHO) {
        IssueChip(
          issue = CallQualityIssue.AUDIO_OTHERS_HEARD_ECHO,
          isSelected = CallQualityIssue.AUDIO_OTHERS_HEARD_ECHO in selectedQualityIssues,
          onClick = { onCallQualityIssueClick(CallQualityIssue.AUDIO_OTHERS_HEARD_ECHO) }
        )
      }
    }

    item(CallQualityIssue.VIDEO_ISSUE) {
      IssueChip(
        issue = CallQualityIssue.VIDEO_ISSUE,
        isSelected = isVideoExpanded,
        onClick = { onCallQualityIssueClick(CallQualityIssue.VIDEO_ISSUE) }
      )
    }

    if (isVideoExpanded) {
      item(CallQualityIssue.VIDEO_POOR_QUALITY) {
        IssueChip(
          issue = CallQualityIssue.VIDEO_POOR_QUALITY,
          isSelected = CallQualityIssue.VIDEO_POOR_QUALITY in selectedQualityIssues,
          onClick = { onCallQualityIssueClick(CallQualityIssue.VIDEO_POOR_QUALITY) }
        )
      }

      item(CallQualityIssue.VIDEO_LOW_RESOLUTION) {
        IssueChip(
          issue = CallQualityIssue.VIDEO_LOW_RESOLUTION,
          isSelected = CallQualityIssue.VIDEO_LOW_RESOLUTION in selectedQualityIssues,
          onClick = { onCallQualityIssueClick(CallQualityIssue.VIDEO_LOW_RESOLUTION) }
        )
      }

      item(CallQualityIssue.VIDEO_CAMERA_MALFUNCTION) {
        IssueChip(
          issue = CallQualityIssue.VIDEO_CAMERA_MALFUNCTION,
          isSelected = CallQualityIssue.VIDEO_CAMERA_MALFUNCTION in selectedQualityIssues,
          onClick = { onCallQualityIssueClick(CallQualityIssue.VIDEO_CAMERA_MALFUNCTION) }
        )
      }
    }

    item(CallQualityIssue.CALL_DROPPED) {
      IssueChip(
        issue = CallQualityIssue.CALL_DROPPED,
        isSelected = CallQualityIssue.CALL_DROPPED in selectedQualityIssues,
        onClick = { onCallQualityIssueClick(CallQualityIssue.CALL_DROPPED) }
      )
    }

    item(CallQualityIssue.SOMETHING_ELSE) {
      IssueChip(
        issue = CallQualityIssue.SOMETHING_ELSE,
        isSelected = CallQualityIssue.SOMETHING_ELSE in selectedQualityIssues,
        onClick = { onCallQualityIssueClick(CallQualityIssue.SOMETHING_ELSE) }
      )
    }
  }

  AnimatedVisibility(
    visible = CallQualityIssue.SOMETHING_ELSE in selectedQualityIssues,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically()
  ) {
    val text = somethingElseDescription.ifEmpty {
      stringResource(R.string.CallQualitySheet__describe_your_issue)
    }

    val textColor = if (somethingElseDescription.isNotEmpty()) {
      MaterialTheme.colorScheme.onSurface
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    }

    val textUnderlineStrokeWidthPx = with(LocalDensity.current) {
      1.dp.toPx()
    }

    val textUnderlineColor = MaterialTheme.colorScheme.outline

    Text(
      text = text,
      color = textColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .clickable(
          role = Role.Button,
          onClick = onDescribeYourIssueClick
        )
        .fillMaxWidth()
        .horizontalGutters()
        .padding(top = 24.dp)
        .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
        .drawWithContent {
          drawContent()

          val width = size.width
          val height = size.height - textUnderlineStrokeWidthPx / 2f

          drawLine(
            color = textUnderlineColor,
            start = Offset(x = 0f, y = height),
            end = Offset(x = width, y = height),
            strokeWidth = textUnderlineStrokeWidthPx
          )
        }
        .padding(16.dp)
    )
  }

  // Buttons - outside FlowRow, stable position
  Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
      .padding(top = 32.dp, bottom = 24.dp)
  ) {
    CancelButton(
      onClick = onCancelClick
    )

    Buttons.LargeTonal(
      onClick = onContinueClick,
      enabled = isContinueEnabled
    ) {
      Text(text = stringResource(R.string.CallQualitySheet__continue))
    }
  }
}

@Composable
private fun IssueChip(
  issue: CallQualityIssue,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  InputChip(
    selected = isSelected,
    onClick = onClick,
    colors = InputChipDefaults.inputChipColors(
      leadingIconColor = MaterialTheme.colorScheme.onSurface,
      selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
      labelColor = MaterialTheme.colorScheme.onSurface
    ),
    leadingIcon = {
      Icon(
        imageVector = if (isSelected) {
          ImageVector.vectorResource(R.drawable.symbol_check_24)
        } else {
          ImageVector.vectorResource(issue.category.icon)
        },
        contentDescription = null
      )
    },
    label = {
      Text(text = stringResource(issue.label))
    },
    modifier = modifier.padding(horizontal = 8.dp)
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpUsImprove(
  isShareDebugLogSelected: Boolean,
  onShareDebugLogChanged: (Boolean) -> Unit,
  onViewDebugLogClick: () -> Unit,
  onCancelClick: () -> Unit,
  onSubmitClick: () -> Unit
) {
  SheetTitle(text = stringResource(R.string.CallQualitySheet__help_us_improve))
  SheetSubtitle(
    text = buildAnnotatedString {
      append(stringResource(R.string.CallQualitySheet__help_us_improve_description_prefix))
      append(" ")

      withLink(
        link = LinkAnnotation.Clickable(
          "view-your-debug-log",
          linkInteractionListener = { onViewDebugLogClick() },
          styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary))
        )
      ) {
        append(stringResource(R.string.CallQualitySheet__view_your_debug_log))
      }

      append(" ")
      append(stringResource(R.string.CallQualitySheet__help_us_improve_description_suffix))
    }
  )

  Rows.ToggleRow(
    checked = isShareDebugLogSelected,
    text = stringResource(R.string.CallQualitySheet__share_debug_log),
    onCheckChanged = onShareDebugLogChanged
  )

  Text(
    text = stringResource(R.string.CallQualitySheet__debug_log_privacy_notice),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .horizontalGutters()
      .padding(top = 14.dp)
  )

  Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
      .padding(top = 32.dp, bottom = 24.dp)
  ) {
    CancelButton(
      onClick = onCancelClick
    )

    Buttons.LargeTonal(
      onClick = onSubmitClick
    ) {
      Text(text = stringResource(R.string.CallQualitySheet__submit))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(
  onDismissRequest: () -> Unit,
  content: @Composable ColumnScope.() -> Unit
) {
  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    dragHandle = null,
    sheetGesturesEnabled = false,
    sheetState = rememberModalBottomSheetState(
      skipPartiallyExpanded = true
    )
  ) {
    content()
  }
}

@Composable
private fun SheetTitle(
  text: String
) {
  Text(
    text = text,
    textAlign = TextAlign.Center,
    style = MaterialTheme.typography.titleLarge,
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 46.dp, bottom = 10.dp)
      .horizontalGutters()
  )
}

@Composable
private fun SheetSubtitle(
  text: String
) {
  Text(
    text = text,
    textAlign = TextAlign.Center,
    style = MaterialTheme.typography.bodyLarge,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
  )
}

@Composable
private fun SheetSubtitle(
  text: AnnotatedString
) {
  Text(
    text = text,
    textAlign = TextAlign.Center,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
  )
}

@Composable
private fun HadIssuesButton(
  onClick: () -> Unit
) {
  FeedbackButton(
    text = stringResource(R.string.CallQualitySheet__had_issues),
    containerColor = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.error,
    onClick = onClick,
    contentDescription = stringResource(R.string.CallQualitySheet__had_issues_content_description),
    imageVector = ImageVector.vectorResource(R.drawable.symbol_thumbs_down_24)
  )
}

@Composable
private fun GreatButton(
  onClick: () -> Unit
) {
  FeedbackButton(
    text = stringResource(R.string.CallQualitySheet__great),
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.primary,
    onClick = onClick,
    contentDescription = stringResource(R.string.CallQualitySheet__great_content_description),
    imageVector = ImageVector.vectorResource(R.drawable.symbol_thumbs_up_24)
  )
}

@Composable
private fun FeedbackButton(
  text: String,
  onClick: () -> Unit,
  containerColor: Color,
  contentColor: Color,
  imageVector: ImageVector,
  contentDescription: String
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = spacedBy(12.dp)
  ) {
    IconButtons.IconButton(
      onClick = onClick,
      size = 72.dp,
      modifier = Modifier
        .clip(CircleShape)
        .background(color = containerColor)
    ) {
      Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = contentColor,
        modifier = Modifier.size(36.dp)
      )
    }

    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge
    )
  }
}

@Composable
fun CancelButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  TextButton(onClick = onClick, modifier = modifier) {
    Text(text = stringResource(android.R.string.cancel))
  }
}

@AllDevicePreviews
@Composable
private fun CallQualityScreenPreview() {
  var state by remember { mutableStateOf(CallQualitySheetState()) }

  Previews.Preview {
    CallQualitySheet(
      state = state,
      callback = remember {
        object : CallQualitySheetCallback by CallQualitySheetCallback.Empty {
          override fun onCallQualityIssueSelectionChanged(selection: Set<CallQualityIssue>) {
            state = state.copy(selectedQualityIssues = selection)
          }
        }
      }
    )
  }
}

@PreviewLightDark
@Composable
private fun HowWasYourCallPreview() {
  Previews.BottomSheetContentPreview {
    Column {
      HowWasYourCall(
        onGreatClick = {},
        onCancelClick = {},
        onHadIssuesClick = {}
      )
    }
  }
}

@PreviewLightDark
@Composable
private fun WhatIssuesDidYouHavePreview() {
  Previews.BottomSheetContentPreview {
    var userSelection by remember { mutableStateOf<Set<CallQualityIssue>>(emptySet()) }

    Column {
      WhatIssuesDidYouHave(
        selectedQualityIssues = userSelection,
        somethingElseDescription = "",
        isContinueEnabled = false,
        onCallQualityIssueSelectionChanged = {
          userSelection = it
        },
        onCancelClick = {},
        onContinueClick = {},
        onDescribeYourIssueClick = {}
      )
    }
  }
}

@PreviewLightDark
@Composable
private fun HelpUsImprovePreview() {
  Previews.BottomSheetContentPreview {
    Column {
      HelpUsImprove(
        isShareDebugLogSelected = true,
        onViewDebugLogClick = {},
        onCancelClick = {},
        onShareDebugLogChanged = {},
        onSubmitClick = {}
      )
    }
  }
}

@PreviewLightDark
@Composable
private fun SomethingElseContentPreview() {
  Previews.Preview {
    CallQualitySomethingElseScreen(
      somethingElseDescription = "About 5 minutes into a call with my friend",
      onCancelClick = {},
      onSaveClick = {}
    )
  }
}

data class CallQualitySheetState(
  val isUserSatisfiedWithCall: Boolean = false,
  val selectedQualityIssues: Set<CallQualityIssue> = emptySet(),
  val somethingElseDescription: String = "",
  val isShareDebugLogSelected: Boolean = false,
  val failedDueToNetworkAvailability: Boolean = false
) {
  fun canContinueAfterIssueSelection(): Boolean {
    val isSomethingElseSelected = selectedQualityIssues.contains(CallQualityIssue.SOMETHING_ELSE)
    val isSomethingElseFieldComplete = somethingElseDescription.isNotEmpty()
    val isSomethingElseValid = isSomethingElseSelected && isSomethingElseFieldComplete || !isSomethingElseSelected

    return selectedQualityIssues.isNotEmpty() && isSomethingElseValid
  }
}

interface CallQualitySheetCallback {
  fun dismiss()
  fun viewDebugLog()
  fun onUserSatisfiedWithCall(isUserSatisfiedWithCall: Boolean)
  fun describeYourIssue()
  fun onCallQualityIssueSelectionChanged(selection: Set<CallQualityIssue>)
  fun onShareDebugLogChanged(shareDebugLog: Boolean)
  fun submit()
  fun tryAgain()

  object Empty : CallQualitySheetCallback {
    override fun dismiss() = Unit
    override fun viewDebugLog() = Unit
    override fun onUserSatisfiedWithCall(isUserSatisfiedWithCall: Boolean) = Unit
    override fun describeYourIssue() = Unit
    override fun onCallQualityIssueSelectionChanged(selection: Set<CallQualityIssue>) = Unit
    override fun onShareDebugLogChanged(shareDebugLog: Boolean) = Unit
    override fun submit() = Unit
    override fun tryAgain() = Unit
  }
}

private enum class CallQualitySheetNavEntry {
  HowWasYourCall,
  WhatIssuesDidYouHave,
  HelpUsImprove
}

enum class CallQualityIssueCategory(
  @param:DrawableRes val icon: Int
) {
  AUDIO(icon = R.drawable.symbol_speaker_24),
  VIDEO(icon = R.drawable.symbol_video_24),
  CALL_DROPPED(icon = R.drawable.symbol_x_circle_24),
  SOMETHING_ELSE(icon = R.drawable.symbol_error_circle_24)
}

enum class CallQualityIssue(
  val code: String,
  val category: CallQualityIssueCategory,
  @param:StringRes val label: Int
) {
  AUDIO_ISSUE(code = "audio", category = CallQualityIssueCategory.AUDIO, label = R.string.CallQualityIssue__audio_issue),
  AUDIO_STUTTERING(code = "audio_stuttering", category = CallQualityIssueCategory.AUDIO, label = R.string.CallQualityIssue__audio_stuttering),
  AUDIO_CUT_OUT(code = "audio_drop", category = CallQualityIssueCategory.AUDIO, label = R.string.CallQualityIssue__audio_cut_out),
  AUDIO_I_HEARD_ECHO(code = "audio_remote_echo", category = CallQualityIssueCategory.AUDIO, label = R.string.CallQualityIssue__i_heard_echo),
  AUDIO_OTHERS_HEARD_ECHO(code = "audio_local_echo", category = CallQualityIssueCategory.AUDIO, label = R.string.CallQualityIssue__others_heard_echo),
  VIDEO_ISSUE(code = "video", category = CallQualityIssueCategory.VIDEO, label = R.string.CallQualityIssue__video_issue),
  VIDEO_POOR_QUALITY(code = "video_low_quality", category = CallQualityIssueCategory.VIDEO, label = R.string.CallQualityIssue__poor_video_quality),
  VIDEO_LOW_RESOLUTION(code = "video_low_resolution", category = CallQualityIssueCategory.VIDEO, label = R.string.CallQualityIssue__low_resolution),
  VIDEO_CAMERA_MALFUNCTION(code = "video_no_camera", category = CallQualityIssueCategory.VIDEO, label = R.string.CallQualityIssue__camera_did_not_work),
  CALL_DROPPED(code = "call_dropped", category = CallQualityIssueCategory.CALL_DROPPED, label = R.string.CallQualityIssue__call_droppped),
  SOMETHING_ELSE(code = "other", category = CallQualityIssueCategory.SOMETHING_ELSE, label = R.string.CallQualityIssue__something_else)
}
