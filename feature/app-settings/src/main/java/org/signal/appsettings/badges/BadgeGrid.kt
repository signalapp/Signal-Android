/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import org.signal.core.models.badges.Badge
import org.signal.core.ui.compose.SignalIcons

private val MIN_CELL_WIDTH = 88.dp
private val CELL_PADDING = 12.dp
private const val FADED_ALPHA = 0.5f

object BadgeGridTestTags {
  fun badge(badge: Badge): String = "badge-grid-badge:${badge.id}"
}

/**
 * The user's badges laid out in rows, each with its name beneath. Expired badges, and the one with [fadedBadgeId],
 * are drawn faded, and [selectedBadge] is marked with a check.
 *
 * @param badgeImage draws a badge at the size of the modifier it's handed.
 */
@Composable
fun BadgeGrid(
  badges: List<Badge>,
  onBadgeClick: (Badge) -> Unit,
  badgeImage: @Composable (Badge, Modifier) -> Unit,
  modifier: Modifier = Modifier,
  selectedBadge: Badge? = null,
  fadedBadgeId: String? = null
) {
  val nameStyle = MaterialTheme.typography.bodySmall
  val textMeasurer = rememberTextMeasurer()
  val density = LocalDensity.current

  // Sized to the longest word in any name, so names only wrap between words.
  val longestWord = remember(badges, nameStyle, density) {
    val widest = badges
      .flatMap { it.name.split(' ') }
      .maxOfOrNull { textMeasurer.measure(it, nameStyle).size.width } ?: 0
    with(density) { widest.toDp() }
  }

  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val cellWidth = max(MIN_CELL_WIDTH, longestWord + CELL_PADDING * 2).coerceAtMost(maxWidth)
    val perRow = (maxWidth / cellWidth).toInt().coerceAtLeast(1)
    val emptyCells = (perRow - badges.size % perRow) % perRow

    FlowRow(
      horizontalArrangement = Arrangement.Center,
      maxItemsInEachRow = perRow,
      modifier = Modifier.fillMaxWidth()
    ) {
      badges.forEach { badge ->
        BadgeCell(
          badge = badge,
          isSelected = badge == selectedBadge,
          isFaded = badge.isExpired() || badge.id == fadedBadgeId,
          width = cellWidth,
          onClick = { onBadgeClick(badge) },
          badgeImage = badgeImage
        )
      }

      // Fills out the last row so its badges line up with the rows above.
      repeat(emptyCells) {
        Spacer(modifier = Modifier.width(cellWidth))
      }
    }
  }
}

@Composable
private fun BadgeCell(
  badge: Badge,
  isSelected: Boolean,
  isFaded: Boolean,
  width: Dp,
  onClick: () -> Unit,
  badgeImage: @Composable (Badge, Modifier) -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .width(width)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = CELL_PADDING)
      .padding(bottom = 16.dp)
      .testTag(BadgeGridTestTags.badge(badge))
  ) {
    Box(modifier = Modifier.size(64.dp)) {
      badgeImage(
        badge,
        Modifier
          .size(64.dp)
          .alpha(if (isFaded) FADED_ALPHA else 1f)
      )

      if (isSelected) {
        Icon(
          imageVector = SignalIcons.Check.imageVector,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier
            .align(Alignment.TopEnd)
            .size(24.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(4.dp)
        )
      }
    }

    Text(
      text = badge.name,
      style = MaterialTheme.typography.bodySmall,
      textAlign = TextAlign.Center,
      minLines = 2,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(top = 6.dp)
    )
  }
}
