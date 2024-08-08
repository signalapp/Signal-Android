/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.ideal

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.ui.Buttons
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.Texts
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.TemporaryScreenshotSecurity
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentCheckoutDelegate
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorAction
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorActionResult
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe.StripePaymentInProgressFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe.StripePaymentInProgressViewModel
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.BankTransferRequestKeys
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.ideal.IdealTransferDetailsViewModel.Field
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewModel

/**
 * Fragment for inputting necessary bank transfer information for iDEAL donation
 */
class IdealTransferDetailsFragment : ComposeFragment(), InAppPaymentCheckoutDelegate.ErrorHandlerCallback {

  private val args: IdealTransferDetailsFragmentArgs by navArgs()
  private val viewModel: IdealTransferDetailsViewModel by viewModel {
    IdealTransferDetailsViewModel(args.inAppPayment.type.recurring)
  }

  private val stripePaymentViewModel: StripePaymentInProgressViewModel by navGraphViewModels(
    R.id.checkout_flow,
    factoryProducer = {
      StripePaymentInProgressViewModel.Factory(requireListener<InAppPaymentComponent>().stripeRepository)
    }
  )

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    TemporaryScreenshotSecurity.bindToViewLifecycleOwner(this)

    InAppPaymentCheckoutDelegate.ErrorHandler().attach(this, this, args.inAppPayment.id)

    setFragmentResultListener(StripePaymentInProgressFragment.REQUEST_KEY) { _, bundle ->
      val result: InAppPaymentProcessorActionResult = bundle.getParcelableCompat(StripePaymentInProgressFragment.REQUEST_KEY, InAppPaymentProcessorActionResult::class.java)!!
      if (result.status == InAppPaymentProcessorActionResult.Status.SUCCESS) {
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

    val donateLabel = remember(args.inAppPayment) {
      if (args.inAppPayment.type.recurring) { // TODO [message-request] -- Handle backups
        getString(
          R.string.BankTransferDetailsFragment__donate_s_month,
          FiatMoneyUtil.format(resources, args.inAppPayment.data.amount!!.toFiatMoney(), FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
        )
      } else {
        getString(
          R.string.BankTransferDetailsFragment__donate_s,
          FiatMoneyUtil.format(resources, args.inAppPayment.data.amount!!.toFiatMoney())
        )
      }
    }

    val idealDirections = remember(args.inAppPayment) {
      if (args.inAppPayment.type.recurring) { // TODO [message-request] -- Handle backups
        R.string.IdealTransferDetailsFragment__enter_your_bank
      } else {
        R.string.IdealTransferDetailsFragment__enter_your_bank_details_one_time
      }
    }

    IdealTransferDetailsContent(
      state = state,
      idealDirections = idealDirections,
      donateLabel = donateLabel,
      onNavigationClick = { findNavController().popBackStack() },
      onLearnMoreClick = { findNavController().navigate(IdealTransferDetailsFragmentDirections.actionBankTransferDetailsFragmentToYourInformationIsPrivateBottomSheet()) },
      onSelectBankClick = { findNavController().navigate(IdealTransferDetailsFragmentDirections.actionIdealTransferDetailsFragmentToIdealTransferBankSelectionDialogFragment()) },
      onNameChanged = viewModel::onNameChanged,
      onEmailChanged = viewModel::onEmailChanged,
      onFocusChanged = viewModel::onFocusChanged,
      onDonateClick = this::onDonateClick
    )
  }

  private fun onDonateClick() {
    val state = viewModel.state.value

    val continueTransfer = {
      stripePaymentViewModel.provideIDEALData(state.asIDEALData())
      findNavController().safeNavigate(
        IdealTransferDetailsFragmentDirections.actionBankTransferDetailsFragmentToStripePaymentInProgressFragment(
          InAppPaymentProcessorAction.PROCESS_NEW_IN_APP_PAYMENT,
          args.inAppPayment,
          args.inAppPayment.type
        )
      )
    }

    if (args.inAppPayment.type.recurring) { // TODO [message-requests] -- handle backup
      val formattedMoney = FiatMoneyUtil.format(requireContext().resources, args.inAppPayment.data.amount!!.toFiatMoney(), FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.IdealTransferDetailsFragment__confirm_your_donation_with_s, getString(state.idealBank!!.getUIValues().name)))
        .setMessage(getString(R.string.IdealTransferDetailsFragment__to_setup_your_recurring_donation, formattedMoney))
        .setPositiveButton(R.string.IdealTransferDetailsFragment__continue) { _, _ ->
          continueTransfer()
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
    } else {
      continueTransfer()
    }
  }

  override fun onUserLaunchedAnExternalApplication() {
    viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onResume(owner: LifecycleOwner) {
        findNavController().popBackStack(R.id.donateToSignalFragment, true)
      }
    })
  }

  override fun navigateToDonationPending(inAppPayment: InAppPaymentTable.InAppPayment) {
    setFragmentResult(BankTransferRequestKeys.PENDING_KEY, bundleOf(BankTransferRequestKeys.PENDING_KEY to inAppPayment))
    viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onResume(owner: LifecycleOwner) {
        findNavController().popBackStack(R.id.donateToSignalFragment, false)
      }
    })
  }

  override fun exitCheckoutFlow() {
    requireActivity().finishAfterTransition()
  }
}

@Preview
@Composable
private fun IdealTransferDetailsContentPreview() {
  IdealTransferDetailsContent(
    state = IdealTransferDetailsState(isMonthly = true),
    idealDirections = R.string.IdealTransferDetailsFragment__enter_your_bank,
    donateLabel = "Donate $5/month",
    onNavigationClick = {},
    onLearnMoreClick = {},
    onSelectBankClick = {},
    onNameChanged = {},
    onEmailChanged = {},
    onFocusChanged = { _, _ -> },
    onDonateClick = {}
  )
}

@Composable
private fun IdealTransferDetailsContent(
  state: IdealTransferDetailsState,
  @StringRes idealDirections: Int,
  donateLabel: String,
  onNavigationClick: () -> Unit,
  onLearnMoreClick: () -> Unit,
  onSelectBankClick: () -> Unit,
  onNameChanged: (String) -> Unit,
  onEmailChanged: (String) -> Unit,
  onFocusChanged: (Field, Boolean) -> Unit,
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
          val fullString = stringResource(id = idealDirections, learnMore)

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
            isError = state.showNameError(),
            supportingText = {
              if (state.showNameError()) {
                Text(text = stringResource(id = R.string.BankTransferDetailsFragment__minimum_2_characters))
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 16.dp)
              .defaultMinSize(minHeight = 78.dp)
              .onFocusChanged { onFocusChanged(Field.NAME, it.hasFocus) }
          )
        }

        if (state.isMonthly) {
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
              isError = state.showEmailError(),
              supportingText = {
                if (state.showEmailError()) {
                  Text(text = stringResource(id = R.string.BankTransferDetailsFragment__invalid_email_address))
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .defaultMinSize(minHeight = 78.dp)
                .onFocusChanged { onFocusChanged(Field.EMAIL, it.hasFocus) }
            )
          }
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
        colorFilter = if (uiValues?.icon == null) ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null,
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
    supportingText = {},
    modifier = modifier
      .defaultMinSize(minHeight = 78.dp)
      .clickable(
        onClick = onSelectBankClick,
        role = Role.Button
      )
  )
}
