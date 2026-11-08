/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.mediakeyboard.R

/**
 * The content padding of a page's grid.
 *
 * Shared with the pages rather than left to each of them because opening scrolled past
 * [MediaKeyboardSearchField] means scrolling the leading padding away too, and a page that measured
 * a different value than it laid out with would leave the field peeking.
 */
internal val GRID_CONTENT_PADDING = PaddingValues(horizontal = 8.dp, vertical = 4.dp)

/** Gap between the search field and the content it sits above. */
internal val SEARCH_FIELD_SPACING = 8.dp

/**
 * Looks like a field but is not one: tapping it hands search off to the host, which has a whole
 * screen to give it rather than the strip of keyboard available here.
 *
 * Pages put this first in their scrolling content and open scrolled past it, so it is something the
 * user pulls down to rather than something holding a row of results' worth of room.
 *
 * @param hint What is being searched, shown in place of anything typed.
 */
@Composable
internal fun MediaKeyboardSearchField(
  hint: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .background(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(50))
      .clip(RoundedCornerShape(50))
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp)
  ) {
    Icon(
      imageVector = SignalIcons.Search.imageVector,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(20.dp)
    )

    Text(
      text = hint,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun MediaKeyboardSearchFieldPreview() {
  MediaKeyboardSearchField(
    hint = stringResource(R.string.MediaKeyboard__search_stickers),
    onClick = {}
  )
}
