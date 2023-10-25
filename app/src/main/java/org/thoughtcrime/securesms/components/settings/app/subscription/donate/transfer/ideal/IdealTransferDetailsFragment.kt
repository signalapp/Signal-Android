/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.ideal

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import org.signal.core.ui.Buttons
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.Texts
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.TemporaryScreenshotSecurity
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonationCheckoutDelegate
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonationProcessorAction
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonationProcessorActionResult
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayRequest
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe.StripePaymentInProgressFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe.StripePaymentInProgressViewModel
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.BankTransferRequestKeys
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Fragment for inputting necessary bank transfer information for iDEAL donation
 */
class IdealTransferDetailsFragment : ComposeFragment(), DonationCheckoutDelegate.ErrorHandlerCallback {

  private val args: IdealTransferDetailsFragmentArgs by navArgs()
  private val viewModel: IdealTransferDetailsViewModel by viewModels()

  private val stripePaymentViewModel: StripePaymentInProgressViewModel by navGraphViewModels(
    R.id.donate_to_signal,
    factoryProducer = {
      StripePaymentInProgressViewModel.Factory(requireListener<DonationPaymentComponent>().stripeRepository)
    }
  )

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    TemporaryScreenshotSecurity.bindToViewLifecycleOwner(this)

    val errorSource: DonationErrorSource = when (args.request.donateToSignalType) {
      DonateToSignalType.ONE_TIME -> DonationErrorSource.ONE_TIME
      DonateToSignalType.MONTHLY -> DonationErrorSource.MONTHLY
      DonateToSignalType.GIFT -> DonationErrorSource.GIFT
    }

    DonationCheckoutDelegate.ErrorHandler().attach(this, this, args.request.uiSessionKey, errorSource)

    setFragmentResultListener(StripePaymentInProgressFragment.REQUEST_KEY) { _, bundle ->
      val result: DonationProcessorActionResult = bundle.getParcelableCompat(StripePaymentInProgressFragment.REQUEST_KEY, DonationProcessorActionResult::class.java)!!
      if (result.status == DonationProcessorActionResult.Status.SUCCESS) {
        findNavController().popBackStack(R.id.donateToSignalFragment, false)
        setFragmentResult(BankTransferRequestKeys.REQUEST_KEY, bundle)
      }
    }

    setFragmentResultListener(IdealTransferDetailsBankSelectionDialogFragment.IDEAL_SELECTED_BANK) { _, bundle ->
      val bankCode = bundle.getString(IdealTransferDetailsBankSelectionDialogFragment.IDEAL_SELECTED_BANK)!!
      viewModel.onBankSelected(IdealBank.fromCode(bankCode))
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state

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

    IdealTransferDetailsContent(
      state = state,
      donateLabel = donateLabel,
      onNavigationClick = { findNavController().popBackStack() },
      onLearnMoreClick = { findNavController().navigate(IdealTransferDetailsFragmentDirections.actionBankTransferDetailsFragmentToYourInformationIsPrivateBottomSheet()) },
      onSelectBankClick = { findNavController().navigate(IdealTransferDetailsFragmentDirections.actionIdealTransferDetailsFragmentToIdealTransferBankSelectionDialogFragment()) },
      onNameChanged = viewModel::onNameChanged,
      onEmailChanged = viewModel::onEmailChanged,
      onDonateClick = this::onDonateClick
    )
  }

  private fun onDonateClick() {
    stripePaymentViewModel.provideIDEALData(viewModel.state.value.asIDEALData())
    findNavController().safeNavigate(
      IdealTransferDetailsFragmentDirections.actionBankTransferDetailsFragmentToStripePaymentInProgressFragment(
        DonationProcessorAction.PROCESS_NEW_DONATION,
        args.request
      )
    )
  }

  override fun onUserCancelledPaymentFlow() = Unit

  override fun navigateToDonationPending(gatewayRequest: GatewayRequest) {
    findNavController().popBackStack()
    findNavController().popBackStack()

    setFragmentResult(BankTransferRequestKeys.PENDING_KEY, bundleOf(BankTransferRequestKeys.PENDING_KEY to gatewayRequest))
  }
}

