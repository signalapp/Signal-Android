/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.mandate

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dividers
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.Texts
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Displays Bank Transfer legal mandate users must agree to to move forward.
 */
class BankTransferMandateFragment : ComposeFragment() {

  private val args: BankTransferMandateFragmentArgs by navArgs()
  private val viewModel: BankTransferMandateViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val mandate by viewModel.mandate

    BankTransferScreen(
      bankMandate = mandate,
      onNavigationClick = this::onNavigationClick,
      onContinueClick = this::onContinueClick
    )
  }

  private fun onNavigationClick() {
    findNavController().popBackStack()
  }

  private fun onContinueClick() {
    findNavController().safeNavigate(
      BankTransferMandateFragmentDirections.actionBankTransferMandateFragmentToBankTransferDetailsFragment(args.request)
    )
  }
}

@Preview
@Composable
fun BankTransferScreenPreview() {
  SignalTheme {
    BankTransferScreen(
      bankMandate = "Test ".repeat(500),
      onNavigationClick = {},
      onContinueClick = {}
    )
  }
}

@Composable
fun BankTransferScreen(
  bankMandate: String,
  onNavigationClick: () -> Unit,
  onContinueClick: () -> Unit
) {
  Scaffolds.Settings(
    title = "",
    onNavigationClick = onNavigationClick,
    navigationIconPainter = rememberVectorPainter(ImageVector.vectorResource(id = R.drawable.symbol_arrow_left_24))
  ) {
    Column(
      horizontalAlignment = CenterHorizontally,
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
    ) {
      LazyColumn(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(top = 64.dp)
      ) {
        item {
          Image(
            painter = painterResource(id = R.drawable.credit_card), // TODO [alex] -- final asset
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
          val context = LocalContext.current

          Texts.LinkifiedText(
            textWithUrlSpans = SpanUtil.urlSubsequence(fullString, learnMore, stringResource(id = R.string.donate_url)), // TODO [alex] -- final URL
            onUrlClick = {
              CommunicationActions.openBrowserLink(context, it)
            },
            style = MaterialTheme.typography.bodyLarge.copy(
              color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(bottom = 12.dp, start = 32.dp, end = 32.dp)
          )
        }

        item {
          Dividers.Default()
        }

        item {
          Text(
            text = bankMandate,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
          )
        }
      }

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
