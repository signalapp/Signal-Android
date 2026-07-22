/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.messagesync

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.signal.core.ui.FormFactor
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.assumedFormFactor
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.KeepScreenOnEffect
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.fonts.SignalSymbols
import org.signal.core.ui.fonts.SignalSymbols.SignalSymbol
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.core.util.kibiBytes
import org.signal.core.util.mebiBytes
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.screens.messagesync.MessageSyncScreenState.Stage
import org.signal.registration.test.TestTags

/**
 * Displayed while we are syncing messages from the primary device.
 */
@Composable
fun MessageSyncScreen(
  state: MessageSyncScreenState,
  onEvent: (MessageSyncScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val layoutParams = RegistrationScaffold.rememberLayoutParams()

  if (!state.showSyncFailedDialog) {
    KeepScreenOnEffect()
  }

  Surface(modifier = modifier.testTag(TestTags.MESSAGE_SYNC_SCREEN)) {
    when (layoutParams) {
      is RegistrationScaffold.Params.OnePane -> OnePane(layoutParams, state, onEvent)
      is RegistrationScaffold.Params.TwoPane -> TwoPane(layoutParams, state, onEvent)
    }
  }

  if (state.showSyncFailedDialog) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.MessageSyncScreen__couldnt_restore_messages),
      body = stringResource(R.string.MessageSyncScreen__your_messages_couldnt_be_transferred),
      confirm = stringResource(R.string.MessageSyncScreen__try_again),
      onConfirm = { onEvent(MessageSyncScreenEvent.RetryClick) },
      dismiss = stringResource(R.string.MessageSyncScreen__continue_without_messages),
      onDeny = { onEvent(MessageSyncScreenEvent.ContinueWithoutMessagesClick) },
      properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
  }
}

@Composable
private fun OnePane(params: RegistrationScaffold.Params.OnePane, state: MessageSyncScreenState, onEvent: (MessageSyncScreenEvent) -> Unit) {
  val scrollState = rememberScrollState()

  OnePaneRegistrationScaffold(
    params = params,
    content = { paddingValues ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .verticalScroll(scrollState)
          .padding(paddingValues)
      ) {
        FirstPaneContent(state)
        Spacer(modifier = Modifier.height(16.dp))
        SecondPaneContent()
      }
    },
    footer = {
      FooterContent(
        params = params,
        isElevated = scrollState.canScrollForward,
        canCancel = !state.isFinishing,
        onEvent = onEvent
      )
    }
  )
}

@Composable
private fun TwoPane(params: RegistrationScaffold.Params.TwoPane, state: MessageSyncScreenState, onEvent: (MessageSyncScreenEvent) -> Unit) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()

  TwoPaneRegistrationScaffold(
    params = params,
    firstPane = { paddingValues ->
      FirstPaneContent(
        state = state,
        twoPane = true,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      )
    },
    secondPane = { paddingValues ->
      SecondPaneContent(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues)
      )
    },
    footer = {
      FooterContent(
        params = params,
        isElevated = firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward,
        canCancel = !state.isFinishing,
        onEvent = onEvent
      )
    }
  )
}

