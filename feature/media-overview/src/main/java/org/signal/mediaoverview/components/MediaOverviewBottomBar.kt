/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * A floating bar of actions for the current multi-selection.
 */
@Composable
internal fun MediaOverviewBottomBar(
  visible: Boolean,
  items: List<BottomBarItem>,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(
    visible = visible,
    enter = slideInVertically(tween(durationMillis = 250, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(durationMillis = 250, easing = FastOutSlowInEasing)),
    exit = slideOutVertically(tween(durationMillis = 250, easing = FastOutSlowInEasing)) { it / 2 } + fadeOut(tween(durationMillis = 250, easing = FastOutSlowInEasing)),
    modifier = modifier
  ) {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = SignalTheme.colors.colorSurface2,
      shadowElevation = 4.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEach { item ->
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .weight(1f)
              .clickable(onClick = item.onClick)
              .padding(horizontal = 6.dp, vertical = 12.dp)
              .testTag(item.testTag)
          ) {
            Icon(
              imageVector = item.icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
              text = item.label,
              style = MaterialTheme.typography.bodyMedium,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }
}

internal data class BottomBarItem(
  val icon: ImageVector,
  val label: String,
  val testTag: String,
  val onClick: () -> Unit
)
