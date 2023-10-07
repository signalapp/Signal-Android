/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import org.signal.core.ui.Buttons
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.Texts
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonationProcessorAction
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe.StripePaymentInProgressViewModel
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Collects SEPA Debit bank transfer details from the user to proceed with donation.
 */
class BankTransferDetailsFragment : ComposeFragment() {

  private val args: BankTransferDetailsFragmentArgs by navArgs()
  private val viewModel: BankTransferDetailsViewModel by viewModels()

  private val stripePaymentViewModel: StripePaymentInProgressViewModel by navGraphViewModels(
    R.id.donate_to_signal,
    factoryProducer = {
      StripePaymentInProgressViewModel.Factory(requireListener<DonationPaymentComponent>().stripeRepository)
    }
  )

  @Composable
  override fun FragmentContent() {
    val state: BankTransferDetailsState by viewModel.state

    val donateLabel = remember(args.request) {
      if (args.request.donateToSignalType == DonateToSignalType.MONTHLY) {
        getString(
          R.string.BankTransferDetailsFragment__donate_s_month,
          FiatMoneyUtil.format(resources, args.request.fiat, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
        )
      } else {
        getString(
          R.string.BankTransferDetailsFragment__donate_s,
          FiatMoneyUtil.format(resources, args.request.fiat)
        )
      }
    }

    BankTransferDetailsContent(
      state = state,
      onNavigationClick = this::onNavigationClick,
      onNameChanged = viewModel::onNameChanged,
      onIBANChanged = viewModel::onIBANChanged,
      onEmailChanged = viewModel::onEmailChanged,
      onFindAccountNumbersClicked = this::onFindAccountNumbersClicked,
      onDonateClick = this::onDonateClick,
      onIBANFocusChanged = viewModel::onIBANFocusChanged,
      donateLabel = donateLabel
    )
  }

  private fun onNavigationClick() {
    findNavController().popBackStack()
  }

  private fun onFindAccountNumbersClicked() {
    // TODO [sepa] -- FindAccountNumbersBottomSheet
  }

  private fun onDonateClick() {
    stripePaymentViewModel.provideSEPADebitData(viewModel.state.value.asSEPADebitData())
    findNavController().safeNavigate(
      BankTransferDetailsFragmentDirections.actionBankTransferDetailsFragmentToStripePaymentInProgressFragment(
        DonationProcessorAction.PROCESS_NEW_DONATION,
        args.request
      )
    )
  }
}

@Preview
@Composable
private fun BankTransferDetailsContentPreview() {
  SignalTheme {
    BankTransferDetailsContent(
      state = BankTransferDetailsState(
        name = "Miles Morales"
      ),
      onNavigationClick = {},
      onNameChanged = {},
      onIBANChanged = {},
      onEmailChanged = {},
      onFindAccountNumbersClicked = {},
      onDonateClick = {},
      onIBANFocusChanged = {},
      donateLabel = "Donate $5/month"
    )
  }
}

@Composable
private fun BankTransferDetailsContent(
  state: BankTransferDetailsState,
  onNavigationClick: () -> Unit,
  onNameChanged: (String) -> Unit,
  onIBANChanged: (String) -> Unit,
  onEmailChanged: (String) -> Unit,
  onFindAccountNumbersClicked: () -> Unit,
  onDonateClick: () -> Unit,
  onIBANFocusChanged: (Boolean) -> Unit,
  donateLabel: String
) {
  Scaffolds.Settings(
    title = "Bank transfer",
    onNavigationClick = onNavigationClick,
    navigationIconPainter = painterResource(id = R.drawable.symbol_arrow_left_24)
  ) {
    Column(
      horizontalAlignment = CenterHorizontally,
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .padding(it)
    ) {
      val focusManager = LocalFocusManager.current
      val focusRequester = remember { FocusRequester() }

      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 24.dp)
      ) {
        item {
          val learnMore = stringResource(id = R.string.BankTransferDetailsFragment__learn_more)
          val fullString = stringResource(id = R.string.BankTransferDetailsFragment__enter_your_bank_details, learnMore)
          val context = LocalContext.current

          Texts.LinkifiedText(
            textWithUrlSpans = SpanUtil.urlSubsequence(fullString, learnMore, stringResource(id = R.string.donate_url)), // TODO [alex] -- final URL
            onUrlClick = {
              CommunicationActions.openBrowserLink(context, it)
            },
            style = MaterialTheme.typography.bodyLarge.copy(
              color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(vertical = 12.dp)
          )
        }

        item {
          TextField(
            value = state.iban,
            onValueChange = onIBANChanged,
            label = {
              Text(text = stringResource(id = R.string.BankTransferDetailsFragment__iban))
            },
            keyboardOptions = KeyboardOptions(
              capitalization = KeyboardCapitalization.Characters,
              imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
              onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            isError = state.ibanValidity.isError,
            supportingText = {
              if (state.ibanValidity.isError) {
                Text(
                  text = when (state.ibanValidity) {
                    IBANValidator.Validity.TOO_SHORT -> stringResource(id = R.string.BankTransferDetailsFragment__iban_nubmer_is_too_short)
                    IBANValidator.Validity.TOO_LONG -> stringResource(id = R.string.BankTransferDetailsFragment__iban_nubmer_is_too_long)
                    IBANValidator.Validity.INVALID_COUNTRY -> stringResource(id = R.string.BankTransferDetailsFragment__iban_country_code_is_not_supported)
                    IBANValidator.Validity.INVALID_CHARACTERS -> stringResource(id = R.string.BankTransferDetailsFragment__invalid_iban_nubmer)
                    IBANValidator.Validity.INVALID_MOD_97 -> stringResource(id = R.string.BankTransferDetailsFragment__invalid_iban_nubmer)
                    else -> error("Unexpected error.")
                  }
                )
              }
            },
            visualTransformation = IBANVisualTransformation,
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 12.dp)
              .onFocusChanged { onIBANFocusChanged(it.hasFocus) }
              .focusRequester(focusRequester)
          )
        }

        item {
          TextField(
            value = state.name,
            onValueChange = onNameChanged,
            label = {
              Text(text = stringResource(id = R.string.BankTransferDetailsFragment__name_on_bank_account))
            },
            keyboardOptions = KeyboardOptions(
              capitalization = KeyboardCapitalization.Words,
              imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
              onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp)
          )
        }

        item {
          TextField(
            value = state.email,
            onValueChange = onEmailChanged,
            label = {
              Text(text = stringResource(id = R.string.BankTransferDetailsFragment__email))
            },
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Email,
              imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
              onDone = { onDonateClick() }
            ),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp)
          )
        }

        item {
          Box(
            contentAlignment = Center,
            modifier = Modifier.fillMaxWidth()
          ) {
            TextButton(
              onClick = onFindAccountNumbersClicked
            ) {
              Text(text = stringResource(id = R.string.BankTransferDetailsFragment__find_account_numbers))
            }
          }
        }
      }

      Buttons.LargeTonal(
        enabled = state.canProceed,
        onClick = onDonateClick,
        modifier = Modifier
          .defaultMinSize(minWidth = 220.dp)
          .padding(bottom = 16.dp)
      ) {
        Text(text = donateLabel)
      }

      LaunchedEffect(Unit) {
        focusRequester.requestFocus()
      }
    }
  }
}
