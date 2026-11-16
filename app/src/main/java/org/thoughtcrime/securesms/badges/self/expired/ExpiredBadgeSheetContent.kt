/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.expired

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.core.models.badges.Badge
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImage112

@VisibleForTesting
object ExpiredBadgeSheetTestTags {
  const val BUTTON_DONATE = "expired-badge-donate"
  const val BUTTON_NOT_NOW = "expired-badge-not-now"
}

/**
 * Tells the user that one of their badges has expired, with the badge fading out above, and invites them to donate for
 * it again.
 */
@Composable
fun ExpiredBadgeSheetContent(
  state: ExpiredBadgeSheetState,
  onEvent: (ExpiredBadgeSheetEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
  ) {
    FadingBadge(
      badge = state.badge,
      modifier = Modifier.padding(top = 28.dp, bottom = 24.dp)
    )

    Text(
      text = stringResource(
        if (state.badge.isBoost()) {
          R.string.ExpiredBadgeBottomSheetDialogFragment__boost_badge_expired
        } else {
          R.string.ExpiredBadgeBottomSheetDialogFragment__monthly_donation_cancelled
        }
      ),
      style = MaterialTheme.typography.titleSmall,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .horizontalGutters()
        .padding(top = 16.dp, bottom = 16.dp)
    )

    Text(
      text = stringResource(R.string.ExpiredBadgeBottomSheetDialogFragment__your_boost_badge_has_expired_and),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.horizontalGutters()
    )

    Text(
      text = stringResource(
        if (state.isLikelyASustainer) {
          R.string.ExpiredBadgeBottomSheetDialogFragment__you_can_reactivate
        } else {
          R.string.ExpiredBadgeBottomSheetDialogFragment__you_can_keep
        }
      ),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .horizontalGutters()
        .padding(top = 16.dp)
    )

    Buttons.LargePrimary(
      onClick = { onEvent(ExpiredBadgeSheetEvent.DonateClicked) },
      modifier = Modifier
        .fillMaxWidth()
        .horizontalGutters()
        .padding(top = 92.dp)
        .testTag(ExpiredBadgeSheetTestTags.BUTTON_DONATE)
    ) {
      Text(
        text = stringResource(
          if (state.isLikelyASustainer) {
            R.string.ExpiredBadgeBottomSheetDialogFragment__add_a_boost
          } else {
            R.string.ExpiredBadgeBottomSheetDialogFragment__become_a_sustainer
          }
        )
      )
    }

    TextButton(
      onClick = { onEvent(ExpiredBadgeSheetEvent.NotNowClicked) },
      modifier = Modifier
        .fillMaxWidth()
        .horizontalGutters()
        .padding(top = 8.dp, bottom = 16.dp)
        .testTag(ExpiredBadgeSheetTestTags.BUTTON_NOT_NOW)
    ) {
      Text(text = stringResource(R.string.ExpiredBadgeBottomSheetDialogFragment__not_now))
    }
  }
}

/** The badge under a gradient into the sheet's colour, nudged apart so the badge seems to fade away to the right. */
@Composable
private fun FadingBadge(badge: Badge, modifier: Modifier = Modifier) {
  Box(modifier = modifier) {
    BadgeImage112(
      badge = badge,
      modifier = Modifier
        .offset(x = (-6).dp)
        .size(110.dp)
        .semantics { contentDescription = badge.name }
    )

    Box(
      modifier = Modifier
        .offset(x = 6.dp)
        .size(110.dp)
        .background(Brush.horizontalGradient(listOf(Color.Transparent, SignalTheme.colors.colorSurface1)))
    )
  }
}

private fun previewBadge(id: String): Badge {
  return Badge(
    id = id,
    category = Badge.Category.Donor,
    name = "Signal Boost",
    description = "",
    imageUrl = Uri.EMPTY,
    imageDensity = "xxxhdpi",
    expirationTimestamp = 1,
    visible = true,
    duration = 0
  )
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun ExpiredBadgeSheetContentBoostPreview() {
  ExpiredBadgeSheetContent(state = ExpiredBadgeSheetState(badge = previewBadge(Badge.BOOST_BADGE_ID)), onEvent = {})
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun ExpiredBadgeSheetContentSustainerPreview() {
  ExpiredBadgeSheetContent(state = ExpiredBadgeSheetState(badge = previewBadge(Badge.BOOST_BADGE_ID), isLikelyASustainer = true), onEvent = {})
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun ExpiredBadgeSheetContentMonthlyPreview() {
  ExpiredBadgeSheetContent(state = ExpiredBadgeSheetState(badge = previewBadge("star")), onEvent = {})
}
