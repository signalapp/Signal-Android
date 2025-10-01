/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription

import android.content.DialogInterface
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.viewModel

/**
 * Displayed after the user completes the donation flow for a bank transfer.
 */
class DonationPendingBottomSheet : ComposeBottomSheetDialogFragment() {

  private val args: DonationPendingBottomSheetArgs by navArgs()
  private val viewModel: DonationPendingBottomSheetViewModel by viewModel {
    DonationPendingBottomSheetViewModel(args.inAppPaymentId)
  }

  @Composable
  override fun SheetContent() {
    val inAppPayment by viewModel.inAppPayment.collectAsStateWithLifecycle()

    if (inAppPayment != null)
      DonationPendingBottomSheetContent(
        badge = Badges.fromDatabaseBadge(inAppPayment!!.data.badge!!),
        onDoneClick = this::onDoneClick
      )
  }

  private fun onDoneClick() {
    dismissAllowingStateLoss()
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)

    val iap = viewModel.inAppPayment.value
    if (iap != null && !iap.type.recurring) {
      findNavController().popBackStack()
    } else {
      requireActivity().finish()
      requireActivity().startActivity(AppSettingsActivity.manageSubscriptions(requireContext()))
    }
  }
}

@DayNightPreviews
@Composable
fun DonationPendingBottomSheetContentPreview() {
  SignalTheme {
    Surface {
      DonationPendingBottomSheetContent(
        badge = Badge(
          id = "",
          category = Badge.Category.Donor,
          name = "Signal Star",
          description = "",
          imageUrl = Uri.EMPTY,
          imageDensity = "",
          expirationTimestamp = 0L,
          visible = true,
          duration = 0L
        ),
        onDoneClick = {}
      )
    }
  }
}

@Composable
private fun DonationPendingBottomSheetContent(
  badge: Badge,
  onDoneClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.padding(horizontal = 44.dp)
  ) {
    BottomSheets.Handle()

    BadgeImage112(
      badge = badge,
      modifier = Modifier
        .padding(top = 21.dp, bottom = 16.dp)
        .size(80.dp)
    )

    Text(
      text = stringResource(id = R.string.DonationPendingBottomSheet__donation_pending),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    val textResource = if (badge.isSubscription()) {
      R.string.DonationPendingBottomSheet__your_monthly_donation_is_pending
    } else {
      R.string.DonationPendingBottomSheet__your_one_time_donation_is_pending
    }

    Text(
      text = stringResource(id = textResource, badge.name),
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 20.dp)
    )

    val context = LocalContext.current
    val learnMore = stringResource(id = R.string.DonationPendingBottomSheet__learn_more)
    val fullString = stringResource(id = R.string.DonationPendingBottomSheet__bank_transfers_usually_take, learnMore)
    val spanned = SpanUtil.urlSubsequence(fullString, learnMore, stringResource(id = R.string.pending_transfer_url))
    Texts.LinkifiedText(
      textWithUrlSpans = spanned,
      onUrlClick = { CommunicationActions.openBrowserLink(context, it) },
      style = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant),
      modifier = Modifier.padding(bottom = 48.dp)
    )

    Buttons.LargeTonal(
      onClick = onDoneClick,
      modifier = Modifier
        .defaultMinSize(minWidth = 220.dp)
        .padding(bottom = 56.dp)
    ) {
      Text(text = stringResource(id = R.string.DonationPendingBottomSheet__done))
    }
  }
}
