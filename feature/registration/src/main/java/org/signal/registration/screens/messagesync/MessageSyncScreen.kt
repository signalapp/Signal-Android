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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.core.util.kibiBytes
import org.signal.core.util.mebiBytes
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
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

  Surface(modifier = modifier.testTag(TestTags.MESSAGE_SYNC_SCREEN)) {
    when (layoutParams) {
      is RegistrationScaffold.Params.OnePane -> OnePane(layoutParams, state, onEvent)
      is RegistrationScaffold.Params.TwoPane -> TwoPane(layoutParams, state, onEvent)
    }
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
        SecondPaneContent()
      }
    },
    footer = {
      FooterContent(
        params = params,
        isElevated = scrollState.canScrollForward,
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
        onEvent = onEvent
      )
    }
  )
}

@Composable
private fun FirstPaneContent(
  state: MessageSyncScreenState,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    Text(
      text = stringResource(R.string.MessageSyncScreen__syncing_messages),
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier
        .fillMaxWidth()
        .attachDebugLogHelper()
    )

    Text(
      text = stringResource(R.string.MessageSyncScreen__this_may_take_a_few_minutes),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 16.dp)
    )

    LinearProgressIndicator(
      progress = {
        if (state.totalBytes.bytes > 0) {
          state.downloadedBytes.bytes.toFloat() / state.totalBytes.bytes.toFloat()
        } else {
          0f
        }
      },
      drawStopIndicator = {},
      gapSize = 0.dp,
      modifier = Modifier
        .padding(top = 48.dp, bottom = 16.dp)
        .widthIn(max = 415.dp)
        .fillMaxWidth()
    )

    Text(
      text = stringResource(
        R.string.MessageSyncScreen__downloading_s_of_s,
        state.downloadedBytes.toUnitString(),
        state.totalBytes.toUnitString()
      ),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun SecondPaneContent(
  modifier: Modifier = Modifier
) {
  // TODO [regv5] Final image asset
  Image(
    painter = painterResource(R.drawable.welcome),
    contentDescription = null,
    modifier = modifier
  )
}

@Composable
private fun FooterContent(
  params: RegistrationScaffold.Params,
  isElevated: Boolean,
  onEvent: (MessageSyncScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val breakpoint = rememberWindowBreakpoint()

  RegistrationScaffold.FooterSurface(
    isElevated = isElevated
  ) {
    when (breakpoint) {
      is WindowBreakpoint.Small, is WindowBreakpoint.Medium -> StackedFooter(params, modifier, onEvent)
      is WindowBreakpoint.Large -> InlineFooter(params, modifier, onEvent)
    }
  }
}

@Composable
private fun StackedFooter(
  params: RegistrationScaffold.Params,
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
    Notice(onEvent = onEvent)

    Box(
      modifier = Modifier.weight(1f),
      contentAlignment = Alignment.CenterEnd
    ) {
      Cancel(
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
    Icon(
      imageVector = SignalIcons.Lock.imageVector,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .padding(end = 2.dp)
        .align(Alignment.CenterVertically)
    )

    Text(
      text = buildAnnotatedString {
        append(stringResource(R.string.MessageSyncScreen__messages_and_chat_info_are_protected_by_e2ee))
        append(' ')

        withLink(
          LinkAnnotation.Clickable(
            tag = "learn-more",
            styles = TextLinkStyles(
              style = SpanStyle(
                color = MaterialTheme.colorScheme.onSurface,
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
private fun Cancel(onEvent: (MessageSyncScreenEvent) -> Unit, modifier: Modifier = Modifier) {
  Buttons.LargeTonal(
    onClick = { onEvent(MessageSyncScreenEvent.CancelClick) },
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
        downloadedBytes = 1.mebiBytes,
        totalBytes = 3300.kibiBytes
      ),
      onEvent = {}
    )
  }
}
