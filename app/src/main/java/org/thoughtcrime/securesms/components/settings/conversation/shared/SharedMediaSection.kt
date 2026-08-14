/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Texts
import org.signal.uicomponents.recentmediarail.RecentMediaRail
import org.signal.uicomponents.recentmediarail.RecentMediaRailEvents
import org.signal.uicomponents.recentmediarail.RecentMediaRailState
import org.thoughtcrime.securesms.R

/**
 * The shared media rail and its "see all" row, which every conversation type shows.
 *
 * Dropped entirely once we know the chat has no media at all -- until then the rail keeps its space, so media arriving
 * later doesn't shove the rest of the screen down.
 */
fun LazyListScope.sharedMediaSection(
  state: RecentMediaRailState,
  onEvent: (RecentMediaRailEvents) -> Unit
) {
  if (!state.visible) {
    return
  }

  item { Dividers.Default() }

  item { Texts.SectionHeader(text = stringResource(R.string.recipient_preference_activity__shared_media)) }

  item {
    RecentMediaRail(
      state = state,
      onEvent = onEvent
    )
  }

  item {
    Rows.TextRow(
      text = stringResource(R.string.ConversationSettingsFragment__see_all),
      onClick = { onEvent(RecentMediaRailEvents.SeeAllClicked) }
    )
  }
}
