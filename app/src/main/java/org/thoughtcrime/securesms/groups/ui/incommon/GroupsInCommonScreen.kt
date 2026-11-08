/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.incommon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.groups.GroupInCommon
import org.thoughtcrime.securesms.recipients.Recipient
import java.text.NumberFormat
import org.signal.core.ui.R as CoreUiR

object GroupsInCommonTestTags {
  const val SCROLLER = "scroller"
  const val ROW_GROUP = "row-group"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsInCommonScreen(
  state: GroupsInCommonState,
  onEvent: (GroupsInCommonEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val groups = state.groups

  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

  Scaffold(
    topBar = { TopAppBar(groupCount = groups.size, scrollBehavior = scrollBehavior, onBackPress = { onEvent(GroupsInCommonEvent.NavigateBackClicked) }) },
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
  ) { padding ->
    LazyColumn(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .padding(padding)
        .testTag(GroupsInCommonTestTags.SCROLLER)
    ) {
      items(groups, key = { it.recipient.id.toLong() }) { group ->
        GroupRow(
          group = group,
          onClick = { onEvent(GroupsInCommonEvent.GroupClicked(group.recipient.id)) },
          modifier = Modifier.testTag(GroupsInCommonTestTags.ROW_GROUP)
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(
  groupCount: Int,
  scrollBehavior: TopAppBarScrollBehavior,
  onBackPress: () -> Unit
) {
  Scaffolds.DefaultTopAppBar(
    title = pluralStringResource(R.plurals.GroupsInCommon__n_groups_in_common_title, groupCount, NumberFormat.getInstance().format(groupCount)),
    titleContent = { _, title -> Text(text = title, style = MaterialTheme.typography.titleLarge) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.DefaultTopAppBar__navigate_up_content_description),
    onNavigationClick = onBackPress,
    scrollBehavior = scrollBehavior
  )
}

@Composable
private fun GroupRow(
  group: GroupInCommon,
  onClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .clickable(onClick = onClick)
      .padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    AvatarImage(
      recipientId = group.recipient.id,
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
    )

    Spacer(modifier = Modifier.width(16.dp))

    Text(
      text = group.name,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@DayNightPreviews
@Composable
fun GroupsInCommonScreenPreview() {
  Previews.Preview {
    GroupsInCommonScreen(
      state = GroupsInCommonState(
        groups = listOf(
          GroupInCommon(recipient = Recipient(id = RecipientId.from(1), isResolving = false), name = "Family"),
          GroupInCommon(recipient = Recipient(id = RecipientId.from(2), isResolving = false), name = "happy birthday"),
          GroupInCommon(recipient = Recipient(id = RecipientId.from(3), isResolving = false), name = "The Cheesecake is a Lie"),
          GroupInCommon(recipient = Recipient(id = RecipientId.from(4), isResolving = false), name = "JEFFPARDY"),
          GroupInCommon(recipient = Recipient(id = RecipientId.from(5), isResolving = false), name = "Roommates"),
          GroupInCommon(recipient = Recipient(id = RecipientId.from(6), isResolving = false), name = "NYC Rock Climbers"),
          GroupInCommon(recipient = Recipient(id = RecipientId.from(7), isResolving = false), name = "Parkdale Run Club")
        )
      ),
      onEvent = {}
    )
  }
}
