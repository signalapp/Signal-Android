/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.completed

import android.content.DialogInterface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImage112
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.database.model.databaseprotos.TerminalDonationQueue
import org.thoughtcrime.securesms.util.viewModel

/**
 * Bottom Sheet displayed when the app notices that a long-running donation has
 * completed.
 */
class TerminalDonationBottomSheet : ComposeBottomSheetDialogFragment() {

  companion object {

    private const val ARG_DONATION_COMPLETED = "arg.donation.completed"

    @JvmStatic
    fun show(fragmentManager: FragmentManager, terminalDonation: TerminalDonationQueue.TerminalDonation) {
      TerminalDonationBottomSheet().apply {
        arguments = bundleOf(
          ARG_DONATION_COMPLETED to terminalDonation.encode()
        )

        show(fragmentManager, null)
      }
    }
  }

  override val peekHeightPercentage: Float = 1f

  private val terminalDonation: TerminalDonationQueue.TerminalDonation by lazy(LazyThreadSafetyMode.NONE) {
    TerminalDonationQueue.TerminalDonation.ADAPTER.decode(requireArguments().getByteArray(ARG_DONATION_COMPLETED)!!)
  }

  private val viewModel: TerminalDonationViewModel by viewModel {
    TerminalDonationViewModel(terminalDonation, badgeRepository = BadgeRepository(requireContext()))
  }

  @Composable
  override fun SheetContent() {
    if (terminalDonation.error != null) {
      PaymentFailureBottomSheet()
    } else {
      CompletedSheet()
    }
  }

  @Composable
  private fun PaymentFailureBottomSheet() {
    val badge by viewModel.badge

    DonationPaymentFailureBottomSheet(
      badge = badge,
      onTryAgainClick = {
        startActivity(AppSettingsActivity.manageSubscriptions(requireContext()))
      },
      onNotNowClick = {
        dismissAllowingStateLoss()
      }
    )
  }

  @Composable
  private fun CompletedSheet() {
    val badge by viewModel.badge
    val isToggleChecked by viewModel.isToggleChecked
    val toggleType by viewModel.toggleType

    DonationCompletedSheetContent(
      badge = badge,
      isToggleChecked = isToggleChecked,
      toggleType = toggleType,
      onCheckChanged = viewModel::onToggleCheckChanged,
      onDoneClick = { dismissAllowingStateLoss() }
    )
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)

    viewModel.commitToggleState()
  }
}

@Preview
@Composable
private fun DonationPaymentFailureBottomSheet() {
  SignalTheme {
    Surface {
      DonationPaymentFailureBottomSheet(
        badge = null,
        onTryAgainClick = {},
        onNotNowClick = {}
      )
    }
  }
}

@Composable
private fun DonationPaymentFailureBottomSheet(
  badge: Badge?,
  onTryAgainClick: () -> Unit,
  onNotNowClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    BottomSheets.Handle()

    Box(
      modifier = Modifier
        .padding(top = 21.dp, bottom = 16.dp)
    ) {
      BadgeImage112(
        badge = badge,
        modifier = Modifier
          .size(80.dp)
      )

      Box(
        modifier = Modifier
          .size(24.dp)
          .padding(2.dp)
          .background(
            color = MaterialTheme.colorScheme.background,
            shape = CircleShape
          )
          .align(Alignment.TopEnd)
      )

      Icon(
        painter = painterResource(id = R.drawable.symbol_error_circle_fill_24),
        tint = MaterialTheme.colorScheme.error,
        contentDescription = null,
        modifier = Modifier.align(Alignment.TopEnd)
      )
    }

    Text(
      text = stringResource(id = R.string.DonationErrorBottomSheet__donation_couldnt_be_processed),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 45.dp)
    )

    Text(
      text = stringResource(id = R.string.DonationErrorBottomSheet__were_having_trouble),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp, bottom = 24.dp)
        .padding(horizontal = 45.dp)
    )

    Buttons.LargeTonal(
      onClick = onTryAgainClick,
      modifier = Modifier
        .defaultMinSize(minWidth = 220.dp)
        .padding(top = 32.dp, bottom = 16.dp)
    ) {
      Text(
        text = stringResource(id = R.string.DonationErrorBottomSheet__try_again)
      )
    }

    TextButton(
      onClick = onNotNowClick,
      modifier = Modifier
        .defaultMinSize(minWidth = 220.dp)
        .padding(bottom = 56.dp)
    ) {
      Text(
        text = stringResource(id = R.string.DonationErrorBottomSheet__not_now)
      )
    }
  }
}

@Preview
@Composable
private fun DonationCompletedSheetContentPreview() {
  SignalTheme {
    Surface {
      DonationCompletedSheetContent(
        badge = null,
        isToggleChecked = false,
        toggleType = TerminalDonationViewModel.ToggleType.NONE,
        onCheckChanged = {},
        onDoneClick = {}
      )
    }
  }
}

@Composable
private fun DonationCompletedSheetContent(
  badge: Badge?,
  isToggleChecked: Boolean,
  toggleType: TerminalDonationViewModel.ToggleType,
  onCheckChanged: (Boolean) -> Unit,
  onDoneClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    BottomSheets.Handle()

    BadgeImage112(
      badge = badge,
      modifier = Modifier
        .padding(top = 21.dp, bottom = 16.dp)
        .size(80.dp)
    )

    Text(
      text = stringResource(id = R.string.DonationCompletedBottomSheet__donation_complete),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 45.dp)
    )

    Text(
      text = stringResource(id = R.string.DonationCompleteBottomSheet__your_bank_transfer_was_received),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp, bottom = 24.dp)
        .padding(horizontal = 45.dp)
    )

    if (toggleType == TerminalDonationViewModel.ToggleType.NONE) {
      CircularProgressIndicator()
    } else {
      DonationToggleRow(
        checked = isToggleChecked,
        text = stringResource(id = toggleType.copyId),
        onCheckChanged = onCheckChanged
      )
    }

    Buttons.LargeTonal(
      onClick = onDoneClick,
      modifier = Modifier
        .defaultMinSize(minWidth = 220.dp)
        .padding(top = 48.dp, bottom = 56.dp)
    ) {
      Text(
        text = stringResource(id = R.string.DonationPendingBottomSheet__done)
      )
    }
  }
}

@Composable
private fun DonationToggleRow(
  checked: Boolean,
  text: String,
  onCheckChanged: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp)
      .border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(12.dp)
      )
      .padding(horizontal = 16.dp)
  ) {
    Text(
      text = text,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .weight(1f)
        .align(Alignment.CenterVertically)
        .padding(vertical = 16.dp)
    )

    Switch(
      checked = checked,
      onCheckedChange = onCheckChanged,
      modifier = Modifier.align(Alignment.CenterVertically)
    )
  }
}
