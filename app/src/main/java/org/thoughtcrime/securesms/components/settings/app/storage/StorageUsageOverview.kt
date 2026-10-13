/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.bytes
import org.thoughtcrime.securesms.R

/**
 * Total on-device usage, a proportional bar of each [StorageUsage] category, and a legend.
 */
@Composable
fun StorageUsageOverview(
  usage: StorageUsage?,
  onReviewStorage: () -> Unit,
  modifier: Modifier = Modifier
) {
  val entries = storageUsageEntries(usage ?: StorageUsage())

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 32.dp)
  ) {
    Text(
      text = usage?.total?.bytes?.toUnitString() ?: stringResource(id = R.string.preferences_storage__calculating),
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.align(Alignment.End)
    )

    val barModifier = Modifier
      .fillMaxWidth()
      .padding(top = 16.dp)

    if (usage == null) {
      PendingStorageUsageBar(modifier = barModifier)
    } else {
      StorageUsageBar(entries = entries, modifier = barModifier)
    }

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp)
    ) {
      entries.forEach { entry ->
        StorageUsageLegendItem(entry)
      }
    }

    Buttons.LargeTonal(
      onClick = onReviewStorage,
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(top = 16.dp)
    ) {
      Text(text = stringResource(id = R.string.preferences_storage__review_storage))
    }
  }
}

/**
 * Pulses an empty bar while usage is still being computed.
 */
@Composable
private fun PendingStorageUsageBar(modifier: Modifier = Modifier) {
  val emptyColor = colorResource(id = R.color.storage_color_empty)

  val pulseAlpha by rememberInfiniteTransition(label = "storage-pending").animateFloat(
    initialValue = 0.4f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(animation = tween(durationMillis = 800), repeatMode = RepeatMode.Reverse),
    label = "storage-pending-alpha"
  )

  Canvas(
    modifier = modifier
      .height(28.dp)
      .clip(CircleShape)
  ) {
    drawRect(color = emptyColor.copy(alpha = emptyColor.alpha * pulseAlpha))
  }
}

/**
 * Proportional bar of each category. The segments grow in from the start edge when first shown.
 */
@Composable
private fun StorageUsageBar(
  entries: List<StorageUsageEntry>,
  modifier: Modifier = Modifier
) {
  val total = entries.sumOf { it.size }
  val emptyColor = colorResource(id = R.color.storage_color_empty)

  val revealProgress = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    revealProgress.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 600))
  }

  Canvas(
    modifier = modifier
      .height(28.dp)
      .clip(CircleShape)
  ) {
    drawRect(color = emptyColor)

    if (total <= 0) {
      return@Canvas
    }

    val revealedWidth = size.width * revealProgress.value
    var startX = 0f
    entries.forEachIndexed { index, entry ->
      val endX = if (index < entries.lastIndex) {
        startX + revealedWidth * entry.size / total
      } else {
        revealedWidth
      }

      drawRect(
        color = entry.color,
        topLeft = Offset(startX, 0f),
        size = Size(endX - startX, size.height)
      )

      startX = endX
    }
  }
}

@Composable
private fun StorageUsageLegendItem(entry: StorageUsageEntry) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Spacer(
      modifier = Modifier
        .size(8.dp)
        .background(color = entry.color, shape = CircleShape)
    )

    Text(
      text = entry.label,
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(start = 4.dp)
    )
  }
}

@Composable
private fun storageUsageEntries(usage: StorageUsage): List<StorageUsageEntry> {
  return listOf(
    StorageUsageEntry(stringResource(id = R.string.preferences_storage__photos), colorResource(id = R.color.storage_color_photos), usage.photos),
    StorageUsageEntry(stringResource(id = R.string.preferences_storage__videos), colorResource(id = R.color.storage_color_videos), usage.videos),
    StorageUsageEntry(stringResource(id = R.string.preferences_storage__files), colorResource(id = R.color.storage_color_files), usage.files),
    StorageUsageEntry(stringResource(id = R.string.preferences_storage__audio), colorResource(id = R.color.storage_color_audio), usage.audio),
    StorageUsageEntry(stringResource(id = R.string.preferences_storage__messages), colorResource(id = R.color.storage_color_messages), usage.messages),
    StorageUsageEntry(stringResource(id = R.string.preferences_storage__stickers), colorResource(id = R.color.storage_color_stickers), usage.stickers),
    StorageUsageEntry(stringResource(id = R.string.preferences_storage__other), colorResource(id = R.color.storage_color_other), usage.other)
  )
}

private data class StorageUsageEntry(
  val label: String,
  val color: Color,
  val size: Long
)

@DayNightPreviews
@Composable
private fun StorageUsageOverviewPreview() {
  Previews.Preview {
    StorageUsageOverview(
      usage = StorageUsage(
        photos = 1_200_000_000,
        videos = 3_400_000_000,
        files = 150_000_000,
        audio = 80_000_000,
        messages = 2_100_000_000,
        stickers = 120_000_000,
        other = 900_000_000
      ),
      onReviewStorage = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun StorageUsageOverviewEmptyPreview() {
  Previews.Preview {
    StorageUsageOverview(
      usage = null,
      onReviewStorage = {}
    )
  }
}
