/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin.viewdetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.BreakpointPreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.signallogin.R
import org.signal.signallogin.SignalLoginTestTags
import org.signal.signallogin.beta.SignalLoginBetaDisclaimer
import org.signal.signallogin.beta.SignalLoginBetaTag
import org.signal.signallogin.details.SignalLoginKeyDetails

/** Aspect ratio of the credential card artwork. */
private const val CARD_ASPECT_RATIO = 1.75f

/** The artwork's 26dp corner radius as a fraction of its 363dp design width, so the radius tracks the card's size. */
private const val CARD_CORNER_RADIUS_RATIO = 26f / 363f

private val smallLayout = Layout.OnePane(
  cardWidth = 175.dp,
  maxButtonWidth = 331.dp
)

private val mediumLayout = Layout.TwoPane(
  cardWidth = 240.dp,
  maxButtonWidth = 331.dp,
  paneOuterInset = 24.dp,
  paneInnerInset = 24.dp,
  paneVerticalInset = 24.dp
)

private val largeWidthLayout = Layout.TwoPane(
  cardWidth = 280.dp,
  maxButtonWidth = 412.dp,
  paneOuterInset = 128.dp,
  paneInnerInset = 64.dp,
  paneVerticalInset = 64.dp
)

private val largeHeightLayout = Layout.OnePane(
  cardWidth = 240.dp,
  maxButtonWidth = 331.dp
)

/**
 * Sizing that the screen switches between as the window grows, mirroring the registration flow's scaffold params.
 */
private sealed interface Layout {
  val cardWidth: Dp
  val maxButtonWidth: Dp

  data class OnePane(
    override val cardWidth: Dp,
    override val maxButtonWidth: Dp
  ) : Layout

  data class TwoPane(
    override val cardWidth: Dp,
    override val maxButtonWidth: Dp,
    val paneOuterInset: Dp,
    val paneInnerInset: Dp,
    val paneVerticalInset: Dp
  ) : Layout
}

@Composable
private fun rememberLayout(): Layout {
  return when (val breakpoint = rememberWindowBreakpoint()) {
    is WindowBreakpoint.Small -> smallLayout
    is WindowBreakpoint.Medium -> mediumLayout
    is WindowBreakpoint.Large -> if (breakpoint.isWidthExpanded) largeWidthLayout else largeHeightLayout
  }
}

/**
 * Shows the user the full keys that make up their Signal Login and offers ways to save them.
 */
@Composable
fun SignalLoginViewDetailsScreen(
  state: SignalLoginViewDetailsState,
  onEvent: (SignalLoginViewDetailsScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val layout = rememberLayout()
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()

  Scaffolds.Settings(
    title = stringResource(R.string.SignalLoginViewDetailsScreen__signal_login),
    onNavigationClick = { onEvent(SignalLoginViewDetailsScreenEvents.BackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(R.string.SignalLoginViewDetailsScreen__navigate_back),
    titleContent = { _, title -> TitleWithBetaTag(title) },
    bottomBar = {
      Footer(
        maxButtonWidth = layout.maxButtonWidth,
        isElevated = firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward,
        showSaveToPasswordManagerButton = state.showSaveToPasswordManagerButton,
        showResetRecoveryKeyButton = state.showResetRecoveryKeyButton,
        resetRecoveryKeyButtonLoading = state.resetRecoveryKeyButtonLoading,
        onEvent = onEvent
      )
    },
    modifier = modifier.testTag(SignalLoginTestTags.VIEW_DETAILS_SCREEN)
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      when (layout) {
        is Layout.OnePane -> OnePaneContent(
          layout = layout,
          scrollState = firstPaneScrollState,
          state = state,
          onEvent = onEvent
        )

        is Layout.TwoPane -> TwoPaneContent(
          layout = layout,
          firstPaneScrollState = firstPaneScrollState,
          secondPaneScrollState = secondPaneScrollState,
          state = state,
          onEvent = onEvent
        )
      }
    }
  }
}

@Composable
private fun OnePaneContent(
  layout: Layout.OnePane,
  scrollState: ScrollState,
  state: SignalLoginViewDetailsState,
  onEvent: (SignalLoginViewDetailsScreenEvents) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
  ) {
    SignalLoginBetaDisclaimer(
      modifier = Modifier
        .horizontalGutters()
        .padding(vertical = 12.dp)
    )

    CredentialCard(
      width = layout.cardWidth,
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(top = 8.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    KeyDetails(state = state, onEvent = onEvent)
  }
}

@Composable
private fun TwoPaneContent(
  layout: Layout.TwoPane,
  firstPaneScrollState: ScrollState,
  secondPaneScrollState: ScrollState,
  state: SignalLoginViewDetailsState,
  onEvent: (SignalLoginViewDetailsScreenEvents) -> Unit
) {
  Row(modifier = Modifier.fillMaxSize()) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .verticalScroll(firstPaneScrollState)
        .padding(
          start = layout.paneOuterInset,
          end = layout.paneInnerInset,
          top = layout.paneVerticalInset,
          bottom = layout.paneVerticalInset
        )
    ) {
      CredentialCard(width = layout.cardWidth)

      Spacer(modifier = Modifier.height(24.dp))

      SignalLoginBetaDisclaimer(textAlign = TextAlign.Center)
    }

    Column(
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .verticalScroll(secondPaneScrollState)
        .padding(vertical = layout.paneVerticalInset)
    ) {
      KeyDetails(state = state, onEvent = onEvent)
    }
  }
}

@Composable
private fun TitleWithBetaTag(title: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.weight(1f, fill = false)
    )

    SignalLoginBetaTag()
  }
}

