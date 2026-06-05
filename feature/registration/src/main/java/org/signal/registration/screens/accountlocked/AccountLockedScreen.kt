/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.accountlocked

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper

/**
 * Screen shown when the user's account is locked due to too many failed PIN attempts
 * and there's no SVR data available to recover.
 */
@Composable
fun AccountLockedScreen(
  state: AccountLockedState,
  onEvent: (AccountLockedScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  when (val layoutParams = RegistrationScaffold.rememberLayoutParams()) {
    is RegistrationScaffold.Params.OnePane -> OnePaneLayout(layoutParams, state, onEvent, modifier)
    is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(layoutParams, state, onEvent, modifier)
  }
}

@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  state: AccountLockedState,
  onEvent: (AccountLockedScreenEvents) -> Unit,
  modifier: Modifier
) {
  val scrollState = rememberScrollState()
  OnePaneRegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    params = params,
    content = { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Title()
        Spacer(modifier = Modifier.height(12.dp))
        Description(state)
      }
    },
    footer = {
      RegistrationScaffold.FooterSurface(
        isContentScrolledUnder = scrollState.canScrollForward
      ) {
        Column(
          modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .horizontalGutters(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          NextButton(
            onEvent,
            modifier = Modifier
              .widthIn(max = params.maxButtonWidth)
              .fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(16.dp))
          LearnMore(onEvent)
        }
      }
    }
  )
}

@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: AccountLockedState,
  onEvent: (AccountLockedScreenEvents) -> Unit,
  modifier: Modifier
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()

  TwoPaneRegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    params = params,
    firstPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Title()
      }
    },
    secondPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues)
      ) {
        Description(state)
      }
    },
    footer = {
      RegistrationScaffold.FooterSurface(
        isContentScrolledUnder = firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward
      ) {
        Row(
          modifier = modifier
            .padding(vertical = 16.dp)
            .fillMaxWidth()
            .horizontalGutters(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          LearnMore(onEvent)
          Spacer(modifier = Modifier.size(16.dp))
          NextButton(onEvent, modifier = Modifier.widthIn(max = params.maxButtonWidth))
        }
      }
    }
  )
}

@Composable
private fun Title() {
  Text(
    text = stringResource(R.string.AccountLockedScreen__account_locked),
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )
}

@Composable
private fun Description(state: AccountLockedState) {
  Text(
    text = stringResource(R.string.AccountLockedScreen__your_account, state.daysRemaining),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth()
  )
}

@Composable
private fun NextButton(onEvent: (AccountLockedScreenEvents) -> Unit, modifier: Modifier = Modifier) {
  Button(
    onClick = { onEvent(AccountLockedScreenEvents.Next) },
    modifier = modifier
  ) {
    Text(stringResource(R.string.RegistrationActivity_next))
  }
}

@Composable
private fun LearnMore(onEvent: (AccountLockedScreenEvents) -> Unit) {
  Text(
    text = buildAnnotatedString {
      withLink(
        LinkAnnotation.Clickable(
          tag = "learn-more",
          styles = TextLinkStyles(
            style = SpanStyle(
              color = MaterialTheme.colorScheme.primary
            )
          )
        ) {
          onEvent(AccountLockedScreenEvents.LearnMore)
        }
      ) {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
          append(stringResource(id = R.string.RegistrationActivity_learn_more))
        }
      }
    },
    textAlign = TextAlign.Center
  )
}

@AllDevicePreviews
@Composable
private fun AccountLockedScreenPreview() {
  Previews.Preview {
    AccountLockedScreen(
      state = AccountLockedState(daysRemaining = 7),
      onEvent = {}
    )
  }
}
