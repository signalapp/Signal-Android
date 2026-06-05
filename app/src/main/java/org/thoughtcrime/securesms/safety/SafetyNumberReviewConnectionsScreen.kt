/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.safety

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.rememberRecipientField
import org.signal.core.ui.R as CoreUiR

/**
 * Full-screen review of all recipients with safety number changes, grouped by destination bucket.
 * Shown as a Compose [androidx.compose.ui.window.Dialog] inside the safety number bottom sheet.
 */
@Composable
fun SafetyNumberReviewConnectionsScreen(
  state: SafetyNumberBottomSheetState,
  onDoneClick: () -> Unit,
  emitter: (SafetyNumberBottomSheetEvent) -> Unit
) {
  val recipientCount = state.destinationToRecipientMap.values.flatten().size

  Scaffolds.Default(
    onNavigationClick = onDoneClick,
    navigationIconRes = CoreUiR.drawable.symbol_arrow_start_24,
    navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description),
    title = stringResource(R.string.SafetyNumberReviewConnectionsFragment__safety_number_changes)
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      LazyColumn(
        contentPadding = PaddingValues(bottom = 76.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        item {
          Text(
            text = pluralStringResource(
              R.plurals.SafetyNumberReviewConnectionsFragment__d_recipients_may_have,
              recipientCount,
              recipientCount
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
          )
        }

        state.destinationToRecipientMap.forEach { (bucket, recipients) ->
          item(key = bucketKey(bucket)) {
            SafetyNumberBucketHeader(bucket = bucket, emitter = emitter)
          }
          items(items = recipients, key = { it.recipient.id.serialize() }) { recipient ->
            SafetyNumberRecipientRow(safetyNumberRecipient = recipient, emitter = emitter)
          }
        }
      }

      Buttons.LargeTonal(
        onClick = onDoneClick,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(end = 16.dp, bottom = 16.dp)
      ) {
        Text(text = stringResource(R.string.SafetyNumberReviewConnectionsFragment__done))
      }
    }
  }
}

private fun bucketKey(bucket: SafetyNumberBucket): String {
  return when (bucket) {
    is SafetyNumberBucket.DistributionListBucket -> "dl_${bucket.distributionListId.serialize()}"
    is SafetyNumberBucket.GroupBucket -> "group_${bucket.recipient.id.serialize()}"
    SafetyNumberBucket.ContactsBucket -> "contacts"
  }
}

@Composable
private fun SafetyNumberBucketHeader(
  bucket: SafetyNumberBucket,
  emitter: (SafetyNumberBottomSheetEvent) -> Unit
) {
  val context = LocalContext.current
  val menuController = remember { DropdownMenus.MenuController() }
  val groupDisplayName by rememberRecipientField(
    (bucket as? SafetyNumberBucket.GroupBucket)?.recipient ?: Recipient.UNKNOWN
  ) { getDisplayName(context) }

  val title = when (bucket) {
    is SafetyNumberBucket.DistributionListBucket -> {
      if (bucket.distributionListId == DistributionListId.MY_STORY) {
        stringResource(R.string.Recipient_my_story)
      } else {
        bucket.name
      }
    }
    is SafetyNumberBucket.GroupBucket -> groupDisplayName
    SafetyNumberBucket.ContactsBucket -> stringResource(R.string.SafetyNumberBucketRowItem__contacts)
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .padding(start = 16.dp, top = 16.dp, bottom = 12.dp)
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier
        .weight(1f)
        .padding(end = 16.dp)
    )

    if (bucket is SafetyNumberBucket.DistributionListBucket) {
      Box {
        IconButton(onClick = { menuController.show() }) {
          Icon(
            imageVector = SignalIcons.MoreVertical.imageVector,
            contentDescription = stringResource(R.string.SafetyNumberRecipientRowItem__open_context_menu),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        DropdownMenus.Menu(controller = menuController) { controller ->
          DropdownMenus.ItemWithIcon(
            menuController = controller,
            drawableResId = R.drawable.symbol_x_circle_24,
            stringResId = R.string.SafetyNumberReviewConnectionsFragment__remove_all,
            onClick = { emitter(SafetyNumberBottomSheetEvent.RemoveAll(bucket)) }
          )
        }
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun PreviewReviewConnections() {
  Previews.Preview {
    SafetyNumberReviewConnectionsScreen(
      state = SafetyNumberBottomSheetState(
        untrustedRecipientCount = 3,
        hasLargeNumberOfUntrustedRecipients = true,
        destinationToRecipientMap = emptyMap(),
        loadState = SafetyNumberBottomSheetState.LoadState.READY
      ),
      onDoneClick = {},
      emitter = {}
    )
  }
}