/**
 * The credential card artwork, without any of the card's content.
 */
@Composable
private fun CredentialCard(width: Dp, modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(R.drawable.image_signal_login_card),
    contentDescription = null,
    contentScale = ContentScale.FillBounds,
    modifier = modifier
      .size(width = width, height = width / CARD_ASPECT_RATIO)
      .shadow(elevation = 6.dp, shape = RoundedCornerShape(width * CARD_CORNER_RADIUS_RATIO))
  )
}

@Composable
private fun KeyDetails(
  state: SignalLoginViewDetailsState,
  onEvent: (SignalLoginViewDetailsScreenEvents) -> Unit
) {
  SignalLoginKeyDetails(
    accountId = state.accountKey,
    recoveryKeyGroups = state.recoveryKeyGroups,
    onCopyAccountId = { onEvent(SignalLoginViewDetailsScreenEvents.CopyAccountIdClicked(it)) },
    onCopyRecoveryKey = { onEvent(SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked(it)) }
  )
}

@Composable
private fun Footer(
  maxButtonWidth: Dp,
  isElevated: Boolean,
  showSaveToPasswordManagerButton: Boolean,
  showResetRecoveryKeyButton: Boolean,
  resetRecoveryKeyButtonLoading: Boolean,
  onEvent: (SignalLoginViewDetailsScreenEvents) -> Unit
) {
  Surface(shadowElevation = if (isElevated) 8.dp else 0.dp) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .horizontalGutters()
        .padding(vertical = 16.dp)
    ) {
      if (showSaveToPasswordManagerButton) {
        Buttons.MediumTonal(
          onClick = { onEvent(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked) },
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
          ),
          modifier = Modifier
            .widthIn(max = maxButtonWidth)
            .fillMaxWidth()
            .testTag(SignalLoginTestTags.VIEW_DETAILS_SAVE_TO_PASSWORD_MANAGER_BUTTON)
        ) {
          Text(stringResource(R.string.SignalLoginViewDetailsScreen__save_to_password_manager))
        }
      }

      Buttons.MediumTonal(
        onClick = { onEvent(SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked) },
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
          .widthIn(max = maxButtonWidth)
          .fillMaxWidth()
          .testTag(SignalLoginTestTags.VIEW_DETAILS_SAVE_AS_PDF_BUTTON)
      ) {
        Text(stringResource(R.string.SignalLoginViewDetailsScreen__save_as_pdf))
      }

      if (showResetRecoveryKeyButton) {
        if (resetRecoveryKeyButtonLoading) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .height(ButtonDefaults.MinHeight)
              .testTag(SignalLoginTestTags.VIEW_DETAILS_RESET_RECOVERY_KEY_SPINNER)
          ) {
            CircularProgressIndicator(
              strokeWidth = 3.dp,
              modifier = Modifier.size(24.dp)
            )
          }
        } else {
          TextButton(
            onClick = { onEvent(SignalLoginViewDetailsScreenEvents.ResetRecoveryKeyClicked) },
            modifier = Modifier
              .widthIn(max = maxButtonWidth)
              .fillMaxWidth()
              .testTag(SignalLoginTestTags.VIEW_DETAILS_RESET_RECOVERY_KEY_BUTTON)
          ) {
            Text(stringResource(R.string.SignalLoginViewDetailsScreen__reset_recovery_key))
          }
        }
      }
    }
  }
}

@BreakpointPreviews
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

@AllDevicePreviews
@Composable
private fun SignalLoginViewDetailsScreenWithResetPreview() {
  Previews.Preview {
    SignalLoginViewDetailsScreen(
      state = SignalLoginViewDetailsState(
        accountKey = "A6B28482-2E32-83D0-7F23-91360A4C2B91",
        recoveryKey = "UY38JH2778HJJHJ8LK19GA61S672JSJ=89R=23S6A578=9BAP92J2YH5T326VV7T",
        showResetRecoveryKeyButton = true
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun SignalLoginViewDetailsScreenWithResetLoadingPreview() {
  Previews.Preview {
    SignalLoginViewDetailsScreen(
      state = SignalLoginViewDetailsState(
        accountKey = "A6B28482-2E32-83D0-7F23-91360A4C2B91",
        recoveryKey = "UY38JH2778HJJHJ8LK19GA61S672JSJ=89R=23S6A578=9BAP92J2YH5T326VV7T",
        showResetRecoveryKeyButton = true,
        resetRecoveryKeyButtonLoading = true
      ),
      onEvent = {}
    )
  }
}
