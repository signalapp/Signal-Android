/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.mandate

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.launch
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dividers
import org.signal.core.ui.Texts
import org.signal.core.ui.theme.SignalTheme
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.StatusBarColorAnimator
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
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

  private lateinit var statusBarColorAnimator: StatusBarColorAnimator

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    statusBarColorAnimator = StatusBarColorAnimator(requireActivity())
  }

  override fun onResume() {
    super.onResume()
    statusBarColorAnimator.setColorImmediate()
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
      onCanScrollUp = statusBarColorAnimator::setCanScrollUp
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
    if (args.inAppPayment.data.paymentMethodType == InAppPaymentData.PaymentMethodType.SEPA_DEBIT) {
      findNavController().safeNavigate(
        BankTransferMandateFragmentDirections.actionBankTransferMandateFragmentToBankTransferDetailsFragment(args.inAppPayment)
      )
    } else {
      findNavController().safeNavigate(
        BankTransferMandateFragmentDirections.actionBankTransferMandateFragmentToIdealTransferDetailsFragment(args.inAppPayment)
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
      onLearnMoreClick = {},
      onCanScrollUp = {}
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun BankTransferScreen(
  bankMandate: String,
  failedToLoadMandate: Boolean,
  onNavigationClick: () -> Unit,
  onContinueClick: () -> Unit,
  onLearnMoreClick: () -> Unit,
  onCanScrollUp: (Boolean) -> Unit
) {
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          AnimatedVisibility(
            visible = listState.canScrollBackward,
            enter = fadeIn(),
            exit = fadeOut()
          ) {
            Text(text = stringResource(id = R.string.BankTransferMandateFragment__bank_transfer), style = MaterialTheme.typography.titleLarge)
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigationClick,
            Modifier.padding(end = 16.dp)
          ) {
            Icon(
              painter = rememberVectorPainter(ImageVector.vectorResource(id = R.drawable.symbol_arrow_left_24)),
              contentDescription = null
            )
          }
        },
        colors = if (listState.canScrollBackward) TopAppBarDefaults.topAppBarColors(containerColor = SignalTheme.colors.colorSurface2) else TopAppBarDefaults.topAppBarColors()
      )
    }
  ) {
    onCanScrollUp(listState.canScrollBackward)

    Column(horizontalAlignment = CenterHorizontally, modifier = Modifier.fillMaxSize()) {
      LazyColumn(
        state = listState,
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, true)
          .padding(top = 64.dp)
      ) {
        item {
          Image(
            painter = painterResource(id = R.drawable.bank_transfer),
            contentScale = ContentScale.FillBounds,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
              .size(72.dp)
              .background(
                SignalTheme.colors.colorSurface2,
                CircleShape
              )
              .padding(18.dp)
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
            textWithUrlSpans = SpanUtil.urlSubsequence(fullString, learnMore, ""),
            onUrlClick = {
              onLearnMoreClick()
            },
            style = MaterialTheme.typography.bodyLarge.copy(
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
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
      }

      if (!failedToLoadMandate) {
        Surface(
          shadowElevation = if (listState.canScrollForward) 8.dp else 0.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Buttons.LargeTonal(
            onClick = {
              if (!listState.canScrollForward) {
                onContinueClick()
              } else {
                scope.launch {
                  listState.animateScrollBy(value = 1000f)
                }
              }
            },
            modifier = Modifier
              .wrapContentWidth()
              .padding(top = 16.dp, bottom = 16.dp)
              .defaultMinSize(minWidth = 220.dp)
          ) {
            Text(text = if (listState.canScrollForward) stringResource(id = R.string.BankTransferMandateFragment__read_more) else stringResource(id = R.string.BankTransferMandateFragment__agree))
          }
        }
      }
    }
  }
}