@Preview
@Composable
private fun IdealTransferDetailsContentPreview() {
  IdealTransferDetailsContent(
    state = IdealTransferDetailsState(),
    donateLabel = "Donate $5/month",
    onNavigationClick = {},
    onLearnMoreClick = {},
    onSelectBankClick = {},
    onNameChanged = {},
    onEmailChanged = {},
    onDonateClick = {}
  )
}

@Composable
private fun IdealTransferDetailsContent(
  state: IdealTransferDetailsState,
  donateLabel: String,
  onNavigationClick: () -> Unit,
  onLearnMoreClick: () -> Unit,
  onSelectBankClick: () -> Unit,
  onNameChanged: (String) -> Unit,
  onEmailChanged: (String) -> Unit,
  onDonateClick: () -> Unit
) {
  Scaffolds.Settings(
    title = stringResource(id = R.string.GatewaySelectorBottomSheet__ideal),
    onNavigationClick = onNavigationClick,
    navigationIconPainter = painterResource(id = R.drawable.symbol_arrow_left_24)
  ) {
    val focusManager = LocalFocusManager.current

    Column(
      horizontalAlignment = CenterHorizontally,
      modifier = Modifier.padding(it)
    ) {
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 24.dp)
      ) {
        item {
          val learnMore = stringResource(id = R.string.IdealTransferDetailsFragment__learn_more)
          val fullString = stringResource(id = R.string.IdealTransferDetailsFragment__enter_your_bank, learnMore)

          Texts.LinkifiedText(
            textWithUrlSpans = SpanUtil.urlSubsequence(fullString, learnMore, stringResource(id = R.string.donate_faq_url)),
            onUrlClick = {
              onLearnMoreClick()
            },
            style = MaterialTheme.typography.bodyMedium.copy(
              color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(vertical = 12.dp)
          )
        }

        item {
          IdealBankSelector(
            idealBank = state.idealBank,
            onSelectBankClick = onSelectBankClick,
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp)
          )
        }

        item {
          TextField(
            value = state.name,
            onValueChange = onNameChanged,
            label = {
              Text(text = stringResource(id = R.string.IdealTransferDetailsFragment__name_on_bank_account))
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
              Text(text = stringResource(id = R.string.IdealTransferDetailsFragment__email))
            },
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Email,
              imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
              onDone = {
                if (state.canProceed()) {
                  onDonateClick()
                }
              }
            ),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp)
          )
        }
      }

      Buttons.LargeTonal(
        enabled = state.canProceed(),
        onClick = onDonateClick,
        modifier = Modifier
          .defaultMinSize(minWidth = 220.dp)
          .padding(bottom = 16.dp)
      ) {
        Text(text = donateLabel)
      }
    }
  }
}

@Preview
@Composable
private fun IdealBankSelectorPreview() {
  IdealBankSelector(
    idealBank = null,
    onSelectBankClick = {}
  )
}

@Composable
private fun IdealBankSelector(
  idealBank: IdealBank?,
  onSelectBankClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val uiValues: IdealBank.UIValues? = remember(idealBank) { idealBank?.getUIValues() }
  val imagePadding: Dp = if (idealBank == null) 4.dp else 0.dp

  TextField(
    value = stringResource(id = uiValues?.name ?: R.string.IdealTransferDetailsFragment__choose_your_bank),
    textStyle = MaterialTheme.typography.bodyLarge,
    onValueChange = {},
    enabled = false,
    readOnly = true,
    leadingIcon = {
      Image(
        painter = painterResource(id = uiValues?.icon ?: R.drawable.bank_transfer),
        contentDescription = null,
        modifier = Modifier
          .padding(start = 16.dp, end = 12.dp)
          .size(32.dp)
          .padding(imagePadding)
      )
    },
    trailingIcon = {
      Icon(
        painter = painterResource(id = R.drawable.symbol_dropdown_triangle_compat_bold_16),
        contentDescription = null
      )
    },
    colors = TextFieldDefaults.colors(
      disabledTextColor = MaterialTheme.colorScheme.onSurface,
      disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface,
      disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      disabledIndicatorColor = MaterialTheme.colorScheme.onSurface
    ),
    modifier = modifier
      .clickable(
        onClick = onSelectBankClick,
        role = Role.Button
      )
  )
}
