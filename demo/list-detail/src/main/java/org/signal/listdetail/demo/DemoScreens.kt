/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.listdetail.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.split.ListDetailEvents

/**
 * List-pane content. Every list is the same nav entry, so moving between the inbox, the archive and the
 * contacts animates this pane alone and leaves the chrome around it untouched.
 *
 * @param selectedItemId the item open in the detail pane, highlighted so that a split-pane window shows
 *   which row the detail beside it belongs to.
 */
@Composable
fun ItemListPane(
  route: DemoListRoute,
  selectedItemId: Int?,
  onEvent: (DemoEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  LazyColumn(modifier = modifier.fillMaxSize()) {
    if (route == DemoListRoute.INBOX) {
      item {
        ItemRow(
          title = DemoListRoute.ARCHIVE.label,
          subtitle = "${DemoData.archive.size} conversations",
          onClick = { onEvent(DemoEvents.ArchiveSelected) },
          icon = Icons.Filled.Archive,
          trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
        )
      }
    }

    items(items = DemoData.itemsFor(route), key = { it.id }) { item ->
      ItemRow(
        title = item.title,
        subtitle = item.subtitle,
        onClick = { onEvent(DemoEvents.ListDetailEvent(ListDetailEvents.Push(DemoDetailRoute.Item(item.id)))) },
        isSelected = item.id == selectedItemId
      )
    }
  }
}

/** Detail-pane content for an item. */
@Composable
fun ItemDetailPane(
  item: DemoItem,
  onEvent: (DemoEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  DetailScaffold(title = item.title, onBack = { onEvent(DemoEvents.ListDetailEvent(ListDetailEvents.Back)) }, modifier = modifier) {
    Text(
      text = item.subtitle,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Button(onClick = { onEvent(DemoEvents.ListDetailEvent(ListDetailEvents.Push(DemoDetailRoute.Notes(item.id)))) }) {
      Text(text = "Open notes")
    }
  }
}

/** Detail-pane content stacked on top of [ItemDetailPane], rather than replacing it. */
@Composable
fun ItemNotesPane(
  item: DemoItem,
  onEvent: (DemoEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  DetailScaffold(title = "Notes", onBack = { onEvent(DemoEvents.ListDetailEvent(ListDetailEvents.Back)) }, modifier = modifier) {
    Text(
      text = "Notes about ${item.title}. Back returns to the item, not to the list.",
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center
    )
  }
}

/**
 * Fills the detail pane before anything has been opened. Only ever seen in a split-pane window; a single
 * pane displays the list instead.
 */
@Composable
fun EmptyDetailPane(modifier: Modifier = Modifier) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = modifier.fillMaxSize()
  ) {
    Text(
      text = "Select something from the list",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Scaffold(
    containerColor = Color.Transparent,
    topBar = {
      TopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
      )
    },
    modifier = modifier
  ) { paddingValues ->
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 32.dp)
    ) {
      content()
    }
  }
}

@Composable
private fun ItemRow(
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  trailingIcon: ImageVector? = null,
  isSelected: Boolean = false
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 2.dp)
      .clip(MaterialTheme.shapes.large)
      .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp)
  ) {
    Row(
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
      if (icon != null) {
        Icon(imageVector = icon, contentDescription = null)
      } else {
        Text(text = title.take(1), style = MaterialTheme.typography.titleMedium)
      }
    }

    Column(
      modifier = Modifier
        .weight(1f)
        .padding(start = 16.dp)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    if (trailingIcon != null) {
      Icon(
        imageVector = trailingIcon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun ItemListPanePreview() {
  Previews.Preview {
    ItemListPane(
      route = DemoListRoute.INBOX,
      selectedItemId = 2,
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun ItemDetailPanePreview() {
  Previews.Preview {
    ItemDetailPane(
      item = DemoData.inbox.first(),
      onEvent = {}
    )
  }
}
