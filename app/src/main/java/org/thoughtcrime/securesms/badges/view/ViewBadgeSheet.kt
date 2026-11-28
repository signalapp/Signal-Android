/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.view

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.signal.core.models.badges.Badge
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.LocalBottomSheetHostState
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.rememberRecipientField
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * A sheet showing [recipientId]'s badges, opened on [startBadge], for use inside a composable screen.
 * [ViewBadgeBottomSheetDialogFragment] shows the same thing from a fragment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewBadgeBottomSheet(
  recipientId: RecipientId,
  startBadge: Badge?,
  onDismissRequest: () -> Unit
) {
  BottomSheets.BottomSheet(onDismissRequest = onDismissRequest) {
    ViewBadgeSheetEntry(recipientId = recipientId, startBadge = startBadge)
  }
}

/**
 * Hosts [ViewBadgeSheetContent] for [recipientId], following changes to their badges, and carries out its events. Closes
 * the sheet it's in if the recipient turns out to have no badges to show.
 */
@Composable
fun ViewBadgeSheetEntry(
  recipientId: RecipientId,
  startBadge: Badge?,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val recipient by rememberRecipientField(recipientId) { this }
  val sheet = LocalBottomSheetHostState.current
  val canDonateInApp = remember { InAppDonations.hasAtLeastOnePaymentMethodAvailable() }
  val donateUrl = stringResource(R.string.donate_url)

  if (recipient.id != recipientId) {
    return
  }

  if (recipient.badges.isEmpty()) {
    LaunchedEffect(Unit) { sheet.dismiss() }
    return
  }

  ViewBadgeSheetContent(
    state = ViewBadgeSheetState(
      badges = recipient.badges,
      startBadge = startBadge,
      shortName = recipient.getShortDisplayName(context),
      showAction = recipientId != Recipient.self().id,
      canDonateInApp = canDonateInApp
    ),
    onEvent = { event ->
      when (event) {
        ViewBadgeSheetEvent.ActionClicked -> {
          if (canDonateInApp) {
            context.startActivity(AppSettingsActivity.subscriptions(context))
          } else {
            CommunicationActions.openBrowserLink(context, donateUrl)
          }
        }
      }
    },
    modifier = modifier
  )
}