@Composable
private fun FirstPaneContent(
  state: MessageSyncScreenState,
  modifier: Modifier = Modifier,
  twoPane: Boolean = false
) {
  Column(modifier = modifier) {
    Text(
      text = stringResource(R.string.MessageSyncScreen__syncing_messages),
      style = if (twoPane) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
      modifier = Modifier
        .fillMaxWidth()
        .attachDebugLogHelper()
    )

    Text(
      text = stringResource(R.string.MessageSyncScreen__this_may_take_a_few_minutes),
      style = if (twoPane) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 16.dp)
    )

    val progressModifier = Modifier
      .padding(top = 48.dp, bottom = 16.dp)
      .widthIn(max = 415.dp)
      .fillMaxWidth()

    when (val stage = state.stage) {
      is Stage.Downloading -> LinearProgressIndicator(
        progress = { stage.downloaded.percentageOf(stage.total) },
        drawStopIndicator = {},
        gapSize = 0.dp,
        modifier = progressModifier
      )
      is Stage.Restoring -> LinearProgressIndicator(
        progress = { stage.restored.percentageOf(stage.total) },
        drawStopIndicator = {},
        gapSize = 0.dp,
        modifier = progressModifier
      )
      Stage.Preparing, Stage.Finishing -> LinearProgressIndicator(modifier = progressModifier)
    }

    Text(
      text = when (val stage = state.stage) {
        Stage.Preparing -> stringResource(R.string.MessageSyncScreen__preparing)
        is Stage.Downloading -> stringResource(
          R.string.MessageSyncScreen__downloading_s_of_s,
          stage.downloaded.toUnitString(),
          stage.total.toUnitString()
        )
        is Stage.Restoring -> stringResource(R.string.MessageSyncScreen__restoring)
        Stage.Finishing -> stringResource(R.string.MessageSyncScreen__finishing)
      },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun SecondPaneContent(
  modifier: Modifier = Modifier
) {
  val deviceImage = when (rememberWindowBreakpoint().assumedFormFactor) {
    FormFactor.PHONE -> R.drawable.device_phone
    FormFactor.FOLDABLE -> R.drawable.device_foldable
    FormFactor.TABLET -> R.drawable.device_tablet
  }

  Image(
    painter = painterResource(deviceImage),
    contentDescription = null,
    modifier = modifier
  )
}

@Composable
private fun FooterContent(
  params: RegistrationScaffold.Params,
  isElevated: Boolean,
  canCancel: Boolean,
  onEvent: (MessageSyncScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val breakpoint = rememberWindowBreakpoint()

  RegistrationScaffold.FooterSurface(
    isElevated = isElevated
  ) {
    when (breakpoint) {
      is WindowBreakpoint.Small, is WindowBreakpoint.Medium -> StackedFooter(params, canCancel, modifier, onEvent)
      is WindowBreakpoint.Large -> InlineFooter(params, canCancel, modifier, onEvent)
    }
  }
}

@Composable
private fun StackedFooter(
  params: RegistrationScaffold.Params,
  canCancel: Boolean,
  modifier: Modifier,
  onEvent: (MessageSyncScreenEvent) -> Unit
) {
  Column(
    modifier = modifier
      .padding(params.footerPadding)
      .fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Notice(
      onEvent = onEvent,
      modifier = Modifier.padding(bottom = 16.dp)
    )
    Cancel(
      enabled = canCancel,
      onEvent = onEvent,
      modifier = Modifier
        .widthIn(max = params.maxButtonWidth)
        .fillMaxWidth()
    )
  }
}

@Composable
private fun InlineFooter(
  params: RegistrationScaffold.Params,
  canCancel: Boolean,
  modifier: Modifier,
  onEvent: (MessageSyncScreenEvent) -> Unit
) {
  Row(
    modifier = modifier
      .padding(params.footerPadding)
      .fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Spacer(modifier = Modifier.weight(1f))

    Box(
      modifier = Modifier.weight(2f),
      contentAlignment = Alignment.Center
    ) {
      Notice(onEvent = onEvent)
    }

    Box(
      modifier = Modifier.weight(1f),
      contentAlignment = Alignment.CenterEnd
    ) {
      Cancel(
        enabled = canCancel,
        onEvent = onEvent,
        modifier = Modifier
          .widthIn(max = params.maxButtonWidth)
          .padding(start = 16.dp)
      )
    }
  }
}

@Composable
private fun Notice(
  modifier: Modifier = Modifier,
  onEvent: (MessageSyncScreenEvent) -> Unit
) {
  Row(modifier = modifier) {
    Text(
      text = buildAnnotatedString {
        SignalSymbol(glyph = SignalSymbols.Glyph.LOCK)
        append(' ')
        append(stringResource(R.string.MessageSyncScreen__messages_and_chat_info_are_protected_by_e2ee))
        append(' ')

        withLink(
          LinkAnnotation.Clickable(
            tag = "learn-more",
            styles = TextLinkStyles(
              style = SpanStyle(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline
              )
            ),
            linkInteractionListener = {
              onEvent(MessageSyncScreenEvent.LearnMoreClick)
            }
          )
        ) {
          append(stringResource(R.string.MessageSyncScreen__learn_more))
        }
      },
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .testTag(TestTags.MESSAGE_SYNC_LEARN_MORE_LINK)
        .widthIn(max = 405.dp)
        .align(Alignment.CenterVertically)
    )
  }
}

@Composable
private fun Cancel(onEvent: (MessageSyncScreenEvent) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  Buttons.LargeTonal(
    onClick = { onEvent(MessageSyncScreenEvent.CancelClick) },
    enabled = enabled,
    modifier = modifier.testTag(TestTags.MESSAGE_SYNC_CANCEL_BUTTON)
  ) {
    Text(text = stringResource(R.string.MessageSyncScreen__cancel))
  }
}

@AllDevicePreviews
@Composable
private fun MessageSyncScreenPreview() {
  Previews.Preview {
    MessageSyncScreen(
      state = MessageSyncScreenState(
        stage = Stage.Downloading(downloaded = 1.mebiBytes, total = 3300.kibiBytes)
      ),
      onEvent = {}
    )
  }
}
