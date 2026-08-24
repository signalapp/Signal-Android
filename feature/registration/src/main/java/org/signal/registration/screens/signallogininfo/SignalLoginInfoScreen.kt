/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogininfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.R
import org.signal.registration.fonts.MonoTypeface
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.screens.shared.BackTopAppBar
import org.signal.registration.test.TestTags

/** Aspect ratio of the credential card artwork, so it scales with the available width. */
private const val CARD_ASPECT_RATIO = 363f / 220f

private val CARD_MAX_WIDTH = 363.dp

/** Number of masking dots shown in front of the revealed suffix of each credential. */
private const val MASK_DOT_COUNT = 4

// Vertical space within the card is split by weight, using the gaps from the design (in its 220dp-tall coordinates) so
// that everything scales together with the artwork.
private const val WORDMARK_WEIGHT = 80f
private const val PILL_GAP_WEIGHT = 28f
private const val BOTTOM_WEIGHT = 24f

/**
 * Presents the Signal Login the user just purchased and prompts them to save it, either into the system password
 * manager or by recording it themselves.
 */
@Composable
fun SignalLoginInfoScreen(
  state: SignalLoginInfoState,
  onEvent: (SignalLoginInfoScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  if (state.dialogs.credentialDetails) {
    Dialogs.SimpleMessageDialog(
      title = stringResource(R.string.SignalLoginInfoScreen__your_signal_login),
      message = stringResource(
        R.string.SignalLoginInfoScreen__account_s_recovery_s,
        state.accountIdentifier,
        state.recoveryKey
      ),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onEvent(SignalLoginInfoScreenEvents.CredentialDetailsDismissed) }
    )
  }

  val simpleError: Pair<String, SignalLoginInfoScreenEvents>? = when {
    state.dialogs.saveFailed -> stringResource(R.string.SignalLoginInfoScreen__your_signal_login_could_not_be_saved) to SignalLoginInfoScreenEvents.SaveFailedDialogDismissed
    state.dialogs.unknownError -> stringResource(R.string.VerificationCodeScreen__an_unexpected_error_occurred) to SignalLoginInfoScreenEvents.UnknownErrorDialogDismissed
    else -> null
  }

  simpleError?.let { (message, dismissedEvent) ->
    Dialogs.SimpleMessageDialog(
      message = message,
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onEvent(dismissedEvent) }
    )
  }

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.SIGNAL_LOGIN_INFO_SCREEN)
  ) {
    when (val params = RegistrationScaffold.rememberLayoutParams()) {
      is RegistrationScaffold.Params.OnePane -> OnePaneLayout(params, state, onEvent)
      is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(params, state, onEvent)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  state: SignalLoginInfoState,
  onEvent: (SignalLoginInfoScreenEvents) -> Unit
) {
  val scrollState = rememberScrollState()
  val topBarScrollBehavior = RegistrationScaffold.rememberTopBarScrollBehavior()

  OnePaneRegistrationScaffold(
    params = params,
    topBar = { BackTopAppBar(scrollBehavior = topBarScrollBehavior, onBackClick = { onEvent(SignalLoginInfoScreenEvents.BackClicked) }) },
    content = { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(scrollState)
          .padding(paddingValues)
      ) {
        Header()

        Spacer(modifier = Modifier.height(32.dp))

        CredentialCard(
          state = state,
          onEvent = onEvent,
          modifier = Modifier.align(Alignment.CenterHorizontally)
        )
      }
    },
    footer = { Footer(params, state, scrollState.canScrollForward, onEvent) }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: SignalLoginInfoState,
  onEvent: (SignalLoginInfoScreenEvents) -> Unit
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()
  val topBarScrollBehavior = RegistrationScaffold.rememberTopBarScrollBehavior()

  TwoPaneRegistrationScaffold(
    params = params,
    topBar = { BackTopAppBar(scrollBehavior = topBarScrollBehavior, onBackClick = { onEvent(SignalLoginInfoScreenEvents.BackClicked) }) },
    firstPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      ) {
        Header(twoPane = true)
      }
    },
    secondPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues),
        verticalArrangement = Arrangement.Center
      ) {
        CredentialCard(
          state = state,
          onEvent = onEvent,
          modifier = Modifier.align(Alignment.CenterHorizontally)
        )
      }
    },
    footer = { Footer(params, state, firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward, onEvent) }
  )
}

@Composable
private fun ColumnScope.Header(twoPane: Boolean = false) {
  Image(
    painter = painterResource(R.drawable.image_signal_login_key),
    contentDescription = null,
    modifier = Modifier
      .padding(bottom = 24.dp)
      .align(Alignment.CenterHorizontally)
      .size(width = 84.dp, height = 92.dp)
  )

  Text(
    text = stringResource(R.string.SignalLoginInfoScreen__your_signal_login),
    style = if (twoPane) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )

  Text(
    text = stringResource(R.string.SignalLoginInfoScreen__thanks_for_your_purchase),
    style = if (twoPane) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 16.dp)
  )
}

/**
 * The Signal-branded card showing the purchased credentials, masked down to their final few characters.
 */
