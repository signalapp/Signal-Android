/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin.viewdetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.signallogin.R
import org.signal.signallogin.SignalLoginTestTags
import org.signal.signallogin.details.SignalLoginKeyDetails

/** Size of the miniature credential card artwork shown at the top of the screen, from the design. */
private val MINI_CARD_WIDTH = 175.dp
private val MINI_CARD_HEIGHT = 100.dp

/** Corner radius of the card artwork (26dp in its 363dp-wide coordinates), scaled down to the miniature size. */
private val MINI_CARD_CORNER_RADIUS = 13.dp

private val BUTTON_MAX_WIDTH = 331.dp

/**
 * Shows the user the full keys that make up their Signal Login and offers ways to save them.
 */
@Composable
fun SignalLoginViewDetailsScreen(
  state: SignalLoginViewDetailsState,
  onEvent: (SignalLoginViewDetailsScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.SignalLoginViewDetailsScreen__signal_login),
    onNavigationClick = { onEvent(SignalLoginViewDetailsScreenEvents.BackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(R.string.SignalLoginViewDetailsScreen__navigate_back),
    modifier = modifier.testTag(SignalLoginTestTags.VIEW_DETAILS_SCREEN)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        MiniCard(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        SignalLoginKeyDetails(
          accountId = state.accountKey,
          recoveryKeyGroups = state.recoveryKeyGroups,
          onCopyAccountId = { onEvent(SignalLoginViewDetailsScreenEvents.CopyAccountIdClicked(it)) },
          onCopyRecoveryKey = { onEvent(SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked(it)) }
        )
      }

      Footer(onEvent = onEvent)
    }
  }
}

/**
 * A miniature of the credential card artwork, without any of the card's content.
 */
@Composable
private fun MiniCard(modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(R.drawable.image_signal_login_card),
    contentDescription = null,
    contentScale = ContentScale.FillBounds,
    modifier = modifier
      .size(width = MINI_CARD_WIDTH, height = MINI_CARD_HEIGHT)
      .shadow(elevation = 6.dp, shape = RoundedCornerShape(MINI_CARD_CORNER_RADIUS))
  )
}

@Composable
private fun Footer(onEvent: (SignalLoginViewDetailsScreenEvents) -> Unit) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 16.dp)
  ) {
    Buttons.MediumTonal(
      onClick = { onEvent(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked) },
      colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
      ),
      modifier = Modifier
        .widthIn(max = BUTTON_MAX_WIDTH)
        .fillMaxWidth()
        .testTag(SignalLoginTestTags.VIEW_DETAILS_SAVE_TO_PASSWORD_MANAGER_BUTTON)
    ) {
      Text(stringResource(R.string.SignalLoginViewDetailsScreen__save_to_password_manager))
    }

    Buttons.MediumTonal(
      onClick = { onEvent(SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked) },
      colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
      ),
      modifier = Modifier
        .widthIn(max = BUTTON_MAX_WIDTH)
        .fillMaxWidth()
        .testTag(SignalLoginTestTags.VIEW_DETAILS_SAVE_AS_PDF_BUTTON)
    ) {
      Text(stringResource(R.string.SignalLoginViewDetailsScreen__save_as_pdf))
    }
  }
}

@DayNightPreviews
@Composable
private fun SignalLoginViewDetailsScreenPreview() {
  Previews.Preview {
    SignalLoginViewDetailsScreen(
      state = SignalLoginViewDetailsState(
        accountKey = "A6B28482-2E32-83D0-7F23-91360A4C2B91",
        recoveryKey = "UY38JH2778HJJHJ8LK19GA61S672JSJ=89R=23S6A578=9BAP92J2YH5T326VV7T"
      ),
      onEvent = {}
    )
  }
}
