/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.mandate

import android.os.Bundle
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dividers
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.Texts
import org.signal.core.ui.theme.SignalTheme
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayResponse
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.StatusBarColorNestedScrollConnection
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewModel

/**
 * Displays Bank Transfer legal mandate users must agree to to move forward.
 */
class BankTransferMandateFragment : ComposeFragment() {

  private val args: BankTransferMandateFragmentArgs by navArgs()
  private val viewModel: BankTransferMandateViewModel by viewModel {
    BankTransferMandateViewModel(PaymentSourceType.Stripe.SEPADebit)
  }

  private lateinit var statusBarColorNestedScrollConnection: StatusBarColorNestedScrollConnection

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    statusBarColorNestedScrollConnection = StatusBarColorNestedScrollConnection(requireActivity())
  }

  override fun onResume() {
    super.onResume()
    statusBarColorNestedScrollConnection.setColorImmediate()
  }

  @Composable
  override fun FragmentContent() {
    val mandate by viewModel.mandate
    val failedToLoadMandate by viewModel.failedToLoadMandate

    BankTransferScreen(
      bankMandate = mandate,
      failedToLoadMandate = failedToLoadMandate,
      onNavigationClick = this::onNavigationClick,
      onContinueClick = this::onContinueClick,
      onLearnMoreClick = this::onLearnMoreClick,
      modifier = Modifier.nestedScroll(statusBarColorNestedScrollConnection)
    )
  }

  private fun onLearnMoreClick() {
    findNavController().safeNavigate(
      BankTransferMandateFragmentDirections.actionBankTransferMandateFragmentToYourInformationIsPrivateBottomSheet()
    )
  }

  private fun onNavigationClick() {
    findNavController().popBackStack()
  }

  private fun onContinueClick() {
    if (args.response.gateway == GatewayResponse.Gateway.SEPA_DEBIT) {
      findNavController().safeNavigate(
        BankTransferMandateFragmentDirections.actionBankTransferMandateFragmentToBankTransferDetailsFragment(args.response.request)
      )
    } else {
      findNavController().safeNavigate(
        BankTransferMandateFragmentDirections.actionBankTransferMandateFragmentToIdealTransferDetailsFragment(args.response.request)
      )
    }
  }
}

@Preview
@Composable
fun BankTransferScreenPreview() {
  SignalTheme {
    BankTransferScreen(
      bankMandate = "Test ".repeat(500),
      failedToLoadMandate = false,
      onNavigationClick = {},
      onContinueClick = {},
      onLearnMoreClick = {}
    )
  }
}

@Composable
fun BankTransferScreen(
  bankMandate: String,
  failedToLoadMandate: Boolean,
  onNavigationClick: () -> Unit,
  onContinueClick: () -> Unit,
  onLearnMoreClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(id = R.string.BankTransferMandateFragment__bank_transfer),
    onNavigationClick = onNavigationClick,
    navigationIconPainter = rememberVectorPainter(ImageVector.vectorResource(id = R.drawable.symbol_arrow_left_24)),
    titleContent = { contentOffset, title ->
      AnimatedVisibility(
        visible = contentOffset < 0f,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
      }
    },
    modifier = modifier
  ) {
    LazyColumn(
      horizontalAlignment = CenterHorizontally,
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .padding(top = 64.dp)
    ) {
      item {
        Image(
          painter = painterResource(id = R.drawable.bank_transfer),
          contentScale = ContentScale.Inside,
          contentDescription = null,
          modifier = Modifier
            .size(72.dp)
            .background(
              SignalTheme.colors.colorSurface2,
              CircleShape
            )
        )
      }

      item {
        Text(
          text = stringResource(id = R.string.BankTransferMandateFragment__bank_transfer),
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.padding(top = 12.dp, bottom = 15.dp)
        )
      }

      item {
        val learnMore = stringResource(id = R.string.BankTransferMandateFragment__learn_more)
        val fullString = stringResource(id = R.string.BankTransferMandateFragment__stripe_processes_donations, learnMore)

        Texts.LinkifiedText(
          textWithUrlSpans = SpanUtil.urlSubsequence(fullString, learnMore, stringResource(id = R.string.donate_url)), // TODO [alex] -- final URL
          onUrlClick = {
            onLearnMoreClick()
          },
          style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
          ),
          modifier = Modifier
            .padding(bottom = 12.dp)
            .padding(horizontal = dimensionResource(id = R.dimen.bank_transfer_mandate_gutter))
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Text(
          text = if (failedToLoadMandate) stringResource(id = R.string.BankTransferMandateFragment__failed_to_load_mandate) else bankMandate,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.bank_transfer_mandate_gutter), vertical = 16.dp)
        )
      }

      if (!failedToLoadMandate) {
        item {
          Buttons.LargeTonal(
            onClick = onContinueClick,
            modifier = Modifier
              .padding(top = 16.dp, bottom = 46.dp)
              .defaultMinSize(minWidth = 220.dp)
          ) {
            Text(text = stringResource(id = R.string.BankTransferMandateFragment__continue))
          }
        }
      }
    }
  }
}