@Composable
private fun CredentialCard(
  state: SignalLoginInfoState,
  onEvent: (SignalLoginInfoScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .widthIn(max = CARD_MAX_WIDTH)
      .fillMaxWidth()
      .aspectRatio(CARD_ASPECT_RATIO)
      .testTag(TestTags.SIGNAL_LOGIN_INFO_CREDENTIAL_CARD)
  ) {
    Image(
      painter = painterResource(R.drawable.image_signal_login_card),
      contentDescription = null,
      contentScale = ContentScale.FillBounds,
      modifier = Modifier.fillMaxSize()
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp)
    ) {
      // The card artwork scales with the box, so the space it reserves for the baked-in "Signal" wordmark is
      // apportioned by weight rather than a fixed height.
      Spacer(modifier = Modifier.weight(WORDMARK_WEIGHT))

      Row(modifier = Modifier.fillMaxWidth()) {
        MaskedCredential(
          label = stringResource(R.string.SignalLoginInfoScreen__account),
          visibleSuffix = state.accountSuffix,
          modifier = Modifier.weight(1f)
        )

        MaskedCredential(
          label = stringResource(R.string.SignalLoginInfoScreen__recovery),
          visibleSuffix = state.recoverySuffix,
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.weight(PILL_GAP_WEIGHT))

      ViewDetailsButton(
        onClick = { onEvent(SignalLoginInfoScreenEvents.ViewDetailsClicked) },
        modifier = Modifier.align(Alignment.CenterHorizontally)
      )

      Spacer(modifier = Modifier.weight(BOTTOM_WEIGHT))
    }
  }
}

@Composable
private fun MaskedCredential(
  label: String,
  visibleSuffix: String,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = Color.White.copy(alpha = 0.6f)
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
      repeat(MASK_DOT_COUNT) {
        Box(
          modifier = Modifier
            .padding(end = 8.dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(Color.White)
        )
      }

      Spacer(modifier = Modifier.width(4.dp))

      Text(
        text = visibleSuffix,
        style = MaterialTheme.typography.bodyMedium.copy(
          fontFamily = MonoTypeface.fontFamily(),
          fontSize = 15.sp,
          letterSpacing = 2.sp
        ),
        color = Color.White
      )
    }
  }
}

@Composable
private fun ViewDetailsButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(18.dp))
      .background(Color.White.copy(alpha = 0.2f))
      .clickable(onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 8.dp)
      .testTag(TestTags.SIGNAL_LOGIN_INFO_VIEW_DETAILS_BUTTON)
  ) {
    Text(
      text = stringResource(R.string.SignalLoginInfoScreen__view_details),
      style = MaterialTheme.typography.labelLarge,
      color = Color.White.copy(alpha = 0.96f)
    )
  }
}

@Composable
private fun Footer(
  params: RegistrationScaffold.Params,
  state: SignalLoginInfoState,
  isElevated: Boolean,
  onEvent: (SignalLoginInfoScreenEvents) -> Unit
) {
  RegistrationScaffold.FooterSurface(isElevated = isElevated) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(params.footerPadding)
    ) {
      if (state.isPasswordManagerAvailable) {
        Buttons.LargeTonal(
          onClick = { onEvent(SignalLoginInfoScreenEvents.SaveToPasswordManagerClicked) },
          enabled = !state.showSpinner,
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
          ),
          modifier = Modifier
            .widthIn(max = params.maxButtonWidth)
            .fillMaxWidth()
            .testTag(TestTags.SIGNAL_LOGIN_INFO_SAVE_TO_PASSWORD_MANAGER_BUTTON)
        ) {
          if (state.showSpinner) {
            CircularProgressIndicator(
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              strokeWidth = 2.dp,
              modifier = Modifier.size(20.dp)
            )
          } else {
            Text(stringResource(R.string.SignalLoginInfoScreen__save_to_password_manager))
          }
        }
      }

      Buttons.LargeTonal(
        onClick = { onEvent(SignalLoginInfoScreenEvents.SaveManuallyClicked) },
        enabled = !state.showSpinner,
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = SignalTheme.colors.colorSurface2,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
          .widthIn(max = params.maxButtonWidth)
          .fillMaxWidth()
          .testTag(TestTags.SIGNAL_LOGIN_INFO_SAVE_MANUALLY_BUTTON)
      ) {
        Text(stringResource(R.string.SignalLoginInfoScreen__save_manually))
      }
    }
  }
}

@AllDevicePreviews
@Composable
private fun SignalLoginInfoScreenPreview() {
  Previews.Preview {
    SignalLoginInfoScreen(
      state = SignalLoginInfoState(
        accountIdentifier = "H7KQ2B91",
        recoveryKey = "3TXPVV7T",
        isPasswordManagerAvailable = true
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun SignalLoginInfoScreenNoPasswordManagerPreview() {
  Previews.Preview {
    SignalLoginInfoScreen(
      state = SignalLoginInfoState(
        accountIdentifier = "H7KQ2B91",
        recoveryKey = "3TXPVV7T"
      ),
      onEvent = {}
    )
  }
}
