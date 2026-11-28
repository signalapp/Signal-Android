/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.compose

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.rememberWindowBreakpoint
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.Avatars

private const val PHONE_COLUMNS = 4
private const val TABLET_COLUMNS = 6
private val SWATCH_SIZE = 72.dp
private val SPACING = 16.dp

@VisibleForTesting
object AvatarColorGridTestTags {
  const val SWATCH = "avatar-color-swatch"
}

/**
 * The colors a text or vector avatar can be drawn in, with [selected] ringed: four to a row on phones, six on anything bigger.
 */
@Composable
fun AvatarColorGrid(
  selected: Avatars.ColorPair,
  onColorSelected: (Avatars.ColorPair) -> Unit,
  modifier: Modifier = Modifier
) {
  val columns = if (rememberWindowBreakpoint() is WindowBreakpoint.Small) PHONE_COLUMNS else TABLET_COLUMNS

  FlowRow(
    maxItemsInEachRow = columns,
    horizontalArrangement = spacedBy(SPACING, Alignment.CenterHorizontally),
    verticalArrangement = spacedBy(SPACING),
    modifier = modifier
      .fillMaxWidth()
      .selectableGroup()
  ) {
    Avatars.colors.forEachIndexed { index, color ->
      ColorSwatch(
        color = color,
        label = stringResource(R.string.AvatarColorGrid__color_d_of_d, index + 1, Avatars.colors.size),
        isSelected = color == selected,
        onClick = { onColorSelected(color) }
      )
    }
  }
}

@Composable
private fun ColorSwatch(
  color: Avatars.ColorPair,
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .size(SWATCH_SIZE)
      .clip(CircleShape)
      .background(Color(color.backgroundColor))
      .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
      .semantics { contentDescription = label }
      .testTag(AvatarColorGridTestTags.SWATCH)
  ) {
    if (isSelected) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .padding(4.dp)
          .border(width = 3.dp, color = MaterialTheme.colorScheme.background, shape = CircleShape)
      )
    }
  }
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun AvatarColorGridPreview() {
  AvatarColorGrid(
    selected = Avatars.colors[2],
    onColorSelected = {}
  )
}
