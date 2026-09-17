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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
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

/**
 * Opens a page's grid scrolled past its [MediaKeyboardSearchField] and reports when the user has
 * pulled back down to it, so the top bar can drop its now-redundant search icon.
 *
 * Effects only. The two pages hold different kinds of grid state, which share no supertype, so the
 * grid is reached through lambdas rather than passed in.
 *
 * @param hasContent Whether the grid has anything to scroll yet; until it does there is nothing to
 *   hide the field behind.
 * @param firstVisibleItemIndex The grid's first visible item. Read inside a [derivedStateOf] so
 *   that scrolling does not recompose the page.
 * @param scrollPastField Scrolls the grid to an item and offset.
 * @param onRevealedChange Receives whether the field is showing.
 * @param resetKey Hides the field again whenever this changes, for a grid whose contents are
 *   replaced and whose scroll goes back to the top with them.
 */
@Composable
internal fun SearchFieldReveal(
  hasContent: Boolean,
  firstVisibleItemIndex: () -> Int,
  scrollPastField: suspend (index: Int, offset: Int) -> Unit,
  onRevealedChange: (Boolean) -> Unit,
  resetKey: Any? = Unit
) {
  // Scrolling to an item stops at the leading content padding rather than the top of the viewport,
  // and that gap is just enough to leave the bottom of the field showing, so it goes too.
  val topContentPaddingPx = with(LocalDensity.current) { GRID_CONTENT_PADDING.calculateTopPadding().roundToPx() }

  var scrolledPast by remember(resetKey) { mutableStateOf(false) }

  LaunchedEffect(resetKey, hasContent) {
    if (hasContent && !scrolledPast) {
      scrollPastField(1, topContentPaddingPx)
      scrolledPast = true
    }
  }

  val currentFirstVisibleItemIndex by rememberUpdatedState(firstVisibleItemIndex)
  val revealed by remember { derivedStateOf { currentFirstVisibleItemIndex() == 0 } }

  // Held back until the grid has settled where it opens, or the field would be reported as showing
  // for the frames before that first scroll and the top bar's icon would blink out and back.
  LaunchedEffect(revealed, scrolledPast) {
    onRevealedChange(scrolledPast && revealed)
  }
}
