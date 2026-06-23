/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.issues

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.LogDatabase.IssueTable.IssueRecord
import org.thoughtcrime.securesms.database.model.IssuePriority
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalIssuesScreen(
  state: InternalIssuesState,
  onEvent: (InternalIssuesScreenEvent) -> Unit = {},
  onBack: () -> Unit = {}
) {
  var showFilterSheet by remember { mutableStateOf(false) }
  var showSortSheet by remember { mutableStateOf(false) }
  var showClearDialog by remember { mutableStateOf(false) }

  Scaffolds.Settings(
    title = "App Issues",
    onNavigationClick = onBack,
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    snackbarHost = {},
    actions = {
      if (state.names.isNotEmpty()) {
        IconButton(onClick = { showFilterSheet = true }) {
          Icon(painter = painterResource(R.drawable.symbol_filter_24), contentDescription = "Filter")
        }
        IconButton(onClick = { showSortSheet = true }) {
          Icon(painter = painterResource(R.drawable.symbol_list_bullet_24), contentDescription = "Sort")
        }
        IconButton(onClick = { showClearDialog = true }) {
          Icon(painter = SignalIcons.Trash.painter, contentDescription = "Clear")
        }
      }
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      Rows.RadioListRow(
        text = "Notification priority threshold",
        labels = IssuePriority.entries.map { it.label }.toTypedArray(),
        values = IssuePriority.entries.map { it.name }.toTypedArray(),
        selectedValue = state.notificationPriority.name,
        onSelected = { onEvent(InternalIssuesScreenEvent.SetNotificationPriority(IssuePriority.valueOf(it))) }
      )

      HorizontalDivider()

      if (!state.loading && state.issues.isEmpty()) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Text(
            text = if (state.nameFilter != null) "No issues match this filter." else "No issues recorded.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
          items(state.issues, key = { it.id }) { issue ->
            IssueRow(
              issue = issue,
              expanded = state.expandedIds.contains(issue.id),
              onClick = { onEvent(InternalIssuesScreenEvent.ToggleExpanded(issue.id)) }
            )
            HorizontalDivider()
          }
        }
      }
    }
  }

  if (showFilterSheet) {
    ModalBottomSheet(
      onDismissRequest = { showFilterSheet = false },
      sheetState = rememberModalBottomSheetState()
    ) {
      SheetTitle("Filter by name")
      SelectionRow(
        text = "All",
        selected = state.nameFilter == null,
        onClick = {
          onEvent(InternalIssuesScreenEvent.SetNameFilter(null))
          showFilterSheet = false
        }
      )
      state.names.forEach { name ->
        SelectionRow(
          text = name,
          selected = state.nameFilter == name,
          onClick = {
            onEvent(InternalIssuesScreenEvent.SetNameFilter(name))
            showFilterSheet = false
          }
        )
      }
      Spacer(modifier = Modifier.size(16.dp))
    }
  }

  if (showSortSheet) {
    ModalBottomSheet(
      onDismissRequest = { showSortSheet = false },
      sheetState = rememberModalBottomSheetState()
    ) {
      SheetTitle("Sort by")
      IssueSortOrder.entries.forEach { order ->
        SelectionRow(
          text = order.label,
          selected = state.sortOrder == order,
          onClick = {
            onEvent(InternalIssuesScreenEvent.SetSortOrder(order))
            showSortSheet = false
          }
        )
      }
      Spacer(modifier = Modifier.size(16.dp))
    }
  }

  if (showClearDialog) {
    Dialogs.SimpleAlertDialog(
      title = "Clear all issues?",
      body = "This will permanently delete all recorded app issues.",
      confirm = "Clear",
      dismiss = "Cancel",
      onConfirm = { onEvent(InternalIssuesScreenEvent.ClearAll) },
      onDismiss = { showClearDialog = false }
    )
  }
}

@Composable
private fun SheetTitle(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
  )
}

@Composable
private fun SelectionRow(
  text: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 24.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.weight(1f)
    )
    if (selected) {
      Icon(
        painter = SignalIcons.Check.painter,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IssueRow(
  issue: IssueRecord,
  expanded: Boolean,
  onClick: () -> Unit
) {
  val clipboardManager = LocalClipboardManager.current
  val context = LocalContext.current

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = onClick,
        onLongClick = {
          clipboardManager.setText(AnnotatedString(issue.toCopyText()))
          Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }
      )
      .padding(horizontal = 16.dp, vertical = 12.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = issue.name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f)
      )
      Text(
        text = issue.priority.label,
        style = MaterialTheme.typography.labelMedium,
        color = priorityColor(issue.priority),
        fontWeight = FontWeight.Bold
      )
    }

    Text(
      text = buildString {
        append(formatTimestamp(issue.createdAt))
        append("  •  v")
        append(issue.version)
        issue.duration?.let { append("  •  ${it}ms") }
      },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Text(
      text = issue.description,
      style = MaterialTheme.typography.bodyMedium,
      maxLines = if (expanded) Int.MAX_VALUE else 2
    )

    if (expanded && !issue.stackTrace.isNullOrBlank()) {
      Text(
        text = issue.stackTrace,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
      )
    }
  }
}

private fun IssueRecord.toCopyText(): String {
  return buildString {
    append(name)
    append(" (")
    append(priority.label)
    append(")\n")
    append(formatTimestamp(createdAt))
    append("  •  v")
    append(version)
    duration?.let { append("  •  ${it}ms") }
    append("\n")
    append(description)
    if (!stackTrace.isNullOrBlank()) {
      append("\n")
      append(stackTrace)
    }
  }
}

@Composable
private fun priorityColor(priority: IssuePriority): Color {
  return when (priority) {
    IssuePriority.HIGH -> MaterialTheme.colorScheme.error
    IssuePriority.MEDIUM -> MaterialTheme.colorScheme.tertiary
    IssuePriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
  }
}

private fun formatTimestamp(time: Long): String {
  return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(time))
}

@DayNightPreviews
@Composable
private fun InternalIssuesScreenPreview() {
  Previews.Preview {
    InternalIssuesScreen(
      state = InternalIssuesState(
        loading = false,
        names = listOf("Slow Database Read", "Slow Database Write"),
        issues = listOf(
          IssueRecord(1, System.currentTimeMillis(), "7.42.1", "Slow Database Write", "Took 812ms. query=transaction hold", "java.lang.Throwable\n\tat Foo.bar(Foo.java:1)", IssuePriority.HIGH, 812),
          IssueRecord(2, System.currentTimeMillis(), "7.42.1", "Slow Database Read", "Took 1043ms. query=SELECT * FROM message", null, IssuePriority.LOW, 1043)
        )
      )
    )
  }
}
