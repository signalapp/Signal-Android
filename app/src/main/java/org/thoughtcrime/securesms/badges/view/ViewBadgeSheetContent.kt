/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.view

import android.content.res.Resources
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.signal.core.models.badges.Badge
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.PagerIndicators
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImageXLarge

@VisibleForTesting
object ViewBadgeSheetTestTags {
  const val PAGER = "view-badge-pager"
  const val BUTTON_ACTION = "view-badge-action"
  const val NO_IN_APP_DONATIONS = "view-badge-no-in-app-donations"

  fun badgeName(index: Int): String = "view-badge-name:$index"
}

/**
 * Someone's badges, one per page with a dot for each when there's more than one, and a button inviting the viewer to
 * donate for one of their own.
 */
@Composable
fun ViewBadgeSheetContent(
  state: ViewBadgeSheetState,
  onEvent: (ViewBadgeSheetEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val pagerState = rememberPagerState(initialPage = state.badges.indexOf(state.startBadge).coerceAtLeast(0)) { state.badges.size }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val descriptionLines = rememberLongestDescriptionLines(state, descriptionWidth = constraints.maxWidth - with(LocalDensity.current) { (DESCRIPTION_PADDING * 2).roundToPx() })

      HorizontalPager(
        state = pagerState,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp)
          .testTag(ViewBadgeSheetTestTags.PAGER)
      ) { page ->
        BadgePage(
          badge = state.badges[page],
          shortName = state.shortName,
          descriptionLines = descriptionLines,
          nameTestTag = ViewBadgeSheetTestTags.badgeName(page)
        )
      }
    }

    if (!state.canDonateInApp) {
      Text(
        text = stringResource(R.string.ViewBadgeBottomSheetDialogFragment__your_device_doesn_t_support),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .horizontalGutters()
          .padding(top = 12.dp)
          .testTag(ViewBadgeSheetTestTags.NO_IN_APP_DONATIONS)
      )
    }

    if (state.badges.size > 1) {
      PagerIndicators.Dots(
        pagerState = pagerState,
        modifier = Modifier.padding(top = 8.dp)
      )
    }

    if (state.showAction) {
      Buttons.LargeTonal(
        onClick = { onEvent(ViewBadgeSheetEvent.ActionClicked) },
        modifier = Modifier
          .padding(start = 48.dp, top = 16.dp, end = 48.dp, bottom = 32.dp)
          .widthIn(min = 220.dp)
          .testTag(ViewBadgeSheetTestTags.BUTTON_ACTION)
      ) {
        if (state.canDonateInApp) {
          Text(text = stringResource(R.string.ViewBadgeBottomSheetDialogFragment__donate_now))
        } else {
          Text(text = stringResource(R.string.preferences__donate_to_signal))
          Icon(
            imageVector = SignalIcons.Open.imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
              .padding(start = 8.dp)
              .size(20.dp)
          )
        }
      }
    }

    if (state.badges.size > 1) {
      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

private val DESCRIPTION_PADDING = 32.dp

@Composable
private fun BadgePage(
  badge: Badge,
  shortName: String,
  descriptionLines: Int,
  nameTestTag: String
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
  ) {
    BadgeImageXLarge(
      badge = badge,
      modifier = Modifier
        .padding(top = 36.dp)
        .size(112.dp)
        .semantics { contentDescription = badge.name }
    )

    Text(
      text = stringResource(R.string.ViewBadgeBottomSheetDialogFragment__s_supports_signal, shortName),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .horizontalGutters()
        .padding(top = 16.dp)
        .testTag(nameTestTag)
    )

    Text(
      text = description(LocalResources.current, badge, shortName),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      minLines = descriptionLines,
      modifier = Modifier.padding(start = DESCRIPTION_PADDING, top = 8.dp, end = DESCRIPTION_PADDING)
    )
  }
}

/**
 * The most lines any badge's description takes at [descriptionWidth], so every page is as tall as the tallest and the
 * sheet doesn't change height as the user swipes.
 */
@Composable
private fun rememberLongestDescriptionLines(state: ViewBadgeSheetState, descriptionWidth: Int): Int {
  val resources = LocalResources.current
  val textMeasurer = rememberTextMeasurer()
  val style = MaterialTheme.typography.bodyLarge

  return remember(state.badges, state.shortName, descriptionWidth, style, resources) {
    state.badges.maxOfOrNull { badge ->
      textMeasurer.measure(
        text = description(resources, badge, state.shortName),
        style = style,
        constraints = Constraints(maxWidth = descriptionWidth.coerceAtLeast(0))
      ).lineCount
    } ?: 1
  }
}

private fun description(resources: Resources, badge: Badge, shortName: String): String {
  return if (badge.isSubscription()) {
    resources.getString(R.string.ViewBadgeBottomSheetDialogFragment__s_supports_signal_with_a_monthly, shortName)
  } else {
    resources.getString(R.string.ViewBadgeBottomSheetDialogFragment__s_supports_signal_with_a_donation, shortName)
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

private val previewState = ViewBadgeSheetState(
  badges = listOf(previewBadge("star", "Signal Star"), previewBadge(Badge.BOOST_BADGE_ID, "Signal Boost"), previewBadge("planet", "Signal Planet")),
  shortName = "Alex"
)

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun ViewBadgeSheetContentPreview() {
  ViewBadgeSheetContent(state = previewState, onEvent = {})
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun ViewBadgeSheetContentSingleBadgePreview() {
  ViewBadgeSheetContent(state = previewState.copy(badges = previewState.badges.take(1)), onEvent = {})
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun ViewBadgeSheetContentNoInAppDonationsPreview() {
  ViewBadgeSheetContent(state = previewState.copy(canDonateInApp = false), onEvent = {})
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun ViewBadgeSheetContentOwnBadgesPreview() {
  ViewBadgeSheetContent(state = previewState.copy(showAction = false), onEvent = {})
}
