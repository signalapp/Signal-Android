/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.remoteconfig

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.ClearableTextField
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons

/** Lists every remote config flag, with a filter, and lets an internal user override any of them. */
@Composable
fun InternalRemoteConfigScreen(
  state: InternalRemoteConfigState,
  onEvent: (InternalRemoteConfigEvent) -> Unit
) {
  val listState = rememberLazyListState()

  LaunchedEffect(state.filter, state.overrideCount) {
    listState.scrollToItem(0)
  }

  Scaffolds.Settings(
    title = "Remote config",
    onNavigationClick = { onEvent(InternalRemoteConfigEvent.BackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    actions = {
      if (state.overrideCount > 0) {
        IconButton(onClick = { onEvent(InternalRemoteConfigEvent.ClearAllClicked) }) {
          Icon(painter = SignalIcons.Trash.painter, contentDescription = "Clear all overrides")
        }
      }
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      ClearableTextField(
        value = state.filter,
        onValueChange = { onEvent(InternalRemoteConfigEvent.FilterChanged(it)) },
        hint = "Filter by key or value",
        clearContentDescription = "Clear filter",
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp)
      )

      Text(
        text = if (state.overrideCount > 0) {
          "${state.overrideCount} override(s) active. Overrides are applied on top of the values from the service and persist across restarts."
        } else {
          "Tap a flag to override it locally. Overrides are applied on top of the values from the service and persist across restarts."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
      )

      HorizontalDivider()

      if (state.showEmptyState) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Text(
            text = "No flags match this filter.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
          items(state.configs, key = { it.key }) { item ->
            ConfigRow(
              item = item,
              onClick = { onEvent(InternalRemoteConfigEvent.ConfigClicked(item.key)) }
            )
          }
        }
      }
    }
  }

  if (state.editor != null) {
    EditOverrideDialog(
      editor = state.editor,
      onEvent = onEvent
    )
  }

  if (state.showRestartDialog) {
    Dialogs.SimpleAlertDialog(
      title = "Restart app?",
      body = if (state.restartPromptKeys.size == 1) {
        "${state.restartPromptKeys.first()} isn't hot-swappable, so anything that already read it is still using the old value. Restart to apply it everywhere."
      } else {
        "${state.restartPromptKeys.size} of the flags you changed aren't hot-swappable, so anything that already read them is still using the old values. Restart to apply them everywhere."
      },
      confirm = "Restart",
      dismiss = "Not now",
      onConfirm = { onEvent(InternalRemoteConfigEvent.RestartConfirmed) },
      onDismiss = { onEvent(InternalRemoteConfigEvent.RestartDismissed) }
    )
  }

  if (state.showClearAllDialog) {
    Dialogs.SimpleAlertDialog(
      title = "Clear all overrides?",
      body = "All ${state.overrideCount} override(s) will be removed and the values from the service will be used instead.",
      confirm = "Clear",
      dismiss = "Cancel",
      onConfirm = { onEvent(InternalRemoteConfigEvent.ClearAllConfirmed) },
      onDismiss = { onEvent(InternalRemoteConfigEvent.ClearAllDismissed) }
    )
  }
}

@Composable
private fun ConfigRow(item: RemoteConfigListItem, onClick: () -> Unit) {
  val label = buildString {
    append(item.effectiveValue)
    if (item.isOverridden) {
      append("\nOVERRIDDEN (service value: ${item.remoteValue ?: "none"})")
    }
    if (!item.active) {
      append("\nInactive: the service value is ignored, but an override from here still applies")
    }
  }

  Rows.TextRow(
    text = AnnotatedString(item.key),
    label = AnnotatedString(label),
    foregroundTint = if (item.isOverridden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    onClick = onClick
  )
}

@Composable
private fun EditOverrideDialog(
  editor: InternalRemoteConfigState.Editor,
  onEvent: (InternalRemoteConfigEvent) -> Unit
) {
  val item = editor.config

  Dialogs.BaseAlertDialog(
    onDismissRequest = { onEvent(InternalRemoteConfigEvent.EditorDismissed) },
    modifier = Modifier,
    title = {
      Text(
        text = item.key,
        style = MaterialTheme.typography.titleMedium
      )
    },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SelectionContainer {
          Column {
            DetailLine(name = "Current", value = item.effectiveValue)
            DetailLine(name = "Service", value = item.remoteValue ?: "none")
            DetailLine(name = "Default", value = item.defaultValue)
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (item.isBoolean) {
          Rows.RadioRow(
            selected = editor.value.toBoolean(),
            text = "true",
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onEvent(InternalRemoteConfigEvent.EditorValueChanged("true")) }
          )
          Rows.RadioRow(
            selected = !editor.value.toBoolean(),
            text = "false",
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onEvent(InternalRemoteConfigEvent.EditorValueChanged("false")) }
          )
        } else {
          TextField(
            value = editor.value,
            onValueChange = { onEvent(InternalRemoteConfigEvent.EditorValueChanged(it)) },
            label = { Text("Override value") },
            modifier = Modifier.fillMaxWidth()
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "This is the raw value the service would have sent. The flag's own transformer is still applied on top of it.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!item.hotSwappable) {
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "This flag isn't hot-swappable, so you'll be offered a restart after changing it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onEvent(InternalRemoteConfigEvent.EditorSaveClicked) }) {
        Text(text = "Save")
      }
    },
    dismissButton = {
      Row {
        if (item.isOverridden) {
          TextButton(onClick = { onEvent(InternalRemoteConfigEvent.EditorClearClicked) }) {
            Text(text = "Clear")
          }
        }
        TextButton(onClick = { onEvent(InternalRemoteConfigEvent.EditorDismissed) }) {
          Text(text = "Cancel")
        }
      }
    }
  )
}

@Composable
private fun DetailLine(name: String, value: String) {
  Text(
    text = "$name: $value",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}

@DayNightPreviews
@Composable
private fun InternalRemoteConfigScreenPreview() {
  Previews.Preview {
    InternalRemoteConfigScreen(
      state = InternalRemoteConfigState(
        loaded = true,
        overrideCount = 1,
        configs = listOf(
          RemoteConfigListItem(
            key = "android.contactSharingV2",
            effectiveValue = "true",
            defaultValue = "false",
            rawDefaultValue = "false",
            remoteValue = "false",
            overrideValue = "true",
            isBoolean = true,
            hotSwappable = true,
            active = true
          ),
          RemoteConfigListItem(
            key = "global.groupsv2.maxGroupSize",
            effectiveValue = "151",
            defaultValue = "151",
            rawDefaultValue = "",
            remoteValue = null,
            overrideValue = null,
            isBoolean = false,
            hotSwappable = true,
            active = true
          )
        )
      ),
      onEvent = {}
    )
  }
}
