/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.shakereport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.shakereport.ShakeToReportState.Step

/**
 * A single dialog that walks through prompting, submitting, and sharing a debug log. Each step
 * renders in place so the dialog never disappears while the log is being built.
 */
@Composable
fun ShakeToReportDialog(
  state: ShakeToReportState,
  onEvent: (ShakeToReportEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val dismissible = state.step != Step.Submitting

  Dialogs.BaseAlertDialog(
    onDismissRequest = { onEvent(ShakeToReportEvent.DismissRequested) },
    properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
    modifier = modifier,
    icon = if (state.step == Step.Submitting) {
      { CircularProgressIndicator(modifier = Modifier.size(32.dp)) }
    } else {
      null
    },
    title = { Text(text = stringResource(titleRes(state.step))) },
    text = { StepBody(step = state.step) },
    confirmButton = { ConfirmButtons(step = state.step, onEvent = onEvent) },
    dismissButton = { DismissButton(step = state.step, onEvent = onEvent) }
  )
}

private fun titleRes(step: Step): Int {
  return when (step) {
    Step.Prompt -> R.string.ShakeToReport_shake_detected
    Step.Submitting -> R.string.ShakeToReport_submitting
    is Step.Submitted -> R.string.ShakeToReport_success
    Step.Failed -> R.string.ShakeToReport_failed_to_submit
  }
}

@Composable
private fun StepBody(step: Step) {
  when (step) {
    Step.Prompt -> {
      Text(text = stringResource(R.string.ShakeToReport_submit_debug_log))
    }
    Step.Submitting -> {
      Text(text = stringResource(R.string.ShakeToReport_submitting_body))
    }
    is Step.Submitted -> {
      SelectionContainer {
        Text(text = step.url)
      }
    }
    Step.Failed -> Unit
  }
}

@Composable
private fun ConfirmButtons(step: Step, onEvent: (ShakeToReportEvent) -> Unit) {
  when (step) {
    Step.Prompt -> {
      TextButton(
        onClick = { onEvent(ShakeToReportEvent.SubmitClicked) }
      ) {
        Text(text = stringResource(R.string.ShakeToReport_submit))
      }
    }
    Step.Submitting -> Unit
    is Step.Submitted -> {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
          onClick = { onEvent(ShakeToReportEvent.CopyClicked) }
        ) {
          Text(text = stringResource(R.string.ShakeToReport_copy))
        }
        TextButton(
          onClick = { onEvent(ShakeToReportEvent.ShareClicked) }
        ) {
          Text(text = stringResource(R.string.ShakeToReport_share))
        }
      }
    }
    Step.Failed -> {
      TextButton(
        onClick = { onEvent(ShakeToReportEvent.RetryClicked) }
      ) {
        Text(text = stringResource(R.string.ShakeToReport_retry))
      }
    }
  }
}

@Composable
private fun DismissButton(step: Step, onEvent: (ShakeToReportEvent) -> Unit) {
  when (step) {
    Step.Prompt -> {
      TextButton(
        onClick = { onEvent(ShakeToReportEvent.DismissRequested) }
      ) {
        Text(text = stringResource(android.R.string.cancel))
      }
    }
    Step.Submitting -> Unit
    is Step.Submitted, Step.Failed -> {
      TextButton(
        onClick = { onEvent(ShakeToReportEvent.DismissRequested) }
      ) {
        Text(text = stringResource(R.string.ShakeToReport_close))
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun ShakeToReportDialogPromptPreview() {
  Previews.Preview {
    ShakeToReportDialog(state = ShakeToReportState(step = Step.Prompt), onEvent = {})
  }
}

@DayNightPreviews
@Composable
private fun ShakeToReportDialogSubmittingPreview() {
  Previews.Preview {
    ShakeToReportDialog(state = ShakeToReportState(step = Step.Submitting), onEvent = {})
  }
}

@DayNightPreviews
@Composable
private fun ShakeToReportDialogSubmittedPreview() {
  Previews.Preview {
    ShakeToReportDialog(state = ShakeToReportState(step = Step.Submitted("https://debuglogs.org/android/8.32.0/abc123.zip")), onEvent = {})
  }
}

@DayNightPreviews
@Composable
private fun ShakeToReportDialogFailedPreview() {
  Previews.Preview {
    ShakeToReportDialog(state = ShakeToReportState(step = Step.Failed), onEvent = {})
  }
}
