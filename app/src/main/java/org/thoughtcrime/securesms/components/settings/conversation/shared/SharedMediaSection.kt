/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Texts
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ThreadPhotoRailView
import org.thoughtcrime.securesms.database.MediaTable

private val RAIL_HEIGHT = 80.dp

/**
 * The shared media rail and its "see all" row, which every conversation type shows.
 *
 * The rail is a fixed height and stays in the layout while [loaded] is false, so that media arriving later fills in
 * space that was already there instead of shoving the rest of the screen down. It's only dropped once we know the chat
 * has no media at all.
 */
fun LazyListScope.sharedMediaSection(
  media: List<MediaTable.MediaRecord>,
  loaded: Boolean,
  onMediaClick: (MediaTable.MediaRecord, Boolean) -> Unit,
  onMediaViewClicked: (View) -> Unit,
  onSeeAllClick: () -> Unit
) {
  if (loaded && media.isEmpty()) {
    return
  }

  item { Dividers.Default() }

  item { Texts.SectionHeader(text = stringResource(R.string.recipient_preference_activity__shared_media)) }

  item {
    SharedMediaRail(
      media = media,
      onMediaClick = onMediaClick,
      onMediaViewClicked = onMediaViewClicked
    )
  }

  item {
    Rows.TextRow(
      text = stringResource(R.string.ConversationSettingsFragment__see_all),
      onClick = onSeeAllClick
    )
  }
}

@Composable
private fun SharedMediaRail(
  media: List<MediaTable.MediaRecord>,
  onMediaClick: (MediaTable.MediaRecord, Boolean) -> Unit,
  onMediaViewClicked: (View) -> Unit,
  modifier: Modifier = Modifier
) {
  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

  AndroidView(
    factory = { context -> ThreadPhotoRailView(context) },
    modifier = modifier
      .fillMaxWidth()
      .height(RAIL_HEIGHT)
  ) { railView ->
    railView.setListener { view, mediaRecord ->
      onMediaViewClicked(view)
      onMediaClick(mediaRecord, isLtr)
    }
    railView.setMediaRecords(Glide.with(railView), media)
  }
}
