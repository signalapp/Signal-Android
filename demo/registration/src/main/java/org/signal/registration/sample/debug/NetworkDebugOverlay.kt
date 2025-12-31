/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import kotlin.math.roundToInt

/**
 * Debug overlay that provides a draggable floating button to access network override controls.
 * When tapped, opens a dialog allowing developers to force specific network responses.
 */
@Composable
fun NetworkDebugOverlay(
  modifier: Modifier = Modifier
) {
  var showDialog by remember { mutableStateOf(false) }
  var dragOffset by remember { mutableStateOf(Offset.Zero) }

  Box(modifier = modifier) {
    DebugFloatingButton(
      onClick = { showDialog = true },
      dragOffset = dragOffset,
      onDrag = { delta -> dragOffset += delta },
      modifier = Modifier.align(Alignment.BottomEnd)
    )

    if (showDialog) {
      NetworkDebugDialog(
        onDismiss = { showDialog = false }
      )
    }
  }
}

@Composable
private fun DebugFloatingButton(
  onClick: () -> Unit,
  dragOffset: Offset,
  onDrag: (Offset) -> Unit,
  modifier: Modifier = Modifier
) {
  val overrideSelections by NetworkDebugState.overrideSelections.collectAsState()
  val hasActiveOverrides = overrideSelections.isNotEmpty()

  FilledTonalIconButton(
    onClick = onClick,
    modifier = modifier
      .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
      .pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
          change.consume()
          onDrag(dragAmount)
        }
      }
      .padding(16.dp)
      .size(56.dp),
    shape = RoundedCornerShape(18.dp)
  ) {
    Box {
      Text(
        text = "\uD83D\uDD27", // Wrench emoji
        style = MaterialTheme.typography.titleLarge
      )

      if (hasActiveOverrides) {
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .size(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
        )
      }
    }
  }
}

@Composable
private fun NetworkDebugDialog(
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false
    )
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .padding(16.dp),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp
    ) {
      Column(
        modifier = Modifier.padding(24.dp)
      ) {
        Text(
          text = "Network Debug",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "Force specific responses from NetworkController methods",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(DebugNetworkMockData.allMethods) { methodInfo ->
            MethodOverrideRow(methodInfo = methodInfo)
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          TextButton(
            onClick = { NetworkDebugState.clearAllOverrides() }
          ) {
            Text("Clear All")
          }

          Spacer(modifier = Modifier.width(8.dp))

          TextButton(onClick = onDismiss) {
            Text("Close")
          }
        }
      }
    }
  }
}

@Composable
private fun MethodOverrideRow(
  methodInfo: DebugNetworkMockData.MethodOverrideInfo
) {
  val overrideSelections by NetworkDebugState.overrideSelections.collectAsState()
  val currentSelection = overrideSelections[methodInfo.methodName] ?: "unset"
  val currentOption = methodInfo.options.find { it.name == currentSelection }
    ?: methodInfo.options.first()

  var expanded by remember { mutableStateOf(false) }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
    ) {
      Text(
        text = methodInfo.displayName,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(8.dp))

      Box {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
              if (currentSelection != "unset") {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.surfaceVariant
              }
            )
            .clickable { expanded = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = currentOption.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = if (currentSelection != "unset") {
              MaterialTheme.colorScheme.onPrimaryContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f)
          )

          Text(
            text = "▼",
            color = if (currentSelection != "unset") {
              MaterialTheme.colorScheme.onPrimaryContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            }
          )
        }

        DropdownMenu(
          expanded = expanded,
          onDismissRequest = { expanded = false },
          modifier = Modifier.fillMaxWidth(0.8f)
        ) {
          methodInfo.options.forEach { option ->
            DropdownMenuItem(
              text = {
                Text(
                  text = option.displayName,
                  fontWeight = if (option.name == currentSelection) FontWeight.Bold else FontWeight.Normal
                )
              },
              onClick = {
                if (option.name == "unset") {
                  NetworkDebugState.clearOverride(methodInfo.methodName)
                } else {
                  NetworkDebugState.setOverride(
                    methodName = methodInfo.methodName,
                    optionName = option.name,
                    result = option.createResult()
                  )
                }
                expanded = false
              }
            )
          }
        }
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun NetworkDebugOverlayPreview() {
  Previews.Preview {
    NetworkDebugOverlay(
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@DayNightPreviews
@Composable
private fun NetworkDebugDialogPreview() {
  Previews.Preview {
    NetworkDebugDialog(
      onDismiss = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun MethodOverrideRowPreview() {
  Previews.Preview {
    MethodOverrideRow(
      methodInfo = DebugNetworkMockData.allMethods.first()
    )
  }
}
