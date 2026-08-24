/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Rows.TextAndLabel
import org.signal.core.ui.compose.Texts
import org.thoughtcrime.securesms.components.settings.conversation.CallRowResources
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.util.DateUtils

/** A single call in the call-info variant of a conversation settings screen. */
data class CallEntry(
  val call: CallTable.Call,
  val record: MessageRecord
)

/**
 * The list of calls shown when the screen is opened as call info. Only individual and group conversations have calls,
 * so only those two screens include this section.
 */
fun LazyListScope.callLogSection(calls: List<CallEntry>) {
  if (calls.isEmpty()) {
    return
  }

  item {
    Texts.SectionHeader(text = DateUtils.formatDate(LocalLocale.current.platformLocale, calls.first().record.timestamp))
  }

  items(
    items = calls,
    key = { it.record.id }
  ) { entry ->
    CallRow(entry = entry)
  }

  item { Dividers.Default() }
}

@Composable
private fun CallRow(
  entry: CallEntry,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  Rows.TextRow(
    text = {
      TextAndLabel(
        text = stringResource(CallRowResources.typeStringRes(entry.call)),
        label = DateUtils.getOnlyTimeString(context, entry.record.timestamp)
      )
    },
    icon = {
      Icon(
        painter = painterResource(CallRowResources.iconRes(entry.call)),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface
      )
    },
    modifier = modifier
  )
}
