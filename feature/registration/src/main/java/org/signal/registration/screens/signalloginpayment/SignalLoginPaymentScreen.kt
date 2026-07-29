/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.screens.shared.BackTopAppBar
import org.signal.registration.screens.signalloginpayment.SignalLoginPaymentState.Option
import org.signal.registration.test.TestTags

private val CARD_SHAPE = RoundedCornerShape(18.dp)
private val CARD_BORDER_WIDTH = 3.5.dp

/**
 * Lets the user buy a Signal Login so they can register without a phone number, or indicate that they already have one.
 */
@Composable
fun SignalLoginPaymentScreen(
  state: SignalLoginPaymentState,
  onEvent: (SignalLoginPaymentScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val simpleError: Pair<String, SignalLoginPaymentScreenEvents>? = when {
    state.dialogs.networkError -> stringResource(R.string.VerificationCodeScreen__network_error) to SignalLoginPaymentScreenEvents.NetworkErrorDialogDismissed
    state.dialogs.purchaseFailed -> stringResource(R.string.SignalLoginPaymentScreen__your_purchase_could_not_be_completed) to SignalLoginPaymentScreenEvents.PurchaseFailedDialogDismissed
    state.dialogs.unknownError -> stringResource(R.string.VerificationCodeScreen__an_unexpected_error_occurred) to SignalLoginPaymentScreenEvents.UnknownErrorDialogDismissed
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
      .testTag(TestTags.SIGNAL_LOGIN_PAYMENT_SCREEN)
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
  state: SignalLoginPaymentState,
  onEvent: (SignalLoginPaymentScreenEvents) -> Unit
) {
  val scrollState = rememberScrollState()
  val topBarScrollBehavior = RegistrationScaffold.rememberTopBarScrollBehavior()

  OnePaneRegistrationScaffold(
    params = params,
    topBar = { BackTopAppBar(scrollBehavior = topBarScrollBehavior, onBackClick = { onEvent(SignalLoginPaymentScreenEvents.BackClicked) }) },
    content = { paddingValues ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxSize()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(scrollState)
          .padding(paddingValues)
      ) {
        Header(onEvent = onEvent)

        Spacer(modifier = Modifier.height(32.dp))

        OptionCards(state = state, onEvent = onEvent)
      }
    },
    footer = { Footer(params, state, scrollState.canScrollForward, onEvent) }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: SignalLoginPaymentState,
  onEvent: (SignalLoginPaymentScreenEvents) -> Unit
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()
  val topBarScrollBehavior = RegistrationScaffold.rememberTopBarScrollBehavior()

  TwoPaneRegistrationScaffold(
    params = params,
    topBar = { BackTopAppBar(scrollBehavior = topBarScrollBehavior, onBackClick = { onEvent(SignalLoginPaymentScreenEvents.BackClicked) }) },
    firstPane = { paddingValues ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      ) {
        Header(twoPane = true, onEvent = onEvent)
      }
    },
    secondPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues)
      ) {
        OptionCards(state = state, onEvent = onEvent)
      }
    },
    footer = { Footer(params, state, firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward, onEvent) }
  )
}

@Composable
private fun Header(
  onEvent: (SignalLoginPaymentScreenEvents) -> Unit,
  twoPane: Boolean = false
) {
  Image(
    painter = painterResource(R.drawable.image_signal_login_ring),
    contentDescription = null,
    modifier = Modifier.size(64.dp)
  )

  Spacer(modifier = Modifier.height(20.dp))

  Text(
    text = stringResource(R.string.SignalLoginPaymentScreen__signal_login),
    style = if (twoPane) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )

  Spacer(modifier = Modifier.height(12.dp))

  Text(
    text = buildAnnotatedString {
      append(stringResource(R.string.SignalLoginPaymentScreen__register_without_a_phone_number))
      append(' ')

      withLink(
        LinkAnnotation.Clickable(
          tag = "learn-more",
          styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary)),
          linkInteractionListener = { onEvent(SignalLoginPaymentScreenEvents.LearnMoreClicked) }
        )
      ) {
        append(stringResource(R.string.SignalLoginPaymentScreen__learn_more))
      }
    },
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .fillMaxWidth()
      .testTag(TestTags.SIGNAL_LOGIN_PAYMENT_LEARN_MORE_LINK)
  )
}

