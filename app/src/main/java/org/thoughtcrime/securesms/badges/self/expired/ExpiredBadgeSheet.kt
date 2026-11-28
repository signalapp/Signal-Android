/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.expired

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.signal.core.models.badges.Badge
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.LocalBottomSheetHostState
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository

/**
 * A sheet telling the user that [badge] has expired, for use inside a composable screen.
 * [ExpiredOneTimeBadgeBottomSheetDialogFragment] shows the same thing from a fragment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiredBadgeBottomSheet(
  badge: Badge,
  onDismissRequest: () -> Unit
) {
  BottomSheets.BottomSheet(
    onDismissRequest = onDismissRequest,
    containerColor = SignalTheme.colors.colorSurface1
  ) {
    ExpiredBadgeSheetEntry(badge = badge)
  }
}

/**
 * Hosts [ExpiredBadgeSheetContent] for [badge] and carries out its events, closing the sheet it's in once the user has
 * chosen.
 */
@Composable
fun ExpiredBadgeSheetEntry(badge: Badge) {
  val context = LocalContext.current
  val sheet = LocalBottomSheetHostState.current
  val isLikelyASustainer = remember { InAppPaymentsRepository.isLikelyASustainer() }

  ExpiredBadgeSheetContent(
    state = ExpiredBadgeSheetState(badge = badge, isLikelyASustainer = isLikelyASustainer),
    onEvent = { event ->
      sheet.dismiss()
      when (event) {
        ExpiredBadgeSheetEvent.DonateClicked -> {
          if (isLikelyASustainer) {
            context.startActivity(AppSettingsActivity.boost(context))
          } else {
            context.startActivity(AppSettingsActivity.subscriptions(context))
          }
        }

        ExpiredBadgeSheetEvent.NotNowClicked -> Unit
      }
    }
  )
}
