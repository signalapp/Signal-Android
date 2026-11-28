/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.badges

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.core.models.badges.Badge
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.theme.SignalTheme

@VisibleForTesting
object SelectFeaturedBadgeTestTags {
  const val LIST = "list"
  const val BUTTON_SAVE = "button-save"
}

/**
 * Lets the user pick which of their badges shows on their profile, previewing it on their avatar before they save.
 *
 * @param avatarPreview the user's avatar wearing the given badge, drawn in an 80dp box.
 * @param badgeImage draws a badge at the size of the modifier it's handed.
 */
@Composable
fun SelectFeaturedBadgeScreen(
  state: SelectFeaturedBadgeState,
  onEvent: (SelectFeaturedBadgeEvent) -> Unit,
  modifier: Modifier = Modifier,
  avatarPreview: @Composable (Badge?) -> Unit = {},
  badgeImage: @Composable (Badge, Modifier) -> Unit = { _, imageModifier -> Box(modifier = imageModifier) }
) {
  Scaffolds.Settings(
    title = stringResource(R.string.SelectFeaturedBadgeScreen__featured_badge),
    onNavigationClick = { onEvent(SelectFeaturedBadgeEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    snackbarHost = {
      Snackbars.Host(
        message = state.snackbar.message(),
        onDismissed = { onEvent(SelectFeaturedBadgeEvent.SnackbarDismissed) }
      )
    },
    modifier = modifier
  ) { contentPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
        ) {
          Box(modifier = Modifier.size(80.dp)) {
            avatarPreview(state.selectedBadge)
          }
        }

        AppSettingsLazyColumn(
          contentPadding = PaddingValues(bottom = 88.dp),
          modifier = Modifier
            .weight(1f)
            .testTag(SelectFeaturedBadgeTestTags.LIST)
        ) {
          item(key = "header") {
            Texts.SectionHeader(text = stringResource(R.string.SelectFeaturedBadgeFragment__select_a_badge))
          }

          item(key = "badges") {
            BadgeGrid(
              badges = state.badges,
              onBadgeClick = { onEvent(SelectFeaturedBadgeEvent.BadgeClicked(it)) },
              badgeImage = badgeImage,
              selectedBadge = state.selectedBadge
            )
          }
        }
      }

      Buttons.LargePrimary(
        onClick = { onEvent(SelectFeaturedBadgeEvent.SaveClicked) },
        enabled = state.canSave,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
          .testTag(SelectFeaturedBadgeTestTags.BUTTON_SAVE)
      ) {
        Text(text = stringResource(R.string.SelectFeaturedBadgeScreen__save))
      }
    }
  }
}

@Composable
private fun SelectFeaturedBadgeState.Snackbar.message(): String? {
  return when (this) {
    SelectFeaturedBadgeState.Snackbar.None -> null
    SelectFeaturedBadgeState.Snackbar.FailedToUpdateProfile -> stringResource(R.string.SelectFeaturedBadgeFragment__failed_to_update_profile)
  }
}

private fun previewBadge(id: String, name: String): Badge {
  return Badge(
    id = id,
    category = Badge.Category.Donor,
    name = name,
    description = "",
    imageUrl = Uri.EMPTY,
    imageDensity = "xxxhdpi",
    expirationTimestamp = Long.MAX_VALUE,
    visible = true,
    duration = 0
  )
}

private val previewBadges = listOf(
  previewBadge("star", "Signal Star"),
  previewBadge("planet", "Signal Planet"),
  previewBadge("heart", "Signal Heart")
)

@Composable
private fun PreviewCircle(modifier: Modifier = Modifier) {
  Box(modifier = modifier.background(SignalTheme.colors.colorSurface5, CircleShape))
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun SelectFeaturedBadgeScreenPreview() {
  SelectFeaturedBadgeScreen(
    state = SelectFeaturedBadgeState(stage = SelectFeaturedBadgeState.Stage.READY, selectedBadge = previewBadges[1], badges = previewBadges),
    onEvent = {},
    avatarPreview = { PreviewCircle(Modifier.fillMaxSize()) },
    badgeImage = { _, modifier -> PreviewCircle(modifier) }
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun SelectFeaturedBadgeScreenSavingPreview() {
  SelectFeaturedBadgeScreen(
    state = SelectFeaturedBadgeState(stage = SelectFeaturedBadgeState.Stage.SAVING, selectedBadge = previewBadges[1], badges = previewBadges),
    onEvent = {},
    avatarPreview = { PreviewCircle(Modifier.fillMaxSize()) },
    badgeImage = { _, modifier -> PreviewCircle(modifier) }
  )
}