@Composable
private fun OptionCards(
  state: SignalLoginPaymentState,
  onEvent: (SignalLoginPaymentScreenEvents) -> Unit
) {
  OptionCard(
    title = {
      if (state.formattedPrice != null) {
        Text(text = state.formattedPrice, style = MaterialTheme.typography.titleMedium)
      } else {
        CircularProgressIndicator(
          strokeWidth = 2.dp,
          modifier = Modifier.size(20.dp)
        )
      }
    },
    subtitle = stringResource(R.string.SignalLoginPaymentScreen__one_time_purchase),
    selected = state.selectedOption == Option.Purchase,
    onClick = { onEvent(SignalLoginPaymentScreenEvents.OptionSelected(Option.Purchase)) },
    modifier = Modifier.testTag(TestTags.SIGNAL_LOGIN_PAYMENT_PURCHASE_OPTION)
  ) {
    FeatureRow(painterResource(R.drawable.symbol_no_phone_44), stringResource(R.string.SignalLoginPaymentScreen__no_phone_number_needed))
    FeatureRow(SignalIcons.At.painter, stringResource(R.string.SignalLoginPaymentScreen__username_for_messaging_and_calls))
    FeatureRow(painterResource(R.drawable.symbol_heart_24), stringResource(R.string.SignalLoginPaymentScreen__signal_is_a_non_profit))
  }

  Spacer(modifier = Modifier.height(16.dp))

  OptionCard(
    title = {
      Text(
        text = stringResource(R.string.SignalLoginPaymentScreen__i_have_a_signal_login),
        style = MaterialTheme.typography.titleMedium
      )
    },
    subtitle = stringResource(R.string.SignalLoginPaymentScreen__use_your_existing_account_key),
    selected = state.selectedOption == Option.ExistingLogin,
    onClick = { onEvent(SignalLoginPaymentScreenEvents.OptionSelected(Option.ExistingLogin)) },
    modifier = Modifier.testTag(TestTags.SIGNAL_LOGIN_PAYMENT_EXISTING_LOGIN_OPTION)
  ) {
    FeatureRow(SignalIcons.DevicePhone.painter, stringResource(R.string.SignalLoginPaymentScreen__login_and_restore_your_account))
    FeatureRow(painterResource(R.drawable.symbol_heart_24), stringResource(R.string.SignalLoginPaymentScreen__thanks_for_supporting_signal))
  }
}

@Composable
private fun OptionCard(
  title: @Composable () -> Unit,
  subtitle: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  features: @Composable ColumnScope.() -> Unit
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(CARD_SHAPE)
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .border(
        width = CARD_BORDER_WIDTH,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        shape = CARD_SHAPE
      )
      .padding(CARD_BORDER_WIDTH)
      .clip(CARD_SHAPE)
      .background(SignalTheme.colors.colorSurface2)
      .padding(horizontal = 20.dp, vertical = 16.dp)
  ) {
    title()

    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(16.dp))

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
      features()
    }
  }
}

@Composable
private fun FeatureRow(
  icon: Painter,
  text: String
) {
  Row(verticalAlignment = Alignment.Top) {
    Icon(
      painter = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .padding(top = 2.dp)
        .size(20.dp)
    )

    Spacer(modifier = Modifier.width(12.dp))

    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge
    )
  }
}

@Composable
private fun Footer(
  params: RegistrationScaffold.Params,
  state: SignalLoginPaymentState,
  isElevated: Boolean,
  onEvent: (SignalLoginPaymentScreenEvents) -> Unit
) {
  RegistrationScaffold.FooterSurface(isElevated = isElevated) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(params.footerPadding),
      contentAlignment = Alignment.Center
    ) {
      Buttons.LargeTonal(
        onClick = { onEvent(SignalLoginPaymentScreenEvents.ContinueClicked) },
        enabled = state.isActionEnabled,
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
          .widthIn(max = params.maxButtonWidth)
          .fillMaxWidth()
          .testTag(TestTags.SIGNAL_LOGIN_PAYMENT_CONTINUE_BUTTON)
      ) {
        if (state.showSpinner) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp)
          )
        } else {
          Text(
            text = when {
              state.selectedOption == Option.ExistingLogin -> stringResource(R.string.SignalLoginPaymentScreen__continue)
              state.formattedPrice != null -> stringResource(R.string.SignalLoginPaymentScreen__pay_s, state.formattedPrice)
              else -> stringResource(R.string.SignalLoginPaymentScreen__pay)
            }
          )
        }
      }
    }
  }
}

@AllDevicePreviews
@Composable
private fun SignalLoginPaymentScreenPreview() {
  Previews.Preview {
    SignalLoginPaymentScreen(
      state = SignalLoginPaymentState(formattedPrice = "$1.99"),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun SignalLoginPaymentScreenExistingLoginPreview() {
  Previews.Preview {
    SignalLoginPaymentScreen(
      state = SignalLoginPaymentState(
        formattedPrice = "$1.99",
        selectedOption = Option.ExistingLogin
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun SignalLoginPaymentScreenLoadingPricePreview() {
  Previews.Preview {
    SignalLoginPaymentScreen(
      state = SignalLoginPaymentState(),
      onEvent = {}
    )
  }
}
