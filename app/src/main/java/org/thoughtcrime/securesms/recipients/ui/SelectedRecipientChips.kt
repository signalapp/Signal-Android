/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.Chips
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.recipients.rememberRecipientField

/**
 * A row of chips for the recipients a user has picked, in the order they picked them, each of which removes its
 * recipient when tapped. Scrolls to the newest pick, and collapses away while nothing is picked.
 */
@Composable
fun SelectedRecipientChips(
  recipientIds: List<RecipientId>,
  onRemove: (RecipientId) -> Unit,
  modifier: Modifier = Modifier
) {
  val listState = rememberLazyListState()
  var previousCount by remember { mutableIntStateOf(recipientIds.size) }

  LaunchedEffect(recipientIds.size) {
    if (recipientIds.size > previousCount) {
      listState.animateScrollToItem(recipientIds.lastIndex)
    }
    previousCount = recipientIds.size
  }

  AnimatedVisibility(
    visible = recipientIds.isNotEmpty(),
    modifier = modifier
  ) {
    LazyRow(
      state = listState,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(horizontal = 16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 8.dp)
    ) {
      items(recipientIds, key = { it.toLong() }) { recipientId ->
        RecipientChip(
          recipientId = recipientId,
          onRemove = { onRemove(recipientId) },
          modifier = Modifier.animateItem()
        )
      }
    }
  }
}

@Composable
private fun RecipientChip(
  recipientId: RecipientId,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val noteToSelf = stringResource(R.string.note_to_self)
  val label by rememberRecipientField(recipientId) {
    if (isSelf) noteToSelf else getShortDisplayName(context)
  }

  Chips.Removable(
    label = label,
    onRemove = onRemove,
    avatar = { AvatarImage(recipientId = recipientId, modifier = Modifier.fillMaxSize()) },
    modifier = modifier
  )
}
