/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.incommon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.compose.StatusBarColorNestedScrollConnection
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.viewModel
import java.text.NumberFormat

/**
 * Displays a list of groups that the user has in common with the specified [RecipientId].
 */
class GroupsInCommonActivity : PassphraseRequiredActivity() {
  companion object {
    private const val EXTRA_RECIPIENT_ID = "recipient_id"

    fun createIntent(
      context: Context,
      recipientId: RecipientId
    ): Intent {
      return Intent(context, GroupsInCommonActivity::class.java).apply {
        putExtra(EXTRA_RECIPIENT_ID, recipientId)
      }
    }
  }

  private val viewModel by viewModel {
    GroupsInCommonViewModel(
      context = this,
      recipientId = intent.getParcelableExtraCompat(EXTRA_RECIPIENT_ID, RecipientId::class.java)!!
    )
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    setContent {
      SignalTheme {
        GroupsInCommonScreen(
          activity = this,
          viewModel = viewModel,
          onNavigateBack = ::supportFinishAfterTransition,
          onNavigateToConversation = ::navigateToConversation
        )
      }
    }
  }

  override fun finish() {
    super.finish()
    overridePendingTransition(0, R.anim.slide_fade_to_bottom)
  }

  private fun navigateToConversation(group: Recipient) {
    CommunicationActions.startConversation(this, group, null)
    finish()
  }
}

@Composable
private fun GroupsInCommonScreen(
  activity: Activity,
  viewModel: GroupsInCommonViewModel,
  onNavigateBack: () -> Unit = {},
  onNavigateToConversation: (recipient: Recipient) -> Unit = {}
) {
  val groups by viewModel.groups.collectAsStateWithLifecycle()
  val nestedScrollConnection = remember { StatusBarColorNestedScrollConnection(activity) }

  GroupsInCommonContent(
    groups = groups,
    onBackPress = onNavigateBack,
    onRowClick = onNavigateToConversation,
    modifier = Modifier.nestedScroll(nestedScrollConnection)
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupsInCommonContent(
  groups: List<Recipient>,
  onBackPress: () -> Unit = {},
  onRowClick: (recipient: Recipient) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

  Scaffold(
    topBar = { TopAppBar(groupCount = groups.size, scrollBehavior = scrollBehavior, onBackPress = onBackPress) },
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
  ) { padding ->
    LazyColumn(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(padding)
    ) {
      items(groups) {
        GroupRow(group = it, onRowClick = onRowClick)
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
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
    navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description),
    onNavigationClick = onBackPress,
    scrollBehavior = scrollBehavior
  )
}

@Composable
private fun GroupRow(
  group: Recipient,
  onRowClick: (recipient: Recipient) -> Unit = { }
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .clickable(onClick = { onRowClick(group) })
      .padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    AvatarImage(
      recipient = group,
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
    )

    Spacer(modifier = Modifier.width(16.dp))

    Text(
      text = group.getGroupName(LocalContext.current)!!,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@DayNightPreviews
@Composable
fun GroupsInCommonContentPreview() {
  Previews.Preview {
    GroupsInCommonContent(
      groups = listOf(
        Recipient(groupName = "Family"),
        Recipient(groupName = "happy birthday"),
        Recipient(groupName = "The Cheesecake is a Lie"),
        Recipient(groupName = "JEFFPARDY"),
        Recipient(groupName = "Roommates"),
        Recipient(groupName = "NYC Rock Climbers"),
        Recipient(groupName = "Parkdale Run Club")
      )
    )
  }
}
