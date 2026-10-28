/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.badges

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.core.models.badges.Badge
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.theme.SignalTheme

@VisibleForTesting
object BadgesOverviewTestTags {
  const val LIST = "list"
  const val TOGGLE_DISPLAY_BADGES = "toggle-display-badges"
  const val ROW_FEATURED_BADGE = "row-featured-badge"
}

/**
 * The badges the user has unlocked, with the settings for showing them on their profile.
 *
 * @param badgeImage draws a badge at the size of the modifier it's handed.
 */
@Composable
fun BadgesOverviewScreen(
  state: BadgesOverviewState,
  onEvent: (BadgesOverviewEvent) -> Unit,
  modifier: Modifier = Modifier,
  badgeImage: @Composable (Badge, Modifier) -> Unit = { _, imageModifier -> Box(modifier = imageModifier) }
) {
  Scaffolds.Settings(
    title = stringResource(R.string.BadgesOverviewScreen__badges),
    onNavigationClick = { onEvent(BadgesOverviewEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    snackbarHost = {
      Snackbars.Host(
        message = state.snackbar.message(),
        onDismissed = { onEvent(BadgesOverviewEvent.SnackbarDismissed) }
      )
    },
    modifier = modifier
  ) { contentPadding ->
    AppSettingsLazyColumn(
      scaffoldPadding = contentPadding,
      modifier = Modifier.testTag(BadgesOverviewTestTags.LIST)
    ) {
      item(key = "header") {
        Texts.SectionHeader(text = stringResource(R.string.BadgesOverviewFragment__my_badges))
      }

      item(key = "badges") {
        BadgeGrid(
          badges = state.badges,
          onBadgeClick = { onEvent(BadgesOverviewEvent.BadgeClicked(it)) },
          badgeImage = badgeImage,
          fadedBadgeId = state.fadedBadgeId
        )
      }

      item(key = "display-badges") {
        Rows.ToggleRow(
          checked = state.displayBadgesOnProfile,
          text = stringResource(R.string.BadgesOverviewFragment__display_badges_on_profile),
          onCheckChanged = { onEvent(BadgesOverviewEvent.DisplayBadgesOnProfileToggled(it)) },
          enabled = state.canChangeSettings,
          isLoading = state.stage == BadgesOverviewState.Stage.UPDATING_BADGE_DISPLAY_STATE,
          modifier = Modifier.testTag(BadgesOverviewTestTags.TOGGLE_DISPLAY_BADGES)
        )
      }

      item(key = "featured-badge") {
        Rows.TextRow(
          text = stringResource(R.string.BadgesOverviewScreen__featured_badge),
          label = state.featuredBadge?.name,
          onClick = { onEvent(BadgesOverviewEvent.FeaturedBadgeClicked) },
          enabled = state.canChangeSettings,
          modifier = Modifier.testTag(BadgesOverviewTestTags.ROW_FEATURED_BADGE)
        )
      }
    }
  }
}

@Composable
private fun BadgesOverviewState.Snackbar.message(): String? {
  return when (this) {
    BadgesOverviewState.Snackbar.None -> null
    BadgesOverviewState.Snackbar.FailedToUpdateProfile -> stringResource(R.string.BadgesOverviewFragment__failed_to_update_profile)
  }
}

private fun previewBadge(id: String, name: String, expirationTimestamp: Long = Long.MAX_VALUE): Badge {
  return Badge(
    id = id,
    category = Badge.Category.Donor,
    name = name,
    description = "",
    imageUrl = Uri.EMPTY,
    imageDensity = "xxxhdpi",
    expirationTimestamp = expirationTimestamp,
    visible = true,
    duration = 0
  )
}

private val previewBadges = listOf(
  previewBadge("star", "Signal Star"),
  previewBadge("planet", "Signal Planet"),
  previewBadge("heart", "Signal Heart"),
  previewBadge("boost", "Signal Boost", expirationTimestamp = 1)
)

private val previewState = BadgesOverviewState(
  stage = BadgesOverviewState.Stage.READY,
  badges = previewBadges,
  featuredBadge = previewBadges.first(),
  displayBadgesOnProfile = true,
  hasUnexpiredBadges = true,
  hasInternet = true
)

@Composable
private fun PreviewBadgeImage(modifier: Modifier) {
  Box(modifier = modifier.background(SignalTheme.colors.colorSurface5, CircleShape))
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun BadgesOverviewScreenPreview() {
  BadgesOverviewScreen(state = previewState, onEvent = {}, badgeImage = { _, modifier -> PreviewBadgeImage(modifier) })
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun BadgesOverviewScreenUpdatingPreview() {
  BadgesOverviewScreen(
    state = previewState.copy(stage = BadgesOverviewState.Stage.UPDATING_BADGE_DISPLAY_STATE),
    onEvent = {},
    badgeImage = { _, modifier -> PreviewBadgeImage(modifier) }
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun BadgesOverviewScreenOfflinePreview() {
  BadgesOverviewScreen(
    state = previewState.copy(hasInternet = false),
    onEvent = {},
    badgeImage = { _, modifier -> PreviewBadgeImage(modifier) }
  )
}
